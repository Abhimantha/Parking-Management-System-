package com.example.parking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ViewRoutes implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // These were 500ing with "No static resource ..." -> map them to Thymeleaf views
        registry.addViewController("/slots").setViewName("slots");
        registry.addViewController("/users").setViewName("users");
        // If you later add real controllers for these paths, remove these lines.
    }
}
