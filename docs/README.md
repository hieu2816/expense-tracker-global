# Expense Tracking Global - Documentation

Expense Tracking Global is a personal finance web application for tracking income and expenses manually or automatically through Plaid Open Banking. The project now includes a production deployment pipeline and a Phase 2 observability stack with Prometheus and Grafana Cloud.

---

## Documentation Map

| Document | What It Covers |
|---|---|
| [Project Overview](./PROJECT_OVERVIEW.md) | Product scope, features, high-level architecture, current phase status |
| [Backend Reference](./BACKEND.md) | Spring Boot API, auth, transactions, Plaid, database, actuator metrics |
| [Frontend Architecture](./FRONTEND.md) | React SPA, routing, API client, Nginx production behavior |
| [DevOps Reference](./DEVOPS.md) | EC2, Docker Compose, GHCR, GitHub Actions, TLS, Prometheus, Grafana Cloud |
| [Plaid Integration](./PLAID_INTEGRATION.md) | Plaid design history, link flow, sync details |
| [Webhook Implementation](./WEBHOOK_IMPLEMENTATION.md) | Real-time Plaid webhook handling and async sync |
| [Phase 2 Summary](./plan/Upgrade/PHASE_2_SUMMARIZE.md) | Detailed observability summary and verification checklist |
| [Phase 2 Diagram Drafts](./plan/Upgrade/PHASE_2_DIAGRAM_DRAFT.md) | Draft Mermaid diagrams for runtime, Docker, CI/CD, and observability |

---

## Quick Start

```bash
# 1. Start PostgreSQL and local services
docker compose up -d

# 2. Set required backend environment variables
export DB_URL=jdbc:postgresql://localhost:5433/postgres
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=your_jwt_secret
export PLAID_CLIENT_ID=your_client_id
export PLAID_SECRET=your_secret
export PLAID_ENV=sandbox
export PLAID_WEBHOOK_URL=https://your-domain.com/api/banks/webhook
export ENCRYPTION_KEY=your_base64_32_byte_key

# 3. Run backend
./mvnw spring-boot:run

# 4. Run frontend
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

---

## API Summary

### Public

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Login and receive JWT |
| POST | `/api/banks/webhook` | Plaid webhook callback |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics endpoint |

### Protected

| Area | Endpoints |
|---|---|
| User | `GET/PUT /api/user/profile`, `PUT /api/user/change-password` |
| Transactions | CRUD `/api/transactions`, dashboard, category summary, CSV export |
| Categories | CRUD `/api/categories` |
| Banks | Link, complete link, list, sync, sync history, unlink |

---

## Current Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5.14, Maven |
| Persistence | PostgreSQL 16, Flyway, Spring Data JPA |
| Auth | JWT HS256, BCrypt |
| Bank API | Plaid API, Plaid Link, webhooks |
| Frontend | React 19, Vite 7, Ant Design 6, Recharts, Axios |
| Runtime | Docker Compose, Nginx, Certbot |
| CI/CD | GitHub Actions, GHCR, Trivy, SonarCloud, JaCoCo |
| Observability | Prometheus, Node Exporter, cAdvisor, PostgreSQL Exporter, Grafana Cloud |

---

## Production Flow

```text
User Browser
  -> HTTPS 443
  -> AWS Security Group
  -> Nginx container
  -> React static files or /api proxy
  -> Spring Boot backend
  -> PostgreSQL
```

Observability runs beside the app:

```text
backend/exporters
  -> Prometheus scrape every 30s
  -> remote_write HTTPS to Grafana Cloud
  -> dashboards and alerts
```

Deployment runs through GitHub Actions:

```text
push to main
  -> test + SonarCloud
  -> build Docker images
  -> Trivy scan
  -> push images to GHCR
  -> SSH deploy to EC2
  -> docker compose pull/up
  -> recreate Prometheus
  -> smoke test
  -> Telegram notification
```

---

## External Resources

- [Plaid API Docs](https://plaid.com/docs/api/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Grafana Cloud Documentation](https://grafana.com/docs/grafana-cloud/)
