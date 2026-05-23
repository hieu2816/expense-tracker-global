# Hướng dẫn Cấu hình Phase 1: DevSecOps & Artifact Management

Dưới đây là các bước bạn (người dùng) cần thực hiện thủ công trên GitHub và SonarCloud để cấp quyền cho Pipeline (GitHub Actions) có thể tự động quét mã nguồn và đẩy Docker Image.

## Bước 1: Cấp quyền cho GitHub Actions ghi Docker Image lên GHCR (GitHub Container Registry)

Theo mặc định, GitHub Actions chỉ có quyền "Đọc" mã nguồn. Để nó có thể "Ghi" (Push) file Docker Image lên vùng lưu trữ của kho lưu trữ, bạn phải bật quyền này lên.

1. Truy cập vào Repository của bạn trên GitHub.
2. Bấm vào tab **Settings** (Cài đặt của kho lưu trữ).
3. Ở menu bên trái, cuộn xuống phần **Actions** -> Chọn **General**.
4. Cuộn xuống cuối trang tìm mục **Workflow permissions**.
5. Chọn **Read and write permissions**.
6. Bấm **Save**.

## Bước 2: Cấu hình SonarCloud (Quét chất lượng code tự động)

SonarCloud là một nền tảng chấm điểm mã nguồn hoàn toàn miễn phí cho các kho lưu trữ công khai (Public repo).

1. Truy cập [https://sonarcloud.io/](https://sonarcloud.io/) và đăng nhập bằng tài khoản GitHub của bạn.
2. Bấm vào biểu tượng dấu **+** ở góc trên bên phải -> Chọn **Analyze new project**.
3. Chọn Repository `expense-tracking-global` của bạn và bấm **Set up**.
4. Khi được hỏi chọn phương thức phân tích (Analysis method), hãy chọn **With GitHub Actions**.
5. Lúc này, SonarCloud sẽ cung cấp cho bạn 2 thông số rất quan trọng. Bạn cần copy chúng lại:
   - `SONAR_TOKEN`: (Một chuỗi mã bí mật để xác thực).
   - `SONAR_PROJECT_KEY`: (Ví dụ: `kunlu_expense-tracking-global`).
   - `SONAR_ORG`: (Thường là tên username GitHub của bạn, ví dụ: `kunlu`).

## Bước 3: Thêm các biến bảo mật vào GitHub Secrets

Bây giờ bạn cần khai báo các khóa bí mật lấy được từ Bước 2 vào GitHub để Pipeline có thể sử dụng.

1. Quay lại trang GitHub Repository của bạn.
2. Vào **Settings** -> **Secrets and variables** -> **Actions**.
3. Bấm **New repository secret** và thêm lần lượt 3 biến sau:
   - Name: `SONAR_TOKEN` | Value: (Dán token bạn vừa copy ở Bước 2).
   - Name: `SONAR_PROJECT_KEY` | Value: (Dán project key, ví dụ: `kunlu_expense-tracking-global`).
   - Name: `SONAR_ORG` | Value: (Dán tên Organization/Username GitHub của bạn).

---

Sau khi bạn hoàn thành 3 bước trên, hạ tầng trên GitHub của bạn đã **hoàn toàn sẵn sàng**.
