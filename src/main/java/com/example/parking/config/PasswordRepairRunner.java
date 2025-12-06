package com.example.parking.config;

import com.example.parking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class PasswordRepairRunner {
    private static final Logger log = LoggerFactory.getLogger(PasswordRepairRunner.class);

    @Bean
    CommandLineRunner repairPasswords(UserRepository userRepo, PasswordEncoder encoder) {
        return args -> repair(userRepo, encoder);
    }

    @Transactional
    void repair(UserRepository userRepo, PasswordEncoder encoder) {
        var users = userRepo.findAll();
        int fixed = 0;
        for (var u : users) {
            var pw = u.getPassword();
            if (pw != null && !pw.isBlank() && !pw.startsWith("{")) {
                u.setPassword(encoder.encode(pw));
                userRepo.save(u);
                fixed++;
            }
        }
        log.info("Password repair complete. Re-encoded {}", fixed);
    }
}
