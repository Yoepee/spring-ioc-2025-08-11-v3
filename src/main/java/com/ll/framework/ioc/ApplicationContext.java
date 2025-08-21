package com.ll.framework.ioc;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApplicationContext {
    private final String basePackage;
    private final BeanRegistry beanRegistry = new BeanRegistry();
    private final BeanScanner beanScanner = new BeanScanner(basePackage, beanRegistry);
    private final DependencyResolver dependencyResolver = new DependencyResolver(beanRegistry, this);

    public void init() {
        beanScanner.scan();
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        if (beanRegistry.containsBean(beanName)) {
            return (T) beanRegistry.getBean(beanName);
        }

        return (T) dependencyResolver.createBean(beanName);
    }
}