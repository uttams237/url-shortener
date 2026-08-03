package com.uttam.urlshortener.controller;

import com.uttam.urlshortener.dto.AuthRequest;
import com.uttam.urlshortener.dto.AuthResponse;
import com.uttam.urlshortener.security.JwtTokenProvider;
import com.uttam.urlshortener.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;

    public AuthController(UserService userService, JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Register a new user account.
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return userService.register(request.username(), request.password())
                .map(user -> {
                    String token = tokenProvider.generateToken(user.getUsername());
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(new AuthResponse(token, user.getUsername(), "Registration successful"));
                })
                .orElse(ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new AuthResponse(null, request.username(), "Username already exists")));
    }

    /**
     * Login and receive a JWT token.
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return userService.authenticate(request.username(), request.password())
                .map(user -> {
                    String token = tokenProvider.generateToken(user.getUsername());
                    return ResponseEntity.ok(
                            new AuthResponse(token, user.getUsername(), "Login successful"));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(null, request.username(), "Invalid credentials")));
    }
}
