package com.example.parking.user;

import com.example.parking.config.SpringContext;
import com.example.parking.entity.User;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserPasswordEntityListener {

    @PrePersist
    @PreUpdate
    public void encode(User user) {
        String pw = user.getPassword();
        if (pw == null || pw.isBlank()) return;
        // If already encoded with an id prefix (e.g. {bcrypt}), leave it alone
        if (pw.startsWith("{")) return;

        PasswordEncoder encoder = SpringContext.getBean(PasswordEncoder.class);
        user.setPassword(encoder.encode(pw)); // -> {bcrypt}...
    }
}
