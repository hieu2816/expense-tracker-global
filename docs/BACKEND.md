# Expense Tracking - Backend Technical Reference

> **Audience:** backend developers, reviewers, DevOps learners  
> **Last Updated:** 2026-05-29  
> **Stack:** Java 21, Spring Boot 3.5.14, PostgreSQL 16, Maven, Flyway, JWT, Plaid, Lombok, Micrometer Prometheus

---

## Architecture

```text
React SPA
  -> Axios /api requests with JWT
  -> Nginx reverse proxy
  -> Spring Boot controllers
  -> Services with ownership checks and transactions
  -> Spring Data JPA repositories
  -> PostgreSQL
```

Bank sync path:

```text
Frontend Plaid Link
  -> backend creates link token
  -> frontend receives public token
  -> backend exchanges token with Plaid
  -> encrypted access token stored in PostgreSQL
  -> Plaid webhook triggers async transaction sync
```

Metrics path:

```text
Spring Boot Actuator + Micrometer
  -> /actuator/prometheus
  -> Prometheus scrape
  -> Grafana Cloud remote write
```

---

## Project Structure

```text
src/main/java/com/example/expense_tracking/
|-- config/        Security, JWT filter, Plaid client, app beans
|-- controller/    REST endpoints
|-- dto/           Request/response objects
|-- entity/        JPA entities
|-- exception/     Global exception handler and business exceptions
|-- repository/    Spring Data repositories
|-- service/       Business logic and Plaid sync
|-- utils/         JWT and token utilities
```

Key backend areas:

| Area | Responsibility |
|---|---|
| Auth | Register, login, JWT generation, profile loading |
| Transactions | CRUD, filtering, dashboard totals, CSV export |
| Categories | Per-user category management |
| Plaid | Link token, token exchange, bank accounts, webhooks, sync |
| Security | Stateless JWT filter chain and ownership checks |
| Observability | Health, info, metrics, Prometheus endpoint |

---

## Database and Migrations

Flyway owns schema changes. JPA uses `ddl-auto: validate`, so the app validates the schema but does not generate it.

Current migration history:

| Migration | Purpose |
|---|---|
| V1 | Initial users, transactions, categories, webhook logs |
| V2 | Legacy GoCardless support |
| V3 | Bank config/user adjustments |
| V4 | Link reference UUID |
| V5 | Category uniqueness |
| V6 | Plaid tables and Plaid transaction IDs |
| V7 | Plaid naming cleanup |

Important model rules:

- Money uses `BigDecimal`.
- Dates use `LocalDateTime`.
- Users own transactions, categories, and Plaid items.
- Imported transactions use Plaid IDs for deduplication.
- Category deletion preserves transactions by nullifying the category reference first.

---

## Authentication and Security

Public backend endpoints:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/banks/webhook
GET  /actuator/health/**
GET  /actuator/info
GET  /actuator/prometheus
```

All other API endpoints require JWT.

Request flow:

```text
Authorization: Bearer <jwt>
  -> JwtAuthenticationFilter
  -> JwtUtils validates signature and expiry
  -> UserDetailsService loads user
  -> SecurityContext is populated
  -> controller/service receives authenticated user
```

Service-layer ownership checks are required before mutating user-owned resources:

```java
if (!entity.getUser().getId().equals(user.getId())) {
    throw new ForbiddenException("You do not own this resource");
}
```

Security note: `/actuator/prometheus` is permitted so Prometheus can scrape the backend. In the current Nginx config, `/actuator/` is proxied to the backend, so production hardening should later restrict public access to Prometheus metrics while keeping internal Prometheus scrape working.

---

## Plaid Integration

Plaid provides global bank connectivity.

Main flow:

1. Backend creates a Plaid Link token for the user.
2. Frontend opens Plaid Link.
3. User connects a bank.
4. Frontend sends `public_token` to backend.
5. Backend exchanges it for Plaid `access_token` and `item_id`.
6. Access token is encrypted before being stored.
7. Initial and later syncs import transactions.
8. Plaid webhooks notify the backend when new transaction data is available.

Async sync avoids blocking webhook responses:

```text
Plaid webhook
  -> WebhookService routes event
  -> TransactionSyncService syncs item asynchronously
  -> sync_logs records result
```

Performance protections:

- Sync uses direct indexed lookups instead of loading all transactions.
- Duplicate Plaid transactions are skipped safely.
- Webhook handling returns quickly and avoids passing detached JPA entities across async threads.

---

## Actuator and Prometheus Metrics

The backend includes:

```xml
spring-boot-starter-actuator
micrometer-registry-prometheus
```

Exposed actuator endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
```

Prometheus scrapes:

```text
backend:8080/actuator/prometheus
```

Useful metric families:

```text
jvm_memory_used_bytes
jvm_threads_*
process_uptime_seconds
http_server_requests_*
application_started_time_seconds
application_ready_time_seconds
```

These metrics are sent to Grafana Cloud through the Prometheus container, not directly by the backend.

---

## Configuration

Required production environment variables:

| Variable | Purpose |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | Spring Boot database username |
| `DB_PASSWORD` | Spring Boot database password |
| `JWT_SECRET` | JWT signing secret |
| `ALLOWED_ORIGINS` | CORS allowed origins |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile |
| `PLAID_CLIENT_ID` | Plaid client id |
| `PLAID_SECRET` | Plaid secret |
| `PLAID_ENV` | Plaid environment |
| `PLAID_WEBHOOK_URL` | Public Plaid webhook URL |
| `ENCRYPTION_KEY` | Key for sensitive token encryption |

Docker Compose injects these values into the backend container from EC2 `.env`, which is written by GitHub Actions from GitHub Secrets.

---

## Error Handling

`GlobalExceptionHandler` centralizes API errors:

| Exception | HTTP Status |
|---|---|
| Validation errors | 400 |
| `BadRequestException` | 400 |
| `ForbiddenException` | 403 |
| `ResourceNotFoundException` | 404 |
| `ConflictException` | 409 |
| Unhandled runtime error | 500 |

All business logic errors should use explicit custom exceptions where possible.
