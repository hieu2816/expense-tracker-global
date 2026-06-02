# Expense Tracking - DevOps Technical Reference

> **Audience:** developers, DevOps learners, operators  
> **Last Updated:** 2026-05-29  
> **Stack:** AWS EC2, Ubuntu, Docker Compose, Nginx, Certbot, GitHub Actions, GHCR, SonarCloud, Trivy, Prometheus, Grafana Cloud

---

## Scope

This document covers the production and operations setup:

- AWS EC2 single-instance deployment.
- Docker Compose runtime.
- Nginx HTTPS reverse proxy.
- Certbot/Let's Encrypt TLS automation.
- GitHub Actions CI/CD.
- GHCR image publishing and EC2 image pulling.
- Telegram deploy and monitoring alerts.
- Prometheus metrics collection.
- Grafana Cloud remote write, dashboards, and alert-ready metrics.

---

## Current Production Architecture

```text
Internet
  -> AWS Security Group
  -> EC2 Ubuntu
  -> Nginx container
      |-- serves React static files
      |-- proxies /api/* to backend:8080
      |-- proxies /actuator/* to backend:8080
  -> Spring Boot backend
  -> PostgreSQL
```

Monitoring side path:

```text
backend /actuator/prometheus
node-exporter
cadvisor
postgres-exporter
prometheus self metrics
        |
        | Prometheus scrape every 30s
        v
Prometheus on EC2
        |
        | remote_write HTTPS
        v
Grafana Cloud Metrics
```

---

## Docker Compose Services

| Service | Image | Role | Public? |
|---|---|---|---|
| `nginx` | `FRONTEND_IMAGE` from GHCR | Serves React app, TLS, reverse proxy | Yes, `80/443` |
| `backend` | `BACKEND_IMAGE` from GHCR | Spring Boot API | No |
| `database` | `postgres:16-alpine` | PostgreSQL storage | Host mapped `5433`, protect with SG |
| `certbot` | `certbot/certbot` | TLS certificate issue/renew | No |
| `prometheus` | `prom/prometheus:v2.55.1` | Scrapes metrics, remote writes to Grafana | No |
| `node-exporter` | `prom/node-exporter` | EC2 host metrics | No |
| `cadvisor` | `gcr.io/cadvisor/cadvisor` | Docker container metrics | No |
| `postgres-exporter` | `prometheuscommunity/postgres-exporter:v0.17.1` | PostgreSQL metrics | No |

Memory limits are used for monitoring services to protect the small EC2 instance:

| Service | Limit |
|---|---:|
| Prometheus | 150MB |
| Node Exporter | 30MB |
| cAdvisor | 80MB |
| PostgreSQL Exporter | 40MB |

---

## Public and Private Network Boundaries

Public inbound traffic should be limited to:

```text
22/tcp  SSH, restricted to trusted IPs
80/tcp  HTTP, redirects to HTTPS and Let's Encrypt challenge
443/tcp HTTPS application traffic
```

Internal Docker traffic:

```text
nginx -> backend:8080
backend -> database:5432
prometheus -> backend:8080/actuator/prometheus
prometheus -> node-exporter:9100
prometheus -> cadvisor:8080
prometheus -> postgres-exporter:9187
postgres-exporter -> database:5432
```

Grafana Cloud does not connect inbound to EC2. Prometheus sends metrics outbound using HTTPS remote write.

---

## CI/CD Pipeline

Workflow: `.github/workflows/deploy.yml`

Triggered by:

```text
push to main
```

Pipeline stages:

1. **Build and test**
   - Starts temporary PostgreSQL service.
   - Runs Maven verify and JaCoCo coverage.
   - Runs SonarCloud scan with quality gate.
   - Installs frontend dependencies and runs critical npm audit.

2. **Build and push images**
   - Builds backend Docker image.
   - Builds frontend/Nginx Docker image.
   - Runs Trivy scan on backend image.
   - Pushes both images to GHCR with the Git SHA tag.

3. **Deploy to EC2**
   - SSH into EC2.
   - Fetch/reset repo to `origin/main`.
   - Write `.env` from GitHub Secrets.
   - Append Grafana remote write secrets.
   - Inject `BACKEND_IMAGE` and `FRONTEND_IMAGE`.
   - Login to GHCR.
   - `docker compose pull`.
   - Bootstrap TLS certificates.
   - `docker compose up -d`.
   - Recreate Prometheus so `/tmp/prometheus.yml` is rendered from the latest template and secrets.
   - Wait for backend and Nginx health.
   - Install/update cron monitoring.
   - Run smoke tests.
   - Save `.last_successful_deploy`.
   - Send Telegram success/failure notification.

Important image flow:

```text
GitHub Actions pushes images to GHCR.
EC2 pulls images from GHCR.
EC2 does not build backend/frontend images in production.
```

---

## Runtime Configuration

GitHub Actions writes EC2 `.env` during deploy.

Application secrets/config:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Spring Boot database connection |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | PostgreSQL container setup |
| `JWT_SECRET` | JWT signing |
| `ENCRYPTION_KEY` | Sensitive token encryption |
| `ALLOWED_ORIGINS` | CORS origins |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile |
| `PLAID_CLIENT_ID`, `PLAID_SECRET`, `PLAID_ENV`, `PLAID_WEBHOOK_URL` | Plaid integration |
| `TLS_DOMAIN`, `LETSENCRYPT_EMAIL` | TLS automation |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | Deploy/monitoring alerts |
| `BACKEND_IMAGE`, `FRONTEND_IMAGE` | GHCR images pinned to Git SHA |

Grafana remote write config:

| Variable | Purpose |
|---|---|
| `GRAFANA_PROM_URL` | Grafana Cloud remote write URL ending in `/api/prom/push` |
| `GRAFANA_PROM_USER` | Grafana Cloud metrics user/instance id |
| `GRAFANA_PROM_PASS` | Grafana Cloud access token |

Do not commit `.env`, Grafana tokens, rendered Prometheus configs, or Plaid secrets.

---

## Nginx and TLS

Nginx responsibilities:

- Listen on `80` and `443`.
- Redirect HTTP to HTTPS.
- Serve React static files.
- Proxy `/api/*` to Spring Boot.
- Proxy `/actuator/*` to Spring Boot.
- Serve Let's Encrypt challenge files.
- Apply security headers.

TLS is handled through Certbot and mounted certificate volumes:

```text
certbot-etc
certbot-www
```

`script/bootstrap-tls.sh` is idempotent:

- Creates temporary self-signed certificates if needed so Nginx can start.
- Requests real Let's Encrypt certificates.
- Skips renewal if the current certificate is still valid enough.

---

## Prometheus and Grafana Cloud

Prometheus uses a template config:

```text
monitoring/prometheus.yml
```

At container startup, `monitoring/prometheus-entrypoint.sh`:

1. Requires `GRAFANA_PROM_URL`, `GRAFANA_PROM_USER`, `GRAFANA_PROM_PASS`.
2. Renders the template into `/tmp/prometheus.yml`.
3. Starts Prometheus with the rendered config.

Prometheus scrape jobs:

| Job | Target | Purpose |
|---|---|---|
| `prometheus` | `prometheus:9090` | Prometheus self metrics |
| `spring-boot` | `backend:8080/actuator/prometheus` | JVM/API metrics |
| `node-exporter` | `node-exporter:9100` | EC2 CPU/RAM/disk/network |
| `cadvisor` | `cadvisor:8080` | Container CPU/RAM/network |
| `postgres-exporter` | `postgres-exporter:9187` | PostgreSQL metrics |

Remote write:

```text
Prometheus on EC2
  -> HTTPS POST
  -> Grafana Cloud /api/prom/push
```

Useful Grafana verification queries:

```promql
up
up{job="spring-boot"}
up{job="node-exporter"}
up{job="cadvisor"}
up{job="postgres-exporter"}
up{job="prometheus"}
jvm_memory_used_bytes
pg_up
container_memory_working_set_bytes
```

---

## Monitoring and Self-Healing

There are two monitoring layers.

### Script-based monitoring

`script/monitor.sh` runs via cron and handles:

- Backend health checks.
- Container crash remediation.
- Restart loop guard.
- Plaid sync failure checks through `sync_logs`.
- Disk cleanup and disk pressure alerts.
- SSL expiration tracking.
- Telegram alerts.

### Metrics-based monitoring

Prometheus and Grafana Cloud handle:

- EC2 CPU/RAM/swap/disk trend.
- Container resource trend.
- Spring Boot JVM and HTTP metrics.
- PostgreSQL availability and database metrics.
- Prometheus remote write health.
- Grafana alert rules and dashboards.

The script layer catches immediate operational failures. The Grafana layer provides trends, dashboards, and metric-based alerts.

---

## Common Operations

Check containers:

```bash
docker compose ps
```

Check backend logs:

```bash
docker compose logs backend --tail=100
```

Check Prometheus rendered config:

```bash
docker compose exec prometheus cat /tmp/prometheus.yml
```

Recreate Prometheus after monitoring config or Grafana secret changes:

```bash
docker compose up -d --no-deps --force-recreate prometheus
```

Check Prometheus logs:

```bash
docker compose logs prometheus --tail=100
```

Run smoke test:

```bash
./script/smoke-test.sh
```

---

## Known Hardening Notes

- Nginx currently proxies `/actuator/*`; consider restricting `/actuator/prometheus` to internal Prometheus only.
- PostgreSQL host port `5433` must remain blocked from public internet by AWS Security Group.
- Trivy currently uses `exit-code: 0`; later phases can make critical vulnerabilities block deployment again.
- Business Prometheus metrics and centralized logs are planned future work.
