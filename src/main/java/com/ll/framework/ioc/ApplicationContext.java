package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ApplicationContext {
    private final String basePackage;
    private static final Map<String, Object> BEAN_MAP = new HashMap<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> beanTargetClasses = reflections.getTypesAnnotatedWith(Component.class);

        //컴포넌트 대상 클래스들 == 빈 등록 대상 클래스들
        for (Class<?> clazz : beanTargetClasses) {
            if (!clazz.isAnnotation()) {

                //빈 등록
                Object targetInstance = registerBeanFromClass(clazz);

                //대상 클래스가 설정 클래스
                if (clazz.isAnnotationPresent(Configuration.class)) {
                    Method[] methods = clazz.getMethods();

                    //@Bean이 선언된 메서드의 반환 타입을 빈으로 등록
                    for (Method method : methods) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            registerBeanFromMethod(targetInstance, method);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        return (T) BEAN_MAP.get(beanName.toLowerCase());
    }

    private Object registerBeanFromClass(Class<?> clazz) {
        String beanName = clazz.getSimpleName().toLowerCase();

        if (BEAN_MAP.containsKey(beanName)) {
            return BEAN_MAP.get(beanName);
        }

        try {
            Constructor<?> constructor = clazz.getConstructors()[0];
            Class<?>[] parameterTypes = constructor.getParameterTypes();

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

    private void registerBeanFromMethod(Object targetInstance, Method method) {
        try {
            Class<?>[] parameterTypes = method.getParameterTypes();
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
