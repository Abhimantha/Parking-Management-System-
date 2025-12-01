package com.example.parking.controller;

import com.example.parking.audit.AuditLog;
import com.example.parking.audit.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping
public class AuditLogController {

    private final AuditLogRepository repo;

    public AuditLogController(AuditLogRepository repo) {
        this.repo = repo;
    }

    // Friendly redirect so /logs works
    @GetMapping("/logs")
    public String redirectLogs() {
        return "redirect:/audit/logs";
    }

    // Page
    @PreAuthorize("hasAnyRole('ADMIN','IT')")
    @GetMapping("/audit/logs")
    public String page() {
        return "audit-logs"; // templates/audit-logs.html
    }

    // JSON API with lightweight filters (top N recent)
    @PreAuthorize("hasAnyRole('ADMIN','IT')")
    @GetMapping("/api/audit/logs")
    @ResponseBody
    public List<AuditLog> list(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String pathLike,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end
    ) {
        int cap = Math.min(Math.max(limit, 1), 1000);
        Pageable p = PageRequest.of(0, cap, Sort.by(Sort.Direction.DESC, "ts"));

        // Page from DB (index on ts recommended). Filter in memory to avoid changing your repo.
        List<AuditLog> rows = repo.findAll(p).getContent();

        return rows.stream().filter(a -> {
            if (action != null && !action.isBlank()) {
                if (a.getAction() == null || !a.getAction().toLowerCase(Locale.ROOT)
                        .contains(action.toLowerCase(Locale.ROOT))) return false;
            }
            if (username != null && !username.isBlank()) {
                if (a.getUsername() == null || !a.getUsername().toLowerCase(Locale.ROOT)
                        .contains(username.toLowerCase(Locale.ROOT))) return false;
            }
            if (status != null && (a.getStatus() == null || !status.equals(a.getStatus()))) return false;
            if (pathLike != null && !pathLike.isBlank()) {
                if (a.getPath() == null || !a.getPath().toLowerCase(Locale.ROOT)
                        .contains(pathLike.toLowerCase(Locale.ROOT))) return false;
            }
            if (start != null && (a.getTs() == null || a.getTs().isBefore(start))) return false;
            if (end != null && (a.getTs() == null || a.getTs().isAfter(end))) return false;
            return true;
        }).toList();
    }
}
