// src/main/java/com/example/parking/controller/AuthController.java
package com.example.parking.controller;

import com.example.parking.entity.User;
import com.example.parking.entity.enums.Role;
import com.example.parking.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public String doRegister(@RequestParam @NotBlank String username,
                             @RequestParam @Email String email,
                             @RequestParam @NotBlank String password,
                             @RequestParam(defaultValue = "DRIVER") String role,
                             Model model) {
        try {
            User u = new User();
            u.setUsername(username);
            u.setEmail(email);
            u.setPassword(password); // UserService will encode if needed
            try {
                u.setRole(Role.valueOf(role));
            } catch (Exception e) {
                u.setRole(Role.DRIVER);
            }
            userService.create(u);   // <--- use service (encodes + validations)
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "register";
        }
    }
}
