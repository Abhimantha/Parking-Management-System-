package com.example.parking.controller;

import com.example.parking.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/users/browse")
public class UserBrowseController {
    private final UserService users;

    public UserBrowseController(UserService users) { this.users = users; }

    @GetMapping
    public String list(Authentication auth, Model model) {
        // rely on your SecurityConfig to protect this path as needed
        model.addAttribute("users", users.findAll());
        return "users-browse";
    }
}
