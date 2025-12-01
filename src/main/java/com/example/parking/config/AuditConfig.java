package com.example.parking.config;

import com.example.parking.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuditConfig implements WebMvcConfigurer {

    private final AuditService audit;

    public AuditConfig(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public void afterCompletion(HttpServletRequest request,
                                                HttpServletResponse response,
                                                Object handler,
                                                Exception ex) throws Exception {
                        // Log every completed HTTP request; AuditService swallows errors
                        Integer status = (response != null) ? response.getStatus() : null;
                        String msg = (ex != null)
                                ? (ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : ""))
                                : null;
                        audit.log("HTTP_REQUEST", request, msg, status);
                    }
                })
                // Reduce audit noise: skip static assets and webjars
                .excludePathPatterns(
                        "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico"
                );
    }

    /* ========================= Security events ========================= */

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        audit.log("LOGIN_SUCCESS", currentRequest(), null, 200);
    }

    @EventListener
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        String msg = event.getException() != null ? event.getException().getMessage() : "Authentication failed";
        audit.log("LOGIN_FAILURE", currentRequest(), msg, 401);
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        audit.log("LOGOUT_SUCCESS", currentRequest(), null, 200);
    }

    /* ========================= Helpers ========================= */

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
