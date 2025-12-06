// src/main/java/com/example/parking/controller/PageController.java
// NOTE: no /payments or /reservations mappings here (prevents ambiguity)
package com.example.parking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() { return "index"; }

    @GetMapping("/login")
    public String login() { return "login"; }

}
