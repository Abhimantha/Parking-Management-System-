package com.example.parking.config;

import com.example.parking.service.FeatureGate;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

@Component
public class FeatureGateInjector implements BeanPostProcessor {
    private final FeatureGate gate;

    public FeatureGateInjector(FeatureGate gate) { this.gate = gate; }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // If a bean has a field named "gate" of type FeatureGate but wasn't wired, wire it.
        Field f = ReflectionUtils.findField(bean.getClass(), "gate", FeatureGate.class);
        if (f != null) {
            ReflectionUtils.makeAccessible(f);
            Object current = ReflectionUtils.getField(f, bean);
            if (current == null) {
                ReflectionUtils.setField(f, bean, gate);
            }
        }
        return bean;
    }
}
