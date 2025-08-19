package com.ll.framework.ioc;

import com.ll.standard.util.Ut;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

public class DependencyResolver {
    private final BeanRegistry beanRegistry;
    private final ApplicationContext context;

    public DependencyResolver(BeanRegistry beanRegistry, ApplicationContext context) {
        this.beanRegistry = beanRegistry;
        this.context = context;
    }

    public Object createBean(String beanName) {
        if (!beanRegistry.containsBeanDefinition(beanName)) {
            throw new RuntimeException("빈 정의를 찾을 수 없습니다: " + beanName);
        }

        try {
            Object bean;
            Method factoryMethod = beanRegistry.getFactoryMethod(beanName);

            if (factoryMethod != null) {
                // 팩토리 메서드를 통한 빈 생성
                Class<?> configClass = beanRegistry.getConfigClass(beanName);
                String configBeanName = Ut.str.lcfirst(configClass.getSimpleName());
                Object configInstance = context.genBean(configBeanName);

                // 파라미터 의존성 주입
                Object[] args = Arrays.stream(factoryMethod.getParameterTypes())
                        .map(this::findBeanNameByType)
                        .map(context::genBean)
                        .toArray();

                bean = factoryMethod.invoke(configInstance, args);
            } else {
                // 일반 클래스 생성자를 통한 빈 생성
                Class<?> beanClass = beanRegistry.getBeanClass(beanName);
                if (beanClass == null) {
                    throw new RuntimeException("빈 클래스를 찾을 수 없습니다: " + beanName);
                }

                // 가장 많은 파라미터를 가진 생성자 선택
                Constructor<?> constructor = Arrays.stream(beanClass.getConstructors())
                        .max(Comparator.comparingInt(Constructor::getParameterCount))
                        .orElseThrow(() -> new RuntimeException("생성자를 찾을 수 없습니다 " + beanClass.getName()));

                // 파라미터 의존성 주입
                Object[] args = Arrays.stream(constructor.getParameterTypes())
                        .map(this::findBeanNameByType)
                        .map(context::genBean)
                        .toArray();

                bean = constructor.newInstance(args);
            }

            beanRegistry.registerBean(beanName, bean);
            return bean;
        } catch (Exception e) {
            throw new RuntimeException("빈 생성에 실패했습니다: " + beanName, e);
        }
    }

    private String findBeanNameByType(Class<?> requiredType) {
        Set<String> matchingNames = beanRegistry.getBeanNamesForType(requiredType);
        if (matchingNames.isEmpty()) {
            throw new RuntimeException("해당 타입의 빈을 찾을 수 없습니다: " + requiredType.getName());
        }
        if (matchingNames.size() > 1) {
            throw new RuntimeException("해당 타입의 빈이 여러 개 발견되었습니다: " + requiredType.getName() + ": " + matchingNames);
        }
        return matchingNames.stream().findFirst().orElse(null);
    }
}