package com.example.parking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder del =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        del.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance());
        return del;
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder pe) {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(pe);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(auth -> auth
                        // public
                        .requestMatchers("/", "/login", "/register",
                                "/css/**", "/js/**", "/images/**", "/webjars/**",
                                "/api/me").permitAll()

                        // admin-only
                        .requestMatchers("/settings/**").hasRole("ADMIN")
                        .requestMatchers("/users").hasRole("ADMIN")

                        // admin or manager for lot mgmt
                        .requestMatchers("/lots", "/api/lotmgmt/**").hasAnyRole("ADMIN","MANAGER")

                        // pricing admin UI
                        .requestMatchers("/pricing/**").hasAnyRole("ADMIN","MANAGER")

                        // audit
                        .requestMatchers("/audit/logs", "/api/audit/**").hasAnyRole("ADMIN","IT")

                        // ================== CHANGED BLOCK (reports) ==================
                        // Accept DRIVER whether authority is DRIVER or ROLE_DRIVER; keep ADMIN/MANAGER.
                        .requestMatchers("/reports/**")
                        .access(new WebExpressionAuthorizationManager(
                                "hasAnyRole('ADMIN','MANAGER') or hasAnyAuthority('DRIVER','ROLE_DRIVER')"
                        ))
                        // =============================================================

                        // LOGS — Admin and IT/IT_CONSULTANT
                        .requestMatchers("/logs", "/api/logs/**").hasAnyRole("ADMIN","IT","IT_CONSULTANT")

                        // primary tabs
                        .requestMatchers("/reservations/**", "/payments/**")
                        .hasAnyRole("ADMIN","MANAGER","DRIVER","USER")

                        // find parking
                        .requestMatchers("/find-parking/**", "/findparking/**", "/find/**")
                        .hasAnyRole("ADMIN","MANAGER","DRIVER","USER")

                        // misc
                        .requestMatchers("/slots").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/pricing/quote", "/api/pricing/rules")
                        .hasAnyRole("ADMIN","MANAGER","DRIVER","USER")

                        // pricing writes
                        .requestMatchers(HttpMethod.POST,   "/api/pricing/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers(HttpMethod.PUT,    "/api/pricing/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers(HttpMethod.PATCH,  "/api/pricing/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/pricing/**").hasAnyRole("ADMIN","MANAGER")

                        // deals
                        .requestMatchers("/deals/**").hasAnyRole("DRIVER","USER")

                        // anything else
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login").permitAll()
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout").permitAll()
                );

        return http.build();
    }
}
