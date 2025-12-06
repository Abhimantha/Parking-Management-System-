package com.example.parking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DealsController {
    @GetMapping("/deals")
    public String deals(Model model) {
        model.addAttribute("title", "Deals");
        return "deals";
    }
}
