package com.example.parking.web;

import com.example.parking.service.FeatureGate;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReportsAccessInterceptor implements HandlerInterceptor {
    private final FeatureGate gate;

    public ReportsAccessInterceptor(FeatureGate gate) { this.gate = gate; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!path.startsWith("/reports")) return true;

        // Only restrict Drivers; Admins/Managers always pass.
        Authentication auth = (Authentication) request.getUserPrincipal();
        if (auth == null) return true;

        Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean isDriver = roles.contains("ROLE_DRIVER") || roles.contains("DRIVER");
        if (isDriver && !gate.driversCanSeeReports()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain");
            response.getWriter().write("Reports are hidden for drivers.");
            return false;
        }
        return true;
    }
}
