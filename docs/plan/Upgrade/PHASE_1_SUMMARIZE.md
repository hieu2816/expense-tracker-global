# Báo cáo Tổng kết Giai đoạn 1 (Phase 1): DevSecOps & Artifact Management

**Dự án:** Expense Tracking Global
**Thời gian hoàn thành:** 21/05/2026

## 1. Mục tiêu đã đạt được
Giai đoạn 1 đã chuyển đổi thành công kiến trúc triển khai của dự án từ một kịch bản deploy cơ bản, tốn kém tài nguyên sang một quy trình **DevSecOps** chuẩn công nghiệp, tự động hóa cao và cực kỳ an toàn.

---

## 2. Các thay đổi Kiến trúc Cốt lõi (Core Architecture Changes)

### A. Tối ưu hóa Máy chủ EC2 (Resource Optimization)
- **Vấn đề cũ:** Mỗi lần có code mới, máy chủ EC2 `t2.micro` (RAM rất thấp) phải gồng gánh chạy Maven build và npm build, đẩy CPU lên 100% và gây rủi ro tràn RAM (OOM) làm sập hệ thống.
- **Giải pháp mới:** Tước bỏ quyền build của EC2. Quá trình biên dịch (Build), quét lỗi, và đóng gói thành Docker Image giờ đây được thực hiện hoàn toàn bởi các máy chủ mạnh mẽ (Runner) của GitHub Actions.
- **Kết quả:** 
  - Thời gian downtime trên EC2 khi deploy giảm từ gần 10 phút xuống còn **dưới ....... giây**. 
  - EC2 chỉ đơn thuần thực hiện lệnh `docker compose pull` để kéo Image về và chạy. 
  - Cấu hình JVM của Spring Boot đã được chốt cứng (`JAVA_TOOL_OPTIONS: -Xms256m -Xmx450m -XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError`) để bảo vệ RAM tuyệt đối.

### B. Quản lý Artifact (GHCR Integration)
- Thay vì dùng tag `latest` mơ hồ, mỗi bản Build mới đều được đính kèm chính xác mã **Git SHA** của lần commit đó.
- Các Docker Image được đẩy lên và lưu trữ an toàn tại **GitHub Container Registry (GHCR)** dưới dạng Private Packages.
- File `docker-compose.yaml` được cập nhật để nhận biến động `BACKEND_IMAGE` và `FRONTEND_IMAGE` từ `.env`, đảm bảo EC2 luôn kéo đúng phiên bản đã được kiểm duyệt.

---

## 3. Tích hợp Bảo mật & Chất lượng Mã nguồn (DevSecOps)

### A. SonarCloud & Quality Gate
- Đã thiết lập SonarCloud để chấm điểm mã nguồn tự động sau mỗi lượt push.
- **Giải quyết lỗi Quality Gate:** Xử lý triệt để lỗi "0% New Code Coverage" bằng cách đồng bộ luật miễn trừ (exclusions) trong `pom.xml` (bỏ qua DTO, config) và bổ sung các Unit Test (`MockMvc` & `Mockito`) cho các Service/Controller cốt lõi.
- **Khắc phục Security Hotspots:** 
  - Sửa lỗi lộ `secretKey` ở `PlaidConfig`.
  - Sửa lỗi bảo mật ép kiểu ký tự ở `JwtUtils` (Sử dụng `StandardCharsets.UTF_8`).
  - Thay thế `printStackTrace()` bằng SLF4J logger.
  - Xử lý ngoại lệ Plaid Webhook: Chèn `@SuppressWarnings("java:S6863")` để dập tắt cảnh báo của SonarCloud khi trả về `HTTP 200 OK` chứa mã lỗi. (Thiết kế có chủ đích để ngăn chặn Plaid gọi lại liên tục gây DDoS hệ thống).

### B. Trivy Vulnerability Scanner
- Trivy được đưa vào Pipeline đóng vai trò là "người gác đền" quét sâu vào nhân của Docker Image để tìm lỗ hổng bảo mật (CVEs) trước khi deploy.
- **Sự cố và Giải quyết:** Trivy đã đánh rớt (Block) Pipeline khi phát hiện 4 lỗi CRITICAL trong thư viện `tomcat-embed-core` của Spring Boot `3.5.9`. Vấn đề đã được giải quyết bằng cách nâng cấp `spring-boot-starter-parent` lên bản an toàn (`3.5.14`), giúp dự án vượt ải bảo mật thành công.

---

## 4. Tự động Phục hồi (Self-Healing & Rollback)

### A. Tăng tốc độ phục hồi (Monitor.sh)
- Rút ngắn chu kỳ kiểm tra sức khỏe hệ thống từ 5 phút xuống còn **1 phút**. 
- Sửa lỗi cú pháp `jq` của Docker V2, giúp script có khả năng thực sự tự gõ lệnh `restart` cứu sống backend nếu Java bị treo, thay vì chỉ nhắn tin báo lỗi lên Telegram.

### B. Cơ chế Quay xe (Rollback.sh)
- Xây dựng kịch bản `script/rollback.sh` mới hoàn toàn. 
- Github Actions giờ đây sẽ ghi lại mã SHA của bản deploy thành công gần nhất vào file `.last_successful_deploy`. 
- Khi có sự cố vỡ hệ thống, quản trị viên chỉ cần SSH vào và gõ đúng 1 lệnh để kéo hệ thống quay ngược về phiên bản an toàn trước đó trong nháy mắt.

---

## 5. Tầm nhìn cho Giai đoạn tiếp theo (Phase 2)
Kiến trúc nền tảng (Foundation) hiện tại đã đủ mạnh và an toàn để tiếp nhận các công cụ giám sát đo lường (Observability) chuyên sâu.
- **Mục tiêu:** Giám sát thời gian thực bằng Metric và Log tập trung.
- **Công cụ dự kiến:** Cài đặt Prometheus Exporters (Node, cAdvisor, Promtail) siêu nhẹ và đẩy luồng dữ liệu lên Grafana Cloud để xây dựng Dashboard theo dõi sức khỏe hệ thống mà không vắt kiệt tài nguyên của EC2.
