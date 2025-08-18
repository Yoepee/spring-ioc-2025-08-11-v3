package com.ll.framework.ioc;

public class ApplicationContext {
    private final BeanRegistry beanRegistry;
    private final BeanScanner beanScanner;
    private final DependencyResolver dependencyResolver;

    public ApplicationContext(String basePackage) {
        this.beanRegistry = new BeanRegistry();
        this.beanScanner = new BeanScanner(basePackage, beanRegistry);
        this.dependencyResolver = new DependencyResolver(beanRegistry, this);  // this로 재귀 전달
    }

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