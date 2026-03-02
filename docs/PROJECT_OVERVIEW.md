# Expense Tracking Global — Project Overview

> **Audience:** Non-technical stakeholders, product managers, new team members  
> **Last Updated:** 2026-03-03

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
| 4 | Frontend & Real-time | ⏳ Not Started | Web dashboard with live updates |
| 5 | DevSecOps & Deploy | 🔧 Partial | Docker DB exists, needs app container & CI/CD |

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

### 🖥 Phase 4: Frontend & Real-time (Not Yet Built)

**Planned features:**
- Web dashboard showing balance, recent transactions, and category charts
- Settings page for managing bank connections
- Real-time updates via WebSocket — live transaction feed without refreshing
- Pie chart visualization by spending category

---

### 🚀 Phase 5: DevSecOps & Deploy (Partially Done)

| Item | Status | Description |
|------|--------|-------------|
| Docker (Database) | ✅ Done | PostgreSQL runs in a Docker container |
| Docker (App) | ❌ Pending | Spring Boot app needs its own Dockerfile |
| Cloud Deploy | ❌ Pending | Deploy to VPS or Render/Railway |
| CI/CD | ❌ Pending | GitHub Actions for automatic deployment |
| SSL/HTTPS | ❌ Pending | Required for bank API callbacks in production |

---

## How the System Works — Big Picture

```
┌──────────────┐        ┌────────────────────────┐        ┌────────────┐
│              │  JWT   │                        │  SQL   │            │
│  User / App  │───────▶│   Expense Tracker API  │───────▶│ PostgreSQL │
│  (Client)    │◀───────│   (Spring Boot)        │◀───────│ (Database) │
│              │  JSON  │                        │        │            │
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
| Bank Operations | 8 | JWT* | Link, Sync, History, Unlink |

*The bank callback endpoint is public (GoCardless redirects without JWT)

**Total: 19 API endpoints**
