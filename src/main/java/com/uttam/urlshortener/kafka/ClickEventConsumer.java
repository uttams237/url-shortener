package com.uttam.urlshortener.kafka;

import com.uttam.urlshortener.config.KafkaConfig;
import com.uttam.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes click events from Kafka and updates the database.
 *
 * This is the "other side" of the async analytics pipeline:
 * 1. ClickEventProducer publishes events on redirect (fast, non-blocking)
 * 2. ClickEventConsumer processes them here (async, can be slow)
 *
 * The consumer runs in a separate thread pool managed by Spring Kafka.
 * Even if this consumer falls behind, redirects are unaffected.
 *
 * Group ID "url-analytics-group" ensures:
 * - Multiple app instances share the workload (each gets some partitions)
 * - If a consumer dies, its partitions are reassigned to surviving consumers
 * - Events are processed exactly once per consumer group
 */
@Component
public class ClickEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventConsumer.class);

    private final UrlRepository urlRepository;

    public ClickEventConsumer(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    /**
     * Processes a click event by incrementing the URL's click count.
     *
     * @KafkaListener automatically:
     * - Subscribes to the "url-clicks" topic
     * - Deserializes JSON → ClickEvent
     * - Calls this method for each message
     * - Commits the offset after successful processing
     */
    @KafkaListener(
            topics = KafkaConfig.CLICK_TOPIC,
            groupId = "url-analytics-group",
            properties = {
                    "spring.json.value.default.type=com.uttam.urlshortener.kafka.ClickEvent"
            }
    )
    public void handleClickEvent(ClickEvent event) {
        log.debug("Received click event for: {}", event.shortCode());

        urlRepository.findByShortCode(event.shortCode())
                .ifPresentOrElse(
                        mapping -> {
                            mapping.setClickCount(mapping.getClickCount() + 1);
                            urlRepository.save(mapping);
                            log.debug("Updated click count for {}: {}",
                                    event.shortCode(), mapping.getClickCount());
                        },
                        () -> log.warn("Received click event for unknown short code: {}",
                                event.shortCode())
                );
    }
}
