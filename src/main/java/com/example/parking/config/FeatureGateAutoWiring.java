package com.example.parking.config;

import com.example.parking.service.FeatureGate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;

/**
 * Safety-net: make sure any controller/service that declared a FeatureGate field
 * (e.g., "private FeatureGate gate;") actually gets a bean injected even if the
 * original code forgot constructor/autowired injection.
 *
 * This DOES NOT replace proper DI, but prevents NPEs like:
 *   "Cannot invoke ... ensureCancellationAllowed ... because this.gate is null"
 */
@Configuration
public class FeatureGateAutoWiring {

    @Bean
    public BeanPostProcessor featureGateFieldInjector(FeatureGate gate) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                injectIfNeeded(bean, gate);
                return bean;
            }
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                injectIfNeeded(bean, gate);
                return bean;
            }
        };
    }

    private static void injectIfNeeded(Object bean, FeatureGate gate) {
        Class<?> c = bean.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == FeatureGate.class) {
                    try {
                        f.setAccessible(true);
                        if (f.get(bean) == null) f.set(bean, gate);
                    } catch (Throwable ignored) {}
                }
            }
            c = c.getSuperclass();
        }
    }
}
