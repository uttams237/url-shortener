package com.uttam.urlshortener.dto;

import java.time.LocalDateTime;

public record UrlResponse(
        String originalUrl,
        String shortUrl,
        LocalDateTime createdAt
) {}