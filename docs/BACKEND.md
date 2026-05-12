# Expense Tracking — Backend Technical Reference

> **Audience:** Developers, code reviewers, technical leads
> **Last Updated:** 2026-05-12
> **Stack:** Java 21 · Spring Boot 3.5.9 · PostgreSQL 16 · Maven · Flyway · JWT · Plaid API · Lombok · Apache Commons CSV

---

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Database Migrations](#database-migrations)
4. [Authentication & Security](#authentication--security)
5. [Transaction Management](#transaction-management)
6. [Category Management](#category-management)
7. [Bank Integration (Plaid)](#bank-integration-plaid)
8. [Database Schema](#database-schema)
9. [Data Flow Diagrams](#data-flow-diagrams)
10. [Configuration Reference](#configuration-reference)
11. [Error Handling](#error-handling)

---

## Architecture

```text
┌────────────────────┐      ┌──────────────────────────────────────┐
│  React SPA (Vite)  │      │           Spring Boot App             │
│                    │ JWT  │  Controller ──▶ Service ──▶ Repo   │
│  App.jsx           │─────▶│       │                    │        │
│  ├── AuthContext   │◀─────│       ▼                    ▼        │
│  ├── AppLayout     │ JSON │  DTOs/Validation      PostgreSQL    │
│  └── Pages         │      │                                      │
│  port 5173         │      │  ┌──────────────────────────────┐   │
└────────────────────┘      │  │ WebhookService (Real-time)    │   │
                            │  │ TransactionSyncScheduler      │   │
                            │  └──────────┬─────────────────┘   │
                            │             ▼                      │
                            └──────────── PlaidService ─────────▶ Plaid API
```

### Why These Technologies?

| Choice | Why |
|--------|-----|
| **Java 21** | Modern LTS, records/pattern matching reduce boilerplate, ZGC for low-latency GC |
| **Spring Boot 3** | Auto-config, `@Transactional`, `@Scheduled`, `@RestControllerAdvice` — less glue code |
| **PostgreSQL 16** | JSONB support, strong consistency, `deferrable unique` constraints for dedup |
| **Flyway** | Schema versioned as SQL files, migrations run automatically on startup |
| **JWT (jjwt)** | Stateless auth — no server-side session storage, scales horizontally |
| **BCrypt** | Adaptive cost factor (work factor), resistant to rainbow table / GPU attacks |
| **Lombok** | Eliminates getters/setters/constructors, keeps entity files readable |
| **Apache Commons CSV** | RFC-4180 compliant, streaming-friendly, no extra dependencies |
| **Plaid API** | Global bank connectivity with real-time webhook updates for transactions and item states |

### Layer Responsibilities

| Layer | Responsibility | Key Pattern |
|-------|---------------|-------------|
| Controller | REST endpoints, validation, extract user | `@RestController`, `@AuthenticationPrincipal` |
| Service | Business logic, ownership checks, mapping | `@Service`, `@Transactional` |
| Repository | Data access, custom JPQL queries | `JpaRepository` |
| Config | Beans, external credentials, security | `@Configuration` |

---

## Project Structure

```text
src/main/java/com/example/expense_tracking/
├── config/
│   ├── ApplicationConfig.java       # BCrypt encoder, AuthenticationManager, UserDetailsService
│   ├── SecurityConfig.java          # CORS, stateless session, JWT filter chain, public webhook endpoints
│   ├── JwtAuthenticationFilter.java # Token extraction, validation, lastActiveAt tracking
│   ├── PlaidConfig.java             # Plaid API credentials & client configuration
│   └── SyncConfig.java              # Scheduler settings
│
├── controller/
│   ├── AuthController.java          # POST /api/auth/register, /login
│   ├── UserController.java          # GET/PUT /api/user/profile, PUT /change-password
│   ├── TransactionController.java   # CRUD, dashboard, category-summary, export
│   ├── CategoryController.java      # CRUD /api/categories
│   └── BankController.java          # link, complete, sync, unlink, sync-history, webhook
│
├── dto/
│   ├── bank/: PlaidExchangeRequest, BankAccountResponse, LinkBankResponse
│   └── webhook/: PlaidWebhookRequest # DTO for incoming Plaid events
│
├── entity/
│   ├── User.java                    # implements UserDetails (Spring Security)
│   ├── Transaction.java             # UNIQUE(plaid_transaction_id, bank_account_id)
│   ├── Category.java                # per-user, typed IN/OUT, UNIQUE(user_id, name)
│   ├── PlaidItem.java               # Represents a bank connection (credentials)
│   └── BankAccount.java             # Individual bank accounts within a PlaidItem
│
├── exception/
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice
│
├── repository/
│   ├── UserRepository.java
│   ├── TransactionRepository.java   # Custom JPQL, O(1) lookups via PlaidTransactionId
│   ├── CategoryRepository.java
│   ├── PlaidItemRepository.java
│   └── BankAccountRepository.java
│
└── service/
    ├── AuthService.java
    ├── UserService.java
    ├── TransactionService.java
    ├── CategoryService.java
    ├── PlaidService.java            # Plaid SDK client wrapper (Link tokens, sync)
    ├── BankLinkingService.java      # Business logic for completing Plaid linking
    ├── WebhookService.java          # Routes Plaid webhooks (TRANSACTIONS, ITEM)
    └── TransactionSyncService.java  # Async transaction sync, O(1) deduplication
```

---

## Database Migrations

Flyway runs migrations on startup. JPA `ddl-auto: validate` — Flyway owns the schema.

| File | What It Does |
|------|-------------|
| `V1__Init_database.sql` | `users`, `transactions`, `categories`, `webhook_logs` |
| `V2__GoCardless_Migration.sql` | GoCardless support (deprecated) |
| `V3__Adjust_bank_configs_And_users.sql` | Add `last_active_at` to users, add indexes |
| `V4__Add_link_reference_to_bank_configs.sql` | Add `link_reference` (UUID) |
| `V5__Add_category_unique_constraint.sql` | Add `UNIQUE(user_id, name)` |
| `V6__Plaid_Migration.sql` | Introduces `plaid_items` and `bank_accounts` tables, adds `plaid_transaction_id` |
| `V7__Refactor_Plaid_Naming.sql` | Cleans up legacy naming and enforces Plaid nomenclature |

---

## Authentication & Security

### JWT Authentication Flow

```
POST /api/auth/register
  → Validate unique email (409 if duplicate)
  → BCrypt hash password (cost factor from encoder)
  → Save User entity
  → Return success message

POST /api/auth/login
  → Find user by email
  → BCrypt.matches(password, hash)
  → JwtUtils.generateToken(user)  [HS256, 24h, claims: email + fullName]
  → Return { token, fullName }

Every subsequent request:
  → JwtAuthenticationFilter.doFilterInternal()
  → Extract "Bearer <token>" from Authorization header
  → Load UserDetails from DB via UserDetailsService
  → Validate signature + expiration
  → Set SecurityContext → request is now authenticated
  → Update lastActiveAt (throttled: max once per hour per user)
```

### Security Configuration

```java
SecurityConfig.java
  .requestMatchers("/api/auth/**").permitAll()       // register, login
  .requestMatchers("/api/banks/webhook").permitAll() // Plaid Webhooks
  .anyRequest().authenticated()                      // everything else needs JWT
  .csrf(AbstractHttpConfigurer::disable)              // REST — no session cookies
  .sessionManagement(session -> STATELESS)            // No HttpSession used
```

- **Webhook is public** so Plaid can deliver real-time events.
- **User enumeration prevented**: both "email not found" and "wrong password" return the same error message

### `@AuthenticationPrincipal` vs `SecurityContextHolder`

```java
// Preferred approach
@GetMapping("/profile")
public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(userService.getProfile(user));
}
```

Spring resolves `@AuthenticationPrincipal` from the `SecurityContext` automatically. No cast needed.

---

## Transaction Management

### TransactionRequest Validation

```java
@Data
public class TransactionRequest {
    @NotNull(message = "Category name cannot be empty")
    private String category;       // auto-creates if new for this user

    @NotNull @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;     // BigDecimal — never float or double

    @NotNull(message = "Type is required (IN/OUT)")
    private TransactionType type;  // IN (income) or OUT (expense)

    private String description;    // optional
    private LocalDateTime transactionDate;  // optional, defaults to LocalDateTime.now()
    private Long bankAccountId;     // optional — links to bank account
}
```

### CSV Export & Sanitization

```java
// CSV injection prevention
private String sanitizeCsvValue(String value) {
    if (value == null) return "";
    if (value.startsWith("=") || value.startsWith("+") ||
        value.startsWith("-") || value.startsWith("@") ||
        value.startsWith("\t") || value.startsWith("\r"))
        return "'" + value;  // Prefix dangerous chars with single quote
    return value;
}
```

Why sanitize? A CSV cell starting with `=` executes as a formula in Excel/Google Sheets.

---

## Category Management

### Why delete nullifies transactions first?

```java
// CategoryService.deleteCategory()
transactionRepository.nullifyCategoryOnTransactions(category);  // SET category_id = NULL
categoryRepository.delete(category);
```

If a category is deleted without nullifying, the foreign key constraint on `transactions.category_id` would either reject the delete or cascade-delete all transactions. The transactions should survive the category's deletion.

---

## Bank Integration (Plaid)

We use Plaid to provide secure, stable access to bank transactions globally.

### Plaid Token Flow
1. **Link Token:** Backend calls `PlaidService.createLinkToken(user)`. It includes the `webhookUrl` to configure real-time updates.
2. **Public Token:** Frontend opens Plaid Link widget. User connects bank. Frontend receives `public_token`.
3. **Access Token:** Frontend sends `public_token` to Backend (`/api/banks/link/complete`). Backend exchanges it for a permanent `access_token` and `item_id`.
4. **Encryption:** `access_token` is encrypted in PostgreSQL using `AccessTokenEncryptor` (AES).

### Webhooks & Real-time Sync
Plaid pushes updates to `POST /api/banks/webhook`.
- **TRANSACTIONS (SYNC_UPDATES_AVAILABLE):** Triggers `transactionSyncService.syncItem(itemId)`.
- **ITEM (ERROR / PENDING_EXPIRATION):** Updates the `PlaidItem` status. Frontend reads this status to prompt the user to re-authenticate.

### Async & Performance Optimizations (Crucial)

**1. Fixing Detached Entities in `@Async`:**
Webhooks must respond within 10 seconds. We process sync asynchronously via `@Async`. Instead of passing the `PlaidItem` entity across threads (which causes `LazyInitializationException`), we pass the `String itemId` and query the DB inside the async method.
```java
@Async
@Transactional
public CompletableFuture<SyncResult> syncItem(String itemId) { ... }
```

**2. O(1) Memory Database Lookups:**
When processing removed or modified transactions from Plaid, the system avoids `transactionRepository.findAll()` (which causes $O(N)$ OutOfMemory exceptions) by using a direct index lookup:
```java
// Direct DB query replaces O(N) memory load with O(1) index lookup for performance.
Transaction tx = transactionRepository.findByPlaidTransactionIdAndBankAccount(modified.getTransactionId(), account);
```

**3. Idempotency:**
Duplicate transactions are gracefully ignored. The `saveAndFlush(tx)` is wrapped in a `try-catch` inside the sync loop. If a `DataIntegrityViolationException` occurs (due to unique constraints), the loop silently skips the duplicate and continues.

---

## Database Schema

```text
users ──1:N── transactions    users ──1:N── categories    users ──1:N── plaid_items
         │                         │                                        │
         └─── nullable FK          └─── nullable FK                     1:N bank_accounts
         (cash txns)              (orphaned txns preserved)                 │
                                                                       1:N sync_logs
```

### Key Relationships

| Relationship | Type | Meaning |
|-------------|------|--------|
| users → transactions | 1:N | A user owns many transactions |
| users → categories | 1:N | Each user's categories are isolated |
| users → plaid_items | 1:N | A user can link multiple banks |
| plaid_items → bank_accounts | 1:N | A bank connection has multiple accounts |
| bank_accounts → transactions | 1:N nullable | Cash transactions have no bank |

---

## Configuration Reference

### application.yaml (dev defaults)

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/postgres}
    username: ${DB_USERNAME}        # no fallback
    password: ${DB_PASSWORD}        # no fallback
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate: { ddl-auto: validate }  # Flyway owns schema
    show-sql: true
    properties: { hibernate: { format_sql: true } }
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET:MySuperStrongSuperLongSecretKeyForSecurity2806}
  expiration: 86400000  # 24 hours in ms

plaid:
  client-id: ${PLAID_CLIENT_ID:your-client-id}
  secret: ${PLAID_SECRET:your-secret}
  environment: ${PLAID_ENV:sandbox}
  webhook-url: ${PLAID_WEBHOOK_URL:}
```

### Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `DB_URL` | Prod | PostgreSQL JDBC URL |
| `DB_USERNAME` | Prod | Database user |
| `DB_PASSWORD` | Prod | Database password |
| `JWT_SECRET` | Prod | HS256 signing key (min 256-bit recommended) |
| `PLAID_CLIENT_ID` | Bank features | Plaid API Client ID |
| `PLAID_SECRET` | Bank features | Plaid API Secret |
| `PLAID_ENV` | Bank features | Plaid Environment (`sandbox`, `production`) |
| `PLAID_WEBHOOK_URL`| Bank features | Public Webhook URL for Plaid events |

---

## Error Handling

### GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)  // 400 — field errors
    @ExceptionHandler(BadRequestException.class)              // 400 — business rules
    @ExceptionHandler(ForbiddenException.class)               // 403 — unauthorized access
    @ExceptionHandler(ResourceNotFoundException.class)        // 404 — missing resource
    @ExceptionHandler(ConflictException.class)                // 409 — duplicate
    @ExceptionHandler(RuntimeException.class)                 // 500 — catch-all
}
```

### Ownership Verification Pattern

```java
// Every mutating service method verifies ownership:
if (!entity.getUser().getId().equals(user.getId())) {
    throw new ForbiddenException("You do not own this resource");
}
```

This is enforced at the **service layer**, not the controller, so it applies to all callers (REST API, future CLI, tests, etc.).