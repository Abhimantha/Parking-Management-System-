// src/main/java/com/example/parking/controller/SettingsController.java
package com.example.parking.controller;

import com.example.parking.entity.SystemSettings;
import com.example.parking.service.SystemSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SystemSettingsService settings;

    public SettingsController(SystemSettingsService settings) { this.settings = settings; }

    @GetMapping
    public String page(Model model, @RequestParam(required = false) String msg) {
        model.addAttribute("settings", settings.get());
        model.addAttribute("msg", msg);
        return "settings";
    }

    @PostMapping
    public String update(@ModelAttribute SystemSettings s) {
        settings.update(s);
        return "redirect:/settings?msg=Updated";
    }
}
