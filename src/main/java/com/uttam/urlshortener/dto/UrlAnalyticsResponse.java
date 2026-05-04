package com.uttam.urlshortener.dto;

import java.time.LocalDateTime;

public record UrlAnalyticsResponse(
        String originalUrl,
        String shortCode,
        int clickCount,
        LocalDateTime createdAt
) {}