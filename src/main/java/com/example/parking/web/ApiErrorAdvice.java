package com.example.parking.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@ControllerAdvice
public class ApiErrorAdvice {

    private boolean wantsJson(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        String uri = req.getRequestURI();
        return (accept != null && accept.contains("application/json")) || (uri != null && uri.startsWith("/api/"));
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public Object handleFeatureDisabled(FeatureDisabledException ex, HttpServletRequest req, Model model) {
        if (wantsJson(req)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", 403, "message", ex.getMessage()));
        }
        // For MVC pages, show a compact error screen
        model.addAttribute("err", ex.getMessage());
        // Reuse a lightweight generic error view if you have one; otherwise inline simple fragment:
        return "error/feature"; // create templates/error/feature.html or change to your generic error page
    }
}
