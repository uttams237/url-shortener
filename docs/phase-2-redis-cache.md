# Phase 2 — Redis Cache Layer

## What Changed

Added Redis as a **read-through cache** for URL redirect lookups. Before this phase, every redirect did a PostgreSQL query. Now, redirects hit Redis first (sub-millisecond) and only fall through to PostgreSQL on cache miss.

---

## Architecture

```
         Redirect Request: GET /api/v1/urls/{shortCode}
                          │
                          ▼
                    ┌───────────┐
                    │  Redis    │ ← Check cache first (< 1ms)
                    │  Cache    │
                    └─────┬─────┘
                          │
                    HIT?  │
                   ┌──────┴──────┐
                   │ YES         │ NO (cache miss)
                   │             │
                   ▼             ▼
              Return URL   ┌──────────┐
              immediately  │PostgreSQL│ ← Query DB
                           └────┬─────┘
                                │
                                ▼
                          Backfill Redis
                          (for next request)
```

---

## Changes Made

### 1. `RedisConfig.java` (NEW)
**Path**: `src/main/java/com/uttam/urlshortener/config/RedisConfig.java`

Configures `RedisTemplate<String, String>` with `StringRedisSerializer`.

**Why StringRedisSerializer?**
By default, Spring uses Java serialization which stores values as binary blobs. With `StringRedisSerializer`, data is stored as plain strings — readable when you inspect with `redis-cli`:
```
redis-cli> GET url:g8
"https://google.com"
```

### 2. `RedisCacheService.java` (NEW)
**Path**: `src/main/java/com/uttam/urlshortener/service/RedisCacheService.java`

A dedicated service for Redis cache operations with two methods:

| Method | Purpose | When Called |
|--------|---------|------------|
| `cacheUrl(shortCode, originalUrl)` | Write to cache | On URL creation (write-through) |
| `getCachedUrl(shortCode)` | Read from cache | On every redirect (before DB) |

**Key design decisions**:

1. **24-hour TTL**: Balance between memory and hit rate. URLs don't change, so stale data isn't a concern.

2. **Graceful degradation**: All Redis calls are wrapped in try-catch. If Redis goes down:
   - The app continues working (falls through to PostgreSQL)
   - A warning is logged
   - No user-facing errors

3. **Key prefix `url:`**: Namespaces our cache entries. When we add rate limiting in Phase 4, those keys will use a different prefix (`ratelimit:`), preventing collisions.

### 3. `UrlService.java` (MODIFIED)
Updated to use Redis cache in the redirect flow:

**Shorten (write path)**:
```java
// After saving to DB, also cache in Redis
redisCacheService.cacheUrl(result.getShortCode(), result.getOriginalUrl());
```

**Redirect (read path)**:
```java
// 1. Try Redis first
Optional<String> cachedUrl = redisCacheService.getCachedUrl(shortCode);
if (cachedUrl.isPresent()) {
    // Cache HIT — still do DB call for click tracking
    // (In Phase 3, this becomes a Kafka event — no DB call at all)
}

// 2. Cache MISS — query DB and backfill Redis
redisCacheService.cacheUrl(shortCode, mapping.getOriginalUrl());
```

**Note**: Even on cache HIT, we currently still hit the DB for click count increment. This will be fixed in Phase 3 when we move analytics to Kafka — then a cache HIT means zero DB calls on the redirect path.

### 4. `docker-compose.yml` (NEW)
Docker Compose for local development:
- **PostgreSQL 16** (Alpine) on port 5432
- **Redis 7** (Alpine) on port 6379
- Health checks for both services
- Named volume for PostgreSQL data persistence

### 5. `application.properties` (MODIFIED)
Added Redis connection settings:
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

---

## How to Run

```bash
# Start PostgreSQL and Redis
docker compose up -d

# Run the app
./mvnw spring-boot:run

# Test: shorten a URL
curl -X POST http://localhost:8080/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://google.com"}'

# Verify it's cached in Redis
docker exec url-shortener-redis redis-cli GET "url:1"
```

---

## Interview Talking Points

> "Redirect lookups hit Redis first — P99 under 1ms. On cache miss, we query PostgreSQL and backfill the cache for future requests."

> "I chose a 24-hour TTL as a balance between memory and cache hit rate. URLs don't change once created, so cache invalidation isn't a concern."

> "Redis failures are handled gracefully — the app falls through to the database. Redis is a performance optimization, not a single point of failure."

---

## What's Next → Phase 3: Kafka Async Click Analytics
Even with Redis caching, every redirect still does a synchronous DB write (click count increment). In Phase 3, we'll decouple analytics by publishing click events to Kafka.
