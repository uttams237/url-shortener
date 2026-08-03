package com.uttam.urlshortener.controller;

import com.uttam.urlshortener.dto.UrlAnalyticsResponse;
import com.uttam.urlshortener.dto.UrlRequest;
import com.uttam.urlshortener.dto.UrlResponse;
import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;
    private final String baseUrl;

    public UrlController(UrlService urlService,
                         @Value("${app.base-url}") String baseUrl) {
        this.urlService = urlService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/shorten")
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody UrlRequest request) {
        UrlMapping mapping = urlService.shortenUrl(request.originalUrl());

        UrlResponse response = new UrlResponse(
                mapping.getOriginalUrl(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getCreatedAt()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Redirects to the original URL.
     *
     * Now passes User-Agent and IP to the service so Kafka click events
     * include request metadata (useful for analytics dashboards later).
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirectToOriginal(@PathVariable String shortCode,
                                                 HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = request.getRemoteAddr();

        return urlService.getOriginalUrl(shortCode, userAgent, ipAddress)
                .map(originalUrl -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(originalUrl))
                        .build())
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/analytics/{shortCode}")
    public ResponseEntity<UrlAnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        return urlService.getUrlAnalytics(shortCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}