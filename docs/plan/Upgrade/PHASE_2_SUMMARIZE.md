# Phase 2 Summary: Observability with Prometheus and Grafana Cloud

**Project:** Expense Tracking Global  
**Phase:** Phase 2, Observability Core  
**Status:** Core implementation completed locally, pending deployment verification on EC2 and Grafana Cloud  
**Scope:** Prometheus, Grafana Cloud remote write, Node Exporter, cAdvisor, Spring Boot metrics, PostgreSQL Exporter, and alert-ready metrics  
**Out of scope for this pass:** Business metrics and dashboard screenshots/documentation images

---

## 1. Goal of Phase 2

Phase 2 moves the project from basic uptime checks and Telegram cron monitoring into a real observability foundation.

Before this phase, the system could answer questions like:

- Is the backend container alive?
- Did the smoke test pass?
- Did the cron monitor detect a failure?
- Did Telegram receive a deploy or failure notification?

After this phase, the system can answer deeper operational questions:

- How much CPU, RAM, swap, disk, and network is the EC2 instance using?
- Which Docker container is consuming memory or CPU?
- Is Prometheus itself healthy?
- Is the Spring Boot backend exposing JVM and HTTP metrics?
- Is PostgreSQL reachable and producing database metrics?
- Are metrics stored long enough in Grafana Cloud to see trends over time?
- Can Grafana alert when backend, database, disk, memory, or API latency becomes unhealthy?

The main design constraint is the EC2 instance size. Because the production server has limited RAM, Grafana is not self-hosted on EC2. Instead, EC2 runs only the lightweight metric collectors and a local Prometheus agent-style service, then pushes metrics to Grafana Cloud.

---

## 2. Final Architecture

The Phase 2 architecture is:

```text
GitHub Actions
    |
    | build backend/frontend images
    | push images to GHCR
    v
GitHub Container Registry

Production EC2
    |
    | docker compose pull
    | docker compose up -d
    v
Docker Compose stack
    |
    | backend exposes /actuator/prometheus
    | node-exporter exposes host metrics
    | cAdvisor exposes container metrics
    | postgres-exporter exposes PostgreSQL metrics
    | Prometheus scrapes all metric endpoints
    v
Prometheus on EC2
    |
    | remote_write over HTTPS
    | basic auth using Grafana Cloud credentials
    v
Grafana Cloud Metrics
    |
    | query with PromQL
    | dashboards
    | alert rules
    v
Grafana UI and notifications
```

The important direction is:

- GitHub Actions **pushes Docker images** to GHCR.
- EC2 **pulls Docker images** from GHCR.
- Prometheus **pulls metrics** from local containers by scraping them.
- Prometheus **pushes metrics** to Grafana Cloud using `remote_write`.
- Grafana Cloud **stores and queries metrics**.
- Grafana dashboards and alerts **read from Grafana Cloud Metrics**, not directly from EC2.

---

## 3. CI/CD Pipeline Flow

Phase 2 reuses the Phase 1 CI/CD foundation.

### Step 1: Developer pushes code to GitHub

When code is pushed to the configured branch, GitHub Actions starts the `Build and Deploy` workflow.

### Step 2: GitHub Actions builds Docker images

The pipeline builds two images:

```text
ghcr.io/<repo>/backend:<git-sha>
ghcr.io/<repo>/frontend:<git-sha>
```

The backend image now includes:

- Spring Boot Actuator
- Micrometer Prometheus Registry
- `/actuator/prometheus` support

The frontend image remains the Nginx-hosted React SPA.

### Step 3: GitHub Actions pushes images to GHCR

GitHub Actions logs in to GitHub Container Registry using `GITHUB_TOKEN`, then pushes:

```text
backend:<git-sha>
frontend:<git-sha>
```

EC2 does not build the app. This keeps production deployment lightweight.

### Step 4: GitHub Actions connects to EC2 by SSH

The deploy job writes runtime values into `.env` on EC2.

For application containers, it writes values such as:

```text
BACKEND_IMAGE=ghcr.io/<repo>/backend:<git-sha>
FRONTEND_IMAGE=ghcr.io/<repo>/frontend:<git-sha>
```

For Prometheus remote write, it writes:

```text
GRAFANA_PROM_URL=<Grafana Cloud remote_write URL>
GRAFANA_PROM_USER=<Grafana Cloud metrics user or instance id>
GRAFANA_PROM_PASS=<Grafana Cloud access token>
```

These values come from GitHub Secrets. They are not committed to the repository.

### Step 5: EC2 pulls images and restarts the stack

On EC2, the deploy script runs:

```text
docker compose pull
docker compose up -d
```

This means:

- Backend and frontend are pulled from GHCR.
- Prometheus, Node Exporter, cAdvisor, and PostgreSQL Exporter are pulled from public container registries.
- Docker Compose starts or updates all services.
- The backend and Nginx health checks are used before smoke tests continue.

### Step 6: Smoke tests and Telegram deploy notification

After containers are healthy, the pipeline runs smoke tests.

If deploy succeeds:

- `.last_successful_deploy` is updated with the current Git SHA.
- Telegram receives a success message.

If deploy fails:

- The pipeline captures recent backend logs.
- Telegram receives a failure message.
- The workflow exits with failure.

---

## 4. Prometheus Startup Flow

Prometheus has a special startup flow because its config needs Grafana Cloud credentials.

The repository stores a safe template:

```text
monitoring/prometheus.yml
```

This file contains placeholders:

```yaml
remote_write:
  - url: ${GRAFANA_PROM_URL}
    basic_auth:
      username: ${GRAFANA_PROM_USER}
      password: ${GRAFANA_PROM_PASS}
```

Docker Compose mounts it into the Prometheus container as:

```text
/etc/prometheus/prometheus.yml.template
```

Then the container starts with:

```text
/bin/sh /etc/prometheus/prometheus-entrypoint.sh
```

The entrypoint script:

1. Requires `GRAFANA_PROM_URL`, `GRAFANA_PROM_USER`, and `GRAFANA_PROM_PASS`.
2. Replaces the placeholders in the template.
3. Writes the rendered config to:

```text
/tmp/prometheus.yml
```

4. Starts Prometheus with:

```text
/bin/prometheus --config.file=/tmp/prometheus.yml
```

This avoids committing secrets and avoids the earlier bug where Prometheus read `${GRAFANA_PROM_URL}` literally.

The correct Grafana Cloud remote write URL must end with:

```text
/api/prom/push
```

Example:

```text
https://prometheus-prod-37-prod-ap-southeast-1.grafana.net/api/prom/push
```

---

## 5. Metrics Collection Flow

Prometheus uses a pull model for collection.

Every `30s`, Prometheus scrapes each configured job:

```yaml
global:
  scrape_interval: 30s
  evaluation_interval: 30s
```

The configured jobs are:

```text
prometheus
spring-boot
node-exporter
cadvisor
postgres-exporter
```

After scraping, Prometheus stores a short local copy of data on EC2, then sends the samples to Grafana Cloud using remote write.

Local retention is:

```text
7 days
```

This gives EC2 a short local buffer while Grafana Cloud becomes the long-term place for dashboards and queries.

---

## 6. What Each Component Exposes

### A. Spring Boot backend

The backend exposes application metrics through:

```text
/actuator/prometheus
```

This endpoint is enabled by:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

It is made available to Prometheus by Spring Security:

```java
.requestMatchers("/actuator/prometheus").permitAll()
```

The backend now provides metrics such as:

- HTTP request count
- HTTP request duration
- API latency buckets
- JVM memory usage
- JVM garbage collection
- JVM thread metrics
- Process uptime
- Application startup metrics

Important security note:

- `/actuator/prometheus` is public inside the Docker network and may also be reachable through Nginx if proxied.
- `/actuator/health` remains safe because detailed health output is protected with `show-details: when_authorized`.
- No application secrets are exposed by Micrometer metrics.

### B. Prometheus

Prometheus exposes its own health and internal metrics on:

```text
prometheus:9090
```

The Prometheus scrape job lets Grafana answer:

- Is Prometheus up?
- Is scraping working?
- Is remote write queuing or failing?
- How many samples are being sent?
- Is Prometheus under memory or CPU pressure?

Useful metric families include:

```text
prometheus_remote_storage_*
prometheus_tsdb_*
prometheus_target_*
scrape_*
up
```

### C. Node Exporter

Node Exporter exposes EC2 host metrics on:

```text
node-exporter:9100
```

It reads host system information through mounted host paths:

```text
/proc
/sys
/
```

It provides:

- CPU usage
- RAM usage
- Swap usage
- Disk usage
- Filesystem availability
- Network traffic
- Load average
- Host uptime

This is the main source for EC2 infrastructure dashboards.

### D. cAdvisor

cAdvisor exposes container metrics on:

```text
cadvisor:8080
```

It reads Docker/container data from host-mounted paths:

```text
/var/run
/sys
/var/lib/docker
/
```

It provides:

- CPU usage per container
- Memory usage per container
- Container network receive/transmit
- Container filesystem usage
- Container runtime metadata

This is the main source for Docker service dashboards.

### E. PostgreSQL Exporter

PostgreSQL Exporter exposes database metrics on:

```text
postgres-exporter:9187
```

It connects to the internal database service with:

```text
postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@database:5432/${POSTGRES_DB}?sslmode=disable
```

The exporter gets database credentials from `.env`, not from committed code.

It provides:

- PostgreSQL up/down status
- Active connections
- Transaction counts
- Locks
- Database size
- Table and index statistics
- Checkpoint and background writer metrics, depending on exporter/default query support

---

## 7. What Is Pulled and What Is Pushed

This phase has two different directions of data movement.

### Docker image flow

```text
GitHub Actions builds images
GitHub Actions pushes images to GHCR
EC2 pulls images from GHCR
Docker Compose starts containers
```

This is the application deployment flow.

### Metrics scrape flow

```text
Prometheus pulls metrics from local targets
```

Prometheus scrapes:

```text
prometheus:9090
backend:8080/actuator/prometheus
node-exporter:9100
cadvisor:8080
postgres-exporter:9187
```

This is a pull model.

### Grafana Cloud remote write flow

```text
Prometheus pushes scraped samples to Grafana Cloud
```

This is a push model from Prometheus to Grafana Cloud:

```text
EC2 Prometheus -> HTTPS -> Grafana Cloud Metrics
```

Grafana Cloud does not scrape EC2 directly. This is important because:

- EC2 does not need to expose Prometheus publicly.
- Security Groups do not need to allow inbound Grafana traffic.
- Only outbound HTTPS from EC2 to Grafana Cloud is required.

---

## 8. Exposed Endpoints and Network Boundaries

### Public internet

The normal public entry points remain:

```text
80/tcp
443/tcp
```

These are served by Nginx.

### Internal Docker network

Prometheus scrapes services by Docker Compose service name:

```text
backend:8080
prometheus:9090
node-exporter:9100
cadvisor:8080
postgres-exporter:9187
```

These targets are intended for internal container-to-container communication.

### EC2 host exposure

Prometheus, Node Exporter, cAdvisor, and PostgreSQL Exporter do not need public ports in `docker-compose.yaml`.

This keeps the monitoring stack private by default.

### Outbound traffic

Prometheus requires outbound HTTPS to Grafana Cloud:

```text
https://prometheus-prod-37-prod-ap-southeast-1.grafana.net/api/prom/push
```

No inbound Grafana Cloud access to EC2 is required.

---

## 9. Grafana Cloud Usage

Grafana Cloud is the central UI and metric store for this phase.

In Grafana Explore, the first query should be:

```promql
up
```

Expected jobs:

```text
prometheus
spring-boot
node-exporter
cadvisor
postgres-exporter
```

Each healthy target should return:

```text
1
```

Recommended verification queries:

```promql
up{job="prometheus"}
up{job="spring-boot"}
up{job="node-exporter"}
up{job="cadvisor"}
up{job="postgres-exporter"}
```

Spring Boot metrics:

```promql
jvm_memory_used_bytes
http_server_requests_seconds_count
```

PostgreSQL metrics:

```promql
pg_up
pg_stat_database_numbackends
pg_database_size_bytes
```

Host metrics:

```promql
node_memory_MemAvailable_bytes
node_memory_MemTotal_bytes
node_cpu_seconds_total
node_filesystem_avail_bytes
```

Container metrics:

```promql
container_cpu_usage_seconds_total
container_memory_working_set_bytes
container_network_receive_bytes_total
container_network_transmit_bytes_total
```

Prometheus remote write health:

```promql
prometheus_remote_storage_samples_total
prometheus_remote_storage_failed_samples_total
prometheus_remote_storage_samples_pending
```

---

## 10. Recommended Dashboards

The dashboards should be created or imported in Grafana Cloud.

Recommended dashboard groups:

| Dashboard | Purpose |
|---|---|
| System Overview | EC2 CPU, RAM, swap, disk, network, load |
| Container Overview | Docker container CPU, RAM, network, filesystem |
| Spring Boot API | Request rate, error rate, latency, endpoint behavior |
| JVM Dashboard | Heap, non-heap, GC, threads, uptime |
| PostgreSQL Dashboard | DB availability, connections, locks, transactions, size |
| Prometheus Health | Scrape status, remote write queue, failed samples |

The dashboards read data from Grafana Cloud Metrics using PromQL.

They do not connect to EC2 directly.

---

## 11. Alerting Plan

Alert rules should be configured in Grafana Cloud.

Recommended alerts:

| Alert | Example condition |
|---|---|
| Backend down | `up{job="spring-boot"} == 0` |
| Prometheus down or missing data | `up{job="prometheus"} == 0` or no data |
| PostgreSQL exporter down | `up{job="postgres-exporter"} == 0` |
| Database unavailable | `pg_up == 0` |
| High RAM usage | EC2 memory usage above threshold for several minutes |
| High swap usage | Swap usage above threshold |
| High disk usage | Disk usage above 85 percent |
| High API error rate | 5xx rate above threshold |
| High API latency | P95 latency above threshold |
| Remote write failing | Failed samples increasing |

Telegram can still be used as the notification destination.

The difference from Phase 1 is:

- Phase 1 cron alerts are script-based and event-based.
- Phase 2 Grafana alerts are metric-based and trend-aware.

Both can coexist.

---

## 12. Resource Budget on EC2

The monitoring stack is intentionally lightweight.

Current memory limits:

| Service | Memory limit |
|---|---:|
| Prometheus | 150MB |
| Node Exporter | 30MB |
| cAdvisor | 80MB |
| PostgreSQL Exporter | 40MB |

Grafana is not installed on EC2.

This avoids adding another 100MB to 150MB of RAM pressure on a small production instance.

---

## 13. Failure History and Fixes

Two important Prometheus remote write failures were found and fixed during this phase.

### Failure 1: Prometheus read environment variables literally

Log symptom:

```text
url=$%7BGRAFANA_PROM_URL%7D
unsupported protocol scheme ""
```

Root cause:

Prometheus was reading `${GRAFANA_PROM_URL}` literally from the config file instead of receiving a rendered URL.

Fix:

- Mount `monitoring/prometheus.yml` as a template.
- Add `monitoring/prometheus-entrypoint.sh`.
- Render Grafana Cloud values into `/tmp/prometheus.yml`.
- Start Prometheus with the rendered config.

### Failure 2: Grafana Cloud returned 404

Log symptom:

```text
server returned HTTP status 404 Not Found
url=https://prometheus-prod-37-prod-ap-southeast-1.grafana.net/api/prom
```

Root cause:

The URL was missing `/push`.

Fix:

Use:

```text
https://prometheus-prod-37-prod-ap-southeast-1.grafana.net/api/prom/push
```

After this fix, Prometheus logs showed:

```text
Starting WAL watcher
Done replaying WAL
Remote storage resharding
```

with no retry, 404, 401, or 403 errors.

---

## 14. Current Completion Status

| Item | Status |
|---|---|
| Prometheus container | Completed |
| Grafana Cloud remote write | Completed |
| Remote write secret rendering | Completed |
| Prometheus healthcheck | Completed |
| Prometheus self-scrape | Completed |
| Node Exporter | Completed |
| cAdvisor | Completed |
| Spring Boot Prometheus metrics | Completed in code |
| PostgreSQL Exporter | Completed in compose |
| Grafana dashboards | To configure in Grafana Cloud |
| Grafana alert rules | To configure in Grafana Cloud |
| Business metrics | Out of scope for this pass |
| Docs/screenshots | Out of scope for this pass |

---

## 15. Deployment Verification Checklist

After pushing to GitHub and allowing CI/CD to deploy to EC2, verify:

### On EC2

```text
docker compose ps
docker compose logs prometheus --tail=100
docker compose logs postgres-exporter --tail=100
```

Prometheus should not show:

```text
unsupported protocol scheme
404 Not Found
401 Unauthorized
403 Forbidden
Failed to send batch
```

### In Grafana Explore

Run:

```promql
up
```

Expected:

```text
prometheus = 1
spring-boot = 1
node-exporter = 1
cadvisor = 1
postgres-exporter = 1
```

Then verify at least one metric from each source:

```promql
prometheus_remote_storage_samples_total
jvm_memory_used_bytes
node_memory_MemTotal_bytes
container_memory_working_set_bytes
pg_up
```

---

## 16. What Phase 2 Gives the Project

Phase 2 gives the project a production-style observability baseline:

- Metrics are collected continuously.
- Metrics survive beyond container restarts because they are sent to Grafana Cloud.
- EC2 stays lightweight because Grafana is not self-hosted.
- Application, host, container, database, and Prometheus health are all visible.
- The system is ready for Grafana dashboards and alert rules.
- Future incidents can be debugged with trends instead of only log snapshots.

The project now has the core observability pipeline:

```text
Application and infrastructure expose metrics
Prometheus pulls metrics
Prometheus pushes metrics to Grafana Cloud
Grafana visualizes and alerts on those metrics
```

Business-level metrics and screenshots can be added later without changing the core pipeline.
