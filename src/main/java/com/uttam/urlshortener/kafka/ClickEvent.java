package com.uttam.urlshortener.kafka;

import java.time.LocalDateTime;

/**
 * Represents a click event published to Kafka when a user
 * accesses a short URL.
 *
 * This is a simple record (immutable data carrier) that gets
 * serialized to JSON and sent to the "url-clicks" Kafka topic.
 *
 * By publishing events asynchronously, we decouple analytics
 * processing from the redirect hot path — the user gets their
 * redirect immediately while analytics are processed in the background.
 */
public record ClickEvent(
        String shortCode,
        String userAgent,
        String ipAddress,
        LocalDateTime clickedAt
) {
    /**
     * Factory method for creating a click event with the current timestamp.
     */
    public static ClickEvent of(String shortCode, String userAgent, String ipAddress) {
        return new ClickEvent(shortCode, userAgent, ipAddress, LocalDateTime.now());
    }
}
