package com.uttam.urlshortener.service;

import com.uttam.urlshortener.dto.UrlAnalyticsResponse;
import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
public class UrlService {

    @Autowired
    private UrlRepository urlRepository;

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final Random random = new Random();


    public UrlMapping shortenUrl(String originalUrl) {
        // Check if we've already shortened this URL
        return urlRepository.findByOriginalUrl(originalUrl)
                .orElseGet(() -> {
                    // If not, create and save a new one
                    UrlMapping mapping = new UrlMapping();
                    mapping.setOriginalUrl(originalUrl);
                    mapping.setShortCode(generateUniqueShortCode());
                    return urlRepository.save(mapping);
                });
    }

    public Optional<UrlMapping> getOriginalUrl(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .map(mapping -> {
                    // 1. Increment the counter
                    mapping.setClickCount(mapping.getClickCount() + 1);
                    // 2. Save the update back to PostgreSQL
                    return urlRepository.save(mapping);
                });
    }

    private String generateUniqueShortCode() {
        String code;
        do {
            code = generateRandomCode(6);
        } while (urlRepository.findByShortCode(code).isPresent());
        return code;
    }

    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public Optional<UrlAnalyticsResponse> getUrlAnalytics(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .map(mapping -> new UrlAnalyticsResponse(
                        mapping.getOriginalUrl(),
                        mapping.getShortCode(),
                        mapping.getClickCount(),
                        mapping.getCreatedAt()
                ));
    }
}