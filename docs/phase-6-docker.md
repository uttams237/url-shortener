# Phase 6 — Docker Compose (Full Stack)

## What Changed

Containerized the entire application stack with Docker. One command (`docker compose up`) starts everything — the Spring Boot app, PostgreSQL, Redis, Kafka, and Zookeeper.

---

## Architecture

```
docker compose up
       │
       ├── app (Spring Boot)      → port 8080
       │    └── depends on: postgres, redis, kafka
       │
       ├── postgres (PostgreSQL 16) → port 5432
       │    └── volume: postgres_data
       │
       ├── redis (Redis 7)         → port 6379
       │
       ├── kafka (Confluent 7.6)   → port 9092
       │    └── depends on: zookeeper
       │
       └── zookeeper               → port 2181
```

---

## Multi-Stage Dockerfile

```dockerfile
# Stage 1: Build (JDK — compiles code)
FROM eclipse-temurin:21-jdk AS build
COPY . .
RUN ./mvnw package -DskipTests

# Stage 2: Runtime (JRE only — runs the JAR)
FROM eclipse-temurin:21-jre
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage?**
- Build stage needs JDK + Maven (~400MB)
- Runtime stage only needs JRE (~200MB)
- Final image is ~50% smaller
- Build tools aren't in the production image (smaller attack surface)

**Layer caching optimization**:
```dockerfile
# Copy pom.xml first → cached if dependencies haven't changed
COPY pom.xml ./
RUN ./mvnw dependency:go-offline

# Then copy source → only this layer rebuilds on code changes
COPY src/ src/
RUN ./mvnw package -DskipTests
```

This means `mvn dependency:go-offline` only re-runs when `pom.xml` changes, not on every code change. Saves minutes on rebuilds.

---

## Spring Profiles

| Profile | File | When to Use |
|---------|------|-------------|
| (default) | `application.properties` | `./mvnw spring-boot:run` on host |
| `docker` | `application-docker.properties` | `docker compose up` |
| `aws` | `application-aws.properties` | AWS deployment (Phase 7) |

**Key difference**: Docker profile uses service names (`postgres`, `redis`, `kafka`) instead of `localhost` for inter-container DNS resolution.

```properties
# Default (host):
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres

# Docker:
spring.datasource.url=jdbc:postgresql://postgres:5432/postgres
```

---

## Docker Compose Details

### Service Dependencies
```yaml
app:
  depends_on:
    postgres:
      condition: service_healthy   # Wait for health check
    redis:
      condition: service_healthy
    kafka:
      condition: service_started   # No health check, just start
```

### Environment Variable Override
Docker Compose sets Spring properties via environment variables:
```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/postgres
  SPRING_DATA_REDIS_HOST: redis
  SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

Spring Boot automatically maps `SPRING_DATASOURCE_URL` → `spring.datasource.url` (relaxed binding).

### Security: Non-Root User
```dockerfile
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser
```
The app runs as a non-root user inside the container — a security best practice.

---

## Commands

```bash
# Start everything
docker compose up -d

# View logs
docker compose logs -f app

# Rebuild after code changes
docker compose up -d --build

# Stop everything
docker compose down

# Stop and delete all data (fresh start)
docker compose down -v

# Check Redis cache
docker exec url-shortener-redis redis-cli KEYS "url:*"
```

---

## Interview Talking Points

> "Docker Compose orchestrates 5 services — the app, PostgreSQL, Redis, Kafka, and Zookeeper. Health checks ensure the database and cache are ready before the app starts."

> "I used a multi-stage Docker build — the build stage uses the full JDK for compilation, the runtime stage uses JRE only, cutting the image size roughly in half."

> "Spring profiles manage environment-specific config — the Docker profile uses container service names for DNS, while the default profile uses localhost for host development."

---

## What's Next → Phase 7: AWS Deployment
Deploying the stack to AWS with EC2, RDS PostgreSQL, and ElastiCache Redis.
