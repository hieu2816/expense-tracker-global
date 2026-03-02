# Expense Tracking API

A personal finance management REST API built with Spring Boot. Tracks income and expenses — both manually and automatically via Open Banking (GoCardless). Originally designed for Vietnam's Casso webhook, migrated to GoCardless Bank Account Data API for EU/UK coverage.

---

## Project Roadmap

| Phase | Name | Status | Description |
|-------|------|--------|-------------|
| 1 | [Core Foundation](#phase-1-core-foundation) | ✅ Complete | Database, Spring Boot setup, JWT authentication |
| 2 | [Business Logic & Manual Features](#phase-2-business-logic--manual-features) | ✅ Complete | Transaction CRUD, categories, dashboard stats, CSV export |
| 3 | [Bank Integration (GoCardless)](#phase-3-bank-integration-gocardless) | ✅ Complete | Open Banking linking, auto-sync, background scheduler |
| 4 | [Frontend & Real-time](#phase-4-frontend--real-time) | ⏳ Pending | Web dashboard, WebSocket live updates |
| 5 | [DevSecOps & Deploy](#phase-5-devsecops--deploy) | 🔧 Partial | Docker (DB only), needs app container, CI/CD, SSL |

---

## Phase Details

### Phase 1: Core Foundation
> *Database design, project setup, and authentication system*

- **Database (PostgreSQL 16):** Tables for `users`, `transactions`, `categories`, `bank_configs`, `sync_logs`
- **Schema Management:** Flyway migrations (3 migration files), JPA set to `validate` only
- **Authentication:** Register/Login with JWT (HS256, 24h expiry), BCrypt password hashing
- **Security:** Per-user data isolation — User A cannot access User B's data
- **User Profile:** View profile, update name, change password

### Phase 2: Business Logic & Manual Features
> *Core expense tracking features that work without bank integration*

- **Transaction CRUD:** Create, read, update, delete transactions with amount, type (IN/OUT), category, description, date
- **Categories:** Auto-created per-user when first used, typed as income (IN) or expense (OUT)
- **Dashboard:** Total income, total expense, calculated balance
- **CSV Export:** Download filtered transactions as spreadsheet
- **Filtering:** Paginated transaction list with category and date range filters
- **Bank/Cash Switching:** Transactions can be linked to a bank account or marked as cash

### Phase 3: Bank Integration (GoCardless)
> *Originally Casso webhook (Vietnam) → Migrated to GoCardless Open Banking (EU/UK)*

**Migration from Casso:** The original design used Casso's webhook to receive real-time bank notifications from Vietnamese banks. This was migrated to GoCardless Bank Account Data API (PSD2-compliant) for broader EU/UK bank coverage with a pull-based model.

- **Bank Browsing:** List available banks by country (Revolut, Barclays, HSBC, etc.)
- **Bank Linking:** OAuth-style flow — user authorizes on their bank's website
- **Initial Sync:** 90 days of transaction history imported on first link
- **Manual Sync:** On-demand transaction pull with custom date range
- **Background Scheduler:** Automatic sync every 15 minutes for active users
- **Sync History:** View past sync attempts, results, and errors
- **Unlink:** Disconnect bank while preserving all imported transactions
- **Deduplication:** Prevents duplicate transactions across overlapping syncs
- **Activity Tracking:** Skips syncing for users inactive >30 days
- **Expiry Detection:** Auto-detects expired bank access and marks accounts

### Phase 4: Frontend & Real-time
> *Web dashboard with real-time updates — NOT YET IMPLEMENTED*

**Planned features:**
- [ ] Dashboard page: balance, recent transactions, charts
- [ ] Settings page: bank account management, API key configuration
- [ ] Real-time updates via WebSocket (STOMP) — live transaction feed without page refresh
- [ ] Pie chart visualization by category

### Phase 5: DevSecOps & Deploy
> *Production deployment infrastructure — PARTIALLY IMPLEMENTED*

**Done:**
- [x] Docker Compose for PostgreSQL database

**Pending:**
- [ ] Dockerfile for the Spring Boot application
- [ ] Full `docker-compose.yml` with app + DB containers
- [ ] Deploy to VPS or cloud platform (Render/Railway)
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] SSL/HTTPS certificate configuration

---

## Quick Start

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Set environment variables
export GOCARDLESS_SECRET_ID=your_secret_id
export GOCARDLESS_SECRET_KEY=your_secret_key
export APP_BASE_URL=http://localhost:8080

# 3. Run the application
./mvnw spring-boot:run
```

---

## API Endpoints

### Authentication (`/api/auth`) — Public
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/register` | Create new account |
| POST | `/login` | Login, receive JWT token |

### User Profile (`/api/user`) — Requires JWT
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/profile` | View profile |
| PUT | `/profile` | Update display name |
| PUT | `/change-password` | Change password |

### Transactions (`/api/transactions`) — Requires JWT
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | Create transaction |
| GET | `/` | List transactions (paginated, filterable) |
| PUT | `/{id}` | Update transaction |
| DELETE | `/{id}` | Delete transaction |
| GET | `/dashboard` | Get income/expense/balance summary |
| GET | `/export` | Download transactions as CSV |

### Bank Operations (`/api/banks`) — Requires JWT
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/institutions?country=` | List available banks |
| POST | `/link` | Start bank linking |
| GET | `/callback?ref=` | GoCardless redirect callback (public) |
| GET | `/` | List linked bank accounts |
| GET | `/{id}` | Get specific bank account |
| POST | `/{id}/sync` | Trigger manual sync |
| GET | `/{id}/sync-history` | View sync history |
| DELETE | `/{id}` | Unlink bank account |

---

## Configuration

### Environment Variables
```bash
GOCARDLESS_SECRET_ID=xxx      # GoCardless API credentials
GOCARDLESS_SECRET_KEY=xxx     # GoCardless API credentials
APP_BASE_URL=http://host:port # Base URL for callback redirect
```

### Key Settings (`application.yaml`)
```yaml
jwt:
  secret: "your-jwt-secret"
  expiration: 86400000          # 24 hours

sync:
  enabled: true                 # Toggle background scheduler
  interval-minutes: 15          # Sync frequency
  look-back-days: 3             # Incremental sync window
  active-user-days: 30          # Skip inactive users
  initial-sync-days: 90         # Initial sync history depth
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.9 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | JWT (jjwt 0.11.5) + BCrypt |
| Security | Spring Security (stateless) |
| Bank API | GoCardless Bank Account Data API |
| CSV | Apache Commons CSV |
| Build | Maven |
| Containers | Docker (PostgreSQL) |

---

## Documentation

| Document | Description |
|----------|-------------|
| [Project Overview](./PROJECT_OVERVIEW.md) | High-level feature guide (abstraction level) |
| [Technical Reference](./TECHNICAL_REFERENCE.md) | Full implementation details (technical level) |
| [Phase 4 Bank Linking](./PHASE4_BANK_LINKING.md) | Detailed Phase 3 bank linking workflow documentation |

---

## External Resources

- [GoCardless Bank Account Data API Docs](https://developer.gocardless.com/bank-account-data/overview)
- [GoCardless Sandbox](https://bankaccountdata.gocardless.com/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
