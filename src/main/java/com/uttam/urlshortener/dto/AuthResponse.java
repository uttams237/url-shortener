package com.uttam.urlshortener.dto;

public record AuthResponse(
        String token,
        String username,
        String message
) {}
