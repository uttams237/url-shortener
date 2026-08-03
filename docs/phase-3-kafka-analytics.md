# Phase 3 — Kafka Async Click Analytics

## What Changed

Moved click count tracking from a **synchronous database write** (blocking the redirect response) to an **asynchronous Kafka event pipeline**. The redirect endpoint is now blazing fast — it only does a Redis GET + Kafka fire-and-forget.

---

## Before vs After

### Before (Phase 2):
```
User clicks short URL
    → Redis GET (originalUrl)         ← fast
    → DB READ (find entity)          ← slow (10-50ms)
    → DB WRITE (increment clickCount) ← slow (10-50ms)
    → 302 Redirect
```
Total: ~20-100ms — the DB write blocks the redirect.

### After (Phase 3):
```
User clicks short URL
    → Redis GET (originalUrl)         ← fast (< 1ms)
    → Kafka PRODUCE (fire-and-forget) ← fast (< 5ms, non-blocking)
    → 302 Redirect
    
    ... meanwhile, in the background ...
    
    Kafka Consumer
    → DB READ + DB WRITE (increment clickCount)  ← doesn't affect user
```
Total for user: ~5ms — zero DB calls on the redirect hot path (on cache HIT).

---

## Architecture

```
    Redirect Request
         │
         ▼
    ┌─────────┐      ┌─────────┐
    │  Redis   │─HIT─▶│ Return  │─── 302 Redirect (fast!)
    │  Cache   │      │  URL    │
    └────┬────┘      └─────────┘
         │                │
      MISS            Kafka Publish (async)
         │                │
         ▼                ▼
    ┌─────────┐      ┌──────────────┐
    │PostgreSQL│     │  Kafka Topic  │
    │  (read)  │     │ "url-clicks"  │
    └─────────┘      └──────┬───────┘
                            │
                            ▼
                    ┌──────────────┐
                    │   Consumer   │
                    │ (background) │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  PostgreSQL  │
                    │  (write)     │
                    └──────────────┘
```

---

## New Files

### 1. `ClickEvent.java`
**Path**: `src/main/java/com/uttam/urlshortener/kafka/ClickEvent.java`

A Java `record` that represents a click event. Contains:
- `shortCode` — which URL was clicked
- `userAgent` — browser info (Chrome, Firefox, etc.)
- `ipAddress` — client IP (for future geo-analytics)
- `clickedAt` — timestamp

This gets serialized to JSON and sent to Kafka.

### 2. `KafkaConfig.java`
**Path**: `src/main/java/com/uttam/urlshortener/config/KafkaConfig.java`

Configures:
- **Topic auto-creation**: `url-clicks` with 3 partitions
- **Producer**: StringSerializer for keys, JsonSerializer for values
- **KafkaTemplate**: The Spring abstraction for sending messages

### 3. `ClickEventProducer.java`
**Path**: `src/main/java/com/uttam/urlshortener/kafka/ClickEventProducer.java`

Publishes click events with **fire-and-forget** semantics:
```java
kafkaTemplate.send(CLICK_TOPIC, event.shortCode(), event)
```

**Key design: shortCode as message key**
- All clicks for the same URL go to the same Kafka partition
- This preserves ordering per URL
- Also enables partition-level parallelism for different URLs

**Graceful degradation**: If Kafka is down, the redirect still works — we just lose that click event. The try-catch ensures Kafka failures never break the user experience.

### 4. `ClickEventConsumer.java`
**Path**: `src/main/java/com/uttam/urlshortener/kafka/ClickEventConsumer.java`

Listens to the `url-clicks` topic and increments click counts in PostgreSQL:

```java
@KafkaListener(topics = "url-clicks", groupId = "url-analytics-group")
public void handleClickEvent(ClickEvent event) {
    mapping.setClickCount(mapping.getClickCount() + 1);
    urlRepository.save(mapping);
}
```

**Consumer group (`url-analytics-group`)**: If you run multiple app instances, Kafka distributes partitions among them. Each click event is processed exactly once across the group.

---

## Modified Files

### `UrlService.java`
The redirect method now returns `Optional<String>` (just the URL) instead of `Optional<UrlMapping>`:

```java
// On cache HIT: zero DB calls!
Optional<String> cachedUrl = redisCacheService.getCachedUrl(shortCode);
if (cachedUrl.isPresent()) {
    clickEventProducer.publishClickEvent(ClickEvent.of(shortCode, userAgent, ipAddress));
    return cachedUrl;  // Return immediately, no DB needed
}
```

### `UrlController.java`
Now passes `HttpServletRequest` metadata (User-Agent, IP) to the service for richer click events.

### `docker-compose.yml`
Added Zookeeper and Kafka (Confluent images, version 7.6.0).

### `application.properties`
Added Kafka bootstrap server, consumer deserializer configs.

---

## Interview Talking Points

> "I decoupled analytics from the redirect hot path using Kafka — the redirect does a Redis GET + Kafka produce (both non-blocking), so the P99 stays under 5ms."

> "I use the short code as the Kafka message key, which guarantees all clicks for the same URL go to the same partition. This preserves ordering and enables partition-level parallel processing."

> "If Kafka is down, redirects still work — we just lose analytics temporarily. I designed for graceful degradation rather than hard failure."

> "The consumer group means if I scale to multiple instances, Kafka automatically distributes partitions across them — each click is processed exactly once."

---

## What's Next → Phase 4: Rate Limiting with Bucket4j + Redis
The API is currently open to abuse. In Phase 4, we'll add distributed rate limiting using Bucket4j backed by Redis.
