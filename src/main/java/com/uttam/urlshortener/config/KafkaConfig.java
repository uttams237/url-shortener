package com.uttam.urlshortener.config;

import com.uttam.urlshortener.kafka.ClickEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for click event publishing.
 *
 * Creates:
 * 1. The "url-clicks" topic (auto-created on startup)
 * 2. A KafkaTemplate configured for JSON serialization
 *
 * The producer uses:
 * - StringSerializer for keys (the short code)
 * - JsonSerializer for values (ClickEvent objects → JSON)
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public static final String CLICK_TOPIC = "url-clicks";

    /**
     * Auto-create the topic on startup with 3 partitions.
     * In production, topics would be pre-created by ops.
     */
    @Bean
    public NewTopic clicksTopic() {
        return TopicBuilder.name(CLICK_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, ClickEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Don't add type info headers — keeps messages clean
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, ClickEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
