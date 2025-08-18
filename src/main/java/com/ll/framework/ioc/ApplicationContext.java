package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.ll.standard.util.Ut.str.lcfirst;

public class ApplicationContext {

    private final String basePackage;
    private final Map<String, Class<?>> beanTypes = new HashMap<>();
    private final Map<String, Object> beans = new HashMap<>();


    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Bean.class);
        annotatedClasses.removeIf(Class::isAnnotation);

        for (Class<?> c : annotatedClasses) {
            String beanName = lcfirst(c.getSimpleName());
//            System.out.println(beanName);
            beanTypes.put(beanName, c);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        if(beans.containsKey(beanName)) {
            return (T) beans.get(beanName);
        }

        Class<?> c = beanTypes.get(beanName);
        if(c == null) return null;
        try {
            Constructor<?> constructor = c.getDeclaredConstructors()[0];
            Class<?>[] paramTypes = constructor.getParameterTypes();

            Object[] params = new Object[paramTypes.length];

            for(int i=0; i<paramTypes.length; i++) {
                String paramBeanName = lcfirst(paramTypes[i].getSimpleName());
                params[i] = genBean(paramBeanName);
            }

            Object bean = constructor.newInstance(params);
            beans.put(beanName, bean);
            return (T) bean;

        } catch (Exception e) {
            return null;
        }
    }
}

