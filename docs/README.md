# Expense Tracking Global — Documentation

A personal finance management REST API built with Spring Boot. Tracks income and expenses — both manually and automatically via Open Banking (Plaid).

---

## Documentation

| Document | What It Covers |
|----------|--------------|
| [Project Overview](./PROJECT_OVERVIEW.md) | High-level features, architecture diagram, user stories |
| [Backend Reference](./BACKEND.md) | Full backend implementation — auth, transactions, Plaid SDK, webhooks, DB schema |
| [Frontend Architecture](./FRONTEND.md) | React SPA — routing, Plaid Link, Axios interceptors, design system |
| [DevOps Reference](./DEVOPS.md) | Docker Compose, EC2, Nginx, Certbot, GitHub Actions CI/CD |
| [Plaid Integration](./PLAID_INTEGRATION.md) | Technical history and manual sync details for Plaid |
| [Webhook Implementation](./WEBHOOK_IMPLEMENTATION.md) | How real-time transaction webhooks were designed and optimized |

---

## Quick Start

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Set required environment variables
export PLAID_CLIENT_ID=your_client_id
export PLAID_SECRET=your_secret
export PLAID_ENV=sandbox
export PLAID_WEBHOOK_URL=https://your-ngrok-or-domain.com/api/banks/webhook
export DB_URL=jdbc:postgresql://localhost:5433/postgres
export DB_USERNAME=postgres
export DB_PASSWORD=your_password

# 3. Run backend (port 8080)
./mvnw spring-boot:run

# 4. Run frontend (port 5173)
cd frontend && npm install && npm run dev
```

Open **http://localhost:5173** in your browser.

---

## API Endpoints

### Public
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Login → receive JWT |
| POST | `/api/banks/webhook` | Plaid real-time event webhook |

### Protected (requires JWT)
| Area | Endpoints |
|------|---------|
| User | GET/PUT `/api/user/profile`, PUT `/api/user/change-password` |
| Transactions | CRUD `/api/transactions`, GET `/dashboard`, GET `/category-summary`, GET `/export` |
| Categories | CRUD `/api/categories` |
| Banks | POST `/link`, POST `/link/complete`, GET `/`, GET `/{id}`, POST `/{id}/sync` |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.5.9, PostgreSQL 16 |
| Auth | JWT (HS256, 24h), BCrypt |
| Bank API | Plaid API |
| Migrations | Flyway |
| Frontend | React 19, Vite 7, Ant Design 6, Recharts 3, Axios |
| Containers | Docker + Nginx + Certbot |

## External Resources

- [Plaid API Docs](https://plaid.com/docs/api/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)