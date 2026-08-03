package com.uttam.urlshortener.config;

import com.uttam.urlshortener.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * Key decisions:
 * 1. Stateless sessions (JWT-based, no server-side sessions)
 * 2. Public web & API endpoints: static UI, register, login, shorten, redirect, analytics, Swagger
 * 3. Protected endpoints: /api/v1/urls/my-urls (requires JWT)
 * 4. CSRF disabled (safe for stateless APIs — no cookies/sessions to hijack)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Stateless API — no CSRF needed
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public Web UI & PWA assets
                        .requestMatchers("/", "/index.html", "/manifest.json", "/sw.js", "/favicon.ico", "/*.png", "/*.jpg", "/*.css", "/*.js", "/static/**").permitAll()
                        // Public REST API endpoints
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/urls/shorten").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/analytics/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/{shortCode}").permitAll()
                        // Swagger UI
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Protected API endpoints
                        .requestMatchers("/api/v1/urls/my-urls").authenticated()
                        // Everything else requires auth
                        .anyRequest().authenticated()
                )
                // Add JWT filter before Spring's default username/password filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
