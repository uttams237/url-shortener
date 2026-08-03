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

    // Constructor injection (best practice over @Autowired field injection)
    // - Makes dependencies explicit
    // - Enables easier unit testing (pass mocks via constructor)
    // - Spring auto-detects this as the injection point
    public UrlService(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    /**
     * Shortens a URL using Base62 encoding of the database-generated ID.
     *
     * Flow:
     * 1. Check if URL was already shortened (idempotent — same URL = same short code)
     * 2. If new: save to DB first (to get auto-generated ID)
     * 3. Encode the ID using Base62 (e.g., ID 1000 → "g8")
     * 4. Set the short code and save again
     *
     * Why two saves? We need the DB-generated ID to produce the Base62 code,
     * and we can only get the ID after the first save (INSERT).
     */
    public UrlMapping shortenUrl(String originalUrl) {
        // Idempotent: if we've already shortened this URL, return existing mapping
        return urlRepository.findByOriginalUrl(originalUrl)
                .orElseGet(() -> {
                    // First save: get the auto-generated ID from the database
                    UrlMapping mapping = new UrlMapping();
                    mapping.setOriginalUrl(originalUrl);
                    mapping.setShortCode("temp"); // placeholder, will be replaced
                    UrlMapping saved = urlRepository.save(mapping);

                    // Encode the DB ID to Base62 for a short, deterministic code
                    saved.setShortCode(Base62Encoder.encode(saved.getId()));
                    return urlRepository.save(saved);
                });
    }

    /**
     * Looks up the original URL by short code and increments click count.
     *
     * Note: Currently this does a synchronous DB write on every redirect.
     * In Phase 3, we'll move this to Kafka for async processing.
     */
    public Optional<UrlMapping> getOriginalUrl(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .map(mapping -> {
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