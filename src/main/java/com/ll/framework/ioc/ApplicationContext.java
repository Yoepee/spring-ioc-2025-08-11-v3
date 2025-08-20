package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static com.ll.standard.util.Ut.str.lcfirst;

public class ApplicationContext {

    private final String basePackage; // 스캔할 기본 패키지

    // beanName → class 타입
    private final Map<String, Class<?>> beanNameToType = new HashMap<>();

    // class 타입 → beanName
    private final Map<Class<?>, String> typeToBeanName = new HashMap<>();

    // beanName → 실제 생성된 객체 (싱글톤 저장소)
    private final Map<String, Object> beans = new HashMap<>();

    // @Configuration 클래스들의 인스턴스 저장소
    private final List<Object> configObjects = new ArrayList<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * IoC 컨테이너 초기화
     * - basePackage 안에서 @Component, @Configuration 붙은 클래스 찾기
     * - 클래스 타입과 beanName 매핑 등록
     * - @Bean 메서드 반환 타입도 beanName 매핑 등록
     */
    public void init() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Component.class);
        annotatedClasses.removeIf(Class::isAnnotation); // @Component 인터페이스 제외

        // 스캔된 클래스 처리
        for (Class<?> c : annotatedClasses) {
            // @Configuration 클래스라면
            if (c.isAnnotationPresent(Configuration.class)) {
                try {
                    // 설정 클래스 인스턴스 생성
                    Object configInstance = c.getDeclaredConstructor().newInstance();
                    configObjects.add(configInstance);

                    // 설정 클래스 안의 @Bean 메서드 확인
                    for (Method method : c.getDeclaredMethods()) {
                        if (!method.isAnnotationPresent(Bean.class)) continue;
                        Class<?> returnType = method.getReturnType();
                        String beanName = method.getName(); // 메서드 이름이 곧 beanName
                        typeToBeanName.put(returnType, beanName);
                    }

                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate config class: " + c.getName(), e);
                }
                continue;
            }

            // 일반 @Component 클래스라면
            String beanName = lcfirst(c.getSimpleName()); // 클래스명 → 소문자 시작
            beanNameToType.put(beanName, c);
            typeToBeanName.put(c, beanName);
        }
    }

    /**
     * beanName 으로 Bean을 생성하거나 가져오기
     */
    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        // 이미 생성된 Bean이 있으면 그대로 반환 (싱글톤)
        if (beans.containsKey(beanName)) {
            return (T) beans.get(beanName);
        }

        // @Component 클래스에서 생성하는 경우
        if (beanNameToType.containsKey(beanName)) {
            return (T) createClassBean(beanName);
        }

        // @Bean 메서드에서 생성하는 경우
        for (Object configObj : configObjects) {
            if (typeToBeanName.containsValue(beanName)) {
                return (T) createMethodBean(configObj, beanName);
            }
        }

        throw new RuntimeException("No bean found with name: " + beanName);
    }

    /**
     * @Component 기반 클래스 Bean 생성
     * - 생성자의 파라미터도 자동으로 DI (재귀 호출)
     */
    private Object createClassBean(String beanName) {
        try {
            Class<?> c = beanNameToType.get(beanName);
            Constructor<?> constructor = c.getDeclaredConstructors()[0]; // 첫 번째 생성자 사용

            Class<?>[] paramTypes = constructor.getParameterTypes();
            Object[] params = new Object[paramTypes.length];

            // 생성자 파라미터 주입
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> paramType = paramTypes[i];
                // 타입 기반으로 beanName 찾기, 없으면 단순 lcfirst() 적용
                String paramBeanName = typeToBeanName.getOrDefault(paramType, lcfirst(paramType.getSimpleName()));
                params[i] = genBean(paramBeanName); // 재귀 호출
            }

            Object bean = constructor.newInstance(params); // 실제 객체 생성
            beans.put(beanName, bean); // 싱글톤 저장
            return bean;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create class bean: " + beanName, e);
        }
    }

    /**
     * @Bean 메서드 기반 Bean 생성
     * - 메서드 파라미터도 자동으로 DI
     */
    private Object createMethodBean(Object configObj, String beanName) {
        try {
            for (Method method : configObj.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class)) continue;
                if (!method.getName().equals(beanName)) continue;

                // 메서드 파라미터 타입 확인
                Class<?>[] paramTypes = method.getParameterTypes();
                Object[] params = new Object[paramTypes.length];

                // 파라미터 주입
                for (int i = 0; i < paramTypes.length; i++) {
                    Class<?> paramType = paramTypes[i];
                    String paramBeanName = typeToBeanName.getOrDefault(paramType, lcfirst(paramType.getSimpleName()));
                    params[i] = genBean(paramBeanName); // 재귀 호출
                }

                Object bean = method.invoke(configObj, params); // 메서드 실행
                beans.put(beanName, bean); // 싱글톤 저장
                return bean;
            }
            throw new RuntimeException("No @Bean method found for bean: " + beanName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create method bean: " + beanName, e);
        }
    }
}
