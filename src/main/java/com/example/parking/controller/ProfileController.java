package com.example.parking.controller;

import com.example.parking.entity.User;
import com.example.parking.repository.UserRepository;
import com.example.parking.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {
    private final UserRepository users;
    private final UserService userService;

    public ProfileController(UserRepository users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    @GetMapping
    public String view(Authentication auth,
                       @RequestParam(required = false) String msg,
                       @RequestParam(required = false) String err,
                       Model model) {
        if (auth == null) {
            model.addAttribute("err", "Not authenticated");
            return "profile";
        }
        User me = users.findByEmail(auth.getName()).orElse(null);
        if (me == null) {
            model.addAttribute("err", "User not found");
            return "profile";
        }
        model.addAttribute("me", me);
        if (msg != null) model.addAttribute("msg", msg);
        if (err != null) model.addAttribute("err", err);
        return "profile";
    }

    /** Update your own info; password optional. */
    @PostMapping("/update")
    public String update(Authentication auth,
                         @RequestParam(required = false) String username,
                         @RequestParam(required = false) String phone,
                         @RequestParam(required = false) String carModel,
                         @RequestParam(required = false) String carPlate,
                         @RequestParam(required = false) String nicNumber,
                         @RequestParam(required = false) String newPassword,
                         RedirectAttributes ra) {
        if (auth == null) {
            ra.addFlashAttribute("err", "Not authenticated");
            return "redirect:/profile";
        }
        User me = users.findByEmail(auth.getName()).orElse(null);
        if (me == null) {
            ra.addFlashAttribute("err", "User not found");
            return "redirect:/profile";
        }

        User patch = new User();
        patch.setUsername((username == null || username.isBlank()) ? me.getUsername() : username);
        if (phone != null)     patch.setPhone(phone);
        if (carModel != null)  patch.setCarModel(carModel);
        if (carPlate != null)  patch.setCarPlate(carPlate);
        if (nicNumber != null) patch.setNicNumber(nicNumber);
        if (newPassword != null && !newPassword.isBlank()) {
            patch.setPassword(newPassword);
        }
        patch.setEmail(me.getEmail()); // keep email
        patch.setRole(me.getRole());   // keep role

        userService.update(me.getId(), patch);
        ra.addFlashAttribute("msg", "Profile updated");
        return "redirect:/profile";
    }
}
