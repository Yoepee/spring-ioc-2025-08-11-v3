package com.ll.framework.ioc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class BeanRegistry {
    private final Map<String, Class<?>> beanClasses = new HashMap<>();
    private final Map<String, Method> factoryMethods = new HashMap<>();
    private final Map<String, Class<?>> configClasses = new HashMap<>();
    private final Map<String, Object> beans = new HashMap<>();

    public void registerBeanClass(String beanName, Class<?> beanClass) {
        beanClasses.put(beanName, beanClass);
    }

    public void registerFactoryMethod(String beanName, Class<?> configClass, Method factoryMethod) {
        factoryMethods.put(beanName, factoryMethod);
        configClasses.put(beanName, configClass);
    }

    public Set<String> getBeanNamesForType(Class<?> type) {
        Set<String> result = new HashSet<>();

        // 일반 빈 클래스에서 검색
        result.addAll(beanClasses.entrySet().stream()
                .filter(entry -> type.isAssignableFrom(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()));

        // 팩토리 메서드 반환 타입에서 검색
        result.addAll(factoryMethods.entrySet().stream()
                .filter(entry -> type.isAssignableFrom(entry.getValue().getReturnType()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()));

        return result;
    }

    public void registerBean(String beanName, Object bean) {
        beans.put(beanName, bean);
    }

    public Object getBean(String beanName) {
        return beans.get(beanName);
    }

    public boolean containsBean(String beanName) {
        return beans.containsKey(beanName);
    }

    public boolean containsBeanDefinition(String beanName) {
        return beanClasses.containsKey(beanName) || factoryMethods.containsKey(beanName);
    }

    public Class<?> getBeanClass(String beanName) {
        return beanClasses.get(beanName);
    }

    public Method getFactoryMethod(String beanName) {
        return factoryMethods.get(beanName);
    }

    public Class<?> getConfigClass(String beanName) {
        return configClasses.get(beanName);
    }
}