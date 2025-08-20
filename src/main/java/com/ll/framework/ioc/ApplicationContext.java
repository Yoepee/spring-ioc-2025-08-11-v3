package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import lombok.SneakyThrows;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ll.standard.util.Ut.str.lcfirst;

public class ApplicationContext {
    private String basePackage;
    private static Map<String, Object> singletonObjects = new ConcurrentHashMap<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated, Scanners.MethodsAnnotated);
        reflections.getTypesAnnotatedWith(Component.class)
                .stream()
                .filter(c -> !c.isAnnotation())
                .forEach(c -> getBean(lcfirst(c.getSimpleName()), c));
        reflections.getMethodsAnnotatedWith(Bean.class)
                .stream()
                .forEach(m -> getBean(lcfirst(m.getName()), m));
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        return (T) singletonObjects.get(beanName);
    }

    @SuppressWarnings("unchecked")
    private <T> T getBean(String beanName, Class<T> cls) {
        return (T) singletonObjects.computeIfAbsent(beanName, k -> construct(cls));
    }
    @SuppressWarnings("unchecked")
    private <T> T getBean(String beanName, Method method) {
        return (T) singletonObjects.computeIfAbsent(beanName, k -> construct(method));
    }

    @SneakyThrows
    private <T> T construct(Class<T> cls) {
        Constructor<T> constructor = (Constructor<T>) cls.getDeclaredConstructors()[0];
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(dep -> getBean(lcfirst(dep.getSimpleName()), dep))
                .toArray();

        return constructor.newInstance(args);
    }

    @SneakyThrows
    private <T> T construct(Method method) {
        Object target = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            Class<?> decl = method.getDeclaringClass();
            target = getBean(lcfirst(decl.getSimpleName()), decl); // ← 반드시 생성 보장
        }

        Object[] args = Arrays.stream(method.getParameterTypes())
                .map(pt -> getBean(lcfirst(pt.getSimpleName()), pt))  // ← 타입 기반 주입
                .toArray();

        return (T) method.invoke(target, args);
    }
}