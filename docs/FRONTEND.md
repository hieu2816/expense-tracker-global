# Expense Tracking - Frontend Architecture

> **Audience:** frontend developers and reviewers  
> **Last Updated:** 2026-05-29  
> **Stack:** React 19.1, Vite 7.3, Ant Design 6.3, Recharts 3.8, Axios, React Router 7.13

---

## Role of the Frontend

The frontend is a React single-page application for:

- User login/register.
- Dashboard visualization.
- Transaction CRUD and CSV export.
- Category management.
- Plaid bank linking and sync history.
- Profile update and password changes.

In development, Vite serves the app on `localhost:5173`.

In production, the frontend is built into static files and served by the Nginx container. Browser API calls use relative `/api` paths, and Nginx proxies them to the backend container.

---

## Project Structure

```text
frontend/
|-- index.html
|-- package.json
|-- vite.config.js
|-- .env.development
|-- .env.production
|-- src/
    |-- main.jsx
    |-- App.jsx
    |-- index.css
    |-- api/
    |   |-- axios.js
    |-- contexts/
    |   |-- AuthContext.jsx
    |-- components/
    |   |-- AppLayout.jsx
    |   |-- ProtectedRoute.jsx
    |-- pages/
        |-- Login.jsx
        |-- Register.jsx
        |-- Dashboard.jsx
        |-- Transactions.jsx
        |-- Categories.jsx
        |-- BankAccounts.jsx
        |-- Profile.jsx
```

---

## Runtime Flow

```text
User Browser
  -> Nginx HTTPS
  -> React static files
  -> Axios requests to /api
  -> Nginx reverse proxy
  -> Spring Boot backend
```

Production API base URL:

```text
VITE_API_URL=/api
```

Because `/api` is relative, the browser talks to the same domain that served the SPA. This avoids production CORS complexity.

---

## App Composition

```text
App.jsx
  -> ConfigProvider
  -> BrowserRouter
  -> AuthProvider
  -> Routes
      |-- /login
      |-- /register
      |-- protected layout
          |-- /
          |-- /transactions
          |-- /categories
          |-- /banks
          |-- /profile
```

`AppLayout` owns the sidebar/header shell. Child pages render through React Router's outlet pattern, so navigation does not recreate the entire app shell.

---

## Authentication Flow

```text
Login page
  -> POST /api/auth/login
  -> receive JWT
  -> store JWT in localStorage
  -> load /api/user/profile
  -> render protected app
```

On every request, Axios attaches:

```text
Authorization: Bearer <token>
```

If a response returns `401`, the Axios response interceptor removes the token and redirects to `/login`.

---

## Pages

| Page | Purpose |
|---|---|
| Login | Authenticate user and store JWT |
| Register | Create account |
| Dashboard | Summary cards, spending chart, recent transactions |
| Transactions | Filter, paginate, create, edit, delete, export CSV |
| Categories | Manage per-user categories |
| Bank Accounts | Plaid Link, account list, manual sync, sync history |
| Profile | Update user details and password |

---

## Plaid Link Flow

```text
User clicks Link Bank
  -> frontend requests link token from backend
  -> Plaid Link widget opens
  -> user authorizes bank
  -> Plaid returns public_token
  -> frontend sends public_token to backend
  -> backend exchanges and stores encrypted access token
  -> frontend refreshes bank account list
```

The frontend never receives or stores Plaid access tokens.

---

## Production Build and Deployment

The frontend Docker image is built in GitHub Actions:

```text
docker build -t ghcr.io/<repo>/frontend:<git-sha> ./frontend
docker push ghcr.io/<repo>/frontend:<git-sha>
```

On EC2, GitHub Actions writes:

```text
FRONTEND_IMAGE=ghcr.io/<repo>/frontend:<git-sha>
```

Then Docker Compose pulls and starts the Nginx/frontend image.

Nginx responsibilities:

- Listen on `80` and `443`.
- Redirect HTTP to HTTPS.
- Serve React static files.
- Serve `/assets/*` with long cache headers.
- Proxy `/api/*` to `backend:8080`.
- Proxy `/actuator/*` to `backend:8080`.
- Serve `/health` for container and deploy checks.

---

## Development Commands

```bash
cd frontend
npm install
npm run dev
npm run build
npm run preview
```

The `postinstall` script runs `scripts/ensure-rollup-native.mjs` to help avoid Rollup native package issues across environments.

---

## Design Choices

| Choice | Reason |
|---|---|
| React 19 | Component model, stable ecosystem |
| Vite | Fast development server and build |
| Ant Design | Strong CRUD UI primitives: tables, forms, modals, notifications |
| Recharts | React-friendly charts for dashboard views |
| Axios | Centralized auth headers and 401 handling |
| React Router | Protected routes and nested app layout |
| Context API | Auth is the only global state currently needed |
