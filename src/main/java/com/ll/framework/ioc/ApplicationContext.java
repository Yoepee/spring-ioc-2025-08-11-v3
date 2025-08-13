package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.standard.util.Ut;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApplicationContext {
    private final Reflections reflections;
    private final Map<String, Class<?>> beanDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Object> singletons = new ConcurrentHashMap<>();
    // 필드 추가
    private final Map<String, Method> beanFactoryMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<String>> beansByType = new ConcurrentHashMap<>();

    public ApplicationContext(String basePackage) {
        reflections = new Reflections(basePackage, Scanners.TypesAnnotated, Scanners.MethodsAnnotated, Scanners.SubTypes);
    }

    public void init() {
        // 1) @Component 직붙 클래스 수집
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Component.class);

        // 2) 정의된 bean 이름 수집
        for (Class<?> c : classes) {
            String name = Ut.str.lcfirst(c.getSimpleName());
            beanDefinitions.put(name, c);
        }

        Set<Method> methods = reflections.getMethodsAnnotatedWith(Bean.class);
        System.out.println(methods);
        for (Method m : methods) {
            System.out.println(Arrays.stream(m.getParameters()).toList());
            String beanName = m.getName();
            beanFactoryMethods.put(beanName, m);
        }
    }

    public <T> T genBean(String beanName) { // 요청하신 메서드명 유지
        return (T) singletons.computeIfAbsent(beanName, name -> {
            Class<?> clazz = beanDefinitions.get(name);
            if (clazz != null) return createBean(clazz);
            Method factory = beanFactoryMethods.get(name);
            if (factory != null) return createByFactoryMethod(factory, name);
            throw new NoSuchElementException("No bean named '" + name + "'");
        });
    }

    /** 생성자 주입: 가장 파라미터 많은 public 생성자를 골라 타입으로 의존성 주입 */
    private Object createBean(Class<?> clazz) {
        try {
            Constructor<?>[] ctors = clazz.getConstructors();
            if (ctors.length == 0) {
                // public 생성자가 없으면 기본 생성자를 시도(비공개 포함)
                Constructor<?> c = clazz.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            }

            Constructor<?> target = Arrays.stream(ctors)
                    .max(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow();

            Object[] args = Arrays.stream(target.getParameterTypes())
                    .map(pt -> Ut.str.lcfirst(pt.getSimpleName()))
                    .map(this::genBean)
                    .toArray();

            target.setAccessible(true);
            return target.newInstance(args);
        }  catch (NoSuchMethodException e) {
            throw new RuntimeException("No suitable constructor for " + clazz.getName(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create bean: " + clazz.getName(), e);
        }
    }

    /** @Bean 팩토리 메서드로 생성 (이름 기반 주입) */
    private Object createByFactoryMethod(Method m, String beanName) {
        try {
            Object owner = null;
            if (!Modifier.isStatic(m.getModifiers())) {
                // 인스턴스 메서드면 선언 클래스 인스턴스 필요
                String ownerBeanName = Ut.str.lcfirst(m.getDeclaringClass().getSimpleName());
                // 선언 클래스를 @Component/@Configuration로 등록해두는 것을 권장
                owner = genBean(ownerBeanName);
            }

            Object[] args = Arrays.stream(m.getParameters())
                    .map(pt -> "testBaseJavaTimeModule")
                    .map(this::genBean)
                    .toArray();

            if (!m.canAccess(owner)) m.setAccessible(true);
            Object result = m.invoke(owner, args);
            if (result == null) {
                throw new IllegalStateException("@Bean method returned null: " + m);
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create bean by @Bean method '" + beanName + "'", e);
        }
    }
}