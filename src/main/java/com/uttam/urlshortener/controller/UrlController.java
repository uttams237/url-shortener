package com.uttam.urlshortener.controller;

import com.uttam.urlshortener.dto.UrlAnalyticsResponse;
import com.uttam.urlshortener.dto.UrlRequest;
import com.uttam.urlshortener.dto.UrlResponse;
import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    @Autowired
    private UrlService urlService;

    @PostMapping("/shorten")
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody UrlRequest request) {
        UrlMapping mapping = urlService.shortenUrl(request.originalUrl());

        UrlResponse response = new UrlResponse(
                mapping.getOriginalUrl(),
                "http://localhost:8080/" + mapping.getShortCode(),
                mapping.getCreatedAt()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirectToOriginal(@PathVariable String shortCode) {
        return urlService.getOriginalUrl(shortCode)
                .map(mapping -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(mapping.getOriginalUrl()))
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