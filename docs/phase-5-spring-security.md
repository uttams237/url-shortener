# Phase 5 — Spring Security (JWT Authentication)

## What Changed

Added user authentication using **JWT (JSON Web Tokens)** with Spring Security. Users can register, login, and manage their own URLs. Core operations (shorten, redirect, analytics) remain publicly accessible.

---

## Authentication Flow

```
  ┌──────────────────────────────────────────────────────────┐
  │                    Registration Flow                      │
  │                                                          │
  │  POST /api/v1/auth/register                              │
  │  {"username": "uttam", "password": "secret123"}          │
  │                                                          │
  │  1. Hash password with BCrypt                            │
  │  2. Save user to PostgreSQL                              │
  │  3. Generate JWT token                                   │
  │  4. Return: {"token": "eyJhbG...", "username": "uttam"}  │
  └──────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────┐
  │                   Authenticated Request                   │
  │                                                          │
  │  GET /api/v1/urls/my-urls                                │
  │  Authorization: Bearer eyJhbGciOi...                     │
  │                                                          │
  │  1. JwtAuthenticationFilter extracts token                │
  │  2. Validate signature + expiration                      │
  │  3. Extract username from token payload                  │
  │  4. Set SecurityContext → request proceeds                │
  └──────────────────────────────────────────────────────────┘
```

---

## Endpoint Access Rules

| Endpoint | Auth Required | Notes |
|----------|---------------|-------|
| `POST /api/v1/auth/register` | No | Create account |
| `POST /api/v1/auth/login` | No | Get JWT token |
| `POST /api/v1/urls/shorten` | No | Anonymous or authenticated |
| `GET /api/v1/urls/{shortCode}` | No | Redirect — public |
| `GET /api/v1/urls/analytics/{shortCode}` | No | Click stats — public |
| `GET /api/v1/urls/my-urls` | **Yes (JWT)** | List user's URLs |
| Swagger UI | No | API documentation |

---

## New & Modified Files

### Security Layer

**`JwtTokenProvider.java`** — Creates, validates, and parses JWT tokens using HMAC-SHA256.

**`JwtAuthenticationFilter.java`** — OncePerRequestFilter that:
1. Checks for `Authorization: Bearer <token>` header
2. Validates the token
3. Sets the authenticated user in Spring's SecurityContext

**`SecurityConfig.java`** — Configures Spring Security:
- CSRF disabled (stateless API, no cookies)
- Session management: STATELESS (no server-side sessions)
- Public vs protected endpoint rules
- JWT filter inserted before UsernamePasswordAuthenticationFilter

### User Management

**`User.java`** — JPA entity with username and BCrypt-hashed password.

**`UserRepository.java`** — findByUsername, existsByUsername.

**`UserService.java`** — Registration (BCrypt hashing) and authentication (password matching).

**`AuthController.java`** — `/register` and `/login` endpoints.

### Updated Existing Files

**`UrlMapping.java`** — Added `@ManyToOne User owner` (nullable for anonymous URLs).

**`UrlRepository.java`** — Added `findByOwnerUsername(String username)`.

**`UrlService.java`** — `shortenUrl()` now accepts optional username to link URL to user. Added `getUserUrls(username)`.

**`UrlController.java`** — Uses `Authentication` parameter to get current user. Added `GET /my-urls` endpoint.

---

## JWT Token Structure

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1dHRhbSIsImlhdCI6MTcyMjcwMDAwMCwiZXhwIjoxNzIyNzg2NDAwfQ.signature

Header:  {"alg": "HS256"}
Payload: {"sub": "uttam", "iat": 1722700000, "exp": 1722786400}
Signature: HMACSHA256(header.payload, secret)
```

- **sub**: Subject (username)
- **iat**: Issued at (Unix timestamp)
- **exp**: Expiration (24 hours from issue)
- Tokens are **stateless** — any app instance can validate without hitting a DB

---

## Why Stateless JWT over Server-Side Sessions?

| | Server Sessions | JWT |
|---|---|---|
| Storage | Server memory/Redis | Client-side (in token) |
| Scalability | Need sticky sessions or shared store | Any instance can validate |
| Revocation | Easy (delete session) | Hard (wait for expiry) |
| Best for | Traditional web apps | APIs, microservices |

For a URL shortener API, JWT is the right choice — we need horizontal scalability and don't need real-time revocation.

---

## Interview Talking Points

> "I use JWT for stateless authentication — tokens are signed with HMAC-SHA256 and contain the username as the subject claim. Any app instance can validate the token without hitting the database, which is important for horizontal scaling."

> "BCrypt for password hashing — it's intentionally slow and includes a salt, making rainbow table and brute-force attacks impractical."

> "I kept URL creation and redirects public so the service works for anonymous users too, but authenticated users can track and manage their URLs."

---

## What's Next → Phase 6: Docker Compose (Full Stack)
Containerizing the Spring Boot app with a multi-stage Docker build and orchestrating everything with Docker Compose.
