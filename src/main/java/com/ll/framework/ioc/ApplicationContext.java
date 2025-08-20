package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.ll.standard.util.Ut.str.lcfirst;

public class ApplicationContext {

    private final String basePackage;
    private final Map<String, Class<?>> beanNameToType = new HashMap<>();
    private final Map<Class<?>, String> typeToBeanName = new HashMap<>();
    private final Map<String, Object> beans = new HashMap<>();
    private Object configObject;


    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Component.class);
        annotatedClasses.removeIf(Class::isAnnotation);

        //클래스 타입
        for (Class<?> c : annotatedClasses) {
            if(c.isAnnotationPresent(Configuration.class)) {
                try {
                    configObject = c.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            String beanName = lcfirst(c.getSimpleName());
            beanNameToType.put(beanName, c);
            typeToBeanName.put(c, beanName);
        }

        //메서드 타입
        if(configObject != null) {
            for (Method method : configObject.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class)) continue;

                Class<?> returnType = method.getReturnType();
                String beanName = method.getName();
                typeToBeanName.put(returnType, beanName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        if (beans.containsKey(beanName)) {
            return (T) beans.get(beanName);
        }

        // 클래스 타입
        if (beanNameToType.containsKey(beanName)) {
            return (T) createClassBean(beanName);
        }

        // 메서드 타입
        if (configObject != null && typeToBeanName.containsValue(beanName)) {
            return (T) createMethodBean(beanName);
        }

        return null;
    }

    private Object createClassBean(String beanName) {
        try {
            Class<?> c = beanNameToType.get(beanName);
            Constructor<?> constructor = c.getDeclaredConstructors()[0];

            Class<?>[] paramTypes = constructor.getParameterTypes();
            Object[] params = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                String paramBeanName = typeToBeanName.get(paramTypes[i]);
                params[i] = genBean(paramBeanName);
            }

            Object bean = constructor.newInstance(params);
            beans.put(beanName, bean);
            return bean;
        } catch (Exception e) {
            return null;
        }
    }

    private Object createMethodBean(String beanName) {
        try {
            for (Method method : configObject.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class)) continue;
                if (!method.getName().equals(beanName)) continue;

                Class<?>[] paramTypes = method.getParameterTypes();
                Object[] params = new Object[paramTypes.length];

                for (int i = 0; i < paramTypes.length; i++) {
                    String paramBeanName = typeToBeanName.get(paramTypes[i]);
                    params[i] = genBean(paramBeanName);
                }

                Object bean = method.invoke(configObject, params);
                beans.put(beanName, bean);
                return bean;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

