# 🔗 Distributed URL Shortener

A production-grade URL shortening service built with **Java 21**, **Spring Boot 4**, and deployed on **AWS**. Features Redis caching, Kafka-based async analytics, JWT authentication, rate limiting, and Docker containerization.

---

## ⚡ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.0.6 |
| **Database** | PostgreSQL 16 (RDS on AWS) |
| **Cache** | Redis 7 (ElastiCache on AWS) |
| **Message Queue** | Apache Kafka |
| **Security** | Spring Security + JWT (HMAC-SHA256) |
| **Rate Limiting** | Bucket4j (Token Bucket Algorithm) |
| **Containerization** | Docker + Docker Compose |
| **Cloud** | AWS (EC2, RDS, ElastiCache) |
| **API Docs** | SpringDoc OpenAPI (Swagger UI) |

---

## 🏗️ Architecture

```
         Client Request
              │
              ▼
       ┌──────────────┐
       │  Rate Limiter │ (Bucket4j — per-IP token bucket)
       └──────┬───────┘
              │
       ┌──────▼───────┐
       │  JWT Filter   │ (Optional auth — Bearer token)
       └──────┬───────┘
              │
       ┌──────▼───────┐      ┌─────────┐
       │  Controller   │─────▶│ Redis   │ ← Sub-ms redirect lookups
       └──────┬───────┘      │ Cache   │
              │              └────┬────┘
              │                   │ (miss)
       ┌──────▼───────┐      ┌───▼──────┐
       │   Service     │─────▶│PostgreSQL│ ← Source of truth
       └──────┬───────┘      └──────────┘
              │
       ┌──────▼───────┐
       │    Kafka      │ ← Async click analytics
       │  (Producer)   │   (fire-and-forget)
       └──────┬───────┘
              │
       ┌──────▼───────┐
       │    Kafka      │ ← Background processing
       │  (Consumer)   │   (updates click count in DB)
       └──────────────┘
```

---

## 🚀 Quick Start

### Docker Compose (recommended)
```bash
docker compose up -d
# App:     http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

### Local Development
```bash
# Start dependencies
docker compose up -d postgres redis kafka zookeeper

# Run the app
./mvnw spring-boot:run
```

---

## 🧪 API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/v1/auth/register` | Register new user | No |
| POST | `/api/v1/auth/login` | Login, get JWT | No |
| POST | `/api/v1/urls/shorten` | Create short URL | No |
| GET | `/api/v1/urls/{shortCode}` | Redirect (302) | No |
| GET | `/api/v1/urls/analytics/{shortCode}` | Click analytics | No |
| GET | `/api/v1/urls/my-urls` | List user's URLs | JWT |

---

## 📚 Phase Documentation

Each phase has a detailed explanation document covering what changed, why, and interview talking points:

| Phase | Document | Description |
|-------|----------|-------------|
| 1 | [Base62 Encoding](docs/phase-1-base62-encoding.md) | Deterministic short codes from DB IDs |
| 2 | [Redis Cache](docs/phase-2-redis-cache.md) | Sub-millisecond redirect lookups |
| 3 | [Kafka Analytics](docs/phase-3-kafka-analytics.md) | Async click tracking pipeline |
| 4 | [Rate Limiting](docs/phase-4-rate-limiting.md) | Token bucket algorithm per IP |
| 5 | [Spring Security](docs/phase-5-spring-security.md) | JWT authentication |
| 6 | [Docker](docs/phase-6-docker.md) | Full stack containerization |
| 7 | [AWS Deployment](docs/phase-7-aws-deployment.md) | EC2 + RDS + ElastiCache |

---

## 📁 Project Structure

```
src/main/java/com/uttam/urlshortener/
├── UrlShortenerApplication.java
├── config/
│   ├── KafkaConfig.java         # Kafka producer + topic creation
│   ├── RateLimitFilter.java     # Bucket4j token bucket per IP
│   ├── RedisConfig.java         # RedisTemplate with StringSerializer
│   └── SecurityConfig.java      # Spring Security + JWT filter chain
├── controller/
│   ├── AuthController.java      # Register + Login endpoints
│   └── UrlController.java       # Shorten + Redirect + Analytics
├── dto/
│   ├── AuthRequest.java         # Login/register request body
│   ├── AuthResponse.java        # JWT token response
│   ├── UrlAnalyticsResponse.java
│   ├── UrlRequest.java
│   └── UrlResponse.java
├── entity/
│   ├── UrlMapping.java          # URL entity with User owner
│   └── User.java                # User entity (BCrypt password)
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   ├── ClickEvent.java          # Event record (shortCode, IP, UA)
│   ├── ClickEventConsumer.java  # Async DB update
│   └── ClickEventProducer.java  # Fire-and-forget publishing
├── repository/
│   ├── UrlRepository.java
│   └── UserRepository.java
├── security/
│   ├── JwtAuthenticationFilter.java  # Bearer token extraction
│   └── JwtTokenProvider.java         # Token create/validate/parse
├── service/
│   ├── RedisCacheService.java   # Read-through cache with TTL
│   ├── UrlService.java          # Core business logic
│   └── UserService.java         # Registration + authentication
└── util/
    └── Base62Encoder.java       # ID → short code encoding
```

---

## 🔧 Configuration

| Profile | File | Use Case |
|---------|------|----------|
| default | `application.properties` | Local development (host) |
| docker | `application-docker.properties` | Docker Compose |
| aws | `application-aws.properties` | AWS deployment |
