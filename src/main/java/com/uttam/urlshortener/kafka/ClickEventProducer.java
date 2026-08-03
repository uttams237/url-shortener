package com.uttam.urlshortener.kafka;

import com.uttam.urlshortener.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes click events to Kafka asynchronously.
 *
 * Called from UrlService on every redirect. The key design here
 * is fire-and-forget: we send the event and don't wait for
 * acknowledgment. This keeps the redirect response fast.
 *
 * If Kafka is down:
 * - The redirect still works (graceful degradation)
 * - We lose that click event (acceptable tradeoff)
 * - A warning is logged
 *
 * The short code is used as the Kafka message key, which means
 * all clicks for the same URL go to the same partition — preserving
 * order per URL (useful if we ever need to replay events).
 */
@Component
public class ClickEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventProducer.class);

    private final KafkaTemplate<String, ClickEvent> kafkaTemplate;

    public ClickEventProducer(KafkaTemplate<String, ClickEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a click event to Kafka (fire-and-forget).
     *
     * @param event the click event to publish
     */
    public void publishClickEvent(ClickEvent event) {
        try {
            // Key = shortCode → same URL always goes to same partition
            kafkaTemplate.send(KafkaConfig.CLICK_TOPIC, event.shortCode(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish click event for {}: {}",
                                    event.shortCode(), ex.getMessage());
                        } else {
                            log.debug("Published click event for {} to partition {}",
                                    event.shortCode(),
                                    result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            // Don't let Kafka failures break redirects
            log.warn("Kafka unavailable, click event lost for {}: {}",
                    event.shortCode(), e.getMessage());
        }
    }
}
