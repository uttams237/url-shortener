# Phase 1 — Base62 Encoding + Code Cleanup

## What Changed

This phase replaced the random short code generation with **Base62 encoding** of the database-generated ID, and cleaned up the codebase with Spring Boot best practices.

---

## Changes Made

### 1. `Base62Encoder.java` (NEW)
**Path**: `src/main/java/com/uttam/urlshortener/util/Base62Encoder.java`

A utility class that converts numeric IDs to short, URL-safe strings using the Base62 alphabet (`0-9a-zA-Z`).

**How it works**:
```
ID 1     → "1"
ID 62    → "10"  
ID 1000  → "g8"
ID 99999 → "q0T"
```

**Why Base62 over random strings?**
- **Deterministic**: Same ID always produces the same code (no collisions possible)
- **Reversible**: We can decode `"g8"` back to `1000` if needed
- **Short**: 6 characters can encode up to 56.8 billion URLs (62^6)
- **URL-safe**: No special characters that need encoding

**The tradeoff**: Sequential IDs are predictable — someone could enumerate URLs by trying `1`, `2`, `3`... For a production service this might be a concern, but for a personal project it demonstrates understanding of the encoding concept.

---

### 2. `UrlService.java` (MODIFIED)
**Path**: `src/main/java/com/uttam/urlshortener/service/UrlService.java`

**Before** (random generation):
```java
private String generateUniqueShortCode() {
    String code;
    do {
        code = generateRandomCode(6);
    } while (urlRepository.findByShortCode(code).isPresent());
    return code;
}
```
Problem: This loop could theoretically run forever as the code space fills up. Each iteration does a DB query.

**After** (Base62 encoding):
```java
public UrlMapping shortenUrl(String originalUrl) {
    return urlRepository.findByOriginalUrl(originalUrl)
            .orElseGet(() -> {
                UrlMapping mapping = new UrlMapping();
                mapping.setOriginalUrl(originalUrl);
                mapping.setShortCode("temp");
                UrlMapping saved = urlRepository.save(mapping);       // 1st save: get ID
                saved.setShortCode(Base62Encoder.encode(saved.getId())); // encode ID
                return urlRepository.save(saved);                      // 2nd save: set code
            });
}
```

**Why two saves?** We need the database-generated ID to produce the Base62 code, and we can only get the ID after the first INSERT. This is a common pattern — the alternative is using a separate sequence, but this is simpler.

Also switched from `@Autowired` field injection to **constructor injection**:
```java
// Before (field injection)
@Autowired
private UrlRepository urlRepository;

// After (constructor injection)
private final UrlRepository urlRepository;

public UrlService(UrlRepository urlRepository) {
    this.urlRepository = urlRepository;
}
```

**Why constructor injection?**
- Dependencies are **explicit** (you see them in the constructor signature)
- Fields can be `final` (immutable after construction)
- **Easier to test** — just pass mocks via the constructor, no reflection needed
- Spring officially recommends this approach

---

### 3. `GlobalExceptionHandler.java` (NEW)
**Path**: `src/main/java/com/uttam/urlshortener/exception/GlobalExceptionHandler.java`

A `@RestControllerAdvice` class that catches exceptions globally and returns structured JSON error responses.

**Handles**:
| Exception | HTTP Status | When |
|-----------|-------------|------|
| `MethodArgumentNotValidException` | 400 | Invalid request body (e.g., blank URL) |
| `IllegalArgumentException` | 400 | Invalid input (e.g., bad Base62 code) |
| `Exception` (catch-all) | 500 | Any unhandled error |

**Example error response**:
```json
{
  "timestamp": "2026-08-03T19:30:00",
  "status": 400,
  "error": "Validation Failed",
  "fieldErrors": {
    "originalUrl": "URL cannot be empty"
  }
}
```

---

### 4. `UrlController.java` (MODIFIED)
- Switched to constructor injection (same reasoning as service)
- Extracted hardcoded `http://localhost:8080` into `application.properties` as `app.base-url`
- The base URL is now injected via `@Value("${app.base-url}")`

### 5. `application.properties` (MODIFIED)
- Added `app.base-url=http://localhost:8080/api/v1/urls`
- This will change per environment (local vs Docker vs AWS)

---

## Interview Talking Points

> "I use Base62 encoding of the database sequence ID — it's deterministic and collision-free. The tradeoff vs UUIDs or random codes is that sequential IDs are enumerable, but the encoding itself is what interviewers typically want to hear about in URL shortener design discussions."

> "I use constructor injection throughout — it makes dependencies explicit, fields immutable, and testing straightforward. It's Spring's recommended approach over field injection."

---

## What's Next → Phase 2: Redis Cache Layer
Currently, every redirect does a PostgreSQL query. In Phase 2, we'll add Redis as a read-through cache so redirects are served from memory in sub-millisecond time.
