# Tài liệu API: Doanh thu / Thanh toán cho Expert (Lawyer)

Tài liệu này mô tả các API **đã tồn tại trong code hiện tại** liên quan tới việc ghi nhận phí tư vấn (`consultationFee`), tính hoa hồng platform, và trạng thái thanh toán cho chuyên gia (Expert/Lawyer) trên `LegalTicket`. Đây là mô tả "as-built" (đúng với trạng thái code thực tế), không phải thiết kế đề xuất.

**Lưu ý:** hệ thống có tính hoa hồng tự động (`platformFee`/`expertPayout`) theo 1 tỷ lệ chung toàn hệ thống (`commissionRate`), nhưng việc chuyển tiền thật cho Expert vẫn diễn ra **ngoài hệ thống** (chuyển khoản tay); hệ thống chỉ tính toán và lưu trạng thái để đối soát, không tích hợp cổng thanh toán để tự động chi trả.

---

## 1. Entity liên quan

### `LegalTicket` — các field tiền tệ
File: `backend/src/main/java/com/analyzer/api/entity/LegalTicket.java` (dòng 203-222)

| Field Java | Cột DB | Kiểu | Mặc định | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `consultationFee` | `consultation_fee` | `BigDecimal(19,2)` | `0.00` | Phí tư vấn của ticket, do Admin nhập tay |
| `expertPaymentStatus` | `expert_payment_status` | Enum `ExpertPaymentStatus` | `UNPAID` | Trạng thái thanh toán cho expert |
| `expertPaidAt` | `expert_paid_at` | `LocalDateTime` | `null` | Thời điểm được đánh dấu `PAID` |
| `commissionRate` | `commission_rate` | `BigDecimal(5,4)` | `null` | Tỷ lệ hoa hồng platform, **snapshot** tại thời điểm ticket lần đầu chuyển `RESOLVED`/`CLOSED` (hoặc lần đầu set phí nếu ticket resolve trước khi có field này) — không đổi theo `RevenueSetting` sau đó |
| `platformFee` | `platform_fee` | `BigDecimal(19,2)` | `null` | = `consultationFee * commissionRate`, làm tròn 2 chữ số (`HALF_UP`) |
| `expertPayout` | `expert_payout` | `BigDecimal(19,2)` | `null` | = `consultationFee - platformFee` |
| `assignedLawyer` | `assigned_lawyer_id` | `@ManyToOne User` | `null` | Expert đang phụ trách ticket — **bắt buộc phải khác null** thì mới set được phí và mới được snapshot hoa hồng |

### Enum `ExpertPaymentStatus`
File: `backend/src/main/java/com/analyzer/api/enums/ExpertPaymentStatus.java`
```
UNPAID, PENDING, PAID
```
Không có state machine ràng buộc thứ tự chuyển trạng thái — Admin có thể set thẳng từ `UNPAID` sang `PAID` hoặc lùi lại `UNPAID` bất kỳ lúc nào.

### `RevenueSetting` (mới) — cấu hình hoa hồng toàn hệ thống
File: `backend/src/main/java/com/analyzer/api/entity/RevenueSetting.java`, bảng `revenue_settings`, **luôn chỉ có 1 dòng** (`id = 1`, seed sẵn ở migration `V20260721_03`).

| Field Java | Cột DB | Kiểu | Mô tả |
| :--- | :--- | :--- | :--- |
| `commissionRate` | `commission_rate` | `BigDecimal(5,4)` | Tỷ lệ hoa hồng hiện hành, vd `0.2000` = 20%. Mặc định seed `0.2000` |
| `updatedAt` | `updated_at` | `LocalDateTime` | Lần sửa gần nhất |
| `updatedBy` | `updated_by` | `@ManyToOne User` | Admin đã sửa gần nhất |

Nếu bảng chưa có dòng nào (edge case môi trường chưa chạy migration/seed) — `RevenueSettingServiceImpl.getCurrentRate()` fallback về hằng số `0.2000` trong code, không lỗi.

---

## 2. REST API Endpoints

### 2.1. Cập nhật phí + trạng thái thanh toán (Admin)
- **Endpoint**: `PATCH /api/v1/admin/tickets/{id}/expert-payment`
- **Quyền**: `hasRole('ADMIN')`
- **Controller**: `AdminTicketManagementController.java:34-42`
- **Service**: `ExpertRevenueServiceImpl.updatePayment()`

**Request body** (`UpdateExpertPaymentRequest`):
```json
{
  "consultationFee": 500000,
  "paymentStatus": "PAID"
}
```
| Field | Validation |
| :--- | :--- |
| `consultationFee` | `@NotNull`, `@DecimalMin("0.00")` — không giới hạn trần |
| `paymentStatus` | `@NotNull`, phải là 1 trong 3 giá trị enum |

**Response** (`ExpertRevenueTicketResponse`):
```json
{
  "ticketId": "ticket_abc123",
  "ticketCode": "TCK-000123",
  "ticketStatus": "RESOLVED",
  "consultationFee": 500000,
  "commissionRate": 0.2000,
  "platformFee": 100000.00,
  "expertPayout": 400000.00,
  "paymentStatus": "PAID",
  "resolvedAt": "2026-07-20T10:00:00",
  "paidAt": "2026-07-21T09:00:00"
}
```

**Logic xử lý** (nguyên văn từ `ExpertRevenueServiceImpl.updatePayment`):
1. Tìm ticket theo `id` (`findByIdAndDeletedFalse`) — không tìm thấy → `ResourceNotFoundException` (404).
2. Nếu `ticket.assignedLawyer == null` → ném `ConflictException("Ticket has not been assigned to an expert")` (409). **Ticket phải được gán expert trước, không kiểm tra ticket đã ở trạng thái RESOLVED/CLOSED hay chưa** — có thể set phí cho ticket đang `IN_REVIEW`.
3. Tìm admin đang thao tác (`adminId` lấy từ token) — không tồn tại → `ResourceNotFoundException` (404, trường hợp lý thuyết vì token đã xác thực).
4. Ghi đè `consultationFee` và `expertPaymentStatus` bằng giá trị mới.
5. Nếu `paymentStatus == PAID` → set `expertPaidAt = now()`; ngược lại → set `expertPaidAt = null`. Mỗi lần gọi lại với `PAID` sẽ **ghi đè `expertPaidAt` bằng thời điểm hiện tại**, kể cả khi ticket đã `PAID` từ trước (không idempotent).
6. Gọi `applyCommissionSnapshot(ticket)` (xem mục 3) để tính lại `platformFee`/`expertPayout` từ `consultationFee` mới — **giữ nguyên `commissionRate` đã snapshot trước đó nếu có**, chỉ snapshot rate hiện hành nếu ticket chưa từng có `commissionRate`.
7. Lưu ticket, sau đó ghi 1 dòng **audit log** vào `TicketAuditLog` (action `EXPERT_PAYMENT_UPDATED`, `metadataJson` chứa `consultationFee`+`paymentStatus` đã gửi, `actor` = admin thao tác) qua `TicketCollaborationService.auditTicket(...)`. Có thể tra cứu lịch sử qua bảng `ticket_audit_logs` (chưa có API GET riêng, cần query trực tiếp DB hoặc bổ sung endpoint sau).

---

### 2.2. Xem tổng quan doanh thu (Expert)
- **Endpoint**: `GET /api/v1/expert/revenue`
- **Quyền**: `hasRole('EXPERT')` (áp dụng ở class-level `@PreAuthorize` của `ExpertRevenueController`)
- **Controller**: `ExpertRevenueController.java`
- **Service**: `ExpertRevenueServiceImpl.getSummary()`

**Response** (`ExpertRevenueSummaryResponse`):
```json
{
  "assignedTicketCount": 12,
  "resolvedTicketCount": 8,
  "paidTicketCount": 5,
  "pendingPaymentTicketCount": 2,
  "totalRevenue": 4000000,
  "paidRevenue": 2500000,
  "pendingRevenue": 1000000,
  "totalPlatformFee": 800000,
  "totalExpertPayout": 3200000
}
```

**Logic tính toán** (`ExpertRevenueServiceImpl.getSummary`/`sum`/`isResolved`):
- Lấy toàn bộ ticket có `assignedLawyer.id == expertId` và `deleted = false` (`findByAssignedLawyerIdAndDeletedFalse`) — **không phân trang** (đây là API tổng hợp số liệu, cần load hết để cộng dồn).
- `assignedTicketCount` = tổng số ticket được gán, **không lọc theo trạng thái** (kể cả ticket đang mở, chưa resolve).
- `resolvedTicketCount`, `paidTicketCount`, `pendingPaymentTicketCount`, và các giá trị tiền (`totalRevenue`/`paidRevenue`/`pendingRevenue`/`totalPlatformFee`/`totalExpertPayout`) **chỉ tính trên ticket có status `RESOLVED` hoặc `CLOSED`** (hàm `isResolved`).
- `totalRevenue` = tổng `consultationFee`; `totalPlatformFee`/`totalExpertPayout` = tổng 2 field tương ứng — của mọi ticket resolved/closed, bất kể trạng thái thanh toán.
- Nếu field tiền nào `null` (ticket resolve trước khi tính năng hoa hồng tồn tại, chưa từng được `updatePayment`/resolve lại) thì tính là `0`.

---

### 2.3. Xem danh sách ticket kèm doanh thu (Expert)
- **Endpoint**: `GET /api/v1/expert/revenue/tickets?page={page}&size={size}`
- **Quyền**: `hasRole('EXPERT')`
- **Controller**: `ExpertRevenueController.java`
- **Service**: `ExpertRevenueServiceImpl.getTickets(expertId, page, size)`

**Response**: `PageResponse<ExpertRevenueTicketResponse>` (schema từng item giống mục 2.1), **đã phân trang** (`page` mặc định `0`, `size` mặc định `10`, sort `createdAt DESC`) — bao gồm cả ticket chưa resolve (các ticket này có `consultationFee = 0`, `commissionRate/platformFee/expertPayout = null`, `paymentStatus = UNPAID`, `resolvedAt/paidAt = null`).

---

### 2.4. Cấu hình tỷ lệ hoa hồng (Admin, mới)
- **Endpoint**: `GET /api/v1/admin/revenue/settings`, `PATCH /api/v1/admin/revenue/settings`
- **Quyền**: `hasRole('ADMIN')`
- **Controller**: `AdminRevenueController.java`
- **Service**: `RevenueSettingServiceImpl`

**Request body PATCH** (`UpdateRevenueSettingRequest`):
```json
{ "commissionRate": 0.2500 }
```
Validation: `@NotNull`, `0.00 <= commissionRate <= 1.00`.

**Response** (`RevenueSettingResponse`):
```json
{
  "commissionRate": 0.2500,
  "updatedAt": "2026-07-21T10:00:00",
  "updatedByName": "Nguyen Van Admin"
}
```

**Lưu ý quan trọng:** đổi `commissionRate` ở đây **chỉ ảnh hưởng ticket resolve/close SAU thời điểm đổi**, hoặc ticket chưa từng được snapshot `commissionRate`. Ticket đã resolve trước đó (đã có `commissionRate` snapshot) **không bị ảnh hưởng** khi rate global đổi — kể cả khi admin sửa lại `consultationFee` của ticket đó sau này qua mục 2.1 (rate dùng lại vẫn là rate đã khoá trên ticket, không phải rate global mới nhất).

---

### 2.5. Tổng quan doanh thu toàn hệ thống (Admin, mới)
- **Endpoint**: `GET /api/v1/admin/revenue/overview`
- **Quyền**: `hasRole('ADMIN')`
- **Controller**: `AdminRevenueController.java`
- **Service**: `ExpertRevenueServiceImpl.getAdminOverview()`

**Response** (`AdminRevenueOverviewResponse`):
```json
{
  "totalTicketCount": 40,
  "paidTicketCount": 25,
  "pendingPaymentTicketCount": 8,
  "totalConsultationFee": 20000000,
  "totalPlatformFee": 4000000,
  "totalExpertPayout": 16000000,
  "byExpert": [
    {
      "expertId": 12,
      "expertName": "Nguyen Van A",
      "ticketCount": 15,
      "totalConsultationFee": 7500000,
      "totalExpertPayout": 6000000
    }
  ]
}
```

**Logic tính toán:**
- Lấy toàn bộ ticket có status `RESOLVED`/`CLOSED` **và** `assignedLawyer != null` (`findByStatusInAndAssignedLawyerIsNotNullAndDeletedFalse`) — không phân trang, không lọc theo khoảng thời gian.
- Nhóm theo `assignedLawyer.id` để tính `byExpert` — mỗi phần tử là 1 expert có ít nhất 1 ticket resolved/closed.
- Không tính ticket `REFUND_REQUEST` (không thể có `assignedLawyer`, xem mục 3).

---

### 2.6. Reset dữ liệu tài chính của ticket (Admin, mới)
- **Endpoint**: `POST /api/v1/admin/tickets/{id}/expert-payment/reset`
- **Quyền**: `hasRole('ADMIN')`
- **Controller**: `AdminTicketManagementController.java`
- **Service**: `ExpertRevenueServiceImpl.resetFinancials()`

**Mục đích:** dùng khi admin cần **reassign ticket sang expert khác** nhưng bị chặn vì ticket đã có dữ liệu tài chính (xem edge case #3 ở mục 5) — gọi endpoint này trước để xoá sạch, rồi mới `reassign-lawyer` được.

**Logic xử lý:**
1. Tìm ticket — không tồn tại → 404.
2. Nếu `expertPaymentStatus == PAID` → 409 `ConflictException("CANNOT_RESET_PAID_TICKET")`. **Không cho reset ticket đã thực sự chuyển tiền** — tránh xoá dấu vết 1 khoản đã trả thật.
3. Set về trạng thái ban đầu: `consultationFee = 0`, `expertPaymentStatus = UNPAID`, `expertPaidAt = null`, `commissionRate = null`, `platformFee = null`, `expertPayout = null`.
4. Ghi audit log action `EXPERT_PAYMENT_RESET`.

**Response**: `ExpertRevenueTicketResponse` (schema giống mục 2.1) với các field tiền đều về `0`/`null`.

---

## 3. Logic tính hoa hồng — `applyCommissionSnapshot(LegalTicket ticket)`
File: `ExpertRevenueServiceImpl.java`. Đây là hàm dùng chung, được gọi tự động ở **4 điểm**:

| # | Nơi gọi | Khi nào |
| :-- | :--- | :--- |
| 1 | `ExpertLegalTicketServiceImpl.resolveTicket()` | Lawyer resolve ticket |
| 2 | `LegalTicketServiceImpl.closeTicket()` | Customer tự đóng ticket đã `RESOLVED` |
| 3 | `AdminTicketManagementServiceImpl.closeInternal()` | Admin đóng ticket |
| 4 | `ExpertRevenueServiceImpl.updatePayment()` | Admin sửa `consultationFee`/`paymentStatus` (mục 2.1) |

Logic:
```java
void applyCommissionSnapshot(LegalTicket ticket) {
    if (ticket.getAssignedLawyer() == null) return; // ticket chưa có expert thì bỏ qua, không lỗi
    if (ticket.getCommissionRate() == null) {
        ticket.setCommissionRate(revenueSettingService.getCurrentRate()); // chỉ snapshot nếu CHƯA có
    }
    BigDecimal fee = ticket.getConsultationFee() == null ? ZERO : ticket.getConsultationFee();
    BigDecimal platformFee = fee.multiply(ticket.getCommissionRate()).setScale(2, HALF_UP);
    ticket.setPlatformFee(platformFee);
    ticket.setExpertPayout(fee.subtract(platformFee));
}
```
- **`commissionRate` chỉ được set 1 lần** cho mỗi ticket (lần đầu tiên hàm này chạy có `assignedLawyer != null`) — các lần gọi sau chỉ tính lại `platformFee`/`expertPayout` theo `consultationFee` mới nhất, dùng lại rate đã khoá.
- Ticket resolve/close ở thời điểm `consultationFee` vẫn là `0` (mặc định, chưa được admin nhập) → `commissionRate` vẫn được snapshot ngay lúc đó (dùng rate global hiện hành), `platformFee`/`expertPayout` = `0`. Khi admin nhập `consultationFee` thật sau đó qua mục 2.1, số tiền được tính lại nhưng rate vẫn là rate đã khoá từ lúc resolve — **không phải rate global tại thời điểm admin nhập phí**.

---

## 4. Business rules tổng hợp

- Chỉ 3 loại ticket có thể gán cho Expert nên mới có thể sinh doanh thu: `CONTACT_EXPERT`, `SYSTEM_ERROR`, `QUERY_ERROR`. `REFUND_REQUEST` **luôn bị chặn gán lawyer** (`AdminTicketManagementServiceImpl.java`, ném `ConflictException("REFUND_TICKET_ADMIN_ONLY")`) → không bao giờ xuất hiện trong doanh thu expert hay overview admin.
- Ticket được tính vào doanh thu dựa trên **status hiện tại** (`RESOLVED`/`CLOSED`) tại thời điểm gọi API, không dựa trên lịch sử — nếu ticket bị `REOPENED` rồi resolve lại, `commissionRate` đã khoá từ lần resolve trước **không đổi** (vì `applyCommissionSnapshot` chỉ set rate khi đang `null`), chỉ `platformFee`/`expertPayout` được tính lại nếu `consultationFee` thay đổi.
- `LegalTicket` có `@Version` (optimistic locking) nên 2 admin cùng sửa phí một ticket cùng lúc sẽ có 1 request bị conflict thay vì mất dữ liệu ngầm.
- Mọi thay đổi phí/trạng thái thanh toán qua mục 2.1 đều được ghi vào `ticket_audit_logs` (action `EXPERT_PAYMENT_UPDATED`).
- **(2026-07-21)** `assignLawyer`/`reassignLawyer` giờ kiểm tra `lawyerId` phải có role `EXPERT` — ném `ConflictException("USER_IS_NOT_EXPERT")` nếu không, để tránh gán ticket (và do đó gán doanh thu) cho tài khoản không phải chuyên gia.
- **(2026-07-21)** Admin **không thể** đóng ticket (`POST /api/v1/admin/tickets/{id}/close`) khi ticket đã có `assignedLawyer` nhưng status **chưa phải `RESOLVED`** — ném `ConflictException("TICKET_NOT_RESOLVED_BY_EXPERT")`. Trước đây admin có thể đóng thẳng từ mọi trạng thái (trừ `CLOSED`/`CANCELLED`), khiến `applyCommissionSnapshot()` chạy dù expert chưa thực sự resolve xong việc. Ticket **chưa có** expert phụ trách vẫn đóng được từ bất kỳ trạng thái nào (không ảnh hưởng doanh thu).
- **(2026-07-21)** `reassignLawyer` giờ chặn khi ticket đã có dữ liệu tài chính (xem chi tiết edge case #3 ở mục 5) — tránh việc số tiền/hoa hồng tính cho lawyer cũ bị "chuyển nhầm" sang lawyer mới khi reassign. Admin dùng `POST .../expert-payment/reset` (mục 2.6) để mở khoá reassign khi cần, trừ khi ticket đã `PAID`.

---

## 5. Edge case cần lưu ý

| # | Tình huống | Hành vi hiện tại |
| :-- | :--- | :--- |
| 1 | Set phí cho ticket chưa có `assignedLawyer` | 409 `ConflictException` |
| 2 | Set phí cho ticket **chưa** `RESOLVED`/`CLOSED` (vd đang `IN_REVIEW`) | **Không bị chặn** — phí + snapshot hoa hồng được lưu ngay, nhưng sẽ không xuất hiện trong `totalRevenue`/overview cho tới khi ticket resolve/close |
| 3 | Admin **reassign** ticket sang expert khác (`reassign-lawyer`) khi ticket đã có dữ liệu tài chính | **(Fixed 2026-07-21)** Bị chặn: 409 `ConflictException("CANNOT_REASSIGN_TICKET_WITH_PAYMENT_SET")` nếu `expertPaymentStatus != UNPAID`, hoặc `consultationFee > 0`, hoặc `commissionRate` đã được snapshot. Để reassign được, admin gọi `POST /api/v1/admin/tickets/{id}/expert-payment/reset` trước (xem mục 2.6) để xoá sạch dữ liệu tài chính, sau đó mới `reassign-lawyer` được. |
| 4 | Gọi `updatePayment` nhiều lần với `PAID` | `expertPaidAt` bị ghi đè lại bằng thời điểm gọi mới nhất mỗi lần; mỗi lần đều tạo 1 dòng audit log mới (không gộp) |
| 5 | Ticket không tồn tại hoặc đã soft-delete | 404 `ResourceNotFoundException` |
| 6 | Expert gọi `GET /expert/revenue*` khi chưa được gán ticket nào | Trả về response với toàn bộ số 0 / `items` rỗng, không lỗi |
| 7 | `consultationFee` âm, hoặc `commissionRate` ngoài khoảng `[0,1]` | Bị chặn ở validation (`@DecimalMin`/`@DecimalMax`) → 400 |
| 8 | Ticket `RESOLVED` rồi bị `REOPENED`, expert xử lý lại rồi resolve lần 2 | `commissionRate` giữ nguyên từ lần đầu (đúng theo thiết kế — rate bị khoá); `platformFee`/`expertPayout` tính lại theo `consultationFee` hiện tại, không tách theo từng vòng resolve |
| 9 | Bảng `revenue_settings` chưa được seed (môi trường chưa chạy migration) | `getCurrentRate()` fallback về hằng số `0.2000` trong code, không lỗi |
| 10 | `GET /admin/revenue/overview` khi hệ thống chưa có ticket resolved/closed nào | Trả về toàn bộ số `0`, `byExpert` rỗng |

---

## 6. Hạn chế đã biết (chưa triển khai)

- Chưa có UI ở frontend gọi các API này (xem `docs/frontend-flows/12_doanh_thu_expert.txt`).
- Chưa có API GET lịch sử audit log riêng cho từng ticket (dữ liệu đã có trong `ticket_audit_logs` nhưng chưa expose qua REST).
- Việc chuyển tiền thật cho Expert vẫn ngoài hệ thống (không có ví/wallet, không tích hợp cổng thanh toán để tự động payout).
- Danh sách đầy đủ các bug/vấn đề còn tồn đọng (bao gồm cả ngoài phạm vi revenue): xem `docs/be/known-issues.md`.
