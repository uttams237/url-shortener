# 🔗 Distributed URL Shortener

> **Status**: Phase 0 complete (basic CRUD). Following the phased plan below to build the real thing.

A production-grade URL shortening service built with Java 21, Spring Boot 4, and deployed on AWS. Features Redis caching, Kafka-based async analytics, rate limiting, and Docker containerization.

---

## 📋 Current State vs Target State

| Feature | Current State | Target State |
|---------|--------------|--------------|
| URL shortening | ✅ Random 6-char codes | ✅ Base62 encoding of DB sequence |
| Data store | ✅ PostgreSQL only | ✅ PostgreSQL + Redis cache |
| Redirect lookups | ⚠️ Direct DB hit every time | ✅ Redis cache (sub-ms) with DB fallback |
| Click analytics | ⚠️ Synchronous in redirect path | ✅ Kafka async (decoupled from redirect) |
| Rate limiting | ❌ None | ✅ Bucket4j + Redis (distributed) |
| Authentication | ❌ None | ✅ Spring Security (JWT) |
| Docker | ❌ None | ✅ Docker Compose (full stack) |
| AWS deployment | ❌ None | ✅ EC2 + RDS + ElastiCache |
| API docs | ✅ SpringDoc OpenAPI | ✅ SpringDoc OpenAPI |
| Tests | ❌ Placeholder only | ✅ Integration tests |

---

## 🗺️ Implementation Plan — 7 Phases

> Each phase is a self-contained chunk. Complete one, commit, verify, move to next.
> Estimated total: **1 weekend of focused work** (if we go fast together).

---

### Phase 0 — Foundation Cleanup ✅ (already done)
- [x] Basic Spring Boot app with PostgreSQL
- [x] `POST /api/v1/urls/shorten` — create short URL
- [x] `GET /api/v1/urls/{shortCode}` — redirect (302)
- [x] `GET /api/v1/urls/analytics/{shortCode}` — click count
- [x] SpringDoc OpenAPI integration

---

### Phase 1 — Base62 Encoding + Code Cleanup
**Goal**: Replace random code generation with deterministic Base62 encoding of the DB-generated ID. This is what interviewers expect when you say "Base62 encoding."

**What to do**:
1. Create a `Base62Encoder` utility class
   - Converts a `long` (DB auto-increment ID) → Base62 string
   - Alphabet: `0-9a-zA-Z` (62 chars)
2. Update `UrlService.shortenUrl()`:
   - Save entity first (get the auto-generated ID)
   - Encode the ID to Base62
   - Update the entity's `shortCode` with the encoded value
   - Save again (or use `@PostPersist`)
3. Add global exception handler (`@ControllerAdvice`)
4. Extract hardcoded `localhost:8080` into `application.properties` (`app.base-url`)
5. Switch from `@Autowired` field injection → constructor injection (best practice)

**Interview talking points after this phase**:
- "I use Base62 encoding of the database sequence ID — it's deterministic, collision-free, and produces short codes like `dnh3K`"
- "The tradeoff vs random codes: Base62 of sequential IDs is predictable (someone could enumerate URLs), but collision-free without retries"

---

### Phase 2 — Redis Cache Layer
**Goal**: Add Redis as a read-through cache for redirect lookups. This is the "sub-millisecond redirect lookups" claim on your resume.

**What to do**:
1. Add `spring-boot-starter-data-redis` dependency
2. Add Redis to `docker-compose.yml` (create this file now)
3. Create a `RedisCacheService` (or use Spring's `@Cacheable`)
   - On shorten: write to Redis (`shortCode → originalUrl`, TTL 24h)
   - On redirect: check Redis first → if miss, query DB → populate Redis
4. Configure Redis connection in `application.properties`
5. Add health check for Redis

**Interview talking points after this phase**:
- "Redirect lookups hit Redis first — P99 under 1ms. On cache miss, we fall through to PostgreSQL and backfill the cache"
- "I chose a 24-hour TTL as a balance between memory usage and cache hit rate"
- "Cache invalidation isn't a big concern here — URLs don't change once created"

---

### Phase 3 — Kafka Async Click Analytics
**Goal**: Decouple click counting from the redirect path. Right now `getOriginalUrl()` does a synchronous DB write to increment `clickCount` — that's blocking the redirect response. Move analytics to Kafka.

**What to do**:
1. Add `spring-kafka` dependency
2. Add Kafka + Zookeeper to `docker-compose.yml`
3. Create a `ClickEvent` record (shortCode, timestamp, userAgent, ip)
4. Create `ClickEventProducer` — publishes to `url-clicks` topic on every redirect
5. Create `ClickEventConsumer` — consumes from `url-clicks`, batch-updates DB
6. Update `UrlService.getOriginalUrl()`:
   - Remove synchronous `clickCount++` and `save()`
   - Instead: return the cached/DB URL immediately, fire Kafka event async
7. The redirect endpoint now does: Redis lookup + Kafka fire-and-forget = fast

**Interview talking points after this phase**:
- "I decoupled analytics from the redirect hot path using Kafka — the redirect does a Redis GET + Kafka produce (both non-blocking), keeping P99 under 5ms"
- "The consumer batches click events and does bulk DB updates every 5 seconds to reduce write amplification"
- "If Kafka is down, redirects still work — we just lose analytics temporarily (graceful degradation)"

---

### Phase 4 — Rate Limiting with Bucket4j + Redis
**Goal**: Add distributed rate limiting to prevent abuse. Use Bucket4j backed by Redis so it works across multiple app instances.

**What to do**:
1. Add `bucket4j-spring-boot-starter` and `bucket4j-redis` dependencies
2. Create a rate-limiting filter/interceptor:
   - 10 URL creations per minute per IP (for unauthenticated users)
   - 100 redirects per minute per IP
3. Store token buckets in Redis (shared across instances)
4. Return `429 Too Many Requests` with `Retry-After` header when limit exceeded
5. Add rate limit headers to responses (`X-RateLimit-Remaining`, `X-RateLimit-Limit`)

**Interview talking points after this phase**:
- "I used Bucket4j with Redis as the backend — token buckets are stored in Redis so rate limits are enforced consistently across all app instances"
- "The token bucket algorithm allows short bursts while enforcing an average rate"
- "Different limits for different operations — URL creation is more expensive than redirects"

---

### Phase 5 — Spring Security (JWT Authentication)
**Goal**: Add user authentication so users can manage their own URLs. Keep it simple — JWT-based, no OAuth complexity.

**What to do**:
1. Add `spring-boot-starter-security` and `jjwt` dependencies
2. Create `User` entity, `UserRepository`, `AuthController`
   - `POST /api/v1/auth/register` — register with username/password
   - `POST /api/v1/auth/login` — returns JWT token
3. Create `JwtTokenProvider` and `JwtAuthenticationFilter`
4. Update `UrlMapping` entity — add `@ManyToOne User owner` (nullable for anonymous URLs)
5. Add `GET /api/v1/urls/my-urls` — list authenticated user's URLs
6. Keep shorten + redirect endpoints publicly accessible
7. Rate limits: 10/min for anonymous, 50/min for authenticated users

**Interview talking points after this phase**:
- "Anonymous users can create and use short URLs, but authenticated users get higher rate limits and can manage their URLs"
- "JWT tokens are stateless — no server-side session storage needed, which keeps the app horizontally scalable"

---

### Phase 6 — Docker Compose (Full Stack)
**Goal**: Containerize everything for one-command local development and as the foundation for AWS deployment.

**What to do**:
1. Create `Dockerfile` (multi-stage build)
   - Stage 1: Maven build with `./mvnw package -DskipTests`
   - Stage 2: `eclipse-temurin:21-jre` with the JAR
2. Finalize `docker-compose.yml`:
   ```yaml
   services:
     app:           # Spring Boot app (port 8080)
     postgres:      # PostgreSQL 16 (port 5432)
     redis:         # Redis 7 (port 6379)
     kafka:         # Kafka (port 9092)
     zookeeper:     # Zookeeper (port 2181)
   ```
3. Use Spring profiles: `local` (H2/embedded) vs `docker` vs `aws`
4. Add `docker-compose.override.yml` for local dev tweaks
5. Verify: `docker compose up` starts everything from scratch

**Interview talking points after this phase**:
- "Docker Compose orchestrates 5 services locally — the same images deploy to production"
- "Multi-stage Docker build: build stage uses Maven, runtime stage uses JRE-only image (300MB → 200MB)"

---

### Phase 7 — AWS Deployment 🚀
**Goal**: Deploy to AWS. This is the phase that makes the project resume-worthy for cloud/infra questions.

**Deployment architecture**:
```
                    ┌─────────────┐
                    │   Route 53   │  (optional: custom domain)
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │     ALB      │  Application Load Balancer
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼─────┐ ┌───▼───┐ ┌─────▼─────┐
        │  EC2 #1   │ │EC2 #2 │ │  EC2 #3   │  (Auto Scaling Group)
        │ (App JAR) │ │       │ │           │
        └─────┬─────┘ └───┬───┘ └─────┬─────┘
              │            │            │
    ┌─────────┼────────────┼────────────┼─────────┐
    │         │            │            │         │
┌───▼───┐ ┌──▼───┐   ┌────▼────┐  ┌────▼────┐
│  RDS  │ │Redis │   │  MSK    │  │   S3    │
│(Pg16) │ │(EC)  │   │ (Kafka) │  │ (logs)  │
└───────┘ └──────┘   └─────────┘  └─────────┘
```

**What to do (minimal viable AWS — start here)**:
1. **EC2 instance** (t3.micro, free tier eligible)
   - Install Java 21, copy JAR, run as systemd service
   - Security group: allow 8080 from your IP
2. **RDS PostgreSQL** (db.t3.micro, free tier)
   - Private subnet, accessible only from EC2 security group
   - Update `application-aws.properties` with RDS endpoint
3. **ElastiCache Redis** (cache.t3.micro, free tier)
   - Same VPC as EC2
   - Update Redis config for ElastiCache endpoint
4. **For Kafka**: Use a local Kafka on the EC2 instance (MSK is expensive)
   - Or: skip Kafka on AWS and use SQS instead (cheaper, simpler, still resume-worthy)
5. Spring profile: `-Dspring.profiles.active=aws`

**Stretch goals (if you want to go further)**:
- ALB + Auto Scaling Group (horizontal scaling)
- MSK (managed Kafka) instead of local Kafka
- CI/CD with GitHub Actions → ECR → deploy to EC2
- CloudWatch for monitoring + alerts
- Custom domain via Route 53

**Interview talking points after this phase**:
- "I deployed the app to EC2 with RDS PostgreSQL and ElastiCache Redis in the same VPC. The DB and cache are in private subnets, only accessible from the app's security group"
- "I used Spring profiles to manage environment-specific configs — local uses Docker Compose, AWS uses managed services"
- "For production, I'd add an ALB with auto-scaling and move to ECS/Fargate for container orchestration"

---

## 📝 Updated Resume Points (use these after completing all phases)

### Option A — If you complete through Phase 7 (with AWS):
```
Distributed URL Shortener | Java 21, Spring Boot, Redis, Kafka, AWS (EC2, RDS, ElastiCache), Docker
• Built a URL shortening service with Base62-encoded short codes, Redis caching for sub-millisecond
  redirect lookups, and Kafka-based asynchronous click analytics decoupled from the redirect hot path.
• Deployed to AWS with EC2, RDS PostgreSQL, and ElastiCache Redis in a VPC; added Bucket4j
  rate limiting backed by Redis for distributed abuse prevention across instances.
```

### Option B — If you complete through Phase 6 (Docker only, no AWS yet):
```
Distributed URL Shortener | Java 21, Spring Boot, Redis, Kafka, Docker
• Built a URL shortening service with Base62-encoded short codes, Redis caching for sub-millisecond
  redirect lookups, and Kafka-based asynchronous click analytics decoupled from the redirect hot path.
• Added distributed rate limiting with Bucket4j and Redis across instances; containerized the full
  stack (app, PostgreSQL, Redis, Kafka) with Docker Compose for reproducible environments.
```

### ⚠️ What to remove from your current resume immediately:
Your current bullets mention Redis, Kafka, Spring Security, Bucket4j, and Docker Compose — **none of which are implemented yet.** If an interviewer asks "show me how you configured Kafka" or "walk me through your Docker Compose file," you'd have nothing to show. Remove those claims until you've actually built them.

**Current (problematic) resume**:
> Built a URL shortening service handling concurrent redirects: Base62 encoding for short codes, Redis for sub-millisecond redirect lookups, and Kafka-based asynchronous click analytics processing decoupled from the redirect path.

**Honest resume for what exists right now**:
> Built a URL shortening REST API with Spring Boot and PostgreSQL: random short code generation, 302 redirects, and click analytics tracking via OpenAPI-documented endpoints.

---

## 🏗️ Project Structure (target, after all phases)

```
url-shortener/
├── src/main/java/com/uttam/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── KafkaConfig.java
│   │   ├── SecurityConfig.java
│   │   └── RateLimitConfig.java
│   ├── controller/
│   │   ├── UrlController.java
│   │   └── AuthController.java
│   ├── dto/
│   │   ├── UrlRequest.java
│   │   ├── UrlResponse.java
│   │   ├── UrlAnalyticsResponse.java
│   │   ├── AuthRequest.java
│   │   └── AuthResponse.java
│   ├── entity/
│   │   ├── UrlMapping.java
│   │   └── User.java
│   ├── repository/
│   │   ├── UrlRepository.java
│   │   └── UserRepository.java
│   ├── service/
│   │   ├── UrlService.java
│   │   ├── RedisCacheService.java
│   │   └── UserService.java
│   ├── kafka/
│   │   ├── ClickEvent.java
│   │   ├── ClickEventProducer.java
│   │   └── ClickEventConsumer.java
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   └── JwtAuthenticationFilter.java
│   ├── util/
│   │   └── Base62Encoder.java
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.properties
│   ├── application-docker.properties
│   └── application-aws.properties
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 🚀 Quick Start (after all phases are done)

### Local Development (Docker)
```bash
docker compose up -d
# App: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

### AWS Deployment
```bash
./mvnw clean package -DskipTests
scp target/url-shortener-0.0.1-SNAPSHOT.jar ec2-user@<EC2_IP>:~/app.jar
ssh ec2-user@<EC2_IP> "java -jar app.jar --spring.profiles.active=aws"
```

---

## 🧪 API Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/auth/register` | Register new user | No |
| POST | `/api/v1/auth/login` | Login, get JWT | No |
| POST | `/api/v1/urls/shorten` | Create short URL | No |
| GET | `/api/v1/urls/{shortCode}` | Redirect to original | No |
| GET | `/api/v1/urls/analytics/{shortCode}` | Get click analytics | No |
| GET | `/api/v1/urls/my-urls` | List user's URLs | Yes (JWT) |

---

## 📖 Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 4.0.6
- **Database**: PostgreSQL 16 (RDS on AWS)
- **Cache**: Redis 7 (ElastiCache on AWS)
- **Message Queue**: Apache Kafka (or SQS on AWS)
- **Security**: Spring Security + JWT
- **Rate Limiting**: Bucket4j + Redis
- **Containerization**: Docker + Docker Compose
- **Cloud**: AWS (EC2, RDS, ElastiCache)
- **API Docs**: SpringDoc OpenAPI (Swagger UI)

---

## 🎯 Order of Execution

We will work through the phases **in order** (1 → 7). Each phase builds on the previous.
After each phase, we will:
1. Verify it works locally
2. Commit to git with a meaningful message
3. Update this README's checklist

**Let's start with Phase 1 when you're ready!**
