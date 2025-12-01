package com.example.parking.config;

import com.example.parking.entity.User;
import com.example.parking.entity.enums.Role;
import com.example.parking.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminSeeder {

    @Bean
    CommandLineRunner initAdmin(UserRepository repo) {
        return args -> {
            final String email = "admin@local";
            final String expectedPassword = "admin"; // plain text per your choice

            User admin = repo.findByEmail(email).orElse(null);
            if (admin == null) {
                admin = new User();
                admin.setUsername("Administrator");
                admin.setEmail(email);
                admin.setPassword(expectedPassword);
                admin.setRole(Role.ADMIN);
                repo.save(admin);
                System.out.println("Seeded admin: " + email + " / " + expectedPassword);
            } else {
                boolean changed = false;
                if (!expectedPassword.equals(admin.getPassword())) {
                    admin.setPassword(expectedPassword);
                    changed = true;
                }
                if (admin.getRole() != Role.ADMIN) {
                    admin.setRole(Role.ADMIN);
                    changed = true;
                }
                if (changed) {
                    repo.save(admin);
                    System.out.println("Admin credentials/role reset.");
                }

                // AdminSeeder.java (idempotent)
                if (!repo.existsByEmail("admin@local")) {
                    // create admin
                } else {
                    // reset password & role to ADMIN if changed
                }

            }
        };
    }
}
