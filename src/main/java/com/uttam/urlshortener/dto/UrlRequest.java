package com.uttam.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record UrlRequest(
        @NotBlank(message = "URL cannot be empty")
        @URL(message = "Invalid URL format")
        String originalUrl
) {}