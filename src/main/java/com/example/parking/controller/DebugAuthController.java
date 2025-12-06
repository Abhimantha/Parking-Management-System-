// src/main/java/com/example/parking/controller/DebugAuthController.java
package com.example.parking.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// /parking/force-logout — clears current session + auth and redirects to /login
@Controller
public class DebugAuthController {
    @GetMapping("/force-logout")
    public String forceLogout(HttpServletRequest req, HttpServletResponse res, Authentication auth) {
        if (auth != null) new SecurityContextLogoutHandler().logout(req, res, auth);
        return "redirect:/login?logout=true";
    }
}
