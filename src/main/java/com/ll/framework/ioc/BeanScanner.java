package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import com.ll.standard.util.Ut;
import org.reflections.Reflections;

import java.util.Arrays;

public class BeanScanner {
    private final String basePackage;
    private final BeanRegistry beanRegistry;

    public BeanScanner(String basePackage, BeanRegistry beanRegistry) {
        this.basePackage = basePackage;
        this.beanRegistry = beanRegistry;
    }

    public void scan() {
        Reflections reflections = new Reflections(basePackage);

        reflections.getTypesAnnotatedWith(Component.class).stream()
                .filter(clazz -> !clazz.isInterface() && !clazz.isAnnotation())
                .forEach(clazz -> {
                    // 일반 컴포넌트 클래스 등록
                    beanRegistry.registerBeanClass(Ut.str.lcfirst(clazz.getSimpleName()), clazz);

                    // @Configuration 클래스인 경우 @Bean 메서드도 등록
                    if (clazz.isAnnotationPresent(Configuration.class)) {
                        Arrays.stream(clazz.getDeclaredMethods())
                                .filter(method -> method.isAnnotationPresent(Bean.class))
                                .forEach(method ->
                                        beanRegistry.registerFactoryMethod(method.getName(), clazz, method)
                                );
                    }
                });
    }
}