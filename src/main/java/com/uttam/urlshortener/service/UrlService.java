package com.uttam.urlshortener.service;

import com.uttam.urlshortener.dto.UrlAnalyticsResponse;
import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.repository.UrlRepository;
import com.uttam.urlshortener.util.Base62Encoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UrlService {

    private final UrlRepository urlRepository;
    private final RedisCacheService redisCacheService;

    public UrlService(UrlRepository urlRepository, RedisCacheService redisCacheService) {
        this.urlRepository = urlRepository;
        this.redisCacheService = redisCacheService;
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

                    // Cache in Redis on creation (write-through)
                    redisCacheService.cacheUrl(result.getShortCode(), result.getOriginalUrl());

                    return result;
                });
    }

    /**
     * Looks up the original URL — Redis first, then DB fallback.
     *
     * Read path:
     * 1. Check Redis (sub-millisecond) → if HIT, return immediately
     * 2. If MISS → query PostgreSQL → backfill Redis cache
     * 3. Increment click count (synchronous for now, Kafka in Phase 3)
     */
    public Optional<UrlMapping> getOriginalUrl(String shortCode) {
        // Step 1: Try Redis cache first
        Optional<String> cachedUrl = redisCacheService.getCachedUrl(shortCode);
        if (cachedUrl.isPresent()) {
            // Cache HIT — still need the entity for click tracking
            // In Phase 3, we'll fire a Kafka event instead of this DB call
            return urlRepository.findByShortCode(shortCode)
                    .map(mapping -> {
                        mapping.setClickCount(mapping.getClickCount() + 1);
                        return urlRepository.save(mapping);
                    });
        }

        // Step 2: Cache MISS — query DB and backfill cache
        return urlRepository.findByShortCode(shortCode)
                .map(mapping -> {
                    // Backfill Redis cache for future lookups
                    redisCacheService.cacheUrl(shortCode, mapping.getOriginalUrl());

                    mapping.setClickCount(mapping.getClickCount() + 1);
                    return urlRepository.save(mapping);
                });
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