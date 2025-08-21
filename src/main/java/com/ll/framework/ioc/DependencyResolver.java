package com.ll.framework.ioc;

import com.ll.standard.util.Ut;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

@RequiredArgsConstructor
public class DependencyResolver {
    private final BeanRegistry beanRegistry;
    private final ApplicationContext context;

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

                // 사용 가능한 의존성을 기반으로 가장 적합한 생성자 선택
                Constructor<?> constructor = resolveConstructor(beanClass);
                if (constructor == null) {
                    throw new RuntimeException("사용 가능한 생성자를 찾을 수 없습니다: " + beanClass.getName());
                }

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

    private Constructor<?> resolveConstructor(Class<?> clazz) {
        // 모든 public 생성자 가져오기
        Constructor<?>[] constructors = clazz.getConstructors();
        
        // 생성자가 없으면 null 반환
        if (constructors.length == 0) {
            return null;
        }

        // 기본 생성자 확인
        if (constructors.length == 1 && constructors[0].getParameterCount() == 0) {
            return constructors[0];
        }

        // 모든 생성자에 대해 사용 가능한 의존성이 있는지 확인
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            boolean allDependenciesAvailable = true;
            
            for (Class<?> paramType : paramTypes) {
                try {
                    // 의존성 확인 (예외가 발생하지 않으면 사용 가능)
                    findBeanNameByType(paramType);
                } catch (RuntimeException e) {
                    allDependenciesAvailable = false;
                    break;
                }
            }
            
            if (allDependenciesAvailable) {
                return constructor;
            }
        }
        
        // 사용 가능한 생성자가 없으면 null 반환
        return null;
    }
}