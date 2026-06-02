# Phase 2 Diagram Drafts

File nay la ban nhap de hinh dung kien truc truoc khi ve lai chi tiet hon bang Excalidraw, Figma, Draw.io, hoac cong cu ve so do khac.

Muc tieu cua 4 so do:

1. Nhin tong quan he thong production.
2. Hieu duong di cua user request.
3. Hieu Docker Compose dang chay cac container nao va container nao noi chuyen voi nhau.
4. Hieu pipeline CI/CD va observability metrics flow.

---

## 1. Big Picture Production Architecture

So do nay dung de giai thich buc tranh lon: user, GitHub/GHCR, EC2, Docker Compose, Grafana Cloud.

```mermaid
flowchart TB
    Dev["Developer"] -->|"git push main"| GitHub["GitHub Repository"]
    GitHub -->|"trigger workflow"| Actions["GitHub Actions"]
    Actions -->|"push backend/frontend images"| GHCR["GitHub Container Registry"]
    Actions -->|"SSH deploy"| EC2["AWS EC2 Ubuntu"]
    GHCR -->|"docker compose pull"| EC2

    Internet["Internet / User Browser"] -->|"HTTPS 443 / HTTP 80"| SG["AWS Security Group"]
    SG -->|"allow 80, 443"| EC2

    subgraph EC2Box["EC2 Production Server"]
        Compose["Docker Compose Stack"]
        Nginx["Nginx Container\npublic entrypoint"]
        Backend["Spring Boot Backend\ninternal :8080"]
        DB["PostgreSQL\ninternal :5432\nhost mapped :5433"]
        Prom["Prometheus\ninternal :9090"]
        Exporters["Node Exporter\ncAdvisor\nPostgres Exporter"]

        Compose --> Nginx
        Compose --> Backend
        Compose --> DB
        Compose --> Prom
        Compose --> Exporters
    end

    EC2 --> Compose
    Prom -->|"remote_write HTTPS\n/api/prom/push"| Grafana["Grafana Cloud Metrics"]
    Grafana --> Dashboards["Dashboards + Alerts"]

    classDef public fill:#263238,stroke:#ff7043,color:#fff
    classDef private fill:#1b2a41,stroke:#64b5f6,color:#fff
    classDef external fill:#2e1f47,stroke:#b388ff,color:#fff
    classDef data fill:#243b2f,stroke:#81c784,color:#fff

    class Internet,SG,Nginx public
    class Backend,DB,Prom,Exporters,Compose,EC2 private
    class GitHub,Actions,GHCR,Grafana,Dashboards external
```

### Diem can nhan manh khi ve lai

- User chi di vao EC2 qua `80/443`.
- Nginx la public entrypoint duy nhat cho web traffic.
- Backend, Prometheus, exporters nam trong Docker network, khong can public ra internet.
- GitHub Actions push image len GHCR; EC2 pull image tu GHCR.
- Prometheus khong bi Grafana Cloud scrape truc tiep. Prometheus push metrics ra ngoai bang HTTPS.

---

## 2. Runtime Request Flow

So do nay dung de giai thich duong di cua user khi su dung ung dung.

```mermaid
flowchart TB
    User["User Browser"] -->|"https://spendwiser.me"| Internet["Internet"]
    Internet -->|"443 HTTPS\n80 redirects to 443"| SG["AWS Security Group"]
    SG -->|"allowed inbound 80/443"| Nginx["Nginx Container\nports 80/443"]

    Nginx -->|"GET /\nstatic assets"| React["React Static Files\nserved inside Nginx"]
    Nginx -->|"GET/POST /api/*"| Backend["Spring Boot Backend\nbackend:8080"]
    Nginx -->|"GET /actuator/health"| Health["Backend Health Endpoint"]

    Backend -->|"JDBC TCP 5432\ninternal Docker DNS: database"| Postgres["PostgreSQL Container\ndatabase:5432"]
    Postgres -->|"persistent data"| Volume["Docker Volume\nexpense_data-global"]

    Backend -->|"Plaid API calls"| Plaid["Plaid API"]
    Plaid -->|"Webhook /api/banks/webhook"| Nginx

    subgraph PrivateNetwork["Docker Bridge Network"]
        Nginx
        React
        Backend
        Health
        Postgres
        Volume
    end

    classDef public fill:#3a2520,stroke:#ff7043,color:#fff
    classDef internal fill:#1b2a41,stroke:#64b5f6,color:#fff
    classDef storage fill:#243b2f,stroke:#81c784,color:#fff
    classDef external fill:#2e1f47,stroke:#b388ff,color:#fff

    class User,Internet,SG public
    class Nginx,React,Backend,Health,Postgres internal
    class Volume storage
    class Plaid external
```

### Diem can nhan manh khi ve lai

- `/` va `/assets/*` tra ve React SPA tu Nginx.
- `/api/*` duoc Nginx reverse proxy sang backend.
- Backend noi chuyen voi Postgres bang service name `database`, khong di qua internet.
- Plaid webhook la external callback di vao lai qua Nginx roi toi backend.
- Nen danh dau `PostgreSQL 5433:5432` la host mapping can duoc bao ve bang AWS Security Group.

---

## 3. Docker Compose Internal Services

So do nay dung de zoom-in vao ben trong EC2: moi service trong Docker Compose co vai tro gi, doc env nao, expose/call cai nao.

```mermaid
flowchart LR
    Env[".env on EC2\nruntime secrets/config"] --> Compose["docker compose"]

    Compose --> Nginx["nginx\nfrontend image\nports 80:80, 443:443"]
    Compose --> Backend["backend\nSpring Boot image\ninternal 8080"]
    Compose --> DB["database\npostgres:16-alpine\ninternal 5432\nhost 5433"]
    Compose --> Certbot["certbot\nLet's Encrypt certs"]
    Compose --> Prom["prometheus\ninternal 9090"]
    Compose --> Node["node-exporter\ninternal 9100\nhost /proc /sys /"]
    Compose --> Cadvisor["cadvisor\ninternal 8080\nDocker host mounts"]
    Compose --> PgExporter["postgres-exporter\ninternal 9187"]

    Nginx -->|"proxy /api/*"| Backend
    Nginx -->|"serve React static files"| Static["/usr/share/nginx/html"]
    Nginx -->|"read certs"| CertVol["certbot-etc volume"]
    Certbot -->|"write certs"| CertVol

    Backend -->|"DB_URL / JDBC"| DB
    DB -->|"data"| DbVol["expense_data-global volume"]

    PgExporter -->|"DATA_SOURCE_NAME"| DB
    Prom -->|"scrape backend:8080/actuator/prometheus"| Backend
    Prom -->|"scrape prometheus:9090"| Prom
    Prom -->|"scrape node-exporter:9100"| Node
    Prom -->|"scrape cadvisor:8080"| Cadvisor
    Prom -->|"scrape postgres-exporter:9187"| PgExporter
    Prom -->|"remote_write HTTPS"| Grafana["Grafana Cloud"]

    classDef config fill:#2e1f47,stroke:#b388ff,color:#fff
    classDef public fill:#3a2520,stroke:#ff7043,color:#fff
    classDef app fill:#1b2a41,stroke:#64b5f6,color:#fff
    classDef storage fill:#243b2f,stroke:#81c784,color:#fff
    classDef metrics fill:#1d3440,stroke:#4dd0e1,color:#fff

    class Env,Compose config
    class Nginx public
    class Backend,DB,Certbot,Static app
    class DbVol,CertVol storage
    class Prom,Node,Cadvisor,PgExporter,Grafana metrics
```

### Diem can nhan manh khi ve lai

- `.env` la cau noi giua GitHub Secrets va Docker Compose runtime.
- Docker Compose inject env vao container qua `environment`.
- Nginx la container duy nhat publish `80/443`.
- Backend khong publish port ra host, chi duoc Nginx va Prometheus goi qua Docker network.
- Prometheus config that su chay trong container la `/tmp/prometheus.yml`, duoc render tu template luc container start.
- `postgres-exporter` doc DB bang user/pass tu `.env`, roi bien DB health thanh metrics.

---

## 4. CI/CD And Observability Flow

So do nay ket hop hai pipeline khac nhau nhung nen ve tach lane ro rang:

- Deployment pipeline: code -> image -> EC2.
- Metrics pipeline: exporters -> Prometheus -> Grafana Cloud.

```mermaid
flowchart TB
    subgraph CICD["CI/CD Pipeline"]
        Push["Developer pushes to main"] --> Test["Job 1: Build & Test\nMaven test + JaCoCo\nSonarCloud\nFrontend audit/build checks"]
        Test --> Build["Job 2: Build Images\nbackend:<sha>\nfrontend:<sha>"]
        Build --> Trivy["Trivy scan backend image"]
        Trivy --> GHCR["Push images to GHCR"]
        GHCR --> SSH["Job 3: SSH Deploy to EC2"]
        SSH --> WriteEnv["Write .env from GitHub Secrets\nDB/JWT/Plaid/Grafana/Telegram\nBACKEND_IMAGE/FRONTEND_IMAGE"]
        WriteEnv --> Pull["docker login ghcr.io\ndocker compose pull"]
        Pull --> Up["docker compose up -d"]
        Up --> RecreateProm["force recreate prometheus\nrender latest /tmp/prometheus.yml"]
        RecreateProm --> HealthWait["wait backend/nginx healthy"]
        HealthWait --> Smoke["run smoke-test.sh"]
        Smoke --> Notify["Telegram deploy result\n.last_successful_deploy"]
    end

    subgraph Runtime["Production Runtime on EC2"]
        Nginx["nginx"]
        Backend["backend"]
        DB["database"]
        Prom["prometheus"]
        Node["node-exporter"]
        Cadvisor["cadvisor"]
        PgExporter["postgres-exporter"]
    end

    Up -. "starts/updates services" .-> Runtime
    RecreateProm -. "refreshes rendered config" .-> Prom

    subgraph Metrics["Observability Metrics Pipeline"]
        Backend -->|"exposes /actuator/prometheus"| Prom
        Node -->|"host metrics"| Prom
        Cadvisor -->|"container metrics"| Prom
        PgExporter -->|"postgres metrics"| Prom
        Prom -->|"self metrics"| Prom
        Prom -->|"remote_write HTTPS\n/api/prom/push"| Grafana["Grafana Cloud Metrics"]
        Grafana --> Explore["Grafana Explore\nPromQL"]
        Grafana --> Dashboards["Dashboards"]
        Grafana --> Alerts["Alert Rules\nTelegram contact point"]
    end

    classDef cicd fill:#2e1f47,stroke:#b388ff,color:#fff
    classDef runtime fill:#1b2a41,stroke:#64b5f6,color:#fff
    classDef metrics fill:#1d3440,stroke:#4dd0e1,color:#fff
    classDef notify fill:#3a2520,stroke:#ff7043,color:#fff

    class Push,Test,Build,Trivy,GHCR,SSH,WriteEnv,Pull,Up,RecreateProm,HealthWait,Smoke cicd
    class Nginx,Backend,DB,Prom,Node,Cadvisor,PgExporter runtime
    class Grafana,Explore,Dashboards,Alerts metrics
    class Notify notify
```

### Diem can nhan manh khi ve lai

- Deployment pipeline va metrics pipeline la hai dong khac nhau.
- CI/CD chi chay khi push code/deploy.
- Observability chay lien tuc moi 30 giay.
- `docker compose up -d` cap nhat service.
- `docker compose up -d --no-deps --force-recreate prometheus` dam bao Prometheus render lai config moi nhat.
- Grafana Cloud khong can inbound access vao EC2.
- Alert cua Grafana dua tren metrics, khac voi cron monitor/Telegram script cua Phase 1.

---

## Goi Y Khi Ve Lai Bang Excalidraw/Figma

Nen dung quy uoc mau nhat quan:

| Mau | Y nghia |
|---|---|
| Cam / do | Public internet, public port, security boundary |
| Xanh duong | Internal app/container traffic |
| Xanh la | Database, volume, persistent data |
| Tim | Secrets, env vars, GitHub Actions |
| Cyan | Metrics, Prometheus, Grafana |

Nen dung 3 kieu mui ten:

| Mui ten | Y nghia |
|---|---|
| Mui ten lien | Request/runtime traffic |
| Mui ten dut | Deploy/control action |
| Mui ten xanh/cyan | Metrics scrape/remote write |

Nen co label tren moi mui ten:

- `HTTPS 443`
- `HTTP 80 -> 443`
- `/api/*`
- `JDBC 5432`
- `scrape 30s`
- `remote_write /api/prom/push`
- `docker compose pull`
- `docker compose up -d`

---

## Canh Bao Kien Truc Can Ghi Nho

Hien tai Nginx co proxy:

```text
/actuator/ -> backend:8080
```

Va Spring Security permit:

```text
/actuator/prometheus
```

Dieu nay co nghia la neu Nginx cho public `/actuator/`, thi endpoint Prometheus metrics co the truy cap tu internet qua domain.

Khi ve so do production chuan, nen the hien mong muon cuoi cung la:

```text
/actuator/prometheus: internal Prometheus only
/actuator/health: public or semi-public health check
```

Day la mot diem hardening nen xu ly rieng sau khi hoan tat dashboard/alert.
