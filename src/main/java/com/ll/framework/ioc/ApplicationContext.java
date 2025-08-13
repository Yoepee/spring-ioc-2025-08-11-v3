package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.reflections.util.ReflectionUtilsPredicates.withAnnotation;
import static org.reflections.util.ReflectionUtilsPredicates.withPublic;

public class ApplicationContext {
    private final String basePackage;
    private static final Map<String, Object> BEAN_MAP = new HashMap<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(basePackage);

        //컴포넌트 대상 클래스
        reflections.getTypesAnnotatedWith(Component.class)
                   .stream()

                   //@Component가 선언되었지만 @Service, @Repository 등의 어노테이션이 아닌 클래스만 필터링
                   .filter(clazz -> !clazz.isAnnotation())

                   //클래스를 빈으로 등록
                   .map(clazz -> {
                       Object instance = registerBeanFromClass(clazz);
                       return instance.getClass();
                   })

                   //빈 등록된 클래스가 @Configuration이 선언된 설정 클래스인지 필터링
                   .filter(clazz -> clazz.isAnnotationPresent(Configuration.class))

                   //각 설정 클래스의 메서드들을 실행시켜 빈으로 등록해야 함
                   //메서드 배열로 나오기 때문에 flatMap으로 평탄화
                   .flatMap(configClass -> Arrays.stream(configClass.getMethods()))

                   //@Bean이 선언된 메서드 필터링
                   .filter(method -> withAnnotation(Bean.class).test(method))

                   //public 메서드 필터링
                   .filter(method -> withPublic().test(method))

                   //각 메서드의 반환 값을 빈으로 등록
                   .forEach(method -> {
                       Object config = registerBeanFromClass(method.getDeclaringClass());
                       registerBeanFromMethod(config, method);
                   });
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        return (T) BEAN_MAP.get(beanName.toLowerCase());
    }

    /**
     * 클래스 정보로 빈 등록
     * @param clazz 빈 등록 대상 클래스 정보
     * @return 빈 등록된 클래스 인스턴스
     */
    private Object registerBeanFromClass(Class<?> clazz) {
        String beanName = clazz.getSimpleName().toLowerCase();

        //싱글톤으로 등록되어야 하기 때문에 빈 저장소에 존재하면 즉시 반환
        if (BEAN_MAP.containsKey(beanName)) {
            return BEAN_MAP.get(beanName);
        }

        try {
            //클래스의 생성자 정보
            Constructor<?> constructor = clazz.getConstructors()[0];

            //생성자의 파라미터 타입 정보
            Class<?>[] parameterTypes = constructor.getParameterTypes();

            //생성자의 전달될 매개변수 목록
            Object[] args = new Object[parameterTypes.length];

            //생성자의 파라미터를 모두 빈으로 등록
            for (int i = 0; i < args.length; i++) {
                args[i] = registerBeanFromClass(parameterTypes[i]); //재귀 호출
            }

            //생성자의 반환 값을 빈으로 등록
            Object instance = constructor.newInstance(args);
            BEAN_MAP.put(beanName, instance);

            return instance;

        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }

    /**
     * 메서드 정보로 빈 등록
     * @param targetInstance 리플렉션으로 메서드를 실행시킬 때 필요한 타겟 인스턴스
     * @param method 반환 값이 빈 등록 대상인 메서드 정보
     */
    private void registerBeanFromMethod(Object targetInstance, Method method) {
        try {
            //메서드의 파라미터 타입 정보
            Class<?>[] parameterTypes = method.getParameterTypes();

            //메서드의 전달될 매개변수 목록
            Object[] args = new Object[parameterTypes.length];

            //메서드의 파라미터를 모두 빈으로 등록
            for (int i = 0; i < args.length; i++) {
                args[i] = registerBeanFromClass(parameterTypes[i]);
            }

            //메서드의 반환 값을 빈으로 등록
            Object instance = method.invoke(targetInstance, args);
            String beanName = method.getName().toLowerCase();

            BEAN_MAP.put(beanName, instance);

        } catch (Exception e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }
}
