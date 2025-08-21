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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ll.standard.util.Ut.str.lcfirst;

public class ApplicationContext {
    private String basePackage;
    // beanName → class 타입
    private final Map<String, Class<?>> beanNameToType = new HashMap<>();
    // beanName → Method 타입
    private final Map<String, Method> beanNameToMethod = new HashMap<>();
    // class 타입 → beanName
    private final Map<Class<?>, String> typeToBeanName = new HashMap<>();

    private static Map<String, Object> beans = new ConcurrentHashMap<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated, Scanners.MethodsAnnotated);

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
                    String beanName = lcfirst(m.getName());
                    beanNameToType.put(beanName, m.getReturnType());
                    beanNameToMethod.put(beanName, m);
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
        if (beanNameToMethod.containsKey(beanName)) return (T) createMethodBean(beanName);

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

    @SneakyThrows
    private Object createMethodBean(String beanName) {
        Method method = beanNameToMethod.get(beanName);
        Object target = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            Class<?> decl = method.getDeclaringClass();
            target = genBean(lcfirst(decl.getSimpleName())); // ← 반드시 생성 보장
        }

        Object[] params = Arrays.stream(method.getParameterTypes())
                .map(pt -> genBean(lcfirst(pt.getSimpleName())))  // ← 타입 기반 주입
                .toArray();

        Object bean = method.invoke(target, params); // 메서드 실행
        beans.put(beanName, bean); // 싱글톤 저장
        return bean;
    }
}