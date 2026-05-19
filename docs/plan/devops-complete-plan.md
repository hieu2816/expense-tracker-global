# Kế hoạch Nâng cấp DevOps — Spendwiser.me (Bản hoàn chỉnh)

> **Dự án:** Expense Tracker — spendwiser.me
> **Ngày lập:** 2026-05-16
> **Stack hiện tại:** AWS EC2 t2.micro · Ubuntu · Docker Compose · Nginx · Spring Boot · PostgreSQL 16 · GitHub Actions · Certbot · Telegram Alert · Bash/Cron Monitoring
> **Mục tiêu kép:** Nâng cấp hệ thống thực tế + học công nghệ phổ biến, khó outdate, đưa vào portfolio/phỏng vấn

---

## Nguyên tắc lựa chọn công nghệ

Mỗi công nghệ được đưa vào phải thoả mãn ít nhất một tiêu chí:

| Tiêu chí | Ý nghĩa |
|---|---|
| Giải quyết vấn đề thật | Phải xử lý một điểm yếu đang tồn tại trong Spendwiser |
| Công nghệ cốt lõi DevOps | Đại diện cho năng lực quan trọng: CI/CD, security, observability, IaC, orchestration |
| Khó bị outdate | Có vòng đời dài, được industry adopt rộng rãi |
| Giá trị portfolio cao | Xuất hiện thường xuyên trong JD DevOps/SRE |
| Không làm production phức tạp thêm | Production giữ Docker Compose — K8s chỉ là staging/lab |

---

## Baseline đo được (Trước khi nâng cấp)

### Nhóm CI/CD & Triển khai

| Metric | Giá trị | Trạng thái |
|---|---|---|
| Total Pipeline Duration | ~10 phút (cold) / ~2.5 phút (cached) | 🟡 |
| Server Deployment Time | **8 phút 51 giây** | 🔴 |
| Application Downtime | Chưa đo được | 🟡 |

### Nhóm Tài nguyên Máy chủ

| Metric | Trước deploy | Sau deploy | Trạng thái |
|---|---|---|---|
| CPU Core 0 | 65.5% / 88.6% (backend/frontend) | — | 🟡 |
| CPU Core 1 | 65.1% / **94.8%** (backend/frontend) | — | 🔴 |
| RAM Usage | 404MB / 911MB | 652MB / 911MB | 🟡 |
| Swap Usage | 654MB / 2GB | 865MB / 2GB | 🟠 |
| Storage | ~0MB thay đổi | Ổn định | ✅ |

### Nhóm Hiệu năng & Vận hành

| Metric | Giá trị | Trạng thái |
|---|---|---|
| API Latency P95 | 140ms | 🟡 |
| Bank API Sync Time | 1618ms (cold) / 6–700ms (warm) | 🟡 |
| MTTR | **> 10 phút — FAILED** | 🔴 |

### Nhóm Chất lượng & Bảo mật

| Metric | Giá trị | Trạng thái |
|---|---|---|
| Test Coverage | **0%** | 🔴 |
| Vulnerabilities (CVE) | **2 Critical, 12 High** | 🔴 |
| Code Smells | 32 issues (SonarLint IDE) | 🟡 |
| Quality Gate | **None / Manual** | 🔴 |

---

## Tổng quan Roadmap

| Phase | Tên | Công nghệ chính | Ưu tiên | Thời gian ước tính |
|---|---|---|---|---|
| 0 | Hotfix Production | External health-check, JVM tuning, CVE patch | 🔴 Ngay | 2–3 ngày |
| 1 | DevSecOps & Artifact | JaCoCo, SonarCloud, Trivy, GHCR | 🔴 Cao | Tuần 1–3 |
| 2 | Observability | Prometheus, Grafana, Exporters, Micrometer | 🔴 Cao | Tuần 4–6 |
| 3 | Infrastructure as Code | Terraform, AWS Provider, S3 remote state | 🟠 Cao | Tuần 7–8 |
| 4 | Kubernetes Lab | K3s/Kind, Manifests, Ingress, Probes | 🟠 Cao | Tuần 9–10 |
| 5 | Helm | Helm Chart, multi-env values | 🟡 Trung bình | Tuần 11 |
| 6 | GitOps | Argo CD | 🟡 Trung bình | Tuần 12 |
| 7 | Centralized Logging | Loki, Promtail, Grafana Logs | 🟡 Trung bình | Tuần 13 |
| 8 | Production Hardening | RDS, SSM, CloudWatch, Backup | 🔵 Tùy chọn | Khi có user thật |

---

## Bước 0 — Hotfix Production (Làm trước tất cả)

> **Lý do làm trước:** 3 vấn đề Critical đang ảnh hưởng production ngay hôm nay. Không nên bắt đầu nâng cấp pipeline trong khi hệ thống đang có lỗ hổng bảo mật Critical và không tự phục hồi được.

### 0.1 Fix MTTR — External Health-Check Script

**Vấn đề:** Docker `restart: always` không hoạt động khi kernel OOM Kill container Spring Boot. MTTR thực tế > 10 phút.

Tạo `/opt/spendwiser/healthcheck.sh`:

```bash
#!/bin/bash
# External health-check chạy NGOÀI Docker, cron mỗi 1 phút
BACKEND_URL="http://localhost:8080/actuator/health"
LOCK_FILE="/tmp/hc_restart.lock"
LOG_FILE="/var/log/spendwiser-health.log"

send_telegram() {
  curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
    -d "chat_id=${TELEGRAM_CHAT_ID}" -d "text=$1" > /dev/null
}

HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BACKEND_URL")

if [ "$HTTP" != "200" ]; then
  echo "$(date -u) DOWN ($HTTP)" >> "$LOG_FILE"

  # Tránh restart loop
  if [ -f "$LOCK_FILE" ]; then
    LAST=$(cat "$LOCK_FILE")
    NOW=$(date +%s)
    if [ $((NOW - LAST)) -lt 300 ]; then
      send_telegram "🚨 [spendwiser] CRITICAL: Backend down, restart bị block do loop guard. Cần can thiệp thủ công."
      exit 1
    fi
  fi

  date +%s > "$LOCK_FILE"
  cd /opt/spendwiser && docker compose restart backend
  sleep 40

  HTTP2=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BACKEND_URL")
  if [ "$HTTP2" = "200" ]; then
    send_telegram "✅ [spendwiser] Backend RECOVERED sau external restart. Downtime ~1 phút."
    rm -f "$LOCK_FILE"
  else
    LOGS=$(docker compose logs --tail=20 backend 2>&1)
    send_telegram "🚨 [spendwiser] Backend KHÔNG phục hồi được.
Logs:
$LOGS"
  fi
fi
```

```bash
chmod +x /opt/spendwiser/healthcheck.sh

# Thêm vào crontab (chạy mỗi 1 phút)
echo "* * * * * ubuntu /opt/spendwiser/healthcheck.sh" | sudo tee /etc/cron.d/spendwiser-health
```

### 0.2 Tune JVM Heap — Giảm RAM/Swap pressure

Cập nhật `docker-compose.yml` service backend:

```yaml
backend:
  environment:
    JAVA_OPTS: >-
      -Xms256m
      -Xmx450m
      -XX:+UseContainerSupport
      -XX:MaxRAMPercentage=50.0
      -XX:+ExitOnOutOfMemoryError
  deploy:
    resources:
      limits:
        memory: 600m
```

`-XX:+ExitOnOutOfMemoryError` quan trọng: ép container exit thay vì bị SIGKILL, cho phép Docker restart hoạt động bình thường.

### 0.3 Vá 2 CVE Critical ngay

```bash
# Backend — identify CVE cụ thể
mvn dependency-check:check
# Report tại: target/dependency-check-report.html

# Frontend — fix tự động
npm audit
npm audit fix
# Nếu còn CVE Critical sau fix:
npm audit fix --force  # Chỉ dùng nếu hiểu breaking changes
```

### Tiêu chí hoàn thành Bước 0

| Tiêu chí | Mục tiêu |
|---|---|
| MTTR khi backend crash | < 2 phút |
| CVE Critical | 0 |
| Swap usage khi idle | < 700MB |
| JVM có hard memory limit | Có |

---

## Phase 1 — DevSecOps & Artifact Management (Tuần 1–3)

> **Mục tiêu:** Biến pipeline từ "deploy được" thành "deploy an toàn". Build không còn chạy trên EC2. Mọi code merge đều được kiểm tra chất lượng và bảo mật tự động.

### Công nghệ

| Công nghệ | Vai trò | Lý do chọn |
|---|---|---|
| **JaCoCo** | Đo test coverage Java | Chuẩn Maven, tích hợp SonarCloud trực tiếp |
| **SonarCloud** | Static analysis, quality gate | Free cho public repo, industry standard |
| **Trivy** | Scan CVE trong Docker image | Nhẹ, nhanh, đang thay thế Snyk/Anchore |
| **OWASP Dependency-Check** | Scan Java dependency CVE | Báo cáo chi tiết, Maven plugin dễ tích hợp |
| **npm audit** | Scan frontend dependency | Built-in npm, không cần thêm tool |
| **GHCR** | Docker image registry | Native GitHub Actions, miễn phí, không cần service ngoài |

> **Tại sao GHCR thay vì JFrog?** GHCR native với GitHub Actions — không cần thêm credential, không thêm service ngoài để quản lý. JFrog enterprise tốt hơn về artifact management, nhưng với single-developer GitHub project, GHCR là lựa chọn tự nhiên hơn. Có thể bổ sung JFrog vào README dưới dạng "alternative explored" để vẫn có trên CV.

### Pipeline mục tiêu

```
Developer push → main
        ↓
GitHub Actions Runner (ubuntu-latest)
        ↓
[Job 1: build-and-test]
├── Cache Maven dependencies
├── Spin up PostgreSQL service container
├── mvn clean verify (unit + integration tests)
├── JaCoCo coverage report
├── SonarCloud scan + Quality Gate check
│   └── FAIL nếu: coverage < 60%, Critical CVE > 0, Blocker > 0
├── OWASP Dependency-Check
│   └── FAIL nếu có CVSS ≥ 9
└── npm audit (frontend)
        ↓
[Job 2: build-image] (chỉ chạy nếu Job 1 pass)
├── Build Docker image (backend + frontend)
├── Trivy scan image
│   └── FAIL nếu có Critical CVE trong image
├── Tag image theo Git SHA: ghcr.io/user/spendwiser:${{ github.sha }}
└── Push lên GHCR
        ↓
[Job 3: deploy] (chỉ chạy nếu Job 2 pass)
├── SSH vào EC2
├── docker compose pull (pull image mới từ GHCR)
├── docker compose up -d (zero rebuild trên EC2)
├── Health check polling
├── Smoke test
└── Telegram notify (SUCCESS/FAILURE + logs)
```

### Cấu hình JaCoCo (`pom.xml`)

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
    <execution>
      <id>check</id>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.60</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### GitHub Actions workflow (`.github/workflows/deploy.yml`)

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: expense_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports: ["5432:5432"]
        options: --health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5

    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }  # SonarCloud cần full history

      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }

      - name: Cache Maven
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-maven-

      - name: Build, Test & Coverage
        run: mvn clean verify jacoco:report
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/expense_test
          SPRING_DATASOURCE_USERNAME: test
          SPRING_DATASOURCE_PASSWORD: test

      - name: OWASP Dependency-Check
        run: mvn dependency-check:check -DfailBuildOnCVSS=9

      - name: SonarCloud Scan
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: |
          mvn sonar:sonar \
            -Dsonar.projectKey=${{ secrets.SONAR_PROJECT_KEY }} \
            -Dsonar.organization=${{ secrets.SONAR_ORG }} \
            -Dsonar.host.url=https://sonarcloud.io \
            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
            -Dsonar.qualitygate.wait=true

      - name: npm audit (frontend)
        working-directory: ./frontend
        run: |
          npm ci
          npm audit --audit-level=critical

  build-image:
    needs: build-and-test
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build Docker image
        run: |
          docker build -t ghcr.io/${{ github.repository }}/backend:${{ github.sha }} ./backend
          docker build -t ghcr.io/${{ github.repository }}/frontend:${{ github.sha }} ./frontend

      - name: Trivy scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ghcr.io/${{ github.repository }}/backend:${{ github.sha }}
          exit-code: 1
          severity: CRITICAL

      - name: Push to GHCR
        run: |
          docker push ghcr.io/${{ github.repository }}/backend:${{ github.sha }}
          docker push ghcr.io/${{ github.repository }}/frontend:${{ github.sha }}
          # Tag latest
          docker tag ghcr.io/${{ github.repository }}/backend:${{ github.sha }} \
            ghcr.io/${{ github.repository }}/backend:latest
          docker push ghcr.io/${{ github.repository }}/backend:latest

  deploy:
    needs: build-image
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ubuntu
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /opt/spendwiser

            # Inject env
            echo "BACKEND_IMAGE=ghcr.io/${{ github.repository }}/backend:${{ github.sha }}" >> .env
            echo "FRONTEND_IMAGE=ghcr.io/${{ github.repository }}/frontend:${{ github.sha }}" >> .env

            # Pull images từ GHCR — KHÔNG rebuild trên EC2
            docker compose pull

            # Bootstrap TLS nếu cần
            bash script/bootstrap-tls.sh

            # Deploy
            docker compose up -d

            # Health check polling
            bash script/smoke-test.sh

      - name: Notify Telegram (Success)
        if: success()
        run: |
          curl -s -X POST "https://api.telegram.org/bot${{ secrets.TELEGRAM_BOT_TOKEN }}/sendMessage" \
            -d "chat_id=${{ secrets.TELEGRAM_CHAT_ID }}" \
            -d "text=✅ Deploy SUCCESS: ${{ github.sha }} | Pipeline: ${{ github.run_id }}"

      - name: Notify Telegram (Failure)
        if: failure()
        run: |
          curl -s -X POST "https://api.telegram.org/bot${{ secrets.TELEGRAM_BOT_TOKEN }}/sendMessage" \
            -d "chat_id=${{ secrets.TELEGRAM_CHAT_ID }}" \
            -d "text=🚨 Deploy FAILED: ${{ github.sha }} | Check: https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }}"
```

### Rollback script (`script/rollback.sh`)

```bash
#!/bin/bash
# Usage: ./rollback.sh <git-sha>
TARGET_SHA=${1:-"latest"}
echo "Rolling back to: $TARGET_SHA"

cd /opt/spendwiser
sed -i "s|backend:.*|backend:$TARGET_SHA|g" .env
docker compose pull
docker compose up -d
bash script/smoke-test.sh
```

### Tiêu chí hoàn thành Phase 1

| Metric | Mục tiêu |
|---|---|
| CVE Critical trong pipeline | 0 — fail build nếu > 0 |
| Test coverage backend | ≥ 60% |
| Quality Gate | PASS tự động trước mỗi deploy |
| EC2 build Maven/Node khi deploy | Không còn |
| Server Deployment Time | < 2 phút |
| Có thể rollback bằng image tag | Có |

---

## Phase 2 — Observability với Prometheus & Grafana (Tuần 4–6)

> **Mục tiêu:** Thay thế monitoring rời rạc bằng script/Telegram bằng observability chuẩn: metric có trend, dashboard trực quan, alert có ngữ cảnh.

### Giải quyết RAM constraint trước

**Vấn đề thực tế:** Prometheus + Grafana + exporters thêm ~325–425MB. EC2 hiện đang dùng ~652MB / 911MB.

**Giải pháp: Grafana Cloud Free Tier**

| | Self-host trên EC2 | Grafana Cloud Free |
|---|---|---|
| Chi phí | ~$0 nhưng tốn RAM | $0 hoàn toàn |
| RAM tiêu thụ trên EC2 | +325MB → OOM | Chỉ Prometheus (~150MB) |
| Grafana | Trên EC2 | Grafana Cloud (SaaS) |
| Loki | Tự host | Grafana Cloud Loki (50GB free) |
| Alertmanager | Tự host | Grafana Cloud Alerting |
| Portfolio value | Tốt | Tốt tương đương |

→ **Dùng Grafana Cloud Free Tier** + chỉ chạy Prometheus và các exporter trên EC2.

Nếu sau này muốn fully self-hosted để học thêm, tạo EC2 t3.micro riêng (~$8/tháng) cho monitoring stack.

### Kiến trúc Observability

```
EC2 Production
├── Prometheus (scrape, ~150MB RAM)
├── Node Exporter (host metrics, ~10MB)
├── cAdvisor (container metrics, ~50MB)
├── PostgreSQL Exporter (~15MB)
└── Spring Boot /actuator/prometheus
        ↓ remote_write
Grafana Cloud
├── Metrics storage (Mimir)
├── Log storage (Loki)
├── Grafana dashboards
└── Alerting → Telegram webhook
```

### Cài đặt Prometheus Stack trên EC2

Thêm vào `docker-compose.yml`:

```yaml
prometheus:
  image: prom/prometheus:latest
  volumes:
    - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    - prometheus_data:/prometheus
  command:
    - '--config.file=/etc/prometheus/prometheus.yml'
    - '--storage.tsdb.retention.time=7d'  # Giữ 7 ngày, tiết kiệm disk
  restart: unless-stopped
  deploy:
    resources:
      limits:
        memory: 200m

node-exporter:
  image: prom/node-exporter:latest
  pid: host
  volumes:
    - /proc:/host/proc:ro
    - /sys:/host/sys:ro
    - /:/rootfs:ro
  command: '--path.rootfs=/rootfs'
  restart: unless-stopped
  deploy:
    resources:
      limits:
        memory: 30m

cadvisor:
  image: gcr.io/cadvisor/cadvisor:latest
  volumes:
    - /:/rootfs:ro
    - /var/run:/var/run:ro
    - /sys:/sys:ro
    - /var/lib/docker/:/var/lib/docker:ro
  restart: unless-stopped
  deploy:
    resources:
      limits:
        memory: 80m

postgres-exporter:
  image: prometheuscommunity/postgres-exporter:latest
  environment:
    DATA_SOURCE_NAME: "postgresql://${DB_USERNAME}:${DB_PASSWORD}@database:5432/${DB_NAME}?sslmode=disable"
  restart: unless-stopped
  deploy:
    resources:
      limits:
        memory: 30m
```

`monitoring/prometheus.yml`:

```yaml
global:
  scrape_interval: 30s
  evaluation_interval: 30s

remote_write:
  - url: https://prometheus-prod-xx.grafana.net/api/prom/push
    basic_auth:
      username: <grafana_cloud_instance_id>
      password: <grafana_cloud_api_key>

scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend:8080']

  - job_name: 'node-exporter'
    static_configs:
      - targets: ['node-exporter:9100']

  - job_name: 'cadvisor'
    static_configs:
      - targets: ['cadvisor:8080']

  - job_name: 'postgres-exporter'
    static_configs:
      - targets: ['postgres-exporter:9187']
```

### Spring Boot — Expose Prometheus metrics

Thêm dependency `pom.xml`:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

`application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true
```

### Dashboard Grafana nên có

| Dashboard | Import ID (Grafana.com) | Metric chính |
|---|---|---|
| Spring Boot / Micrometer | 12685 | Request rate, P95 latency, error rate, JVM heap, GC |
| Node Exporter Full | 1860 | CPU, RAM, Swap, Disk, Network |
| Docker Container | 893 | CPU/RAM/restart per container |
| PostgreSQL | 9628 | Connections, query time, transaction rate |
| Custom: Business | Tự tạo | Plaid sync success/fail, bank sync duration |
| Custom: Deployment | Tự tạo | Deploy timestamp, downtime event, rollback count |

### Tiêu chí hoàn thành Phase 2

| Tiêu chí | Mục tiêu |
|---|---|
| Dashboard Spring Boot (JVM + API) | Có |
| Dashboard EC2 host (CPU/RAM/Swap/Disk) | Có |
| Dashboard Docker containers | Có |
| Dashboard PostgreSQL | Có |
| API Latency P95 có trend theo thời gian | Có |
| Alert khi backend down | Có (Telegram) |
| Alert khi disk/swap vượt ngưỡng | Có (Telegram) |
| Plaid sync failure metric | Có |

---

## Phase 3 — Infrastructure as Code với Terraform (Tuần 7–8)

> **Mục tiêu:** Quản lý toàn bộ AWS infrastructure bằng code — reproducible, version-controlled, không setup thủ công qua Console.

### Tài nguyên AWS nên Terraform hoá

| Resource | Có nên? | Ghi chú |
|---|---|---|
| EC2 instance | ✅ | Có thể recreate staging chỉ bằng `terraform apply` |
| Security Group | ✅ | Rules được review qua Git |
| Elastic IP | ✅ | IP cố định cho domain |
| IAM Role (EC2) | ✅ | Chuẩn bị cho CloudWatch, SSM |
| S3 bucket (Terraform state) | ✅ | Remote state — bắt buộc từ đầu |
| DynamoDB (state lock) | ✅ | Tránh concurrent apply |
| Key Pair | Có thể | Tùy cách quản lý SSH key |
| Route53 Record | Có thể | Nếu domain đang dùng Route53 |

### Cấu trúc thư mục

```
terraform/
├── provider.tf          # AWS provider + backend config
├── variables.tf         # Input variables
├── outputs.tf           # Output: public IP, instance ID
├── main.tf              # Root module
├── ec2.tf               # EC2 instance + user data
├── security-group.tf    # Inbound/outbound rules
├── iam.tf               # EC2 role + instance profile
├── network.tf           # VPC, subnet, internet gateway
├── storage.tf           # S3 state bucket + DynamoDB lock
└── environments/
    ├── staging.tfvars
    └── prod.tfvars
```

### Remote State — Bắt buộc từ đầu

```hcl
# provider.tf
terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "spendwiser-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "ap-southeast-1"
    encrypt        = true
    dynamodb_table = "spendwiser-terraform-lock"
  }
}
```

> **Tại sao phải có remote state ngay từ đầu?** Đây là câu interviewer hỏi đầu tiên khi thấy Terraform trên CV. State local không thể cộng tác, dễ mất, không có locking. S3 + DynamoDB là pattern chuẩn mọi team dùng.

### Tiêu chí hoàn thành Phase 3

| Tiêu chí | Mục tiêu |
|---|---|
| EC2 tạo được bằng `terraform apply` | Có |
| Security Group quản lý bằng code | Có |
| Remote state trên S3 | Có |
| State locking bằng DynamoDB | Có |
| Có thể recreate staging từ zero | Có |
| IAM role có least privilege | Có |

---

## Phase 4 — Kubernetes Staging/Lab (Tuần 9–10)

> **Mục tiêu:** Học K8s bằng cách deploy Spendwiser thật — không migrate production, giữ Docker Compose ổn định.

### Lựa chọn môi trường K8s

| Option | Chi phí | RAM cần | Phù hợp học | Portfolio |
|---|---|---|---|---|
| **Kind (local)** | $0 | Máy local | ✅✅ Tốt nhất cho học | ✅ Đủ (có thể document + screenshot) |
| **K3s trên EC2 t3.small riêng** | ~$15/tháng | 2GB | ✅✅ Rất tốt | ✅✅ Có URL thật |
| K3s trên EC2 production | $0 | OOM | ❌ Không nên | — |
| EKS | ~$70+/tháng | — | Overkill | ✅✅✅ Nhưng không cần giai đoạn này |

**Khuyến nghị:** Bắt đầu với **Kind local** để học nhanh, sau đó port lên **K3s EC2 riêng** để có môi trường persistent và URL thật cho portfolio.

### K8s Concepts cần nắm qua dự án này

| Concept | Áp dụng vào Spendwiser |
|---|---|
| Pod | Unit chạy backend / frontend |
| Deployment | Quản lý replica, rolling update, rollback |
| Service (ClusterIP) | Backend discovery trong cluster |
| Service (NodePort/LoadBalancer) | Expose frontend ra ngoài |
| Ingress | Thay Nginx reverse proxy — route `/api` vs `/` |
| ConfigMap | Non-sensitive config (DB host, Plaid env) |
| Secret | Sensitive config (DB password, JWT secret, API keys) |
| Liveness Probe | Restart container khi app chết |
| Readiness Probe | Chỉ route traffic khi app ready (Spring Boot khởi động chậm) |
| Resource Request/Limit | Kiểm soát CPU/RAM — tránh OOM |
| Namespace | Tách `staging` / `prod` namespace |
| PersistentVolumeClaim | Lưu PostgreSQL data trong lab |

### Cấu trúc thư mục K8s

```
k8s/
├── namespace.yaml
├── backend/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml
│   └── secret.yaml         # Không commit secret thật, dùng sealed-secrets sau
├── frontend/
│   ├── deployment.yaml
│   └── service.yaml
├── ingress.yaml
└── postgres/
    ├── statefulset.yaml     # StatefulSet cho DB — không Deployment
    ├── service.yaml
    └── pvc.yaml
```

### Ví dụ Backend Deployment

```yaml
# k8s/backend/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spendwiser-backend
  namespace: staging
spec:
  replicas: 1
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
        - name: backend
          image: ghcr.io/user/spendwiser/backend:latest  # Tag theo SHA khi deploy
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60  # Spring Boot cần thời gian khởi động
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
          envFrom:
            - configMapRef:
                name: backend-config
            - secretRef:
                name: backend-secrets
```

### Tiêu chí hoàn thành Phase 4

| Tiêu chí | Mục tiêu |
|---|---|
| Backend chạy được trên K8s | Có |
| Frontend chạy được trên K8s | Có |
| Truy cập được qua Ingress | Có |
| Liveness + Readiness probe | Có |
| Rolling update không downtime | Có |
| Rollback deployment bằng kubectl | Có |
| Resource limits đặt đúng | Có |

---

## Phase 5 — Helm (Tuần 11)

> **Mục tiêu:** Đóng gói K8s manifests thành Helm chart — hỗ trợ multi-environment, deploy theo tag, rollback bằng Helm.

> ⚠️ Chỉ học Helm sau khi đã thành thạo K8s manifest cơ bản. Nếu học Helm quá sớm sẽ không phân biệt được đâu là K8s concept, đâu là Helm templating.

### Cấu trúc Helm Chart

```
helm/spendwiser/
├── Chart.yaml
├── values.yaml            # Default values
├── values-dev.yaml        # Override cho dev/local
├── values-staging.yaml    # Override cho staging
└── templates/
    ├── _helpers.tpl
    ├── backend-deployment.yaml
    ├── backend-service.yaml
    ├── frontend-deployment.yaml
    ├── frontend-service.yaml
    ├── ingress.yaml
    ├── configmap.yaml
    └── secret.yaml
```

### Giá trị cần template hoá

| Giá trị | Lý do |
|---|---|
| `image.repository` | Khác nhau giữa local/GHCR |
| `image.tag` | Deploy theo Git SHA |
| `replicaCount` | Dev=1, staging có thể >1 |
| `resources.limits` | Mỗi môi trường khác nhau |
| `ingress.host` | Domain khác nhau |
| `env` | Config theo môi trường |

### Deploy command

```bash
# Install/upgrade
helm upgrade --install spendwiser ./helm/spendwiser \
  --namespace staging \
  --create-namespace \
  -f helm/spendwiser/values-staging.yaml \
  --set image.tag=$GIT_SHA

# Rollback
helm rollback spendwiser 1 --namespace staging
```

### Tiêu chí hoàn thành Phase 5

| Tiêu chí | Mục tiêu |
|---|---|
| Deploy được bằng `helm upgrade --install` | Có |
| Values riêng cho dev/staging | Có |
| Đổi image tag không sửa YAML | Có |
| Rollback được bằng `helm rollback` | Có |

---

## Phase 6 — GitOps với Argo CD (Tuần 12)

> **Mục tiêu học:** Hiểu mô hình GitOps — Git là source of truth, cluster tự đồng bộ. Giá trị chủ yếu là portfolio và hiểu concept, không phải production necessity.

### Flow GitOps

```
Developer push code
        ↓
CI (GitHub Actions) build + test + scan
        ↓
Build Docker image → push GHCR
        ↓
CI cập nhật image tag trong gitops/ folder
        ↓
Argo CD phát hiện thay đổi trong Git
        ↓
Argo CD sync K8s cluster tự động
```

### Cấu trúc GitOps repo

```
gitops/
├── apps/
│   └── spendwiser/
│       ├── application.yaml    # Argo CD Application CR
│       └── values-staging.yaml
└── clusters/
    └── staging/
        └── spendwiser.yaml
```

### Tiêu chí hoàn thành Phase 6

| Tiêu chí | Mục tiêu |
|---|---|
| Argo CD cài và chạy trên cluster | Có |
| App sync từ Git thành công | Có |
| Push code → auto deploy (không SSH tay) | Có |
| Rollback qua Git revert | Có |
| Drift detection hoạt động | Có |

---

## Phase 7 — Centralized Logging với Loki (Tuần 13)

> **Mục tiêu:** Tập trung log từ tất cả service vào một nơi, tìm kiếm được, correlate với metric.

> **Tại sao Loki thay vì ELK?** Elasticsearch tối thiểu 2GB RAM — không thể chạy trên EC2 nhỏ. Loki nhẹ hơn nhiều, tích hợp tốt với Grafana đã có từ Phase 2, phù hợp cho project quy mô này.

Nếu đã dùng **Grafana Cloud Free Tier** ở Phase 2, Loki đã có sẵn — chỉ cần cài **Promtail** trên EC2 để đẩy log lên.

### Log cần thu thập

| Nguồn | Mục đích |
|---|---|
| Spring Boot logs | Debug exception, API error, Plaid sync |
| Nginx access logs | Request pattern, status code, client IP |
| Nginx error logs | TLS issue, proxy error |
| Docker container logs | Restart/crash event |
| Deploy script logs | Debug pipeline failure |
| health-check script logs | Theo dõi MTTR event |

### Tiêu chí hoàn thành Phase 7

| Tiêu chí | Mục tiêu |
|---|---|
| Xem backend logs trong Grafana | Có |
| Xem Nginx logs trong Grafana | Có |
| Search theo keyword / error level | Có |
| Correlate log spike với metric spike | Có |

---

## Phase 8 — Production Hardening (Tùy chọn)

> Chỉ làm khi có user thật, data thật, hoặc yêu cầu bảo mật cao hơn.

| Công nghệ | Chi phí | Khi nào nên làm |
|---|---|---|
| **AWS SSM Session Manager** | $0 | Khi muốn đóng port SSH 22 — nên làm sớm nếu lo bảo mật |
| **AWS RDS db.t3.micro** | ~$15–20/tháng | Khi data production có giá trị, cần automated backup + PITR |
| **AWS Backup** | Theo dung lượng | Sau khi dùng RDS hoặc EBS nghiêm túc |
| **AWS Secrets Manager** | ~$0.40/secret/tháng | Khi muốn bỏ `.env` file trên server |
| **WAF** | ~$5+/tháng | Khi app public có traffic thật |
| **Upgrade t3.small** | ~$15/tháng | Nếu RAM vẫn là bottleneck sau JVM tuning |

---

## Bảng kế hoạch theo tuần

| Tuần | Công việc | Công nghệ | Kết quả cần đạt |
|---|---|---|---|
| **Tuần 0** | Fix MTTR, JVM tuning, vá CVE | Bash, Docker, OWASP | MTTR < 2 phút, 0 CVE Critical, RAM giảm |
| **Tuần 1** | JaCoCo + test setup | JaCoCo, Maven | Coverage report trong pipeline |
| **Tuần 2** | SonarCloud quality gate | SonarCloud | Pipeline block nếu quality thấp |
| **Tuần 3** | Build image + Trivy + GHCR | GHCR, Trivy, Docker | EC2 không build nữa, deploy < 2 phút |
| **Tuần 4** | Prometheus + exporters trên EC2 | Prometheus, Node Exporter, cAdvisor | Metrics scrape được |
| **Tuần 5** | Grafana Cloud + dashboards | Grafana Cloud, Micrometer | Dashboard host/container/app |
| **Tuần 6** | DB metrics + business metrics + alert | PostgreSQL Exporter, Alertmanager | Dashboard DB + Plaid + Alert Telegram |
| **Tuần 7** | Terraform baseline AWS | Terraform, S3, DynamoDB | EC2/SG/IAM quản lý bằng code |
| **Tuần 8** | Terraform network + remote state | Terraform | Recreate staging từ zero được |
| **Tuần 9** | K8s lab (Kind local) | Kind, kubectl | Backend/frontend chạy được trên K8s |
| **Tuần 10** | Ingress + probes + resources + K3s | K3s, Ingress, Probe | App truy cập được qua Ingress URL thật |
| **Tuần 11** | Helm chart | Helm | Deploy staging bằng `helm upgrade` |
| **Tuần 12** | Argo CD GitOps | Argo CD | Push code → auto sync staging |
| **Tuần 13** | Loki centralized logging | Loki, Promtail | Search log tập trung trong Grafana |

---

## Mục tiêu sau nâng cấp

| Metric | Baseline | Mục tiêu |
|---|---|---|
| Total Pipeline Duration | ~10 phút | < 5 phút |
| Server Deployment Time | 8m51s | < 2 phút |
| CPU Peak khi deploy | 94.8% | < 30% |
| RAM usage | 652/911MB | < 550MB |
| Swap usage | 865MB | < 400MB |
| MTTR | > 10 phút ❌ | < 2 phút |
| API Latency P95 | 140ms | Duy trì < 150ms, có trend |
| Test Coverage | 0% | ≥ 60% |
| CVE Critical | 2 | 0 |
| Quality Gate | None/Manual | Automated PASS/FAIL |

---

## Stack mục tiêu sau khi hoàn thành

### Production (Docker Compose — giữ ổn định)

```
AWS EC2 (t2.micro hoặc t3.small)
├── Docker Compose
│   ├── Nginx (reverse proxy, TLS, static)
│   ├── Spring Boot (backend)
│   ├── PostgreSQL 16 (hoặc RDS)
│   ├── Prometheus (scrape + remote_write)
│   ├── Node Exporter
│   ├── cAdvisor
│   └── PostgreSQL Exporter
├── Certbot / Let's Encrypt
├── External health-check cron
└── Telegram Alert

GitHub Actions CI/CD
├── JaCoCo + SonarCloud
├── Trivy + OWASP Dependency-Check
├── Build Docker image → GHCR
└── Deploy via SSH (pull image, restart)

Grafana Cloud (free tier)
├── Prometheus remote write
├── Loki logs (Promtail từ EC2)
├── Grafana dashboards
└── Alerting → Telegram

Terraform
└── EC2, Security Group, IAM, Elastic IP, S3, DynamoDB
```

### Kubernetes Lab/Staging

```
K3s hoặc Kind
├── Namespace: staging
├── Deployment (backend, frontend)
├── Service, Ingress
├── ConfigMap, Secret
├── Readiness/Liveness Probe
├── PersistentVolumeClaim (PostgreSQL)
├── Helm chart (multi-env values)
└── Argo CD (GitOps sync)
```

---

## Thứ tự ưu tiên nếu chỉ chọn 5 nhóm để làm trước

| # | Nhóm | Lý do |
|---|---|---|
| 1 | **Bước 0: MTTR + CVE** | Vấn đề production thật, không có lý do chờ |
| 2 | **SonarCloud + JaCoCo + Trivy + GHCR** | Nền tảng pipeline an toàn — mọi thứ sau build trên đây |
| 3 | **Prometheus + Grafana** | Kỹ năng observability quan trọng nhất, de-facto standard |
| 4 | **Terraform** | Kỹ năng IaC xuất hiện trong > 80% JD DevOps/SRE |
| 5 | **Kubernetes + Helm** | Phân biệt junior vs mid-level DevOps trên thị trường |
