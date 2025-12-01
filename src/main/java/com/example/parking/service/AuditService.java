package com.example.parking.service;

import com.example.parking.audit.AuditLog;
import com.example.parking.audit.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Insert-only; exceptions swallowed to avoid breaking requests. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, HttpServletRequest req, String message, Integer status) {
        try {
            String username = null;
            String roles = null;

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                username = auth.getName();
                roles = auth.getAuthorities() == null ? null :
                        auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.joining(","));
            } else if (req != null && req.getUserPrincipal() != null) {
                username = req.getUserPrincipal().getName();
            }

            String ip     = req != null ? req.getRemoteAddr() : null;
            String method = req != null ? req.getMethod() : null;
            String path   = req != null ? req.getRequestURI() : null;
            String ua     = req != null ? req.getHeader("User-Agent") : null;

            AuditLog row = AuditLog.builder()
                    .action(action)
                    .username(username)
                    .roles(roles)
                    .ip(ip)
                    .method(method)
                    .path(path)
                    .status(status)
                    .userAgent(ua)
                    .message(message)
                    .build();

            repo.save(row);
        } catch (Exception e) {
            logger.warn("Audit log write skipped: {}", e.getMessage());
        }
    }

    public Page<AuditLog> search(Instant from, Instant to, String user, String action, Integer status, int page, int size) {
        return repo.search(from, to, user, action, status, PageRequest.of(page, size));
    }

    /** System-managed retention */
    @Transactional
    public int purgeOlderThanDays(long days) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        return repo.deleteOlderThan(cutoff);
    }
}
