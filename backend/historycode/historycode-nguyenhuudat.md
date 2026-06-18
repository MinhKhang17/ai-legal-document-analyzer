# History Code - Nguyen Huu Dat

**Date:** 2026-06-18 (Ngày 18 tháng 6 năm 2026)

## Tasks Completed:
- **Tách DTO Subscription thành 3 thư mục con tương ứng**:
  - `customerplan`: Chứa `SubscribeRequestDTO.java`, `CustomerPlanResponseDTO.java`.
  - `paymenttransaction`: Chứa `PaymentTransactionResponseDTO.java`.
  - `subscriptionplan`: Chứa `SubscriptionPlanRequestDTO.java`, `SubscriptionPlanResponseDTO.java`.
- **Cập nhật Import và Mappers**:
  - Cập nhật toàn bộ các import DTO bị thay đổi trong các Controllers, Services, Service Impls, và Mappers (`SubscriptionPlanMapper`, `CustomerPlanMapper`, `PaymentTransactionMapper`).
  - Xóa bỏ package cũ `com.analyzer.api.dto.subscription` để tránh trùng lặp code trên classpath.
- **Đồng bộ DTO và Entity**:
  - Loại bỏ các trường thống kê chi tiết (`usedDocuments`, `usedAnalyses`, `usedChatMessages`, `usedExpertReviews`) ra khỏi `CustomerPlanResponseDTO` để đồng bộ với thực thể `CustomerPlan` mà người dùng đã chỉnh sửa.
- **Cấu hình Tự động Dọn dẹp Database & Khởi tạo Dữ liệu (Data Seeding)**:
  - Tự động kiểm tra và seed tài khoản ADMIN mặc định: `admin@analyzer.com` với mật khẩu `12345678` (mã hóa tự động qua BCrypt).
  - Tự động kiểm tra và seed 3 gói dịch vụ mặc định: *Gói Miễn Phí*, *Gói Tiêu Chuẩn*, và *Gói Cao Cấp* vào bảng `subscription_plans` khi ứng dụng khởi chạy lần đầu.

---

## 2. Chi tiết Các Endpoint & Logic Nghiệp Vụ

Dưới đây là chi tiết logic hoạt động của các Controller và Service đã phát triển để bạn tiện theo dõi và tùy biến khi cần thiết:

### A. Subscription Plan Management (`SubscriptionPlanController`)
Quản lý các gói cước bán hàng của hệ thống.

* **`POST /api/v1/subscription-plans` (ADMIN)**
  * **Input:** `SubscriptionPlanRequestDTO`
  * **Logic:** Kiểm tra trùng tên gói (`planName`). Nếu chưa tồn tại, tạo mới gói ở trạng thái mặc định là `active = true` (hoặc theo request).
* **`GET /api/v1/subscription-plans` (CUSTOMER / ADMIN - Login required)**
  * **Logic:** Trả về danh sách tất cả các gói cước đang ở trạng thái kích hoạt (`active = true`).
* **`GET /api/v1/subscription-plans/{id}` (CUSTOMER / ADMIN - Login required)**
  * **Logic:** Trả về thông tin chi tiết của gói theo `id`. Nếu không thấy, ném ngoại lệ `RuntimeException`.
* **`PUT /api/v1/subscription-plans/{id}` (ADMIN)**
  * **Input:** `SubscriptionPlanRequestDTO`
  * **Logic:** Cập nhật thông tin gói. Nếu thay đổi tên gói, kiểm tra xem tên mới có trùng với gói khác đang tồn tại không.
* **`DELETE /api/v1/subscription-plans/{id}` (ADMIN)**
  * **Logic (Xử lý xóa mềm/cứng thông minh):**
    * Kiểm tra xem gói cước này đã từng có khách hàng đăng ký (`CustomerPlan`) hoặc có lịch sử thanh toán (`PaymentTransaction`) chưa.
    * Nếu **đã có liên kết dữ liệu**, hệ thống tự động chuyển đổi sang **Xóa mềm** (set `active = false`) để tránh lỗi toàn vẹn dữ liệu (Foreign Key Constraint).
    * Nếu **chưa có bất kỳ liên kết nào**, hệ thống thực hiện **Xóa cứng** (xóa hoàn toàn khỏi database).

---

### B. Customer Plan Management (`CustomerPlanController`)
Quản lý việc chọn gói, hủy gói và kiểm tra hạn mức sử dụng của từng khách hàng.

* **`POST /api/v1/customer-plans/subscribe` (CUSTOMER - Login required)**
  * **Input:** `SubscribeRequestDTO` (gồm `subscriptionPlanId` và `paymentMethod`).
  * **Logic:**
    1. Lấy `customerId` trực tiếp từ **SecurityContext** (không truyền từ client để tránh giả mạo ID).
    2. Kiểm tra tính hợp lệ của gói dịch vụ đăng ký (phải tồn tại và đang `active`).
    3. Tìm kiếm xem khách hàng có gói cước nào đang ở trạng thái `PENDING` chưa thanh toán không:
       * Nếu **đã có gói PENDING**, cập nhật gói đó sang gói dịch vụ mới lựa chọn, đặt lại các chỉ số sử dụng (`usedQuota = 0`) và xóa ngày bắt đầu/hết hạn.
       * Nếu **chưa có**, tạo một gói cước mới với trạng thái ban đầu là `PENDING`.
    4. Sinh mã giao dịch duy nhất dạng: `TX + UUID` và tạo một giao dịch thanh toán (`PaymentTransaction`) tương ứng ở trạng thái `PENDING`.
    5. Trả về thông tin gói đăng ký đang chờ thanh toán.
* **`GET /api/v1/customer-plans/me` (CUSTOMER - Login required)**
  * **Logic (Tự động cập nhật gói hết hạn):**
    1. Lấy `customerId` từ **SecurityContext**.
    2. Tìm gói đang hoạt động (`PlanStatus.ACTIVE`).
    3. Nếu tìm thấy gói `ACTIVE`, kiểm tra xem thời gian hiện tại đã vượt quá `endDate` chưa:
       * Nếu **đã quá hạn**, tự động cập nhật trạng thái gói đó thành `EXPIRED` trong database và trả về gói `PENDING` hoặc gói gần nhất của khách hàng.
       * Nếu **chưa quá hạn**, trả về thông tin gói cùng số lượng hạn mức còn lại (`remainingQuota = maxQuota - usedQuota`).
* **`PUT /api/v1/customer-plans/{id}/cancel` (CUSTOMER - Login required)**
  * **Logic:** Xác thực quyền sở hữu gói (chỉ chính khách hàng sở hữu gói mới được hủy). Chuyển trạng thái gói cước thành `CANCELLED`.
* **Logic Kiểm tra Hạn mức Chat (Tích hợp nghiệp vụ)**
  * **`validateChatQuota(Long customerId)`**: Kiểm tra xem khách hàng có gói cước `ACTIVE` hợp lệ không. Nếu đã dùng hết hạn mức (`usedQuota >= maxQuota`), hệ thống lập tức chặn và trả lỗi `Quota exceeded`.
  * **`recordChatUsage(Long customerId)`**: Tương tự như trên, sau khi gửi tin nhắn thành công, tăng số lượng `usedQuota` của gói cước khách hàng lên 1 đơn vị.

---

### C. Payment Transaction Management (`PaymentTransactionController`)
Quản lý lịch sử thanh toán và giả lập xử lý giao dịch.

* **`GET /api/v1/payment-transactions/me` (CUSTOMER - Login required)**
  * **Logic:** Lấy danh sách lịch sử giao dịch của khách hàng đang đăng nhập (sắp xếp theo thời gian tạo mới nhất lên trước).
* **`GET /api/v1/payment-transactions` (ADMIN)**
  * **Logic:** Trả về toàn bộ giao dịch thanh toán của hệ thống.
* **`PUT /api/v1/payment-transactions/{id}/success` (CUSTOMER / ADMIN - Login required)**
  * **Logic (Giả lập thanh toán thành công):**
    1. Tìm giao dịch theo `id`. Yêu cầu trạng thái giao dịch hiện tại phải là `PENDING`.
    2. Cập nhật giao dịch thành `SUCCESS` và gán ngày giờ thanh toán (`paidAt = LocalDateTime.now()`).
    3. **Đảm bảo duy nhất 1 gói ACTIVE tại một thời điểm:**
       * Tìm kiếm các gói đăng ký đang `ACTIVE` khác của khách hàng này.
       * Nếu có gói khác đang kích hoạt, lập tức cập nhật trạng thái của gói cũ đó thành `EXPIRED`.
    4. Kích hoạt gói `CustomerPlan` liên kết với giao dịch này thành `ACTIVE`:
       * Đặt ngày bắt đầu (`startDate = now`).
       * Đặt ngày hết hạn (`endDate = now + durationDays` của gói dịch vụ).
* **`PUT /api/v1/payment-transactions/{id}/failed` (CUSTOMER / ADMIN - Login required)**
  * **Logic (Giả lập thanh toán thất bại):**
    1. Tìm giao dịch theo `id`. Yêu cầu trạng thái hiện tại phải là `PENDING`.
    2. Cập nhật giao dịch thành `FAILED`.
    3. Giữ nguyên trạng thái của `CustomerPlan` tương ứng là `PENDING` (khách hàng có thể thử thanh toán lại hoặc chọn gói khác).
