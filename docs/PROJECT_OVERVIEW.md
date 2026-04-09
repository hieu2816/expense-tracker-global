# Expense Tracking Global — Project Overview

> **Audience:** Non-technical stakeholders, product managers, new team members  
> **Last Updated:** 2026-03-08

---

## What Is This?

A personal finance management system that helps users track their income and expenses. Users can record transactions manually or connect their bank accounts to automatically import transactions in the background.

The system was originally built for Vietnamese banks (via Casso webhook) and has been migrated to support EU/UK banks via GoCardless Open Banking.

---

## Project Phases & Status

| # | Phase | Status | Summary |
|---|-------|--------|---------|
| 1 | Core Foundation | ✅ Done | Database, authentication, user management |
| 2 | Business Logic | ✅ Done | Transaction management, categories, reports, export |
| 3 | Bank Integration | ✅ Done | GoCardless Open Banking, auto-sync, scheduler |
| 4 | Frontend & Real-time | ✅ Done | React SPA built — auth, dashboard, CRUD, bank linking. WebSocket pending |
| 5 | DevSecOps & Deploy | ✅ Done | Docker Compose, GitHub Actions CI/CD, Nginx, Certbot, EC2 |

---

## Features — What Users Can Do

### 🔐 Phase 1: Account & Security

| Feature | Description |
|---------|-------------|
| **Register** | Create an account with email, name, and password |
| **Login** | Receive a secure access token (valid 24 hours) |
| **View Profile** | See account information |
| **Update Profile** | Change display name |
| **Change Password** | Update password (requires current password) |
| **Data Isolation** | Each user can only see their own data |

**How it works:** User registers → logs in → receives a JWT token → includes token in every API request → server verifies token and identifies the user.

---

### 📊 Phase 2: Transaction Management

| Feature | Description |
|---------|-------------|
| **Add Transaction** | Record income or expense with amount, category, description, date |
| **View Transactions** | Browse with pagination, filter by category and date range |
| **Edit Transaction** | Update any field, switch between cash and bank |
| **Delete Transaction** | Remove a transaction (ownership verified) |
| **Dashboard** | See total income, total expense, and balance |
| **Export CSV** | Download transactions as a spreadsheet file |
| **Auto-Categories** | Categories are created automatically when first used |

**How categories work:**
- When a user creates a transaction with category "Food" for the first time, the system auto-creates a "Food" category for that user
- Categories are per-user (your categories are separate from other users')
- Categories are typed: each is associated with income (IN) or expense (OUT)

**Transaction types:**
- **IN** — Income (salary, refund, transfer received)
- **OUT** — Expense (food, rent, transportation)

**Cash vs. Bank:**
- Transactions can be manual (cash) or linked to a bank account
- Users can switch a transaction between cash and bank at any time

---

### 🏦 Phase 3: Bank Integration (GoCardless)

| Feature | Description |
|---------|-------------|
| **Browse Banks** | See available banks by country (Revolut, Barclays, HSBC, etc.) |
| **Link Bank** | Connect a bank via secure redirect to the bank's website |
| **Initial Import** | Automatically imports 90 days of transaction history on first link |
| **Auto Sync** | Background job checks for new transactions every 15 minutes |
| **Manual Sync** | Pull latest transactions on demand with custom date range |
| **Sync History** | View past sync attempts, results, and errors |
| **Unlink Bank** | Disconnect a bank while keeping all imported transactions |
| **Smart Filtering** | Skips syncing for users inactive for 30+ days |
| **Expiry Detection** | Detects expired bank access and marks accounts accordingly |

**Bank linking flow — simplified:**

```
1. User clicks "Link Bank"
2. Selects their bank (e.g., Revolut)
3. Redirected to bank's own website to authorize
4. Bank redirects back to our system
5. System imports 90 days of transaction history
6. Background scheduler keeps syncing every 15 minutes
```

**Why GoCardless instead of Casso?**
- Casso only supports Vietnamese banks and requires downloading their app
- GoCardless supports 2,500+ banks across 31 EU/UK countries
- GoCardless uses Open Banking (PSD2), a regulated standard
- Pull-based model (we fetch transactions) instead of push-based (webhook)

**Deduplication:** When syncing overlapping date ranges, the system prevents duplicate transactions by checking the bank's unique transaction ID + bank account combination.

---

### 🖥 Phase 4: Frontend (Partially Built)

A React web application that connects to the backend API:

| Feature | Description |
|---------|-------------|
| **Login / Register** | Email + password authentication with form validation |
| **Dashboard** | Income/expense/balance cards, spending pie chart, recent transactions |
| **Transactions** | Full table with filtering, pagination, add/edit/delete, CSV export |
| **Categories** | Manage spending categories with emoji icons |
| **Bank Accounts** | Link banks, sync transactions, view sync history, unlink |
| **Profile** | Edit display name and change password |

**How it works:** User opens the web app → logs in → receives a JWT token → token is automatically included in every API request → pages load data from the backend and display it in a professional UI.

**Tech:** React 19, Vite 7 (build tool), Ant Design 6 (UI components), Recharts (charts), Axios (HTTP client)

**Not Yet Built:**
- Real-time updates via WebSocket (transactions appear live without refreshing)

---

### 🚀 Phase 5: DevSecOps & Deploy

| Item | Status | Description |
|------|--------|-------------|
| Docker (Database) | ✅ Done | PostgreSQL in Docker container, exposed on host port 5433 |
| Docker (App) | ✅ Done | Multi-stage Maven Dockerfile, runs via docker-compose |
| Docker (Nginx) | ✅ Done | Multi-stage Node + Nginx, serves React build, proxies /api |
| Cloud Deploy | ✅ Done | AWS EC2 (spendwiser.me) |
| CI/CD | ✅ Done | GitHub Actions deploy.yml — build test + SSH deploy to EC2 |
| SSL/HTTPS | ✅ Done | Certbot + Let's Encrypt via nginx/certbot containers |

---

## How the System Works — Big Picture

```
┌──────────────┐        ┌────────────────────────┐        ┌────────────┐
│   React SPA  │  JWT   │                        │  SQL   │            │
│   (Vite)     │───────▶│   Expense Tracker API  │───────▶│ PostgreSQL │
│              │◀───────│   (Spring Boot)        │◀───────│ (Database) │
│  port 5173   │  JSON  │   port 8080            │        │ 5433:5432* │
└──────────────┘        └───────────┬────────────┘        └────────────┘
                                    │
                              Every 15 min
                                    │
                                    ▼
                        ┌────────────────────────┐
                        │   GoCardless API        │
                        │   (Open Banking)        │
                        │                        │
                        │   2,500+ EU/UK banks   │
                        └────────────────────────┘
```

1. **User registers and logs in** → receives a JWT token
2. **Manual tracking:** User creates transactions by hand (always available)
3. **Bank linking:** User connects their bank → system imports 90 days of history
4. **Auto-sync:** Every 15 minutes, the scheduler pulls new transactions from linked banks
5. **Dashboard:** User views total income, expenses, and balance
6. **Export:** User downloads their data as CSV

---

## Data Relationships

```
User
 ├── has many Transactions (manual or bank-imported)
 ├── has many Categories (auto-created per user)
 └── has many BankConfigs (linked bank accounts)
      └── has many SyncLogs (sync history per account)
```

---

## API Summary

| Area | # Endpoints | Auth | Description |
|------|------------|------|-------------|
| Authentication | 2 | Public | Register, Login |
| User Profile | 3 | JWT | View, Update, Change Password |
| Transactions | 6 | JWT | CRUD, Dashboard, CSV Export |
| Categories | 4 | JWT | List, Create, Update, Delete |
| Bank Operations | 8 | JWT* | Link, Sync, History, Unlink |

*The bank callback endpoint is public (GoCardless redirects without JWT)

**Total: 23 API endpoints**
