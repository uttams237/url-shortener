package com.uttam.urlshortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis cache service for URL redirect lookups.
 *
 * Cache strategy: READ-THROUGH with WRITE-ON-CREATE
 *
 * On URL creation (write path):
 *   1. Save to PostgreSQL (source of truth)
 *   2. Write to Redis (shortCode → originalUrl, TTL 24h)
 *
 * On redirect (read path):
 *   1. Check Redis first (sub-millisecond)
 *   2. If cache MISS → query PostgreSQL → backfill Redis
 *   3. If cache HIT → return immediately (no DB query)
 *
 * Why 24-hour TTL?
 * - Balance between memory usage and cache hit rate
 * - URLs don't change once created, so stale data isn't a concern
 * - Popular URLs get auto-renewed on access (we re-cache on miss)
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String CACHE_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Cache a shortCode → originalUrl mapping.
     * Called when a new URL is shortened.
     */
    public void cacheUrl(String shortCode, String originalUrl) {
        try {
            String key = CACHE_PREFIX + shortCode;
            redisTemplate.opsForValue().set(key, originalUrl, CACHE_TTL);
            log.debug("Cached: {} → {}", shortCode, originalUrl);
        } catch (Exception e) {
            // Redis failures should not break the application
            // The DB is the source of truth — cache is best-effort
            log.warn("Failed to cache URL in Redis: {}", e.getMessage());
        }
    }

    /**
     * Look up the original URL from Redis cache.
     * Returns empty if not cached (caller should fall through to DB).
     */
    public Optional<String> getCachedUrl(String shortCode) {
        try {
            String key = CACHE_PREFIX + shortCode;
            String originalUrl = redisTemplate.opsForValue().get(key);
            if (originalUrl != null) {
                log.debug("Cache HIT: {}", shortCode);
                return Optional.of(originalUrl);
            }
            log.debug("Cache MISS: {}", shortCode);
        } catch (Exception e) {
            // On Redis failure, fall through to DB — graceful degradation
            log.warn("Redis lookup failed, falling through to DB: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
