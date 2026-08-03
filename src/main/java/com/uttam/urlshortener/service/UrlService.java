package com.uttam.urlshortener.service;

import com.uttam.urlshortener.dto.UrlAnalyticsResponse;
import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.kafka.ClickEvent;
import com.uttam.urlshortener.kafka.ClickEventProducer;
import com.uttam.urlshortener.repository.UrlRepository;
import com.uttam.urlshortener.util.Base62Encoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UrlService {

    private final UrlRepository urlRepository;
    private final RedisCacheService redisCacheService;
    private final ClickEventProducer clickEventProducer;

    public UrlService(UrlRepository urlRepository,
                      RedisCacheService redisCacheService,
                      ClickEventProducer clickEventProducer) {
        this.urlRepository = urlRepository;
        this.redisCacheService = redisCacheService;
        this.clickEventProducer = clickEventProducer;
    }

    /**
     * Shortens a URL using Base62 encoding of the database-generated ID.
     * Also caches the mapping in Redis for fast redirect lookups.
     */
    public UrlMapping shortenUrl(String originalUrl) {
        return urlRepository.findByOriginalUrl(originalUrl)
                .orElseGet(() -> {
                    UrlMapping mapping = new UrlMapping();
                    mapping.setOriginalUrl(originalUrl);
                    mapping.setShortCode("temp");
                    UrlMapping saved = urlRepository.save(mapping);

                    saved.setShortCode(Base62Encoder.encode(saved.getId()));
                    UrlMapping result = urlRepository.save(saved);

                    redisCacheService.cacheUrl(result.getShortCode(), result.getOriginalUrl());
                    return result;
                });
    }

    /**
     * Looks up the original URL for redirect.
     *
     * THE KEY OPTIMIZATION (Phase 3):
     * Before: Redis GET + DB READ + DB WRITE (click count) = slow
     * After:  Redis GET + Kafka PRODUCE (fire-and-forget) = fast
     *
     * The redirect path now does:
     * 1. Redis lookup (sub-millisecond)
     * 2. Kafka publish (async, non-blocking)
     * 3. Return the URL immediately
     *
     * Click counting happens asynchronously in ClickEventConsumer.
     */
    public Optional<String> getOriginalUrl(String shortCode, String userAgent, String ipAddress) {
        // Step 1: Try Redis cache first
        Optional<String> cachedUrl = redisCacheService.getCachedUrl(shortCode);
        if (cachedUrl.isPresent()) {
            // Cache HIT — publish click event to Kafka (async, no DB call!)
            clickEventProducer.publishClickEvent(ClickEvent.of(shortCode, userAgent, ipAddress));
            return cachedUrl;
        }

        // Step 2: Cache MISS — query DB
        Optional<String> dbUrl = urlRepository.findByShortCode(shortCode)
                .map(mapping -> {
                    // Backfill Redis cache
                    redisCacheService.cacheUrl(shortCode, mapping.getOriginalUrl());
                    // Publish click event to Kafka
                    clickEventProducer.publishClickEvent(ClickEvent.of(shortCode, userAgent, ipAddress));
                    return mapping.getOriginalUrl();
                });

        return dbUrl;
    }

    /**
     * Returns analytics (click count, creation time) for a short code.
     */
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