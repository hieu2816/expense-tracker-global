# Expense Tracking Global — Documentation

A personal finance management REST API built with Spring Boot. Tracks income and expenses — both manually and automatically via Open Banking (GoCardless).

---

## Documentation

| Document | What It Covers |
|----------|--------------|
| [Project Overview](./PROJECT_OVERVIEW.md) | High-level features, architecture diagram, user stories |
| [Backend Reference](./BACKEND.md) | Full backend implementation — auth, transactions, GoCardless, scheduler, DB schema |
| [Frontend Architecture](./FRONTEND.md) | React SPA — routing, auth flow, Axios interceptors, design system |

---

## Quick Start

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Set required environment variables
export GOCARDLESS_SECRET_ID=your_secret_id
export GOCARDLESS_SECRET_KEY=your_secret_key
export APP_BASE_URL=http://localhost:8080
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
| GET | `/api/banks/callback` | GoCardless redirect (public) |

### Protected (requires JWT)
| Area | Endpoints |
|------|---------|
| User | GET/PUT `/api/user/profile`, PUT `/api/user/change-password` |
| Transactions | CRUD `/api/transactions`, GET `/dashboard`, GET `/category-summary`, GET `/export` |
| Categories | CRUD `/api/categories` |
| Banks | GET `/institutions`, POST `/link`, CRUD `/api/banks`, POST `/{id}/sync`, GET `/{id}/sync-history` |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.5.9, PostgreSQL 16 |
| Auth | JWT (HS256, 24h), BCrypt |
| Bank API | GoCardless Bank Account Data API (PSD2) |
| Migrations | Flyway |
| Frontend | React 19, Vite 7, Ant Design 6, Recharts 3, Axios |
| Containers | Docker + Nginx + Certbot |

## External Resources

- [GoCardless Bank Account Data API Docs](https://developer.gocardless.com/bank-account-data/overview)
- [GoCardless Sandbox](https://bankaccountdata.gocardless.com/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
