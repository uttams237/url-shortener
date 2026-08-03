package com.uttam.urlshortener.service;

import com.uttam.urlshortener.dto.UrlAnalyticsResponse;
import com.uttam.urlshortener.entity.UrlMapping;
import com.uttam.urlshortener.kafka.ClickEvent;
import com.uttam.urlshortener.kafka.ClickEventProducer;
import com.uttam.urlshortener.repository.UrlRepository;
import com.uttam.urlshortener.repository.UserRepository;
import com.uttam.urlshortener.util.Base62Encoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UrlService {

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;
    private final ClickEventProducer clickEventProducer;

    public UrlService(UrlRepository urlRepository,
                      UserRepository userRepository,
                      RedisCacheService redisCacheService,
                      ClickEventProducer clickEventProducer) {
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.redisCacheService = redisCacheService;
        this.clickEventProducer = clickEventProducer;
    }

    /**
     * Shortens a URL. If a username is provided, links the URL to that user.
     */
    public UrlMapping shortenUrl(String originalUrl, String username) {
        return urlRepository.findByOriginalUrl(originalUrl)
                .orElseGet(() -> {
                    UrlMapping mapping = new UrlMapping();
                    mapping.setOriginalUrl(originalUrl);
                    mapping.setShortCode("temp");

                    // Link to user if authenticated
                    if (username != null) {
                        userRepository.findByUsername(username)
                                .ifPresent(mapping::setOwner);
                    }

                    UrlMapping saved = urlRepository.save(mapping);
                    saved.setShortCode(Base62Encoder.encode(saved.getId()));
                    UrlMapping result = urlRepository.save(saved);

                    redisCacheService.cacheUrl(result.getShortCode(), result.getOriginalUrl());
                    return result;
                });
    }

    /**
     * Looks up the original URL for redirect.
     * Redis GET + Kafka fire-and-forget = fast redirect.
     */
    public Optional<String> getOriginalUrl(String shortCode, String userAgent, String ipAddress) {
        Optional<String> cachedUrl = redisCacheService.getCachedUrl(shortCode);
        if (cachedUrl.isPresent()) {
            clickEventProducer.publishClickEvent(ClickEvent.of(shortCode, userAgent, ipAddress));
            return cachedUrl;
        }

        return urlRepository.findByShortCode(shortCode)
                .map(mapping -> {
                    redisCacheService.cacheUrl(shortCode, mapping.getOriginalUrl());
                    clickEventProducer.publishClickEvent(ClickEvent.of(shortCode, userAgent, ipAddress));
                    return mapping.getOriginalUrl();
                });
    }

    /**
     * Returns analytics for a short code.
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

    /**
     * Returns all URLs created by a specific user.
     */
    public List<UrlMapping> getUserUrls(String username) {
        return urlRepository.findByOwnerUsername(username);
    }
}