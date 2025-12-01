package com.example.parking.service;

import com.example.parking.entity.User;
import com.example.parking.entity.enums.Role;
import com.example.parking.exception.ResourceNotFoundException;
import com.example.parking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    public List<User> findAll() {
        return repo.findAll();
    }

    public User findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /** Create new user (password required). Role defaults to DRIVER if null. */
    @Transactional
    public User create(User u) {
        if (u.getEmail() == null || u.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (repo.existsByEmail(u.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (u.getPassword() == null || u.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (u.getUsername() == null || u.getUsername().isBlank()) {
            u.setUsername(u.getEmail()); // fallback
        }
        if (u.getRole() == null) {
            u.setRole(Role.DRIVER);
        }
        if (!u.getPassword().startsWith("{")) {
            u.setPassword(encoder.encode(u.getPassword()));
        }
        // phone, carModel, carPlate, nicNumber persist as provided
        return repo.save(u);
    }

    /** Update existing user; does NOT overwrite password if blank. */
    @Transactional
    public User update(Long id, User u) {
        User existing = findById(id);

        if (u.getUsername() != null && !u.getUsername().isBlank()) {
            existing.setUsername(u.getUsername());
        }
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            if (!u.getEmail().equalsIgnoreCase(existing.getEmail())
                    && repo.existsByEmail(u.getEmail())) {
                throw new IllegalArgumentException("Email already in use");
            }
            existing.setEmail(u.getEmail());
        }
        if (u.getPassword() != null && !u.getPassword().isBlank()) {
            existing.setPassword(
                    u.getPassword().startsWith("{") ? u.getPassword() : encoder.encode(u.getPassword())
            );
        }
        if (u.getRole() != null) {
            existing.setRole(u.getRole());
        }

        // NEW fields — set when provided (null = leave as-is, empty = store empty)
        if (u.getPhone() != null) existing.setPhone(u.getPhone());
        if (u.getCarModel() != null) existing.setCarModel(u.getCarModel());
        if (u.getCarPlate() != null) existing.setCarPlate(u.getCarPlate());
        if (u.getNicNumber() != null) existing.setNicNumber(u.getNicNumber());

        return repo.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        repo.deleteById(id);
    }
}
