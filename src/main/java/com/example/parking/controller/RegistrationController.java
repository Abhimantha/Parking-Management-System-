// src/main/java/com/example/parking/controller/RegistrationController.java
package com.example.parking.controller;

import com.example.parking.entity.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegistrationController {

    /** Serve the registration form (GET /register). */
    @GetMapping("/register")
    public String showRegister(Model model) {
        // Optional: provide an empty user so your template can prefill safely
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new User());
        }
        return "register";
    }

    // IMPORTANT: No @PostMapping here.
    // POST /register is handled by AuthController#doRegister(...)
}
