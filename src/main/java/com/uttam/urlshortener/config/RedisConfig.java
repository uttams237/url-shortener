package com.uttam.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the URL shortener.
 *
 * We use RedisTemplate<String, String> because our cache is simple:
 * - Key:   shortCode (e.g., "g8")
 * - Value: originalUrl (e.g., "https://google.com")
 *
 * StringRedisSerializer ensures keys and values are stored as plain
 * strings in Redis (human-readable when you inspect with redis-cli).
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for both keys and values
        // Without this, Spring uses Java serialization (binary, unreadable in redis-cli)
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        return template;
    }
}
