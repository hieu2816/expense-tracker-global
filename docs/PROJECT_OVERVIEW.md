# Expense Tracking Global — Project Overview

> **Audience:** Non-technical stakeholders, product managers, new team members  
> **Last Updated:** 2026-05-12

---

## What Is This?

A personal finance management system that helps users track their income and expenses. Users can record transactions manually or connect their bank accounts to automatically import transactions in the background.

The system was originally built for Vietnamese banks (via Casso webhook), then migrated to GoCardless, and is now powered by **Plaid Open Banking** for global bank connectivity and real-time transaction webhooks.

---

## Project Phases & Status

| # | Phase | Status | Summary |
|---|-------|--------|---------|
| 1 | Core Foundation | ✅ Done | Database, authentication, user management |
| 2 | Business Logic | ✅ Done | Transaction management, categories, reports, export |
| 3 | Bank Integration | ✅ Done | Plaid Open Banking, Plaid Link, Real-time Webhooks |
| 4 | Frontend & Real-time | ✅ Done | React SPA built — auth, dashboard, CRUD, bank linking. |
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

### 🏦 Phase 3: Bank Integration (Plaid)

| Feature | Description |
|---------|-------------|
| **Browse Banks** | Select banks via the interactive Plaid Link widget |
| **Link Bank** | Connect securely without leaving the app |
| **Initial Import** | Automatically imports 90 days of transaction history on first link |
| **Auto Sync** | Real-time transaction synchronization driven by Plaid Webhooks |
| **Manual Sync** | Pull latest transactions on demand |
| **Sync History** | View past sync attempts, results, and errors |
| **Unlink Bank** | Disconnect a bank while keeping all imported transactions |
| **Expiry Detection** | Detects expired bank access via webhooks and marks accounts for re-auth |

**Bank linking flow — simplified:**
1. User clicks "Link Bank"
2. Plaid Link widget opens securely inside the app
3. User selects their bank and authorizes access
4. Widget succeeds and backend stores the secure access token
5. System imports transaction history
6. **Webhooks** listen for new transactions 24/7 and sync them instantly.

**Why Plaid instead of Casso or GoCardless?**
- Casso only supports Vietnamese banks.
- Plaid provides superior global coverage (US, UK, EU, Canada).
- Plaid supports real-time **Webhooks**, allowing our system to receive instant notifications when new transactions hit the user's bank, eliminating the need for aggressive polling.

---

### 🖥 Phase 4: Frontend (Partially Built)

A React web application that connects to the backend API:

| Feature | Description |
|---------|-------------|
| **Login / Register** | Email + password authentication with form validation |
| **Dashboard** | Income/expense/balance cards, spending pie chart, recent transactions |
| **Transactions** | Full table with filtering, pagination, add/edit/delete, CSV export |
| **Categories** | Manage spending categories with emoji icons |
| **Bank Accounts** | Link banks via Plaid widget, sync transactions, view history, unlink |
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

```text
┌──────────────┐        ┌────────────────────────┐        ┌────────────┐
│   React SPA  │  JWT   │                        │  SQL   │            │
│   (Vite)     │───────▶│   Expense Tracker API  │───────▶│ PostgreSQL │
│              │◀───────│   (Spring Boot)        │◀───────│ (Database) │
│  port 5173   │  JSON  │   port 8080            │        │ 5433:5432* │
└──────────────┘        └───────────┬────────────┘        └────────────┘
                                    │
                              Webhooks (Push) &
                              Manual Sync (Pull)
                                    │
                                    ▼
                         ┌────────────────────────┐
                         │       Plaid API        │
                         │     (Open Banking)     │
                         │                        │
                         │   Global Bank Access   │
                         └────────────────────────┘
```

1. **User registers and logs in** → receives a JWT token
2. **Manual tracking:** User creates transactions by hand (always available)
3. **Bank linking:** User connects their bank via Plaid → system imports 90 days of history
4. **Auto-sync:** Plaid sends webhooks when new transactions are available, triggering instant sync
5. **Dashboard:** User views total income, expenses, and balance
6. **Export:** User downloads their data as CSV

---

## Data Relationships

```
User
 ├── has many Transactions (manual or bank-imported)
 ├── has many Categories (auto-created per user)
 └── has many PlaidItems (linked bank connections)
      └── has many BankAccounts (individual accounts within a bank)
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
| Bank Operations | 6 | JWT* | Link, Sync, History, Unlink, Webhook |

*The Plaid Webhook endpoint (`/api/banks/webhook`) is public so Plaid can reach it.

**Total: 21 API endpoints**
