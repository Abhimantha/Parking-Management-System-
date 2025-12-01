package com.example.parking.config;

import com.example.parking.web.ReportsAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ReportsAccessInterceptor reports;

    public WebConfig(ReportsAccessInterceptor reports) { this.reports = reports; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(reports).addPathPatterns("/reports/**");
    }
}
