package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import lombok.SneakyThrows;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.ll.standard.util.Ut.str.lcfirst;

public class ApplicationContext {
    private String basePackage;
    // beanName → class 타입
    private final Map<String, Class<?>> beanNameToType = new HashMap<>();
    // class 타입 → beanName
    private final Map<Class<?>, String> typeToBeanName = new HashMap<>();

    private static Map<String, Object> beans = new ConcurrentHashMap<>();
    // @Configuration 클래스들의 인스턴스 저장소
    private final List<Object> configObjects = new ArrayList<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(basePackage);

        reflections.getTypesAnnotatedWith(Component.class)
                .stream()
                .filter(c -> !c.isAnnotation())
                .forEach(c -> {
                    String beanName = lcfirst(c.getSimpleName());
                    beanNameToType.put(beanName, c);
                    typeToBeanName.put(c, beanName);
                });

        reflections.getMethodsAnnotatedWith(Bean.class)
                .stream()
                .forEach(m -> {
                    Class<?> returnType = m.getReturnType();
                    typeToBeanName.put(returnType, m.getName());
                });
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        // 이미 생성된 Bean이 있으면 그대로 반환 (싱글톤)
        if (beans.containsKey(beanName)) return (T) beans.get(beanName);

        // @Component 클래스에서 생성하는 경우
        if (beanNameToType.containsKey(beanName)) return (T) createClassBean(beanName);

        // @Bean 메서드에서 생성하는 경우
        for (Object configObj : configObjects) {
            if (typeToBeanName.containsValue(beanName)) return (T) createMethodBean(configObj, beanName);
        }

       return null;
    }

    /**
     * @Component 기반 클래스 Bean 생성
     * - 생성자의 파라미터도 자동으로 DI (재귀 호출)
     */
    @SneakyThrows
    private Object createClassBean(String beanName) {
            Class<?> c = beanNameToType.get(beanName);
            Constructor<?> constructor = c.getDeclaredConstructors()[0]; // 첫 번째 생성자 사용

            Object[] params = Arrays.stream(constructor.getParameterTypes())
                    .map(clazz -> {
                        String paramBeanName = typeToBeanName.getOrDefault(clazz, lcfirst(clazz.getSimpleName()));
                        return genBean(paramBeanName);
                    }).toArray();

            Object bean = constructor.newInstance(params); // 실제 객체 생성
            beans.put(beanName, bean); // 싱글톤 저장
            return bean;
    }

    /**
     * @Bean 메서드 기반 Bean 생성
     * - 메서드 파라미터도 자동으로 DI
     */
    @SneakyThrows
    private Object createMethodBean(Object configObj, String beanName) {
        for (Method method : configObj.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Bean.class)) continue;
            if (!method.getName().equals(beanName)) continue;

            // 메서드 파라미터 타입 확인
            Object[] params = Arrays.stream(method.getParameterTypes())
                    .map(clazz -> {
                        String paramBeanName = typeToBeanName.getOrDefault(clazz, lcfirst(clazz.getSimpleName()));
                        return genBean(paramBeanName);
                    }).toArray();

            Object bean = method.invoke(configObj, params); // 메서드 실행
            beans.put(beanName, bean); // 싱글톤 저장
            return bean;
        }

        throw new RuntimeException("No @Bean method found for bean: " + beanName);
    }
}