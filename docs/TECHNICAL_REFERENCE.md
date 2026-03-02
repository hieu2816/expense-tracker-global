# Expense Tracking Global — Technical Reference

> **Audience:** Developers, code reviewers, technical leads  
> **Last Updated:** 2026-03-03  
> **Stack:** Java 21 · Spring Boot 3.5.9 · PostgreSQL 16 · Maven · Flyway · JWT (jjwt 0.11.5) · GoCardless API · Lombok · Apache Commons CSV

---

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Environment & Infrastructure](#environment--infrastructure)
4. [Phase 1: Authentication & Security](#phase-1-authentication--security)
5. [Phase 2: Transaction Management](#phase-2-transaction-management)
6. [Phase 3: Bank Integration (GoCardless)](#phase-3-bank-integration-gocardless)
7. [Phase 4: Frontend & Real-time](#phase-4-frontend--real-time-not-implemented)
8. [Phase 5: DevSecOps & Deploy](#phase-5-devsecops--deploy)
9. [Database Schema](#database-schema)
10. [Data Flow Diagrams](#data-flow-diagrams)
11. [Configuration Reference](#configuration-reference)
12. [Error Handling](#error-handling)
13. [Known Issues & Potential Improvements](#known-issues--potential-improvements)

---

## Architecture

```
                            ┌──────────────────────────────────────┐
                            │           Spring Boot App            │
┌──────────┐                │                                      │
│          │   HTTP/JWT     │  Controller ──▶ Service ──▶ Repository│
│  Client  │───────────────▶│       │                       │      │
│          │◀───────────────│       ▼                       ▼      │
└──────────┘                │  DTOs/Validation         PostgreSQL  │
                            │                                      │
                            │  ┌─────────────────────────────────┐ │
                            │  │  TransactionSyncScheduler       │ │
                            │  │  (@Scheduled every 15 min)      │ │
                            │  └──────────┬──────────────────────┘ │
                            │             ▼                        │
                            │  GoCardlessService ──▶ GoCardless API│
                            └──────────────────────────────────────┘
```

**Layers:**
| Layer | Responsibility | Pattern |
|-------|---------------|---------|
| Controller | REST endpoints, request validation, user extraction | `@RestController` |
| Service | Business logic, ownership verification, data mapping | `@Service`, `@Transactional` |
| Repository | Data access, custom JPQL queries | `JpaRepository` |
| Config | Security, beans, external API credentials | `@Configuration` |
| Scheduler | Background tasks | `@Scheduled`, `@ConditionalOnProperty` |
| DTO | Request/response data shapes, validation | `@Data`, `@Builder`, Jakarta Validation |
| Entity | JPA-mapped database tables | `@Entity`, Lombok |

---

## Project Structure

```
src/main/java/com/example/expense_tracking/
│
├── config/
│   ├── ApplicationConfig.java        # UserDetailsService, PasswordEncoder, AuthProvider
│   ├── GoCardlessConfig.java         # API credentials + RestTemplate bean
│   ├── JwtAuthenticationFilter.java  # JWT extraction, validation, lastActiveAt tracking
│   ├── SecurityConfig.java           # Endpoint permissions, stateless session
│   └── SyncConfig.java               # Scheduler tuning (interval, lookback, etc.)
│
├── controller/
│   ├── AuthController.java           # POST /register, /login
│   ├── UserController.java           # GET/PUT /profile, PUT /change-password
│   ├── TransactionController.java    # CRUD, dashboard, CSV export
│   └── BankController.java           # Bank linking, sync, history, unlink
│
├── dto/
│   ├── RegisterRequest.java          # { email, password, fullName }
│   ├── LoginRequest.java             # { email, password }
│   ├── LoginResponse.java            # { token, fullName }
│   ├── ChangePasswordRequest.java    # { currentPassword, newPassword }
│   ├── UpdateProfileRequest.java     # { fullName }
│   ├── TransactionRequest.java       # { category, amount, type, description, date, bankConfigId }
│   ├── TransactionResponse.java      # { id, category, amount, type, description, date }
│   ├── CategoryDTO.java              # { id, name, type }
│   ├── DashBoardResponse.java        # { totalIncome, totalExpense, balance }
│   ├── bank/                         # LinkBankRequest/Response, CallbackResponse,
│   │                                 # BankAccountResponse, SyncRequest/Response
│   └── gocardless/                   # GCInstitution, GCRequisitionRequest/Response,
│                                     # GCTokenResponse, GCAccountDetails, GCTransaction, etc.
│
├── entity/
│   ├── User.java                     # implements UserDetails (Spring Security)
│   ├── Transaction.java              # UNIQUE(bank_transaction_id, bank_config_id)
│   ├── Category.java                 # per-user, typed (IN/OUT)
│   ├── TransactionType.java          # enum: IN, OUT
│   ├── BankConfig.java               # bank connection state machine
│   └── SyncLog.java                  # sync attempt audit trail
│
├── exception/
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice: validation + runtime errors
│
├── repository/
│   ├── UserRepository.java           # findByEmail, existsByEmail
│   ├── TransactionRepository.java    # filtered queries, calculateTotal, dedup check, unlink
│   ├── CategoryRepository.java       # findByNameAndUser
│   ├── BankConfigRepository.java     # findByStatus, findByUser, findByIdAndUser
│   └── SyncLogRepository.java        # findByBankConfig ordered by syncedAt desc
│
├── scheduler/
│   └── TransactionSyncScheduler.java # @Scheduled background sync
│
├── service/
│   ├── AuthService.java              # register (BCrypt), login (JWT generation)
│   ├── UserService.java              # updateProfile, changePassword
│   ├── TransactionService.java       # CRUD, dashboard, CSV export, auto-create categories
│   ├── GoCardlessService.java        # GoCardless REST client with token caching
│   ├── BankLinkingService.java       # bank linking orchestration, IBAN masking
│   └── TransactionSyncService.java   # batch sync, initial sync, dedup, SyncLog
│
└── utils/
    └── JwtUtils.java                 # generate, extract, validate (HS256, 24h)
```

---

## Environment & Infrastructure

### Docker Compose (PostgreSQL)

```yaml
services:
  database:
    image: postgres:16
    container_name: expense-db-global
    ports: "5433:5432"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: MinhHieu2816@
      POSTGRES_DB: postgres
    volumes:
      - expense_data-global:/var/lib/postgresql/data
```

### Flyway Migrations

| File | Description |
|------|-------------|
| `V1__Init_database.sql` | Core tables: users, transactions, categories |
| `V2__GoCardless_Migration.sql` | Bank tables: bank_configs, sync_logs, bank fields on transactions |
| `V3__Adjust_bank_configs_And_users.sql` | Added last_active_at on users, bank_config adjustments |

JPA is set to `ddl-auto: validate` — Flyway owns the schema, JPA only validates it matches entities.

---

## Phase 1: Authentication & Security

### JWT Authentication Flow

```
POST /api/auth/register
  → Validate unique email
  → BCrypt hash password
  → Save User entity
  → Return success message

POST /api/auth/login
  → Find user by email
  → BCrypt.matches(password, hash)
  → JwtUtils.generateToken(user) [HS256, 24h, claims: email + fullName]
  → Return { token, fullName }

Every subsequent request:
  → JwtAuthenticationFilter.doFilterInternal()
  → Extract "Bearer <token>" from Authorization header
  → Extract email from token claims
  → Load UserDetails from DB via UserDetailsService
  → Validate token (signature + expiration check)
  → Set SecurityContext → user is now "logged in" for this request
  → Update lastActiveAt (throttled: max once per hour)
```

### Security Configuration

```java
// SecurityConfig.java — endpoint permissions
.requestMatchers("/api/auth/**").permitAll()         // register, login
.requestMatchers("/api/webhook/**").permitAll()       // legacy webhook
.requestMatchers("/api/banks/callback").permitAll()   // GoCardless redirect
.anyRequest().authenticated()                         // everything else needs JWT
```

- CSRF disabled (stateless REST API)
- Session management: `STATELESS`
- Password encoding: `BCryptPasswordEncoder`
- User lookup: `UserRepository.findByEmail()` via `UserDetailsService` bean

### User Profile Endpoints

| Endpoint | Method | Service Method | Logic |
|----------|--------|---------------|-------|
| `/api/user/profile` | GET | — | Extract User from SecurityContext |
| `/api/user/profile` | PUT | `updateProfile()` | Set fullName → save |
| `/api/user/change-password` | PUT | `changePassword()` | Verify current password → BCrypt new → save |

### Key Beans (ApplicationConfig.java)

| Bean | Type | Purpose |
|------|------|---------|
| `userDetailsService` | `UserDetailsService` | Loads user by email |
| `authenticationProvider` | `DaoAuthenticationProvider` | Connects DB + BCrypt |
| `authenticationManager` | `AuthenticationManager` | Spring Security auth flow |
| `passwordEncoder` | `BCryptPasswordEncoder` | Password hashing |

---

## Phase 2: Transaction Management

### Endpoints

| Endpoint | Method | Input | Output |
|----------|--------|-------|--------|
| `/api/transactions` | POST | `TransactionRequest` | `Transaction` entity |
| `/api/transactions` | GET | `?page, size, category, startDate, endDate` | `Page<TransactionResponse>` |
| `/api/transactions/{id}` | PUT | `TransactionRequest` | `TransactionResponse` |
| `/api/transactions/{id}` | DELETE | — | 204 No Content |
| `/api/transactions/dashboard` | GET | — | `DashBoardResponse` |
| `/api/transactions/export` | GET | `?category, startDate, endDate` | CSV file download |

### TransactionRequest Validation

```java
@NotNull  private String category;           // auto-creates if new
@NotNull @Positive  private BigDecimal amount;
@NotNull  private TransactionType type;       // IN or OUT
          private String description;         // optional
          private LocalDateTime transactionDate; // optional, defaults to now
          private Long bankConfigId;          // optional, links to bank account
```

### Create Transaction Logic

```
1. Resolve transactionDate (default: now)
2. Find or auto-create Category for user
3. If bankConfigId provided → verify exists + user ownership
4. Generate manual ID: "MANUAL_{timestamp}_{random}"
5. Build & save Transaction entity
```

### Update Transaction — Bank/Cash Switch Logic

| `bankConfigId` value | Behavior |
|---------------------|----------|
| `null` (not sent) | Keep existing bank/cash assignment |
| `0` or negative | Switch to cash (set bankConfig = null) |
| Positive ID | Switch to that bank account (verify ownership) |

### Dashboard Calculation

```java
BigDecimal totalIncome  = transactionRepository.calculateTotal(user, TransactionType.IN);
BigDecimal totalExpense = transactionRepository.calculateTotal(user, TransactionType.OUT);
BigDecimal balance      = totalIncome.subtract(totalExpense);
```

### CSV Export

- Uses Apache Commons CSV
- Headers: `ID, Date, Type, Category, Amount, Description`
- Max 100,000 rows per export
- Sets `Content-Disposition: attachment; filename=transactions.csv`
- Supports same filters as the list endpoint (category, startDate, endDate)

---

## Phase 3: Bank Integration (GoCardless)

### Migration Context

Originally, this project used **Casso** (Vietnamese bank webhook) for real-time transaction notifications. The Casso approach was push-based: Casso sent webhooks when bank transactions occurred. This was migrated to **GoCardless Bank Account Data API** (PSD2 Open Banking) for EU/UK coverage. GoCardless uses a pull-based model: the application fetches transactions on a schedule.

### GoCardless API Integration

**Service:** `GoCardlessService.java` — REST client using `RestTemplate`

| Method | GoCardless Endpoint | Purpose |
|--------|-------------------|---------|
| `getAccessToken()` | `POST /api/v2/token/new/` | OAuth token (cached, auto-refreshed) |
| `getInstitutions()` | `GET /api/v2/institutions/?country=` | List available banks |
| `createRequisition()` | `POST /api/v2/requisitions/` | Start bank linking session |
| `getRequisition()` | `GET /api/v2/requisitions/{id}/` | Check linking status + account IDs |
| `getAccountDetails()` | `GET /api/v2/accounts/{id}/details/` | IBAN, owner name |
| `getTransactions()` | `GET /api/v2/accounts/{id}/transactions/` | Fetch transactions by date range |

**Token Caching:** Access token is stored in memory. Before each API call, checks if token exists and won't expire within 60 seconds. If invalid → requests new token.

### Bank Linking Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/banks/institutions` | GET | JWT | List banks by country |
| `/api/banks/link` | POST | JWT | Start linking (returns redirect URL) |
| `/api/banks/callback` | GET | **Public** | GoCardless browser redirect |
| `/api/banks` | GET | JWT | List user's linked accounts |
| `/api/banks/{id}` | GET | JWT | Get specific account |
| `/api/banks/{id}/sync` | POST | JWT | Manual sync |
| `/api/banks/{id}/sync-history` | GET | JWT | Paginated sync logs |
| `/api/banks/{id}` | DELETE | JWT | Unlink (preserves transactions) |

### BankConfig Status State Machine

```
PENDING ──▶ LINKED ──▶ EXPIRED
               │
               └──▶ ERROR
```

| Status | Meaning | Transition |
|--------|---------|-----------|
| PENDING | User started linking, not yet authorized | Set on link creation |
| LINKED | Active, syncing transactions | Set after successful callback |
| EXPIRED | Bank access consent expired | Auto-detected by scheduler |
| ERROR | Authorization rejected or API failure | Set on callback error |

### Transaction Sync Pipeline

**Sync Service:** `TransactionSyncService.java`

| Method | When Called | Logic |
|--------|-----------|-------|
| `initialSync()` | After bank linking callback | Fetches 90 days of history |
| `syncTransactions()` | Manual sync + scheduler | Fetches date range, deduplicates, saves |
| `syncAllActiveAccounts()` | Scheduler (every 15 min) | Iterates all LINKED accounts |
| `createSyncLog()` | After every sync attempt | Records status, counts, errors |

**Deduplication:** Two layers prevent duplicate imports:
1. **Application layer:** `existsByBankTransactionIdAndBankConfig()` check before saving
2. **Database layer:** `UNIQUE(bank_transaction_id, bank_config_id)` constraint

**Unlink Behavior:** `unlinkBank()` sets `transaction.bankConfig = null` for all linked transactions via `unlinkTransactionsFromBankConfig()` JPQL query, then deletes the `BankConfig`. **Transactions are preserved.**

### Background Scheduler

**Class:** `TransactionSyncScheduler.java`

```java
@Component
@ConditionalOnProperty(name = "sync.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionSyncScheduler {
    @Scheduled(
        fixedRateString = "#{${sync.interval-minutes:15} * 60 * 1000}",
        initialDelayString = "#{${sync.interval-minutes:15} * 60 * 1000}"
    )
    public void scheduledSync() { ... }
}
```

| Feature | Implementation |
|---------|---------------|
| Toggle on/off | `@ConditionalOnProperty(sync.enabled)` |
| Configurable interval | SpEL reads `sync.interval-minutes` from YAML |
| Initial delay | Waits one full interval before first run |
| Error resilience | Top-level try-catch prevents scheduler thread death |
| Active user filter | `isUserActive()` checks `lastActiveAt` vs `activeUserDays` |
| Expiry detection | `checkAndUpdateExpiredAccess()` auto-marks expired accounts |

**User Activity Tracking:** `JwtAuthenticationFilter` updates `user.lastActiveAt` after successful JWT authentication, throttled to at most once per hour to reduce DB writes.

---

## Phase 4: Frontend & Real-time (Not Implemented)

**Planned:**
- Dashboard UI (Thymeleaf or separate SPA)
- Real-time transaction feed via Spring WebSocket (STOMP)
- Pie chart by category
- Bank account settings page

---

## Phase 5: DevSecOps & Deploy

| Component | Status | Details |
|-----------|--------|---------|
| Docker (PostgreSQL) | ✅ Done | `docker-compose.yaml` with postgres:16 |
| Docker (App) | ❌ Pending | Needs `Dockerfile` for Spring Boot |
| CI/CD | ❌ Pending | GitHub Actions pipeline |
| Cloud Deploy | ❌ Pending | VPS or Render/Railway |
| SSL/HTTPS | ❌ Pending | Required for GoCardless callbacks in production |

---

## Database Schema

```
┌─────────────┐       ┌──────────────┐       ┌──────────────┐
│    users     │──1:N──│ bank_configs  │──1:N──│  sync_logs   │
│              │       │              │       │              │
│ id (PK)      │       │ id (PK)      │       │ id (PK)      │
│ email (UQ)   │       │ user_id (FK) │       │ bank_config_id│
│ password_hash│       │ institution_* │      │ synced_at    │
│ full_name    │       │ requisition_id│      │ date_from/to │
│ is_active    │       │ gc_account_id │      │ txns_fetched │
│ last_active_at│      │ iban         │       │ txns_new     │
│ created_at   │       │ account_name │       │ status       │
└──────┬──────┘       │ status       │       │ error_message│
       │              │ access_exp_at│       │ created_at   │
       │              │ last_synced_at│      └──────────────┘
       │              │ created_at   │
       │              └──────┬───────┘
       │                     │
       │ 1:N          N:1 (nullable)
       ▼                     ▼
┌──────────────┐     ┌──────────────┐
│ transactions │     │  categories  │
│              │     │              │
│ id (PK)      │     │ id (PK)      │
│ user_id (FK) │     │ user_id (FK) │
│ bank_config_id│    │ name         │
│ category_id  │     │ type (IN/OUT)│
│ amount       │     │ icon         │
│ currency     │     │ created_at   │
│ description  │     └──────────────┘
│ txn_date     │
│ type (IN/OUT)│
│ bank_txn_id  │
│ created_at   │
└──────────────┘

Constraints:
  transactions: UNIQUE(bank_transaction_id, bank_config_id)
  users: UNIQUE(email)
```

### Key Relationships
- `users` 1:N `transactions` — a user owns many transactions
- `users` 1:N `categories` — categories are per-user
- `users` 1:N `bank_configs` — a user can link multiple bank accounts
- `bank_configs` 1:N `sync_logs` — each sync attempt is logged
- `bank_configs` 1:N `transactions` — nullable FK (cash transactions have no bank)
- `categories` 1:N `transactions` — each transaction has one category

---

## Data Flow Diagrams

### Manual Transaction Creation
```
Client → POST /api/transactions { category: "Food", amount: 25.50, type: OUT }
  → JwtAuthenticationFilter (extract user from token)
  → TransactionController.createTransaction()
  → TransactionService.createTransaction()
    → CategoryRepository.findByNameAndUser("Food", user)
      → not found? → auto-create & save new Category
    → Generate MANUAL_{timestamp}_{random} ID
    → Build Transaction entity (user, category, amount, type, date)
    → TransactionRepository.save()
  → Return Transaction JSON
```

### Bank Linking + Initial Sync
```
1. POST /api/banks/link { institutionId: "REVOLUT_REVOGB21" }
     → GoCardlessService.createRequisition()
     → Save BankConfig (status=PENDING)
     → Return { link: "https://ob.gocardless.com/...", requisitionId: "req_abc" }

2. User opens link → authorizes on Revolut website

3. Revolut → redirects browser → GET /api/banks/callback?ref=user_1_1699999999
     → Parse ref → find PENDING BankConfig
     → GoCardlessService.getRequisition() → verify status=LN (linked)
     → For each account in requisition:
       → getAccountDetails() → IBAN, owner name
       → Update BankConfig (status=LINKED, accessExpiresAt=now+90d)
       → initialSync() → fetch 90 days of transactions
         → getTransactions(dateFrom, dateTo)
         → Deduplicate → save new → create SyncLog
     → Return CallbackResponse { status: SUCCESS, accounts: [...], txnsSynced: 47 }
```

### Background Scheduler
```
Every 15 minutes (after initial delay):
  TransactionSyncScheduler.scheduledSync()
    → TransactionSyncService.syncAllActiveAccounts()
      → BankConfigRepository.findByStatus("LINKED")
      → For each BankConfig:
        → isUserActive(user)? → skip if lastActiveAt > 30 days ago
        → checkAndUpdateExpiredAccess()? → skip + set status=EXPIRED
        → syncTransactions(bankConfig, now-3days, now)
          → GoCardlessService.getTransactions(accountId, dateFrom, dateTo)
          → For each transaction:
            → existsByBankTransactionIdAndBankConfig()? → skip (duplicate)
            → Build Transaction entity → save
          → Create SyncLog (SUCCESS/FAILED)
      → Log: "Batch sync complete. Success: X, Skipped: Y, Failed: Z"
```

---

## Configuration Reference

```yaml
# application.yaml — complete reference
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/postgres
    username: postgres
    password: MinhHieu2816@
  jpa:
    hibernate:
      ddl-auto: validate              # Flyway owns schema
    properties:
      hibernate:
        format_sql: true
    show-sql: true
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

jwt:
  secret: "MySuperStrongSuperLongSecretKeyForSecurity2806"
  expiration: 86400000                 # 24 hours in milliseconds

gocardless:
  secret-id: ${GOCARDLESS_SECRET_ID}
  secret-key: ${GOCARDLESS_SECRET_KEY}
  base-url: https://bankaccountdata.gocardless.com
  redirect-url: ${APP_BASE_URL:http://localhost:8080}/api/banks/callback

sync:
  enabled: true                        # Toggle scheduler on/off
  interval-minutes: 15                 # How often to sync
  look-back-days: 3                    # Incremental sync window
  active-user-days: 30                 # Skip users inactive this long
  initial-sync-days: 90                # First sync pulls this much history
```

### Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `GOCARDLESS_SECRET_ID` | Yes (for bank features) | GoCardless API credential |
| `GOCARDLESS_SECRET_KEY` | Yes (for bank features) | GoCardless API credential |
| `APP_BASE_URL` | Yes (for bank features) | Base URL for callback redirect |

---

## Error Handling

### GlobalExceptionHandler

| Exception | HTTP Status | Response |
|-----------|-------------|----------|
| `MethodArgumentNotValidException` | 400 Bad Request | `{ fieldName: "error message", ... }` |
| `RuntimeException` | 500 Internal Server Error | `{ error: "message" }` |

### Business Error Messages

| Error | Thrown By | Context |
|-------|----------|---------|
| "Email already exists" | `AuthService.register()` | Duplicate registration |
| "Email not found" | `AuthService.login()` | Invalid login |
| "Passwords do not match" | `AuthService.login()` | Wrong password |
| "Current password is incorrect" | `UserService.changePassword()` | Password change |
| "You do not own this transaction" | `TransactionService` | Ownership violation |
| "You do not own this bank account" | `TransactionService` | Bank ownership violation |
| "Transaction not found" | `TransactionService` | Missing resource |
| "Bank account not found" | `BankLinkingService` | Missing resource |
| "Bank account is not linked" | `BankLinkingService.manualSync()` | Sync on non-LINKED account |
| "Bank access has expired" | `BankLinkingService.manualSync()` | Expired bank connection |

### Bank-Specific Error Handling
- `CallbackResponse` and `SyncResponse` use `status` field (`SUCCESS`/`FAILED`) instead of exceptions
- Scheduler catches exceptions per-account — one failed account doesn't stop others
- `SyncLog` records every attempt with status, counts, and error messages
- Scheduler wraps everything in a top-level try-catch to prevent thread death

---

## Known Issues & Potential Improvements

### Code Issues
- **`UserController`** uses `@Controller` instead of `@RestController` — works but inconsistent with other controllers
- **`startLinking()`** hardcodes `"GB"` when looking up institution name/logo — should use the user's selected country
- **JWT secret** is hardcoded in `application.yaml` — should use `${JWT_SECRET}` environment variable in production
- **`exportToCsv()`** loads up to 100K rows into memory — may need streaming for large datasets

### Security
- No rate limiting on auth endpoints (vulnerable to brute-force)
- No refresh token mechanism (users must re-login after 24 hours)
- No input sanitization warnings for log injection (log messages include user input)

### Testing
- No unit tests or integration tests exist in the project
- GoCardless integration requires sandbox credentials for testing

### Pending Phases
- **Phase 4 (Frontend):** No UI exists — this is API-only
- **Phase 5 (Deploy):** No app Dockerfile, no CI/CD, no SSL configured
