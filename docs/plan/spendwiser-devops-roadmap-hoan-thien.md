# DevOps Roadmap Hoàn Thiện — Spendwiser.me

> **Dự án:** Spendwiser / Expense Tracker  
> **Stack hiện tại:** AWS EC2 t2.micro · Ubuntu · Docker Compose · Nginx · Spring Boot · PostgreSQL 16 · GitHub Actions · Certbot · Telegram Alert  
> **Mục tiêu:** Nâng cấp pipeline DevOps theo hướng thực tế, phù hợp với project hiện tại, đồng thời học các công nghệ cốt lõi dùng trong hệ thống lớn.  
> **Định hướng:** Không nhồi tool theo phong trào. Mỗi công nghệ đưa vào phải giải quyết một vấn đề thật hoặc giúp học một mảng DevOps quan trọng.

---

## 1. Nguyên tắc thiết kế roadmap

Roadmap này được xây dựng theo 4 nguyên tắc:

| Nguyên tắc | Ý nghĩa |
|---|---|
| **Ổn định trước, mở rộng sau** | Trước khi thêm tool mới, cần xử lý MTTR, OOM, RAM pressure và downtime. |
| **Production không bị over-engineering** | Production hiện tại vẫn nên giữ Docker Compose nếu đang ổn. Kubernetes nên học trong lab/staging trước. |
| **Tool phải gắn với vấn đề thật** | Không thêm Prometheus, Terraform, Kubernetes chỉ để “cho đẹp”; mỗi tool phải có mục tiêu rõ. |
| **Portfolio nhưng vẫn thực tế** | Có thể thêm các công nghệ mạnh như Kubernetes, Helm, Argo CD, nhưng nên đặt đúng vai trò: lab/staging/portfolio, không ép vào production quá sớm. |

---

## 2. Tổng quan roadmap

| Giai đoạn | Tên phase | Trọng tâm | Mức ưu tiên |
|---|---|---|---|
| **Bước 0** | Stabilization | MTTR, OOM, RAM, monitoring strategy | 🔴 Bắt buộc làm trước |
| **Phase 1** | DevSecOps & Artifact Management | Test, CVE scan, quality gate, GHCR, rollback | 🔴 Rất cao |
| **Phase 2** | Observability | Prometheus, Grafana, metrics, alerting | 🟠 Cao |
| **Phase 3** | Infrastructure as Code | Terraform, S3 remote state, DynamoDB lock | 🟠 Cao |
| **Phase 4** | Kubernetes Lab/Staging | K8s core concepts, staging environment | 🟡 Trung bình cao |
| **Phase 5** | Helm | K8s packaging, values per environment | 🟡 Trung bình |
| **Phase 6** | Argo CD / GitOps | GitOps, desired state, drift detection | 🟡 Trung bình |
| **Phase 7** | Centralized Logging | Loki, Promtail, Grafana Explore | 🔵 Sau observability |
| **Phase 8** | Production Hardening | RDS, SSM, WAF, backup, scaling | 🔵 Tùy chọn/tương lai |

---

# Bước 0 — Stabilization trước khi thêm công nghệ mới

## Mục tiêu

Trước khi thêm Prometheus, Grafana, Kubernetes hoặc Terraform, cần đảm bảo hệ thống hiện tại không còn các vấn đề nền tảng:

- MTTR hiện tại > 10 phút cần giảm xuống < 2 phút.
- Cần xác minh rõ backend chết do OOM hay nguyên nhân khác.
- Cần giảm RAM pressure trên t2.micro.
- Cần quyết định monitoring stack sẽ chạy ở đâu để tránh làm EC2 production bị OOM.
- Cần refactor monitoring hiện tại thay vì tạo nhiều script restart chồng chéo.

## Việc cần làm

| Công việc | Mục đích | Kết quả mong muốn |
|---|---|---|
| Kiểm tra OOM bằng `docker inspect` | Biết container có bị OOMKilled không | Có bằng chứng rõ về nguyên nhân crash |
| Kiểm tra `dmesg`, `journalctl` | Xem kernel/system có kill process không | Xác định RAM/CPU/system event |
| Tune JVM heap | Giảm RAM backend | Backend ổn định hơn trên máy nhỏ |
| Refactor `monitor.sh` | Tránh nhiều script health-check cạnh tranh nhau | Một cơ chế self-healing thống nhất |
| Giảm cron health check xuống 1 phút | Phát hiện lỗi nhanh hơn | MTTR < 2 phút |
| Thêm restart loop guard | Tránh restart vô hạn | Alert critical khi không tự phục hồi |
| Đo downtime deploy | Biết downtime thực tế | Có baseline để cải tiến |
| Quyết định chiến lược monitoring | Tránh cài full stack lên t2.micro | Không gây OOM khi thêm observability |

## JVM tuning đề xuất

Trong `docker-compose.yml`:

```yaml
services:
  backend:
    environment:
      JAVA_OPTS: "-Xms256m -Xmx450m -XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0"
    mem_limit: 600m
```

Nếu backend vẫn bị OOM, cần kiểm tra:

```bash
docker inspect backend --format='{{json .State}}' | jq
dmesg -T | grep -i "killed process"
journalctl -xe
docker compose logs backend --tail=200
```

## Monitoring strategy trước khi sang Phase 2

Không nên chạy toàn bộ Prometheus + Grafana + cAdvisor + Loki trên EC2 t2.micro production hiện tại.

| Option | Khuyến nghị | Lý do |
|---|---|---|
| **Grafana Cloud Free Tier** | ✅ Khuyến nghị nhất | Không tốn RAM production, dễ học Grafana/metrics/logs |
| **EC2 monitoring riêng** | ✅ Tốt | Tách monitoring khỏi app, thực tế hơn |
| **Upgrade app server lên t3.small** | ✅ Nếu có ngân sách | Giải quyết RAM app + monitoring |
| **Cài tất cả lên t2.micro hiện tại** | ❌ Không nên | Rất dễ OOM |

## Tiêu chí hoàn thành Bước 0

| Metric | Mục tiêu |
|---|---|
| MTTR | < 2 phút |
| Backend restart loop | Có guard |
| OOM root cause | Có bằng chứng rõ ràng |
| JVM memory | Có giới hạn |
| Monitoring location | Đã quyết định |
| Downtime deploy | Có script đo baseline |

---

# Phase 1 — DevSecOps & Artifact Management

## Mục tiêu

Làm pipeline an toàn hơn trước khi tối ưu các phần nâng cao.

Hiện tại các vấn đề chính là:

- Test coverage = 0%.
- Có CVE Critical/High.
- Quality gate còn manual.
- Build đang chạy trên EC2, gây CPU/RAM spike.
- Artifact chưa thật sự immutable.
- Chưa có rollback rõ ràng.

## Công nghệ sử dụng

| Công nghệ | Vai trò |
|---|---|
| **JaCoCo** | Đo test coverage cho Java |
| **SonarCloud** | Static analysis, code smell, bug, quality gate |
| **Trivy** | Scan Docker image và dependency CVE |
| **npm audit / OWASP dependency-check** | Scan dependency frontend/backend |
| **GHCR** | Docker image registry cho production |
| **JFrog Artifactory** | Optional lab/portfolio enterprise artifact management |
| **GitHub Actions** | CI/CD pipeline chính |
| **Git SHA image tag** | Versioning artifact |
| **Rollback script** | Quay lại image cũ khi deploy fail |

## GHCR hay JFrog?

| Tiêu chí | GHCR | JFrog |
|---|---|---|
| Phù hợp production hiện tại | ✅ Rất tốt | Ổn nhưng thêm service ngoài |
| Tích hợp GitHub Actions | ✅ Native | Cần cấu hình thêm |
| Giá trị học enterprise | Trung bình | ✅ Cao |
| Độ phức tạp | Thấp | Trung bình |
| Khuyến nghị | Dùng chính cho production | Làm thêm lab hoặc ghi trong README |

**Quyết định đề xuất:**

```text
Production thật: GHCR
Portfolio mở rộng: JFrog lab / optional documentation
```

## Pipeline mục tiêu

```text
Developer push code
        ↓
GitHub Actions
        ↓
Run unit/integration tests
        ↓
Run JaCoCo coverage
        ↓
Run SonarCloud quality gate
        ↓
Run Trivy security scan
        ↓
Build Docker image
        ↓
Push image to GHCR with Git SHA tag
        ↓
SSH EC2
        ↓
docker compose pull
        ↓
docker compose up -d
        ↓
Smoke test
        ↓
Success: save image tag
Failure: rollback previous image
```

## Image tagging strategy

```text
ghcr.io/<username>/spendwiser-backend:<git-sha>
ghcr.io/<username>/spendwiser-frontend:<git-sha>
ghcr.io/<username>/spendwiser-backend:latest
ghcr.io/<username>/spendwiser-frontend:latest
```

Trong production, nên deploy theo Git SHA, không nên chỉ dùng `latest`.

## Rollback strategy

Tạo file lưu version thành công gần nhất:

```text
/opt/spendwiser/.last_successful_backend_image
/opt/spendwiser/.last_successful_frontend_image
```

Flow rollback:

```text
Deploy image mới
→ smoke test pass
→ ghi image mới vào .last_successful_*

Deploy image mới
→ smoke test fail
→ pull lại image cũ
→ docker compose up -d
→ gửi Telegram alert
```

## Quality gate đề xuất

Không nên ép coverage tổng 60% ngay từ đầu nếu hiện tại coverage = 0%.

| Giai đoạn | Rule |
|---|---|
| Tuần đầu | Critical CVE > 0 → fail |
| Tuần đầu | Build/test fail → fail |
| Sau khi có test nền | New code coverage < 50% → fail |
| Sau đó | Overall coverage < 30% → warning |
| Dài hạn | Overall coverage ≥ 60% |

## Deliverables

- [ ] JaCoCo report trong pipeline.
- [ ] SonarCloud project connected với GitHub repo.
- [ ] Trivy scan Docker image.
- [ ] GHCR image registry.
- [ ] Docker image build trên GitHub Actions.
- [ ] EC2 không còn build Maven/Node trực tiếp.
- [ ] Deploy bằng image tag Git SHA.
- [ ] Rollback script.
- [ ] README có sơ đồ pipeline mới.

## Tiêu chí hoàn thành

| Metric | Baseline | Mục tiêu |
|---|---:|---:|
| Critical CVE | > 0 | 0 |
| Test coverage | 0% | Có baseline, tăng dần |
| Server deployment time | ~8m51s | < 2 phút |
| CPU spike trên EC2 khi deploy | ~94.8% | < 40% |
| Artifact | Không rõ ràng | Immutable Docker image |
| Rollback | Chưa rõ | Có script rollback |

---

# Phase 2 — Observability với Prometheus & Grafana

## Mục tiêu

Chuyển từ monitoring bằng script rời rạc sang observability có metric, dashboard, alert và trend dài hạn.

## Lưu ý quan trọng về RAM

Không nên self-host toàn bộ stack trên EC2 t2.micro production hiện tại.

Ước tính RAM:

| Service | RAM ước tính |
|---|---:|
| Prometheus | 150–200MB |
| Grafana | 100–150MB |
| Node Exporter | ~10MB |
| cAdvisor | ~50MB |
| PostgreSQL Exporter | ~15MB |
| Loki optional | ~100MB |
| **Tổng thêm chưa có Loki** | **325–425MB** |
| **Tổng thêm có Loki** | **425–525MB** |

Nếu production hiện đã dùng khoảng 650MB/911MB, việc thêm full stack vào cùng máy rất dễ gây OOM.

## Deployment option

| Option | Mức phù hợp | Ghi chú |
|---|---|---|
| **Grafana Cloud Free Tier** | ✅ Rất phù hợp | Không ăn RAM EC2, học được Grafana/alert/logs |
| **Monitoring EC2 riêng** | ✅ Phù hợp | Thực tế hơn, tách monitoring khỏi app |
| **Upgrade lên t3.small** | ✅ Tốt nếu có ngân sách | Có 2GB RAM, đỡ swap |
| **Cài chung t2.micro** | ❌ Không nên | Rủi ro OOM cao |

## Thành phần nên có

| Component | Vai trò |
|---|---|
| **Prometheus** | Thu thập metric |
| **Grafana** | Dashboard |
| **Spring Boot Actuator** | Expose app health/metrics |
| **Micrometer Prometheus Registry** | Expose `/actuator/prometheus` |
| **Node Exporter** | Host CPU/RAM/disk/swap |
| **cAdvisor** | Container CPU/RAM/restart metrics |
| **PostgreSQL Exporter** | DB metrics |
| **Grafana Alerting / Alertmanager** | Alert khi vượt ngưỡng |

## Metrics cần theo dõi

| Nhóm | Metric |
|---|---|
| Application | request count, error rate, P95 latency |
| JVM | heap used, GC pause, thread count |
| Container | CPU, RAM, restart count |
| Host | RAM, swap, disk, load average |
| PostgreSQL | active connections, slow queries, DB size |
| Business | bank sync success/fail, sync duration |
| Deployment | last deploy status, deploy duration, downtime |

## Dashboard đề xuất

| Dashboard | Nội dung |
|---|---|
| **System Overview** | CPU, RAM, swap, disk, load |
| **Container Overview** | Container memory, CPU, restart |
| **Spring Boot API** | RPS, error rate, latency P95/P99 |
| **JVM Dashboard** | Heap, GC, thread |
| **PostgreSQL Dashboard** | Connection, query, DB size |
| **Business Sync Dashboard** | Plaid/bank sync success/fail, duration |
| **Deployment Dashboard** | Deploy duration, downtime, latest version |

## Alert rules đề xuất

| Alert | Điều kiện |
|---|---|
| High API latency | P95 > 200ms trong 5 phút |
| High error rate | 5xx > 2% trong 5 phút |
| Backend restart loop | Restart > 3 lần trong 5 phút |
| High memory | RAM > 85% |
| High swap | Swap > 50% |
| Disk warning | Disk > 85% |
| Disk critical | Disk > 95% |
| DB unavailable | PostgreSQL exporter down |
| Bank sync failed | Failed sync > 0 trong 15 phút |

## Deliverables

- [ ] `/actuator/prometheus` hoạt động.
- [ ] Prometheus scrape được backend.
- [ ] Node Exporter hoạt động.
- [ ] cAdvisor hoạt động.
- [ ] PostgreSQL Exporter hoạt động.
- [ ] Grafana dashboard cơ bản.
- [ ] Alert rule cho RAM/swap/disk/API latency.
- [ ] README có ảnh dashboard và giải thích metric.

## Tiêu chí hoàn thành

| Metric | Mục tiêu |
|---|---|
| API latency P95 | Có dashboard |
| Error rate | Có dashboard |
| JVM heap | Có dashboard |
| RAM/swap | Có dashboard |
| Container restart | Có alert |
| Business sync failure | Có alert |

---

# Phase 3 — Infrastructure as Code với Terraform

## Mục tiêu

Chuyển hạ tầng AWS từ thao tác thủ công sang quản lý bằng code.

## Vì sao cần Terraform?

Hiện tại các bước như tạo EC2, mở Security Group, gán IP, cấu hình IAM có thể đang làm thủ công. Terraform giúp:

- recreate hạ tầng dễ hơn;
- version control infrastructure;
- review thay đổi trước khi apply;
- giảm sai sót thao tác tay;
- phù hợp với thực tế DevOps/SRE.

## Thành phần quản lý bằng Terraform

| Resource | Ghi chú |
|---|---|
| EC2 instance | App server |
| Security Group | 80/443, 22 hoặc SSM |
| Elastic IP | IP cố định |
| IAM Role | Quyền tối thiểu cho EC2 |
| S3 bucket | Terraform remote state |
| DynamoDB table | Terraform state locking |
| CloudWatch Log Group | Optional |
| Route53 record | Nếu dùng Route53 |
| Key Pair / SSM | Tùy cách SSH |

## Remote state bắt buộc nên có

Không nên dùng local state cho portfolio DevOps nghiêm túc.

```hcl
terraform {
  backend "s3" {
    bucket         = "spendwiser-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "ap-southeast-1"
    dynamodb_table = "spendwiser-terraform-lock"
    encrypt        = true
  }
}
```

## Cấu trúc thư mục đề xuất

```text
terraform/
├── backend.tf
├── provider.tf
├── variables.tf
├── outputs.tf
├── ec2.tf
├── security-group.tf
├── iam.tf
├── elastic-ip.tf
├── environments/
│   ├── dev.tfvars
│   └── prod.tfvars
└── modules/
    └── ec2-app/
```

## Quy trình Terraform

```bash
terraform init
terraform fmt
terraform validate
terraform plan -var-file=environments/prod.tfvars
terraform apply -var-file=environments/prod.tfvars
```

## CI check cho Terraform

Trong GitHub Actions:

```text
Pull Request
→ terraform fmt -check
→ terraform validate
→ terraform plan
→ comment plan vào PR
```

Không nên tự động `terraform apply` vào production ở giai đoạn đầu.

## Deliverables

- [ ] Terraform tạo được EC2.
- [ ] Terraform tạo được Security Group.
- [ ] Terraform tạo được IAM Role.
- [ ] Terraform tạo được Elastic IP.
- [ ] Terraform dùng S3 remote state.
- [ ] Terraform dùng DynamoDB lock.
- [ ] Có `dev.tfvars` và `prod.tfvars`.
- [ ] GitHub Actions check `fmt/validate/plan`.
- [ ] README có sơ đồ hạ tầng Terraform.

## Tiêu chí hoàn thành

| Tiêu chí | Mục tiêu |
|---|---|
| Infrastructure reproducible | Có |
| State local | Không |
| Remote state | S3 |
| State locking | DynamoDB |
| Manual AWS setup | Giảm tối đa |
| Terraform PR check | Có |

---

# Phase 4 — Kubernetes Lab/Staging

## Mục tiêu

Học Kubernetes core concepts bằng cách triển khai Spendwiser trên môi trường lab/staging, không ảnh hưởng production Docker Compose hiện tại.

## Không nên làm

Không nên chạy K3s trên cùng EC2 production t2.micro, vì RAM không đủ và có thể làm app production bị OOM.

## Môi trường khuyến nghị

| Option | Chi phí | Phù hợp | Ghi chú |
|---|---:|---|---|
| **Kind local** | $0 | Học cơ bản | Rất tốt để bắt đầu |
| **Minikube local** | $0 | Học cơ bản | Dễ dùng |
| **K3s trên EC2 t3.small riêng** | ~$15/tháng | Portfolio/demo thật | Có URL thật, học gần production hơn |
| **EKS** | Cao | Sau này | Không cần ở giai đoạn này |

## Thành phần Kubernetes cần học

| Concept | Áp dụng vào Spendwiser |
|---|---|
| Namespace | `spendwiser-staging` |
| Deployment | Backend, frontend |
| Service | Expose backend/frontend nội bộ |
| Ingress | Route domain vào service |
| ConfigMap | Non-sensitive config |
| Secret | DB password, JWT secret |
| Liveness Probe | Restart container khi chết |
| Readiness Probe | Chỉ nhận traffic khi app ready |
| Resource Requests/Limits | Quản lý CPU/RAM |
| PVC | PostgreSQL lab/staging |
| Rolling Update | Deploy không downtime |
| Rollback | Quay lại revision cũ |

## Cấu trúc thư mục đề xuất

```text
k8s/
├── namespace.yaml
├── backend/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── configmap.yaml
│   └── secret.yaml
├── frontend/
│   ├── deployment.yaml
│   └── service.yaml
├── postgres/
│   ├── statefulset.yaml
│   ├── service.yaml
│   └── pvc.yaml
└── ingress.yaml
```

## Deployment flow trong Kubernetes lab

```text
GitHub Actions build image
        ↓
Push image to GHCR
        ↓
kubectl set image / kubectl apply
        ↓
Kubernetes rolling update
        ↓
Readiness probe pass
        ↓
Traffic chuyển sang pod mới
```

## Lưu ý về database

Không nên đưa PostgreSQL production vào Kubernetes ở giai đoạn này.

| Môi trường | DB nên dùng |
|---|---|
| Production hiện tại | PostgreSQL container hoặc RDS sau này |
| Kubernetes lab | PostgreSQL StatefulSet/PVC để học |
| Production lớn hơn | AWS RDS |

## Deliverables

- [ ] Spendwiser backend chạy được trong K8s.
- [ ] Frontend chạy được trong K8s.
- [ ] Service route đúng.
- [ ] Ingress hoạt động.
- [ ] ConfigMap/Secret được dùng đúng.
- [ ] Liveness/readiness probe hoạt động.
- [ ] Resource requests/limits được cấu hình.
- [ ] Rolling update hoạt động.
- [ ] README có so sánh Docker Compose vs Kubernetes.

## Tiêu chí hoàn thành

| Tiêu chí | Mục tiêu |
|---|---|
| Backend pod | Running |
| Frontend pod | Running |
| Ingress | Access được app |
| Readiness probe | Có |
| Liveness probe | Có |
| Resource limit | Có |
| Rollback | Làm được |
| Production impact | Không ảnh hưởng |

---

# Phase 5 — Helm

## Mục tiêu

Sau khi hiểu Kubernetes manifest thủ công, dùng Helm để package và quản lý deployment linh hoạt hơn.

## Vì sao không học Helm trước Kubernetes?

Nếu chưa hiểu Deployment, Service, Ingress, ConfigMap, Secret thì Helm chỉ là template khó đọc. Vì vậy Helm nên học sau khi bạn đã viết YAML thủ công.

## Cấu trúc Helm chart

```text
helm/
└── spendwiser/
    ├── Chart.yaml
    ├── values.yaml
    ├── values-dev.yaml
    ├── values-staging.yaml
    ├── values-prod.yaml
    └── templates/
        ├── backend-deployment.yaml
        ├── backend-service.yaml
        ├── frontend-deployment.yaml
        ├── frontend-service.yaml
        ├── ingress.yaml
        ├── configmap.yaml
        └── secret.yaml
```

## Helm commands cần nắm

```bash
helm lint helm/spendwiser
helm template spendwiser helm/spendwiser -f values-staging.yaml
helm install spendwiser helm/spendwiser -f values-staging.yaml
helm upgrade spendwiser helm/spendwiser -f values-staging.yaml
helm rollback spendwiser 1
helm uninstall spendwiser
```

## Deliverables

- [ ] Helm chart cho Spendwiser.
- [ ] `values-dev.yaml`.
- [ ] `values-staging.yaml`.
- [ ] `values-prod.yaml`.
- [ ] Deploy được bằng `helm install`.
- [ ] Update được bằng `helm upgrade`.
- [ ] Rollback được bằng `helm rollback`.
- [ ] README giải thích chart structure.

## Tiêu chí hoàn thành

| Tiêu chí | Mục tiêu |
|---|---|
| K8s YAML lặp lại | Được template hóa |
| Environment config | Tách bằng values file |
| Helm upgrade | Hoạt động |
| Helm rollback | Hoạt động |
| Chart lint | Pass |

---

# Phase 6 — Argo CD / GitOps

## Mục tiêu

Học GitOps pattern trong Kubernetes staging/lab.

## Lưu ý quan trọng

Argo CD không phải nhu cầu cấp thiết của production hiện tại. Với single developer và một app, GitOps đang giải quyết vấn đề bạn chưa thật sự gặp. Tuy nhiên, đây là công nghệ rất tốt để học và đưa vào portfolio nếu hiểu đúng trade-off.

## GitOps flow

```text
Developer push code
        ↓
GitHub Actions build/test/scan
        ↓
Push image to GHCR
        ↓
Update image tag trong GitOps repo/folder
        ↓
Argo CD detect change
        ↓
Argo CD sync Kubernetes cluster
        ↓
Cluster đạt desired state
```

## Cấu trúc GitOps đề xuất

```text
gitops/
├── apps/
│   └── spendwiser-staging.yaml
└── environments/
    └── staging/
        ├── values.yaml
        └── kustomization.yaml
```

Hoặc nếu dùng Helm:

```text
gitops/
└── spendwiser-staging/
    ├── Chart.yaml
    ├── values.yaml
    └── templates/
```

## Concepts cần học

| Concept | Ý nghĩa |
|---|---|
| Desired state | Trạng thái mong muốn trong Git |
| Actual state | Trạng thái thật trong cluster |
| Sync | Đồng bộ actual theo desired |
| Drift detection | Phát hiện cluster bị chỉnh tay |
| Auto-sync | Tự động apply khi Git thay đổi |
| Manual sync | Người vận hành bấm sync |
| Rollback via Git | Quay lại commit cũ |

## Deliverables

- [ ] Cài Argo CD trong K8s lab/staging.
- [ ] Argo CD quản lý Spendwiser staging.
- [ ] GitOps repo/folder có manifest hoặc Helm values.
- [ ] Auto-sync hoặc manual sync hoạt động.
- [ ] Demo drift detection.
- [ ] Rollback bằng Git commit.
- [ ] README có sơ đồ GitOps flow.

## Tiêu chí hoàn thành

| Tiêu chí | Mục tiêu |
|---|---|
| App trong Argo CD | Healthy |
| Sync status | Synced |
| Drift detection | Demo được |
| Rollback | Qua Git commit |
| Production impact | Không ảnh hưởng |

---

# Phase 7 — Centralized Logging với Loki

## Mục tiêu

Thu thập log tập trung và xem log cùng hệ thống Grafana.

## Vì sao chọn Loki thay vì ELK?

| Công nghệ | Đánh giá |
|---|---|
| ELK | Mạnh nhưng nặng, Elasticsearch thường cần nhiều RAM |
| Loki | Nhẹ hơn, tích hợp tốt với Grafana, phù hợp project nhỏ |
| Cloud logging | Tốt nhưng có thể phát sinh chi phí |

Với project hiện tại, Loki hợp lý hơn ELK.

## Thành phần

| Component | Vai trò |
|---|---|
| Loki | Lưu log |
| Promtail | Collect log |
| Grafana Explore | Query log |
| Docker log driver / file logs | Nguồn log |

## Log nên thu thập

| Nguồn | Nội dung |
|---|---|
| Backend | Spring Boot logs, exception |
| Nginx | Access log, error log |
| PostgreSQL | DB logs |
| Deploy script | Deploy success/failure |
| monitor.sh | Self-healing logs |
| Certbot | SSL renew logs |

## Use case cần demo

```text
API latency tăng trên Grafana
        ↓
Click sang log cùng timestamp
        ↓
Thấy backend exception hoặc DB timeout
        ↓
Xác định nguyên nhân
```

## Deliverables

- [ ] Loki hoạt động.
- [ ] Promtail collect logs.
- [ ] Grafana Explore query được backend logs.
- [ ] Nginx logs query được.
- [ ] Có dashboard/log panel cơ bản.
- [ ] README có ví dụ debug bằng metric + log.

## Tiêu chí hoàn thành

| Tiêu chí | Mục tiêu |
|---|---|
| Backend logs | Query được |
| Nginx logs | Query được |
| Deploy logs | Query được |
| Metric-log correlation | Demo được |
| RAM impact | Không làm production OOM |

---

# Phase 8 — Production Hardening / Future Options

## Mục tiêu

Các nâng cấp này chỉ nên làm khi project có user thật hơn, dữ liệu quan trọng hơn hoặc bạn muốn tăng độ production-ready.

## Các lựa chọn nâng cấp

| Công nghệ | Khi nào nên làm | Lợi ích |
|---|---|---|
| **AWS RDS PostgreSQL** | Khi data quan trọng, cần backup/PITR | Managed DB, backup, recovery |
| **AWS SSM Session Manager** | Khi muốn đóng SSH port 22 | Security tốt hơn |
| **AWS WAF** | Khi app public có traffic thật | Chặn request độc hại cơ bản |
| **CloudFront** | Khi frontend cần CDN/cache | Tăng tốc static content |
| **S3 backup** | Khi cần backup DB định kỳ | Giảm rủi ro mất data |
| **t3.small upgrade** | Khi RAM t2.micro không đủ | Giảm OOM/swap |
| **RDS + App EC2 split** | Khi DB và app tranh tài nguyên | Ổn định hơn |
| **Blue/Green Deployment** | Khi muốn giảm downtime | Deploy an toàn hơn |
| **EKS** | Khi cần Kubernetes production thật | Managed K8s AWS |

## Thứ tự nên cân nhắc

```text
1. S3 backup cho PostgreSQL
2. SSM thay SSH
3. Upgrade t3.small nếu còn OOM
4. RDS khi data quan trọng
5. Blue/Green deployment
6. CloudFront/WAF
7. EKS nếu thật sự cần Kubernetes production
```

---

# Timeline đề xuất

## Nếu học part-time

| Thời gian | Phase |
|---|---|
| Tháng 1 | Bước 0 + Phase 1 |
| Tháng 2 | Phase 2 |
| Tháng 3 | Phase 3 |
| Tháng 4 | Phase 4 |
| Tháng 5 | Phase 5 |
| Tháng 6 | Phase 6 + Phase 7 |

## Nếu học tập trung hơn

| Tuần | Phase |
|---|---|
| Tuần 1 | Bước 0 |
| Tuần 2–3 | Phase 1 |
| Tuần 4–5 | Phase 2 |
| Tuần 6–7 | Phase 3 |
| Tuần 8–10 | Phase 4 |
| Tuần 11 | Phase 5 |
| Tuần 12 | Phase 6 |
| Sau đó | Phase 7–8 |

---

# Thứ tự ưu tiên nếu muốn portfolio mạnh nhanh nhất

Nếu mục tiêu là profile DevOps ấn tượng sớm, ưu tiên như sau:

```text
Tầng 1 — Foundation
├── Docker / Docker Compose
├── GitHub Actions CI/CD
├── GHCR image registry
├── SonarCloud + JaCoCo
├── Trivy security scan
└── Prometheus + Grafana

Tầng 2 — Differentiator
├── Terraform + remote state
├── Kubernetes staging
└── Helm

Tầng 3 — Advanced
├── Argo CD / GitOps
├── Loki centralized logging
└── Production hardening
```

Chỉ cần hoàn thành Tầng 1 + Tầng 2, project đã rất tốt cho portfolio DevOps junior/fresher.

---

# Kiến trúc mục tiêu sau roadmap

## Production thực tế

```text
Internet
   ↓
Nginx on EC2
   ↓
Docker Compose
   ├── Frontend
   ├── Spring Boot Backend
   └── PostgreSQL

GitHub Actions
   ↓
Build/Test/Scan
   ↓
Push Docker Image to GHCR
   ↓
EC2 Pull Image
   ↓
Smoke Test + Rollback if failed

Monitoring:
   → Grafana Cloud hoặc Monitoring EC2 riêng
```

## Lab/Staging để học hệ thống lớn

```text
Kubernetes Lab/Staging
   ├── Backend Deployment
   ├── Frontend Deployment
   ├── Service
   ├── Ingress
   ├── ConfigMap
   ├── Secret
   ├── Prometheus/Grafana
   ├── Helm Chart
   └── Argo CD GitOps
```

---

# Checklist tổng hợp

## Bước 0

- [ ] Check OOM bằng `docker inspect`.
- [ ] Check `dmesg` và `journalctl`.
- [ ] Tune JVM heap.
- [ ] Refactor `monitor.sh`.
- [ ] Health check mỗi 1 phút.
- [ ] Restart loop guard.
- [ ] Đo MTTR.
- [ ] Đo deploy downtime.
- [ ] Chọn monitoring location.

## Phase 1

- [ ] JaCoCo.
- [ ] SonarCloud.
- [ ] Trivy.
- [ ] npm audit / dependency-check.
- [ ] GHCR.
- [ ] Docker image tag bằng Git SHA.
- [ ] EC2 pull image thay vì build.
- [ ] Smoke test.
- [ ] Rollback script.

## Phase 2

- [ ] Spring Boot Actuator Prometheus.
- [ ] Prometheus.
- [ ] Grafana.
- [ ] Node Exporter.
- [ ] cAdvisor.
- [ ] PostgreSQL Exporter.
- [ ] Alert rules.
- [ ] Dashboard.

## Phase 3

- [ ] Terraform EC2.
- [ ] Terraform Security Group.
- [ ] Terraform IAM Role.
- [ ] Terraform Elastic IP.
- [ ] S3 remote state.
- [ ] DynamoDB locking.
- [ ] dev/prod tfvars.
- [ ] GitHub Actions Terraform validate/plan.

## Phase 4

- [ ] Kind hoặc K3s lab.
- [ ] Namespace.
- [ ] Deployment.
- [ ] Service.
- [ ] Ingress.
- [ ] ConfigMap.
- [ ] Secret.
- [ ] Readiness/liveness probe.
- [ ] Resource requests/limits.
- [ ] Rolling update.
- [ ] Rollback.

## Phase 5

- [ ] Helm chart.
- [ ] values-dev.
- [ ] values-staging.
- [ ] values-prod.
- [ ] helm install.
- [ ] helm upgrade.
- [ ] helm rollback.

## Phase 6

- [ ] Argo CD install.
- [ ] App registered in Argo CD.
- [ ] Sync status healthy.
- [ ] Drift detection demo.
- [ ] Rollback via Git.

## Phase 7

- [ ] Loki.
- [ ] Promtail.
- [ ] Backend logs.
- [ ] Nginx logs.
- [ ] Deploy logs.
- [ ] Grafana Explore.
- [ ] Metric-log correlation demo.

---

# Kết luận

Roadmap hoàn thiện nên đi theo hướng:

```text
Stabilize first
→ Secure and package correctly
→ Observe the system
→ Codify infrastructure
→ Learn Kubernetes in staging
→ Package with Helm
→ Apply GitOps
→ Add centralized logging
→ Harden production only when needed
```

Đây là lộ trình cân bằng giữa:

- phù hợp với hệ thống Spendwiser hiện tại;
- tránh làm EC2 t2.micro bị quá tải;
- học được công nghệ DevOps phổ biến và bền vững;
- đủ mạnh để đưa vào portfolio/phỏng vấn;
- không biến project nhỏ thành một hệ thống over-engineered.
