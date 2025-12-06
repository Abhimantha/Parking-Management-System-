package com.example.parking.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SessionController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        Map<String, Object> out = new HashMap<>();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            out.put("authenticated", false);
            return out;
        }

        out.put("authenticated", true);
        out.put("username", auth.getName());

        // Spring usually exposes authorities like ROLE_ADMIN / ROLE_MANAGER / ROLE_DRIVER
        List<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        out.put("authorities", authorities);

        // normalize to ADMIN / MANAGER / DRIVER (first role if multiple)
        String role = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse(null);
        out.put("role", role);

        return out;
    }
}
