# Báo cáo phân tích API gói dịch vụ khách hàng

## 1. Tổng quan

Báo cáo này phân tích các API gói dịch vụ trong backend Spring Boot hiện tại để phục vụ tích hợp frontend sau này. Phạm vi phân tích gồm:

- Lấy danh sách gói dịch vụ đang hoạt động.
- Đăng ký gói dịch vụ cho customer hiện tại.
- Lấy gói dịch vụ hiện tại của customer.
- Quan hệ giữa gói dịch vụ, gói của khách hàng, giao dịch thanh toán, quota và trạng thái.

Lưu ý quan trọng: đề bài nhắc endpoint `POST /api/v1/customer-plans/subcribe`, nhưng source code backend hiện tại khai báo `POST /api/v1/customer-plans/subscribe`. Không tìm thấy mapping `/subcribe` trong controller. Vì vậy frontend nếu gọi đúng source code hiện tại cần gọi `/subscribe`; nếu bắt buộc dùng `/subcribe` thì backend hiện tại chưa hỗ trợ.

## 2. Danh sách API liên quan

Các API chính trong phạm vi frontend cần dùng:

| Mục đích | Method | Endpoint theo source code | Auth |
|---|---:|---|---|
| Lấy danh sách gói đang hoạt động | `GET` | `/api/v1/subscription-plans` | Cần đăng nhập |
| Đăng ký gói dịch vụ | `POST` | `/api/v1/customer-plans/subscribe` | Cần đăng nhập |
| Lấy gói hiện tại của customer | `GET` | `/api/v1/customer-plans/me` | Cần đăng nhập |

API liên quan đến thanh toán sau khi đăng ký:

| Mục đích | Method | Endpoint | Auth |
|---|---:|---|---|
| Tạo URL thanh toán VNPAY | `POST` | `/api/v1/payment-transactions/{id}/vnpay-url` | Cần đăng nhập |
| Giả lập thanh toán thành công | `PUT` | `/api/v1/payment-transactions/{id}/success` | Cần đăng nhập |
| Giả lập thanh toán thất bại | `PUT` | `/api/v1/payment-transactions/{id}/failed` | Cần đăng nhập |

## 3. API lấy danh sách gói dịch vụ

### 3.1. Endpoint

```http
GET /api/v1/subscription-plans
```

### 3.2. Method

`GET`

### 3.3. Mục đích

Trả về danh sách các gói dịch vụ có `active = true`. API này phục vụ màn hình chọn gói hoặc bảng giá trong frontend.

### 3.4. Request

Không có request body và không có query param.

Yêu cầu authentication:

- Có JWT access token hợp lệ trong header:

```http
Authorization: Bearer <accessToken>
```

Source code dùng `@PreAuthorize("isAuthenticated()")`, nên mọi user đã đăng nhập đều gọi được.

### 3.5. Response

Response wrapper dùng `ApiResponseDTO<T>`:

```json
{
  "code": 200,
  "message": "Lấy danh sách gói dịch vụ thành công",
  "data": []
}
```

Mỗi phần tử trong `data` có shape từ `SubscriptionPlanResponseDTO`:

```json
{
  "id": 1,
  "planName": "Gói Miễn Phí",
  "planType": "FREE",
  "description": "Gói cơ bản trải nghiệm dịch vụ phân tích văn bản pháp lý",
  "price": 0,
  "durationDays": 30,
  "maxQuota": 5,
  "active": true,
  "createdAt": "2026-06-18T02:34:04.905875",
  "updatedAt": "2026-06-18T02:34:04.905875"
}
```

Các mã lỗi có thể gặp:

- `401`: chưa đăng nhập hoặc token không hợp lệ.
- `500`: lỗi không mong đợi.

Không có validation riêng cho request vì API không nhận body.

### 3.6. File backend liên quan

- `backend/src/main/java/com/analyzer/api/controller/SubscriptionPlanController.java`
- `backend/src/main/java/com/analyzer/api/service/SubscriptionPlanService.java`
- `backend/src/main/java/com/analyzer/api/service/impl/SubscriptionPlanServiceImpl.java`
- `backend/src/main/java/com/analyzer/api/repository/SubscriptionPlanRepository.java`
- `backend/src/main/java/com/analyzer/api/entity/SubscriptionPlan.java`
- `backend/src/main/java/com/analyzer/api/dto/subscriptionplan/SubscriptionPlanResponseDTO.java`
- `backend/src/main/java/com/analyzer/api/mapper/SubscriptionPlanMapper.java`

### 3.7. Luồng xử lý backend

1. `SubscriptionPlanController.getActivePlans()` nhận request.
2. Controller yêu cầu user đã authenticated.
3. Controller gọi `subscriptionPlanService.getActivePlans()`.
4. `SubscriptionPlanServiceImpl.getActivePlans()` gọi `subscriptionPlanRepository.findByActiveTrue()`.
5. Repository lấy các record trong bảng `subscription_plans` có `active = true`.
6. Kết quả entity được map sang `SubscriptionPlanResponseDTO`.
7. Controller trả `ApiResponseDTO.success("Lấy danh sách gói dịch vụ thành công", response)`.

### 3.8. Ghi chú cho frontend

- Frontend chỉ nên hiển thị các gói trả về từ API, không hard-code danh sách gói.
- `price` là số tiền dạng `BigDecimal` phía backend, khi sang JSON thường là number; frontend nên format theo VND.
- `maxQuota` là tổng số lượt sử dụng tối đa của gói.
- `durationDays` là thời hạn gói tính theo ngày.
- API này yêu cầu đăng nhập, nên nếu màn hình bảng giá public cần backend mở quyền hoặc frontend dùng dữ liệu khác.

## 4. API đăng ký gói dịch vụ

### 4.1. Endpoint

Endpoint theo source code hiện tại:

```http
POST /api/v1/customer-plans/subscribe
```

Endpoint `POST /api/v1/customer-plans/subcribe` trong đề bài chưa được backend hiện tại map. Không nên gọi `/subcribe` nếu chưa sửa backend.

### 4.2. Method

`POST`

### 4.3. Mục đích

Đăng ký một gói dịch vụ cho customer đang đăng nhập. API này không kích hoạt gói ngay lập tức; nó tạo hoặc cập nhật một `CustomerPlan` trạng thái `PENDING`, đồng thời tạo một `PaymentTransaction` trạng thái `PENDING`.

### 4.4. Request body

DTO: `SubscribeRequestDTO`

```json
{
  "subscriptionPlanId": 2,
  "paymentMethod": "VNPAY"
}
```

Validation:

- `subscriptionPlanId`: bắt buộc, lỗi nếu null: `ID gói không được để trống`.
- `paymentMethod`: bắt buộc, lỗi nếu null: `Phương thức thanh toán không được để trống`.

Các giá trị `paymentMethod` được enum backend hỗ trợ:

- `CASH`
- `BANK_TRANSFER`
- `MOMO`
- `VNPAY`
- `CREDIT_CARD`

Yêu cầu authentication:

```http
Authorization: Bearer <accessToken>
```

Customer ID không truyền từ frontend. Backend lấy từ `SecurityContextHolder`, ép principal sang `UserDetailsImpl`, rồi lấy `userDetails.getId()`.

### 4.5. Response

Khi tạo thành công, controller trả HTTP `201 Created` và body:

```json
{
  "code": 201,
  "message": "Đăng ký gói dịch vụ thành công, vui lòng thanh toán",
  "data": {
    "id": 5,
    "customerId": 8,
    "subscriptionPlan": {
      "id": 2,
      "planName": "Gói Tiêu Chuẩn",
      "planType": "MONTHLY",
      "description": "Gói tiêu chuẩn cho cá nhân, truy cập nhiều lượt phân tích và chat",
      "price": 150000,
      "durationDays": 30,
      "maxQuota": 50,
      "active": true,
      "createdAt": "2026-06-18T02:34:04.913934",
      "updatedAt": "2026-06-18T02:34:04.913934"
    },
    "status": "PENDING",
    "startDate": null,
    "endDate": null,
    "usedQuota": 0,
    "autoRenew": false,
    "remainingQuota": 50,
    "latestTransactionId": 10,
    "latestTransactionCode": "TXABC123DEF456",
    "createdAt": "2026-06-18T08:08:23.481275",
    "updatedAt": "2026-06-18T08:08:23.482288"
  }
}
```

`latestTransactionId` và `latestTransactionCode` được set thủ công trong `CustomerPlanServiceImpl.subscribe()` sau khi tạo `PaymentTransaction`.

Các lỗi có thể gặp:

- `401`: chưa đăng nhập hoặc token không hợp lệ.
- `400` với `Validation failed`: thiếu `subscriptionPlanId` hoặc `paymentMethod`.
- `400` với `Không tìm thấy thông tin khách hàng`: customer ID từ token không tồn tại trong database.
- `400` với `Gói dịch vụ không tồn tại với id: ...`: `subscriptionPlanId` không tồn tại.
- `400` với `Gói dịch vụ này hiện đã ngừng hoạt động`: gói có `active != true`.
- Lỗi parse enum `paymentMethod` không được custom rõ trong code; Spring có thể trả lỗi trước khi vào service.

### 4.6. File backend liên quan

- `backend/src/main/java/com/analyzer/api/controller/CustomerPlanController.java`
- `backend/src/main/java/com/analyzer/api/service/CustomerPlanService.java`
- `backend/src/main/java/com/analyzer/api/service/impl/CustomerPlanServiceImpl.java`
- `backend/src/main/java/com/analyzer/api/dto/customerplan/SubscribeRequestDTO.java`
- `backend/src/main/java/com/analyzer/api/dto/customerplan/CustomerPlanResponseDTO.java`
- `backend/src/main/java/com/analyzer/api/repository/UserRepository.java`
- `backend/src/main/java/com/analyzer/api/repository/SubscriptionPlanRepository.java`
- `backend/src/main/java/com/analyzer/api/repository/CustomerPlanRepository.java`
- `backend/src/main/java/com/analyzer/api/repository/PaymentTransactionRepository.java`
- `backend/src/main/java/com/analyzer/api/entity/User.java`
- `backend/src/main/java/com/analyzer/api/entity/SubscriptionPlan.java`
- `backend/src/main/java/com/analyzer/api/entity/CustomerPlan.java`
- `backend/src/main/java/com/analyzer/api/entity/PaymentTransaction.java`
- `backend/src/main/java/com/analyzer/api/enums/PaymentMethod.java`
- `backend/src/main/java/com/analyzer/api/enums/PaymentStatus.java`
- `backend/src/main/java/com/analyzer/api/enums/PlanStatus.java`
- `backend/src/main/java/com/analyzer/api/mapper/CustomerPlanMapper.java`

### 4.7. Luồng xử lý backend

1. `CustomerPlanController.subscribe()` nhận request body.
2. Controller kiểm tra authentication qua `@PreAuthorize("isAuthenticated()")`.
3. Controller gọi `getCurrentUserId()` để lấy `customerId` từ JWT principal.
4. Controller gọi `customerPlanService.subscribe(customerId, request)`.
5. Service tìm `User` theo `customerId`.
6. Service tìm `SubscriptionPlan` theo `request.subscriptionPlanId`.
7. Service kiểm tra gói còn active hay không.
8. Service tìm `CustomerPlan` trạng thái `PENDING` hiện có của customer.
9. Nếu đã có `PENDING`, service cập nhật lại gói được chọn, reset `usedQuota = 0`, `startDate = null`, `endDate = null`, `autoRenew = false`.
10. Nếu chưa có `PENDING`, service tạo mới `CustomerPlan` với `status = PENDING`, `usedQuota = 0`, `autoRenew = false`.
11. Service lưu `CustomerPlan`.
12. Service tạo `PaymentTransaction` với `amount = plan.price`, `paymentMethod = request.paymentMethod`, `paymentStatus = PENDING`, `transactionCode = "TX" + 12 ký tự UUID`.
13. Service lưu `PaymentTransaction`.
14. Service map `CustomerPlan` sang DTO và set thêm `latestTransactionId`, `latestTransactionCode`.
15. Controller trả HTTP `201`.

### 4.8. Trạng thái gói sau khi đăng ký

Ngay sau khi gọi API đăng ký, `CustomerPlan.status` là:

```text
PENDING
```

Ý nghĩa:

- Customer đã chọn gói.
- Backend đã tạo giao dịch thanh toán.
- Gói chưa được kích hoạt.
- `startDate` và `endDate` vẫn là `null`.
- `usedQuota = 0`.
- `remainingQuota = subscriptionPlan.maxQuota - usedQuota`.

Gói chỉ chuyển sang `ACTIVE` khi giao dịch thanh toán được mark success qua payment flow. Trong `PaymentTransactionServiceImpl.markSuccess()`:

- Transaction chuyển `PaymentStatus.SUCCESS`.
- `paidAt` được set bằng thời điểm hiện tại.
- Customer plan liên quan chuyển `PlanStatus.ACTIVE`.
- `startDate = now`.
- `endDate = now + subscriptionPlan.durationDays`.
- Gói active cũ của cùng customer, nếu khác plan hiện tại, bị chuyển thành `EXPIRED`.

### 4.9. Ghi chú cho frontend

- Frontend cần gọi đúng endpoint backend hiện tại là `/api/v1/customer-plans/subscribe`, không phải `/subcribe`, trừ khi backend được bổ sung alias.
- Sau khi đăng ký, nếu `paymentMethod = VNPAY`, frontend có thể dùng `latestTransactionId` để gọi `POST /api/v1/payment-transactions/{id}/vnpay-url`.
- Không nên tự coi gói đã active sau khi đăng ký; cần dựa vào `status`.
- Nên hiển thị trạng thái `PENDING` là “Chờ thanh toán” hoặc “Chưa kích hoạt”.
- Với response của API đăng ký, `latestTransactionId` và `latestTransactionCode` có dữ liệu; nhưng với API `/me`, hai field này có thể null do mapper đang ignore.

## 5. API lấy gói hiện tại của customer

### 5.1. Endpoint

```http
GET /api/v1/customer-plans/me
```

### 5.2. Method

`GET`

### 5.3. Mục đích

Lấy gói hiện tại của customer đang đăng nhập. Backend ưu tiên trả về gói `ACTIVE`; nếu không có `ACTIVE`, trả về gói `PENDING`; nếu không có cả hai thì lấy gói gần nhất theo `updatedAt`.

### 5.4. Request

Không có request body và không có query param.

Yêu cầu authentication:

```http
Authorization: Bearer <accessToken>
```

Customer ID được lấy từ token/security context, không truyền từ frontend.

### 5.5. Response

Response wrapper:

```json
{
  "code": 200,
  "message": "Lấy gói dịch vụ hiện tại thành công",
  "data": {
    "id": 5,
    "customerId": 8,
    "subscriptionPlan": {
      "id": 2,
      "planName": "Gói Tiêu Chuẩn",
      "planType": "MONTHLY",
      "description": "Gói tiêu chuẩn cho cá nhân, truy cập nhiều lượt phân tích và chat",
      "price": 150000,
      "durationDays": 30,
      "maxQuota": 50,
      "active": true,
      "createdAt": "2026-06-18T02:34:04.913934",
      "updatedAt": "2026-06-18T02:34:04.913934"
    },
    "status": "PENDING",
    "startDate": null,
    "endDate": null,
    "usedQuota": 0,
    "autoRenew": false,
    "remainingQuota": 50,
    "latestTransactionId": null,
    "latestTransactionCode": null,
    "createdAt": "2026-06-18T08:08:23.481275",
    "updatedAt": "2026-06-18T08:08:23.482288"
  }
}
```

Điểm cần chú ý: `CustomerPlanMapper` ignore `latestTransactionId` và `latestTransactionCode`. `getMyPlan()` chỉ gọi mapper, không tự set transaction mới nhất, nên hai field này thường là `null` trong API `/me`.

Các lỗi có thể gặp:

- `401`: chưa đăng nhập hoặc token không hợp lệ.
- `400` với `Bạn chưa đăng ký gói dịch vụ nào`: customer không có bất kỳ `CustomerPlan` nào.
- `400` với `Bạn chưa đăng nhập` hoặc `Thông tin xác thực không hợp lệ`: lỗi principal trong security context.

### 5.6. File backend liên quan

- `backend/src/main/java/com/analyzer/api/controller/CustomerPlanController.java`
- `backend/src/main/java/com/analyzer/api/service/CustomerPlanService.java`
- `backend/src/main/java/com/analyzer/api/service/impl/CustomerPlanServiceImpl.java`
- `backend/src/main/java/com/analyzer/api/repository/CustomerPlanRepository.java`
- `backend/src/main/java/com/analyzer/api/entity/CustomerPlan.java`
- `backend/src/main/java/com/analyzer/api/entity/SubscriptionPlan.java`
- `backend/src/main/java/com/analyzer/api/dto/customerplan/CustomerPlanResponseDTO.java`
- `backend/src/main/java/com/analyzer/api/mapper/CustomerPlanMapper.java`
- `backend/src/main/java/com/analyzer/api/enums/PlanStatus.java`

### 5.7. Luồng xử lý backend

1. `CustomerPlanController.getMyPlan()` nhận request.
2. Controller lấy `customerId` từ `SecurityContextHolder`.
3. Controller gọi `customerPlanService.getMyPlan(customerId)`.
4. Service gọi `getActivePlanOrUpdateIfExpired(customerId)`.
5. Nếu có gói `ACTIVE` và `endDate` đã qua, backend tự set gói đó thành `EXPIRED`, lưu database, rồi trả về `null` để tiếp tục tìm gói khác.
6. Nếu không có `ACTIVE`, service tìm gói `PENDING`.
7. Nếu không có `ACTIVE` hoặc `PENDING`, service lấy danh sách gói của customer và chọn record có `updatedAt` mới nhất.
8. Nếu không tìm thấy gói nào, throw `RuntimeException("Bạn chưa đăng ký gói dịch vụ nào")`.
9. Service map entity sang `CustomerPlanResponseDTO`.
10. Controller trả `ApiResponseDTO.success("Lấy gói dịch vụ hiện tại thành công", response)`.

### 5.8. Ghi chú cho frontend

- Frontend nên gọi API này sau login hoặc sau khi đăng ký/thanh toán để đồng bộ trạng thái gói.
- Không nên dựa vào `latestTransactionId` từ `/me` để tiếp tục thanh toán; field này có thể null. Nên lưu `latestTransactionId` từ response của API đăng ký hoặc dùng API lịch sử giao dịch nếu cần.
- Nếu `status = PENDING`, hiển thị gói đang chờ thanh toán.
- Nếu `status = ACTIVE`, hiển thị ngày bắt đầu, ngày hết hạn, quota đã dùng và quota còn lại.
- Nếu `status = EXPIRED` hoặc `CANCELLED`, nên hiển thị CTA chọn gói mới.

## 6. Phân tích model dữ liệu

### `SubscriptionPlan`

Entity: `backend/src/main/java/com/analyzer/api/entity/SubscriptionPlan.java`

Bảng: `subscription_plans`

Các field chính:

- `id`: khóa chính.
- `planName`: tên gói, không null.
- `planType`: loại gói, không null. Backend đang lưu string, không phải enum.
- `description`: mô tả, kiểu text.
- `price`: giá, `BigDecimal`, không null.
- `durationDays`: thời lượng theo ngày, không null.
- `maxQuota`: quota tối đa, không null.
- `active`: gói còn hoạt động hay không.
- `createdAt`, `updatedAt`: tự set bằng `@PrePersist`, `@PreUpdate`.

### `CustomerPlan`

Entity: `backend/src/main/java/com/analyzer/api/entity/CustomerPlan.java`

Bảng: `customer_plans`

Quan hệ:

- `ManyToOne` tới `User` qua `customer_id`.
- `ManyToOne` tới `SubscriptionPlan` qua `subscription_plan_id`.

Các field chính:

- `status`: enum `PlanStatus`, lưu dạng string.
- `startDate`: ngày bắt đầu gói, có thể null.
- `endDate`: ngày hết hạn gói, có thể null.
- `usedQuota`: số quota đã dùng, mặc định `0`.
- `autoRenew`: tự động gia hạn, mặc định `false`.
- `remainingQuota`: `@Transient`, không lưu DB, tính bằng `subscriptionPlan.maxQuota - usedQuota`.
- `createdAt`, `updatedAt`: tự set bằng lifecycle JPA.

### `PaymentTransaction`

Entity: `backend/src/main/java/com/analyzer/api/entity/PaymentTransaction.java`

Bảng: `payment_transactions`

Quan hệ:

- `ManyToOne` tới `User` qua `customer_id`.
- `ManyToOne` tới `SubscriptionPlan` qua `subscription_plan_id`.
- `ManyToOne` tới `CustomerPlan` qua `customer_plan_id`, nullable.

Các field chính:

- `amount`: số tiền thanh toán.
- `paymentMethod`: enum `PaymentMethod`.
- `paymentStatus`: enum `PaymentStatus`.
- `transactionCode`: mã giao dịch unique.
- `paymentUrl`: URL thanh toán, dùng cho VNPAY.
- `gatewayTransactionNo`, `gatewayResponseCode`: dữ liệu gateway trả về.
- `paidAt`: thời điểm thanh toán thành công.

## 7. Phân tích trạng thái gói dịch vụ

Enum `PlanStatus` gồm:

- `PENDING`: đã đăng ký/chọn gói nhưng chưa thanh toán hoặc chưa kích hoạt.
- `ACTIVE`: gói đang hoạt động, được set khi transaction thành công.
- `EXPIRED`: gói hết hạn hoặc gói active cũ bị thay thế khi gói mới thanh toán thành công.
- `CANCELLED`: gói bị hủy qua API cancel.

Ý nghĩa frontend:

- `PENDING`: hiển thị “Chờ thanh toán”.
- `ACTIVE`: hiển thị “Đang hoạt động”.
- `EXPIRED`: hiển thị “Đã hết hạn”.
- `CANCELLED`: hiển thị “Đã hủy”.

Backend tự chuyển `ACTIVE` sang `EXPIRED` khi gọi `getMyPlan()`, `validateChatQuota()` hoặc `recordChatUsage()` và phát hiện `endDate` đã qua.

## 8. Phân tích quota

Các field quota:

- `subscriptionPlan.maxQuota`: tổng quota của gói.
- `customerPlan.usedQuota`: số lượt đã dùng.
- `customerPlan.remainingQuota`: field transient, tính bằng `maxQuota - usedQuota`.

Logic quota hiện có:

- `validateChatQuota(customerId)` kiểm tra customer có gói `ACTIVE` không.
- Nếu không có gói `ACTIVE`, lỗi: `Bạn không có gói dịch vụ nào đang kích hoạt`.
- Nếu `usedQuota >= maxQuota`, lỗi: `Vượt quá hạn mức sử dụng của gói (Quota exceeded)`.
- `recordChatUsage(customerId)` tăng `usedQuota` thêm `1` sau khi kiểm tra quota.

Điểm chưa rõ:

- Trong source hiện tại không thấy controller nào gọi `validateChatQuota()` hoặc `recordChatUsage()`.
- Vì vậy báo cáo chỉ có thể khẳng định service quota đã có, chưa khẳng định luồng chat/document nào đang tiêu quota.

## 9. Authentication và current customer

Backend dùng Spring Security + JWT:

- Login trả `accessToken` trong body.
- Frontend cần gửi access token qua header `Authorization: Bearer <token>`.
- `JwtAuthenticationFilter` đọc Bearer token, validate, load user theo email và set authentication vào `SecurityContextHolder`.
- `UserDetailsImpl` chứa `id`, `email`, password và authorities dạng `ROLE_<role>`.
- `CustomerPlanController.getCurrentUserId()` lấy ID customer từ `UserDetailsImpl.getId()`.

Security config:

- `/api/v1/auth/**` được permit, trừ `/api/v1/auth/me` yêu cầu authenticated.
- VNPAY return/IPN được permit.
- Các request khác mặc định authenticated.
- Method security bật bằng `@EnableMethodSecurity`.

Với ba API trong báo cáo:

- `GET /api/v1/subscription-plans`: `@PreAuthorize("isAuthenticated()")`.
- `POST /api/v1/customer-plans/subscribe`: `@PreAuthorize("isAuthenticated()")`.
- `GET /api/v1/customer-plans/me`: `@PreAuthorize("isAuthenticated()")`.

## 10. Error handling

Backend dùng `GlobalExceptionHandler`.

Response lỗi chung dùng `ApiResponseDTO`:

```json
{
  "code": 400,
  "message": "Nội dung lỗi"
}
```

Các nhóm lỗi:

- `BadCredentialsException`: HTTP `401`, message `Email hoặc mật khẩu không đúng`.
- `MethodArgumentNotValidException`: HTTP `400`, message `Validation failed`, `data` là object field-error.
- `RuntimeException`: HTTP `400`, message lấy từ exception.
- `Exception`: HTTP `500`, message `An unexpected error occurred`.
- Lỗi chưa authenticated từ Spring Security đi qua `AuthEntryPointJwt`: HTTP `401`, message dạng `Unauthorized: ...`.

Validation request đăng ký gói:

```json
{
  "code": 400,
  "message": "Validation failed",
  "data": {
    "subscriptionPlanId": "ID gói không được để trống",
    "paymentMethod": "Phương thức thanh toán không được để trống"
  }
}
```

## 11. Mapping dữ liệu cho frontend

Gợi ý hiển thị `SubscriptionPlan`:

- `planName`: tiêu đề card gói.
- `description`: mô tả ngắn.
- `price`: giá, format VND; nếu `0` có thể hiển thị “Miễn phí”.
- `durationDays`: thời hạn.
- `maxQuota`: số lượt sử dụng.
- `planType`: nhãn loại gói.
- `active`: thông thường chỉ nhận gói active từ API list, nhưng vẫn nên giữ field.

Gợi ý hiển thị `CustomerPlan`:

- `subscriptionPlan.planName`: tên gói hiện tại.
- `status`: badge trạng thái.
- `startDate`: ngày bắt đầu, có thể null nếu `PENDING`.
- `endDate`: ngày hết hạn, có thể null nếu `PENDING`.
- `usedQuota`: số lượt đã dùng.
- `remainingQuota`: số lượt còn lại.
- `subscriptionPlan.maxQuota`: tổng quota để vẽ progress.
- `autoRenew`: backend mặc định false; hiện chưa thấy logic tự gia hạn.
- `latestTransactionId`: dùng ngay sau API đăng ký để tạo URL thanh toán nếu có.
- `latestTransactionCode`: mã tham chiếu giao dịch, có thể hiển thị trong màn hình thanh toán.

Luồng frontend-facing nên hiểu như sau:

1. Frontend gọi `GET /api/v1/subscription-plans`.
2. Frontend hiển thị danh sách gói.
3. User chọn một gói.
4. Frontend gọi `POST /api/v1/customer-plans/subscribe` với `subscriptionPlanId` và `paymentMethod`.
5. Backend tạo/cập nhật `CustomerPlan` trạng thái `PENDING` và tạo `PaymentTransaction`.
6. Frontend dùng `latestTransactionId` để xử lý thanh toán nếu cần.
7. Frontend gọi `GET /api/v1/customer-plans/me`.
8. Frontend hiển thị gói hiện tại, trạng thái, quota đã dùng và quota còn lại.

## 12. Đề xuất interface TypeScript cho frontend

```ts
export interface ApiResponse<T> {
  code: number;
  message: string;
  data?: T;
}

export interface SubscriptionPlan {
  id: number;
  planName: string;
  planType: string;
  description?: string | null;
  price: number;
  durationDays: number;
  maxQuota: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type PlanStatus = 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

export type PaymentMethod =
  | 'CASH'
  | 'BANK_TRANSFER'
  | 'MOMO'
  | 'VNPAY'
  | 'CREDIT_CARD';

export interface SubscribeCustomerPlanRequest {
  subscriptionPlanId: number;
  paymentMethod: PaymentMethod;
}

export interface CustomerPlan {
  id: number;
  customerId: number;
  subscriptionPlan: SubscriptionPlan;
  status: PlanStatus;
  startDate: string | null;
  endDate: string | null;
  usedQuota: number;
  autoRenew: boolean;
  remainingQuota: number;
  latestTransactionId: number | null;
  latestTransactionCode: string | null;
  createdAt: string;
  updatedAt: string;
}
```

Nếu frontend muốn tích hợp thanh toán VNPAY:

```ts
export interface PaymentUrlResponse {
  transactionId: number;
  transactionCode: string;
  provider: 'VNPAY';
  paymentUrl: string;
}
```

## 13. Cách gọi API từ frontend sau này

Không implement trong task này, nhưng API client tương lai có thể có các hàm:

```ts
getSubscriptionPlans(): Promise<ApiResponse<SubscriptionPlan[]>>

subscribeCustomerPlan(
  payload: SubscribeCustomerPlanRequest,
): Promise<ApiResponse<CustomerPlan>>

getMyCustomerPlan(): Promise<ApiResponse<CustomerPlan>>
```

Nếu dùng VNPAY:

```ts
createVnPayPaymentUrl(
  transactionId: number,
): Promise<ApiResponse<PaymentUrlResponse>>
```

Frontend không nên hard-code `http://localhost:8080`. Project frontend hiện đã có biến:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Nên tái sử dụng biến này thay vì thêm `VITE_BACKEND_API_BASE_URL`, trừ khi team muốn đổi convention. Các endpoint path nên đặt trong env hoặc config API client, tương tự pattern auth hiện tại.

## 14. Các điểm cần chú ý khi tích hợp UI

- Endpoint đăng ký thực tế hiện là `/subscribe`, không phải `/subcribe`.
- API list plan yêu cầu đăng nhập; nếu trang pricing public cần xử lý lại quyền backend.
- Sau đăng ký, `status = PENDING`; không hiển thị như gói đã kích hoạt.
- `startDate` và `endDate` null là bình thường khi gói chưa thanh toán.
- `remainingQuota` được tính từ `maxQuota - usedQuota`; không lưu trực tiếp trong DB.
- `latestTransactionId` có dữ liệu trong response đăng ký, nhưng thường null ở `/me`.
- Nếu user chọn lại gói khi đang có gói `PENDING`, backend cập nhật gói pending cũ thay vì tạo nhiều `CustomerPlan` pending.
- Backend vẫn tạo thêm `PaymentTransaction` mới mỗi lần gọi subscribe.
- Nếu payment success, gói active cũ của customer bị chuyển thành `EXPIRED`.
- `autoRenew` hiện mặc định false và chưa thấy luồng backend xử lý tự gia hạn.
- Cần có UI trạng thái thanh toán riêng nếu dùng `latestTransactionId` và payment transaction APIs.

## 15. Hạn chế hoặc điểm chưa rõ trong backend

- Đề bài nhắc `/api/v1/customer-plans/subcribe`, nhưng source code chỉ có `/api/v1/customer-plans/subscribe`.
- `planType` là string tự do trong entity/request, không có enum backend để frontend biết toàn bộ giá trị hợp lệ.
- `GET /api/v1/subscription-plans` yêu cầu authenticated; điều này có thể không phù hợp nếu frontend muốn public pricing.
- `CustomerPlanMapper` ignore `latestTransactionId` và `latestTransactionCode`, nên `/me` không trả giao dịch mới nhất dù DTO có field.
- Không thấy service `validateChatQuota()` và `recordChatUsage()` được gọi ở controller nào trong source hiện tại.
- Không thấy logic tự gia hạn dù entity có `autoRenew`.
- Không thấy kiểm tra số âm hoặc min value cho `price`, `durationDays`, `maxQuota`; chỉ có `@NotNull` khi admin tạo/sửa plan.
- API `simulateSuccess` và `simulateFailed` không kiểm tra customer sở hữu transaction trong service hiện tại; controller chỉ yêu cầu authenticated.
- Các message lỗi trong payment service có một số chuỗi không dấu tiếng Việt, ví dụ `Khong tim thay giao dich...`.

## 16. Kết luận

Backend hiện đã hỗ trợ đủ luồng cơ bản cho frontend quản lý gói dịch vụ:

1. Lấy danh sách gói active bằng `GET /api/v1/subscription-plans`.
2. Đăng ký gói bằng `POST /api/v1/customer-plans/subscribe`.
3. Lấy gói hiện tại bằng `GET /api/v1/customer-plans/me`.

Luồng đăng ký hiện gắn với thanh toán: sau khi subscribe, gói ở trạng thái `PENDING` và transaction ở trạng thái `PENDING`. Frontend cần hiển thị đúng trạng thái này và chỉ coi gói là đang hoạt động khi backend trả `status = ACTIVE`.

Điểm quan trọng nhất trước khi tích hợp UI là thống nhất lại endpoint đăng ký: backend hiện dùng `/subscribe`, còn đề bài nhắc `/subcribe`. Nếu frontend gọi `/subcribe` theo đề bài, backend hiện tại sẽ không xử lý đúng API đăng ký.
