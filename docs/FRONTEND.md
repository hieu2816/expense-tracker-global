# Expense Tracking — Frontend Architecture

> **Audience:** Developers working on the frontend
> **Last Updated:** 2026-04-09
> **Stack:** React 19 · Vite 7 · Ant Design 6 · Recharts 3 · Axios · React Router 7 · dayjs

---

## Why These Technologies?

| Choice | Why |
|--------|-----|
| **React 19** | Component-based, massive ecosystem, stable hooks, excellent TypeScript support |
| **Vite 7** | Near-instant cold start and HMR — under 100ms vs Webpack's 10–30 seconds |
| **Ant Design 6** | Production-ready tables, forms, modals, notifications — consistent design out of the box |
| **Ant Design theming** | `ConfigProvider` overrides primary color globally — brand customization without CSS overrides |
| **Recharts 3** | Composable SVG charts built for React, tree-shakeable, no canvas dependency |
| **Axios** | Interceptors centralize auth — every request automatically includes the JWT without repetitive code |
| **React Router 7** | Nested routes with `<Outlet>` — sidebar layout renders child pages without prop drilling |
| **Context API** | Auth state is the only global state needed; Redux would add unnecessary boilerplate |
| **dayjs** | Ant Design's DatePicker depends on it; smaller than Moment.js (2KB vs 67KB) |
| **Intl.NumberFormat** | Browser-native currency formatting — no extra library needed |

---

## Project Structure

```
frontend/
├── index.html                      # Entry point
├── package.json                    # React 19, Ant Design 6, Recharts 3, Axios, React Router 7
├── vite.config.js                  # Vite + @vitejs/plugin-react, port 5173
├── .env.development               # VITE_API_URL=http://localhost:8080/api
├── .env.production                # VITE_API_URL=/api  (relative — proxied by Nginx)
│
└── src/
    ├── main.jsx                   # ReactDOM.createRoot() → renders <App />
    ├── App.jsx                   # BrowserRouter + ConfigProvider (theme) + AuthProvider + Routes
    ├── index.css                 # Design system: CSS variables, sidebar, cards, auth pages, typography
    │
    ├── api/
    │   └── axios.js              # Axios instance: JWT interceptor + 401 auto-logout
    │
    ├── contexts/
    │   └── AuthContext.jsx        # Auth state: user, loading, isAuthenticated, login/logout
    │
    ├── components/
    │   ├── AppLayout.jsx         # Collapsible sidebar + sticky header + <Outlet />
    │   └── ProtectedRoute.jsx    # Auth guard: spinner → redirect to /login
    │
    └── pages/
        ├── Login.jsx              # Email + password → POST /auth/login
        ├── Register.jsx           # Name + email + password + confirm → POST /auth/register
        ├── Dashboard.jsx          # Stats + pie chart + recent transactions
        ├── Transactions.jsx       # Filterable table + CRUD modal + CSV export
        ├── Categories.jsx         # Category table + add/edit modal
        ├── BankAccounts.jsx       # Bank cards + link modal + sync + history + unlink
        └── Profile.jsx           # Edit name + change password
```

---

## How It Works

### Component Tree

```
App.jsx
  └── <ConfigProvider theme={green}>           ← Primary color: #0D9F6E
        └── <BrowserRouter>
              └── <AuthProvider>
                    └── <Routes>
                          ├── /login    → <Login />
                          ├── /register → <Register />
                          └── / (protected)
                                └── <ProtectedRoute>
                                      └── <AppLayout>          ← Sidebar + Header
                                            └── <Outlet />        ← Child route renders here
                                                  ├── /           → <Dashboard />
                                                  ├── /transactions → <Transactions />
                                                  ├── /categories  → <Categories />
                                                  ├── /banks       → <BankAccounts />
                                                  └── /profile     → <Profile />
```

### Why `<Outlet>`?

React Router's `<Outlet>` renders the active child route in place — the sidebar and header are rendered once and never unmount. Without it, every navigation would re-render the entire layout, causing flicker and losing sidebar state (e.g. collapsed/expanded).

---

## Authentication Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ App mounts                                                          │
│   → AuthContext.loadProfile() called on mount                      │
│     → Token in localStorage?                                      │
│       YES → GET /user/profile to validate token                   │
│         200 OK → setUser(data) → setLoading(false) → show app    │
│         401     → localStorage.removeItem('token') → show /login  │
│       NO  → setLoading(false) → show /login                       │
└─────────────────────────────────────────────────────────────────┘

Login:
  POST /auth/login { email, password }
    → 200 OK → store token in localStorage → GET /user/profile → navigate('/')

Register:
  POST /auth/register { email, password, fullName }
    → 200 OK → message.success() → navigate('/login')

Logout:
  localStorage.removeItem('token') → setUser(null) → navigate('/login')
```

### Why store the token in localStorage?

JWT is stateless — the server doesn't track sessions. The browser must persist the token between page reloads. `sessionStorage` is cleared on tab close; `localStorage` persists until explicitly removed.

### Why validate the token on mount?

The token could have expired or been revoked. Calling `GET /user/profile` confirms it's still valid. If the backend returns 401, the frontend clears the token and redirects to login.

---

## API Client (axios.js)

```javascript
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,  // .env.production: /api
});

// 1. Attach JWT on every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 2. Auto-logout on 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';  // Hard redirect to reset all state
    }
    return Promise.reject(error);
  }
);
```

### Why hard redirect (`window.location.href`) on 401?

React Router's `navigate()` is a soft redirect — it only changes the URL. But Axios's 401 interceptor fires **after** a page has already loaded with bad data. A hard redirect ensures the browser fetches `/login` fresh, clearing any stale component state.

### Why interceptors and not a wrapper function?

Without interceptors, every API call would need to manually attach the token:
```javascript
// Without interceptor — repetitive, error-prone
api.get('/transactions', {
  headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
})

// With interceptor — clean
api.get('/transactions')
```

---

## Protected Routes

```javascript
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) return <Spin size="large" />;     // Auth check in progress
  if (!isAuthenticated) return <Navigate to="/login" replace />;  // Not logged in
  return children;                                  // Authenticated
};
```

The loading state is critical: without it, the app would briefly flash the protected page before redirecting unauthenticated users, or show the login page to authenticated users during the token validation request.

---

## Design System (index.css)

All styling uses CSS custom properties — no CSS-in-JS, no Tailwind.

```css
:root {
  --color-primary:       #0D9F6E;   /* Green — buttons, active states */
  --color-primary-hover: #087F5B;   /* Darker green — hover states */
  --color-income:        #0D9F6E;   /* Green text for income */
  --color-expense:       #FF4D4F;   /* Red text for expenses */
  --color-bg-page:       #F8F9FA;   /* Light gray page background */
  --color-bg-card:       #FFFFFF;   /* White cards */
  --color-border:        #E5E7EB;   /* Subtle borders */
}
```

Ant Design's theme tokens override the library globally:
```javascript
<ConfigProvider theme={{ token: { colorPrimary: '#0D9F6E', borderRadius: 8 } }}>
```

### Currency Formatting

```javascript
const formatCurrency = (val) =>
  new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(val || 0);
```

`Intl.NumberFormat` is built into every modern browser — no library import needed. `en-GB` ensures comma thousands separator and `£` symbol.

---

## Pages

### Dashboard (`/`)
- **3 stat cards**: Total Income, Total Expense, Balance
- **Pie chart**: Spending breakdown by category via `GET /transactions/category-summary`
- **Recent transactions**: Last 5 via `GET /transactions?page=0&size=5`

Fetching the pie chart from the dashboard endpoint rather than the 5 recent transactions ensures all transactions are represented, not just the most recent ones.

### Transactions (`/transactions`)
- **Filter bar**: Category dropdown + DatePicker.RangePicker + Apply
- **Table**: pagination (`current`, `pageSize`, `total`), sortable columns
- **Add/Edit modal**: Type → Category → Amount → Description → Date
- **Delete**: Popconfirm (confirmation dialog) before DELETE
- **Export**: `GET /transactions/export` with `responseType: 'blob'` → trigger download

### Bank Accounts (`/banks`)
- Country selector → fetches institution list → user picks bank
- After `POST /banks/link`, redirects browser to GoCardless
- GoCardless redirects back to `/api/banks/callback` → frontend polls account status
- Sync shows new transaction count from the response

---

## Routing

| Path | Component | Auth | API Calls |
|------|-----------|------|-----------|
| `/login` | Login | Public | POST /auth/login |
| `/register` | Register | Public | POST /auth/register |
| `/` | Dashboard | Protected | GET /dashboard + GET /category-summary + GET /transactions |
| `/transactions` | Transactions | Protected | Full CRUD + GET /categories + export |
| `/categories` | Categories | Protected | GET /categories + CRUD |
| `/banks` | BankAccounts | Protected | GET /banks + institutions + sync + history |
| `/profile` | Profile | Protected | GET /user/profile + PUT endpoints |

---

## Running the Frontend

```bash
# Prerequisites: Node.js 18+, backend running on localhost:8080

npm install           # First time only — installs React, Ant Design, Vite, etc.
npm run dev          # Dev server on http://localhost:5173
npm run build        # Production build → frontend/dist/
npm run preview      # Preview production build locally
```

### Environment Variables

| Variable | Dev Value | Prod Value | Why |
|----------|-----------|------------|-----|
| `VITE_API_URL` | `http://localhost:8080/api` | `/api` | In prod, Nginx serves both frontend and API on the same domain |

In production, `/api` is a **relative path** — the browser sends it to the same host Nginx is running on, and Nginx forwards it to the backend container. No CORS needed.

---

## Architecture Decisions

### Why SPA over server-rendered (Thymeleaf)?

- **No full page reloads** — navigation is instant
- **Frontend/backend independently deployable** — the API is just a contract
- **Hot module replacement** — changes appear immediately during development

### Why Ant Design over MUI or Chakra UI?

Ant Design's component set (tables, forms, modals, DatePickers) maps directly to the CRUD-heavy nature of an expense tracker. Its Table component handles pagination, sorting, and loading states natively — building this from scratch would be days of work.

### Why Context API over Redux?

Auth state (user, token, loading) is the only truly global state. Redux adds actions, reducers, a store, and provider setup — overhead disproportionate to the problem. Context + `useState` covers it in 20 lines.

### Why Recharts over Chart.js?

Recharts is built in React — components are React components, not imperative DOM manipulation. It tree-shakes better (import `PieChart` without pulling in line/bar charts) and works naturally with Ant Design's Layout system.
