package com.example.parking.controller;

import com.example.parking.entity.User;
import com.example.parking.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public List<User> all() { return service.findAll(); }

    @GetMapping("/{id}")
    public User one(@PathVariable Long id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User u) {
        return ResponseEntity.ok(service.create(u));
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User u) {
        return service.update(id, u);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
