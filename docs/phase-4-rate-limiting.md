# Phase 4 — Rate Limiting with Bucket4j

## What Changed

Added per-IP rate limiting using the **token bucket algorithm** (via Bucket4j) to prevent API abuse. Different endpoints have different limits based on how expensive they are.

---

## Rate Limits

| Endpoint | Limit | Why |
|----------|-------|-----|
| `POST /shorten` | 10/min per IP | Most expensive (DB writes) |
| `GET /{shortCode}` (redirect) | 100/min per IP | High volume, but cheap (Redis + Kafka) |
| Everything else (analytics, etc.) | 50/min per IP | Moderate |

---

## Token Bucket Algorithm — Explained

Think of it like a bucket filled with tokens:

```
 ┌───────────┐
 │ ○ ○ ○ ○ ○ │  ← Bucket starts FULL (e.g., 10 tokens)
 │ ○ ○ ○ ○ ○ │
 └───────────┘

 Request #1: consume 1 token → 9 remaining → ✅ ALLOWED
 Request #2: consume 1 token → 8 remaining → ✅ ALLOWED
 ...
 Request #10: consume 1 token → 0 remaining → ✅ ALLOWED
 Request #11: no tokens left → ❌ 429 TOO MANY REQUESTS

 ... 1 minute passes → bucket refills to 10 tokens ...
```

**Why token bucket over other algorithms?**
- **Fixed Window**: Simple but has the "boundary problem" (2x burst at window edges)
- **Sliding Window**: More accurate but complex to implement
- **Token Bucket**: Allows natural bursts while enforcing average rate — best balance

---

## Implementation

### `RateLimitFilter.java` (NEW)
**Path**: `src/main/java/com/uttam/urlshortener/config/RateLimitFilter.java`

A `jakarta.servlet.Filter` that intercepts every request:

```java
if (bucket.tryConsume(1)) {
    // Token consumed — allow request
    response.setHeader("X-RateLimit-Remaining", remaining);
    chain.doFilter(request, response);
} else {
    // Bucket empty — reject
    response.setStatus(429);
    response.getWriter().write(errorJson);
}
```

**Key design decisions**:

1. **Per-IP identification**: Uses `X-Forwarded-For` header (for requests behind proxies/load balancers), falls back to `getRemoteAddr()`.

2. **Different buckets per endpoint**: The filter checks the URL path and HTTP method to select the right rate limit. URL creation (POST) is more expensive than redirects (GET).

3. **In-memory `ConcurrentHashMap`**: Each IP gets its own bucket stored in a thread-safe map. This works for single-instance deployments. For distributed deployments, you'd use Bucket4j's `ProxyManager` with Redis — the buckets would be shared across instances.

4. **Response headers**: On allowed requests, we include `X-RateLimit-Remaining` so clients can see how many requests they have left.

---

## Example Responses

### Request allowed:
```
HTTP/1.1 201 Created
X-RateLimit-Remaining: 7
Content-Type: application/json
```

### Rate limit exceeded:
```json
HTTP/1.1 429 Too Many Requests
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please try again later.",
  "retryAfterSeconds": 60
}
```

---

## Interview Talking Points

> "I use Bucket4j's token bucket algorithm for rate limiting — it allows short bursts while enforcing an average rate. Different endpoints have different limits based on cost: 10/min for URL creation vs 100/min for redirects."

> "The token buckets are stored per-IP in a ConcurrentHashMap. For distributed deployments with multiple instances, you'd use Bucket4j's ProxyManager backed by Redis so rate limits are enforced consistently across all instances."

> "I check X-Forwarded-For first for the real client IP — important when the app sits behind an ALB or reverse proxy, which it will in the AWS deployment."

---

## What's Next → Phase 5: Spring Security (JWT Authentication)
Adding user accounts with JWT authentication so users can manage their own URLs and get higher rate limits.
