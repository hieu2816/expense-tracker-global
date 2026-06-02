# Expense Tracking Global - Project Overview

> **Audience:** stakeholders, new developers, reviewers  
> **Last Updated:** 2026-05-29

---

## What Is This?

Expense Tracking Global is a personal finance application that lets users track income and expenses manually or by connecting bank accounts through Plaid Open Banking. It includes a React web app, a Spring Boot REST API, PostgreSQL persistence, production deployment on AWS EC2, and Grafana Cloud observability.

The project started with local/manual tracking, moved through earlier bank integration experiments, and now uses Plaid for global bank connectivity, transaction sync, and webhooks.

---

## Current Phase Status

| Area | Status | Summary |
|---|---|---|
| Core backend | Done | Auth, profile, transactions, categories, ownership checks |
| Bank integration | Done | Plaid Link, token exchange, encrypted access tokens, webhooks, sync history |
| Frontend | Done | React SPA with auth, dashboard, CRUD, bank linking, profile |
| DevSecOps | Done | GitHub Actions, SonarCloud, JaCoCo, Trivy, GHCR images, EC2 deploy |
| Observability core | Done | Prometheus remote write, Grafana Cloud, Node Exporter, cAdvisor, PostgreSQL Exporter, Spring Boot metrics |
| Business metrics | Not yet | Plaid/business-specific Prometheus counters are intentionally deferred |
| Centralized logs | Not yet | Loki/Promtail planned for a later phase |

---

## What Users Can Do

### Account and Security

- Register and login with email/password.
- Receive a JWT used for API authentication.
- View and update profile details.
- Change password.
- Access only their own data.

### Transactions and Categories

- Create income or expense transactions.
- Filter, paginate, edit, and delete transactions.
- Export transactions as CSV.
- View dashboard totals and spending breakdown.
- Use per-user categories that are auto-created when needed.

### Bank Integration

- Connect bank accounts using Plaid Link.
- Exchange Plaid public tokens for encrypted access tokens.
- Import historical transactions.
- Trigger manual sync.
- Receive Plaid webhooks for transaction updates.
- View sync history and account status.

### Production Operations

- The app is served through HTTPS at the production domain.
- Nginx serves the frontend and proxies API requests to Spring Boot.
- Docker Compose manages backend, frontend/Nginx, PostgreSQL, Certbot, Prometheus, and exporters.
- Grafana Cloud receives metrics for dashboards and alerts.

---

## High-Level Architecture

```text
User Browser
  |
  | HTTPS 443
  v
AWS Security Group
  |
  v
Nginx container
  |-- serves React static files
  |-- proxies /api/* to backend:8080
  |-- proxies /actuator/* to backend:8080
  v
Spring Boot backend
  |
  | JDBC internal Docker network
  v
PostgreSQL container
```

Observability:

```text
Spring Boot /actuator/prometheus
Node Exporter
cAdvisor
PostgreSQL Exporter
Prometheus self-metrics
        |
        | scraped by Prometheus every 30s
        v
Prometheus on EC2
        |
        | remote_write HTTPS
        v
Grafana Cloud
        |
        | PromQL
        v
Dashboards and alerts
```

CI/CD:

```text
Developer push to main
  -> GitHub Actions build/test
  -> SonarCloud + JaCoCo
  -> Docker build backend/frontend
  -> Trivy scan
  -> Push images to GHCR
  -> SSH into EC2
  -> Write .env from GitHub Secrets
  -> docker compose pull
  -> docker compose up -d
  -> force recreate Prometheus
  -> smoke tests
  -> Telegram notification
```

---

## Data Relationships

```text
User
  |-- many Transactions
  |-- many Categories
  |-- many PlaidItems
        |-- many BankAccounts
              |-- many Transactions
              |-- many SyncLogs
```

Important rules:

- Every user owns their own transactions, categories, and linked bank records.
- Service-layer ownership checks protect update/delete operations.
- Cash transactions can exist without a bank account.
- Bank-imported transactions are deduplicated with Plaid transaction IDs.

---

## Public and Private Boundaries

Public:

- `80/tcp` and `443/tcp` on Nginx.
- Plaid webhook endpoint through Nginx.
- Health endpoint used by deployment and monitoring.

Private/internal:

- Spring Boot backend container port `8080`.
- Prometheus `9090`.
- Node Exporter `9100`.
- cAdvisor `8080`.
- PostgreSQL Exporter `9187`.
- PostgreSQL internal `5432`.

Note: PostgreSQL is also mapped to host port `5433` for operational access; it must remain protected by AWS Security Group rules.

---

## API Summary

| Area | Auth | Description |
|---|---|---|
| Auth | Public | Register and login |
| User | JWT | Profile and password management |
| Transactions | JWT | CRUD, dashboard, filtering, CSV export |
| Categories | JWT | Category CRUD |
| Banks | JWT, except webhook | Plaid linking, sync, account status, sync history |
| Actuator | Mixed | Health and Prometheus metrics for operations |

