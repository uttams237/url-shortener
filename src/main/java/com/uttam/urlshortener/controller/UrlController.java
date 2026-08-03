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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

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

    /**
     * Shorten a URL. Works for both anonymous and authenticated users.
     * If authenticated, the URL is linked to the user's account.
     */
    @PostMapping("/shorten")
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody UrlRequest request,
                                                   Authentication authentication) {
        // Get username if authenticated (null otherwise)
        String username = authentication != null ? authentication.getName() : null;

        UrlMapping mapping = urlService.shortenUrl(request.originalUrl(), username);

        UrlResponse response = new UrlResponse(
                mapping.getOriginalUrl(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getCreatedAt()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

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

    /**
     * List all URLs created by the authenticated user.
     * Requires JWT token in Authorization header.
     */
    @GetMapping("/my-urls")
    public ResponseEntity<List<UrlResponse>> getMyUrls(Authentication authentication) {
        String username = authentication.getName();
        List<UrlResponse> urls = urlService.getUserUrls(username).stream()
                .map(mapping -> new UrlResponse(
                        mapping.getOriginalUrl(),
                        baseUrl + "/" + mapping.getShortCode(),
                        mapping.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(urls);
    }
}