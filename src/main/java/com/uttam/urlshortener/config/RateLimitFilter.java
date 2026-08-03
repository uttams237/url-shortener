package com.uttam.urlshortener.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using Bucket4j's token bucket algorithm.
 *
 * Token Bucket Algorithm (how it works):
 * - Each client (identified by IP) gets a "bucket" with N tokens
 * - Each request consumes 1 token
 * - Tokens are refilled at a steady rate (e.g., 10 per minute)
 * - If the bucket is empty → 429 Too Many Requests
 *
 * Rate limits:
 * - URL creation (POST /shorten):  10 requests per minute per IP
 * - Redirects (GET /{shortCode}): 100 requests per minute per IP
 * - Other endpoints:               50 requests per minute per IP
 *
 * For distributed deployments (multiple app instances), we store
 * the rate limit state in a ConcurrentHashMap per instance. In a true
 * production setup, Bucket4j-Redis would share state across instances.
 * We keep it simple here but demonstrate the pattern.
 *
 * Why per-IP instead of per-user?
 * Rate limiting by IP works even for unauthenticated users (the majority
 * of traffic — anyone clicking a short URL). After Phase 5 adds auth,
 * we can add user-based rate limits on top.
 */
@Component
@Order(1) // Run before other filters
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // In-memory rate limit buckets per client IP
    // In production with multiple instances, use Bucket4j + Redis (ProxyManager)
    private final Map<String, Bucket> shortenBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> redirectBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> defaultBuckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        Bucket bucket = resolveBucket(clientIp, path, method);

        if (bucket.tryConsume(1)) {
            // Token consumed — allow the request
            long remainingTokens = bucket.getAvailableTokens();
            httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remainingTokens));
            chain.doFilter(request, response);
        } else {
            // Bucket empty — reject with 429
            log.warn("Rate limit exceeded for IP: {} on {}", clientIp, path);
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("""
                    {
                      "status": 429,
                      "error": "Too Many Requests",
                      "message": "Rate limit exceeded. Please try again later.",
                      "retryAfterSeconds": 60
                    }
                    """);
        }
    }

    /**
     * Selects the appropriate bucket based on the endpoint being accessed.
     * Different endpoints get different rate limits.
     */
    private Bucket resolveBucket(String clientIp, String path, String method) {
        if (path.contains("/shorten") && "POST".equalsIgnoreCase(method)) {
            // URL creation: 10 per minute (most expensive operation)
            return shortenBuckets.computeIfAbsent(clientIp, k -> createBucket(10, 1));
        } else if (path.matches(".*/api/v1/urls/[^/]+$") && "GET".equalsIgnoreCase(method)
                && !path.contains("/analytics")) {
            // Redirects: 100 per minute (most frequent operation)
            return redirectBuckets.computeIfAbsent(clientIp, k -> createBucket(100, 1));
        } else {
            // Everything else: 50 per minute
            return defaultBuckets.computeIfAbsent(clientIp, k -> createBucket(50, 1));
        }
    }

    /**
     * Creates a token bucket with the specified capacity and refill rate.
     *
     * @param tokensPerMinute how many tokens (requests) per minute
     * @param minutes         the refill interval in minutes
     */
    private Bucket createBucket(int tokensPerMinute, int minutes) {
        Bandwidth limit = Bandwidth.classic(
                tokensPerMinute,
                Refill.intervally(tokensPerMinute, Duration.ofMinutes(minutes))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Extracts the real client IP, considering proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
