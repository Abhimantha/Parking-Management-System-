package com.example.parking.service;

import com.example.parking.entity.User;
import com.example.parking.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User u = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));

        // ROLE_ prefix is required by Spring Security for role voters
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
        return org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(u.getPassword()) // plain text (NoOpPasswordEncoder)
                .authorities(authorities)
                .accountLocked(false).disabled(false).build();
    }
}
