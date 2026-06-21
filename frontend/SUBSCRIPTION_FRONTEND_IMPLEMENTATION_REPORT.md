# Báo cáo tích hợp đăng ký gói dịch vụ vào Frontend

## 1. Tổng quan

Đã tích hợp luồng frontend để người dùng xem gói dịch vụ hiện tại, quota sử dụng và đăng ký/nâng cấp gói dịch vụ trong LexiGuard AI.

Phạm vi đã thực hiện:

- Thêm cấu hình endpoint subscription qua `.env` và `.env.example`.
- Thêm API client cho subscription/customer plan.
- Thêm type TypeScript cho `SubscriptionPlan`, `CustomerPlan`, `PaymentMethod`.
- Thêm route mới `/billing/subscribe`.
- Cập nhật trang `/billing` để lấy current plan thật từ backend.
- Giữ bảng hóa đơn mock hiện có và ghi rõ đây vẫn là dữ liệu mẫu.
- Không tích hợp cổng thanh toán/VNPAY redirect trong task này.

## 2. Mục tiêu đã thực hiện

- Billing page gọi `GET /api/v1/customer-plans/me` qua endpoint env.
- Billing page hiển thị gói hiện tại, trạng thái, giá, thời hạn, ngày bắt đầu/kết thúc, auto renew và quota.
- Subscribe page gọi `GET /api/v1/subscription-plans` để hiển thị danh sách gói.
- Subscribe page cho phép chọn gói, chọn phương thức thanh toán và xác nhận trước khi đăng ký.
- Subscribe page gọi API đăng ký gói qua `VITE_CUSTOMER_PLAN_SUBSCRIBE_API`.
- Sau khi đăng ký thành công, frontend gọi lại `GET /api/v1/customer-plans/me`.
- Các lỗi phổ biến được hiển thị bằng thông điệp thân thiện bằng tiếng Việt khi app ở ngôn ngữ `vi`.

## 3. Các API đã tích hợp

Các API được gọi thông qua `src/services/subscription.service.ts`:

```http
GET /api/v1/subscription-plans
POST /api/v1/customer-plans/subcribe
GET /api/v1/customer-plans/me
```

Lưu ý: path thực tế không được hard-code trong component. Component chỉ gọi service, service đọc endpoint từ `API_ENDPOINTS.subscription`, còn path cụ thể đến từ biến môi trường.

## 4. Biến môi trường đã thêm

Đã thêm vào `.env` và `.env.example`:

```env
VITE_SUBSCRIPTION_PLANS_API=/api/v1/subscription-plans
VITE_CUSTOMER_PLAN_SUBSCRIBE_API=/api/v1/customer-plans/subcribe
VITE_CUSTOMER_PLAN_ME_API=/api/v1/customer-plans/me
```

Frontend tiếp tục tái sử dụng:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Không tạo thêm biến base URL backend mới.

## 5. File đã tạo mới

- `src/types/subscription.ts`
- `src/services/subscription.service.ts`
- `src/api/subscriptionApi.ts`
- `src/components/billing/SubscriptionStatusBadge.tsx`
- `src/pages/billing/SubscribePlanPage.tsx`
- `SUBSCRIPTION_FRONTEND_IMPLEMENTATION_REPORT.md`

## 6. File đã chỉnh sửa

- `.env`
- `.env.example`
- `src/config/api.ts`
- `src/pages/billing/BillingPage.tsx`
- `src/routes/router.tsx`
- `src/layouts/Topbar.tsx`
- `src/utils/i18n.ts`
- `src/utils/format.ts`

## 7. Luồng trang đăng ký gói

Route mới:

```text
/billing/subscribe
```

Luồng xử lý:

1. Trang gọi `getSubscriptionPlans()`.
2. API client gọi endpoint từ `VITE_SUBSCRIPTION_PLANS_API`.
3. UI hiển thị các plan card theo dữ liệu backend.
4. Mỗi card hiển thị `planName`, `description`, `price`, `durationDays`, `maxQuota`, `planType`, `active`.
5. User chọn một plan.
6. User chọn phương thức thanh toán.
7. User bấm nút xem lại và đăng ký.
8. Modal xác nhận hiển thị thông tin plan và payment method.
9. Khi xác nhận, frontend gọi `subscribeCustomerPlan()`.
10. Payload gửi lên backend:

```ts
{
  subscriptionPlanId: selectedPlan.id,
  paymentMethod
}
```

11. Nếu thành công, UI hiển thị `status`, `latestTransactionId`, `latestTransactionCode` nếu backend trả về.
12. Frontend gọi lại `getMyCustomerPlan()` để refresh current plan.
13. User có nút quay về trang Billing.

## 8. Luồng trang Billing

Route hiện có:

```text
/billing
```

Luồng xử lý mới:

1. Trang gọi `getMyCustomerPlan()` khi mount.
2. API client gọi endpoint từ `VITE_CUSTOMER_PLAN_ME_API`.
3. Nếu có dữ liệu, trang hiển thị current plan và quota thật.
4. Nếu backend báo chưa có gói, trang hiển thị empty state:

```text
Bạn chưa có gói dịch vụ nào. Hãy đăng ký một gói để bắt đầu sử dụng.
```

5. Trang có CTA:

```text
Đăng ký / Nâng cấp gói
```

6. CTA điều hướng đến `/billing/subscribe`.
7. Có nút làm mới để gọi lại `/me`.
8. Bảng hóa đơn cũ vẫn hiển thị nhưng được ghi chú là dữ liệu mẫu.

## 9. Mapping dữ liệu backend sang UI

Mapping current plan:

- `subscriptionPlan.planName` -> tên gói hiện tại.
- `subscriptionPlan.description` -> mô tả gói.
- `subscriptionPlan.price` -> giá hiển thị theo VND.
- `subscriptionPlan.planType` -> loại gói.
- `subscriptionPlan.durationDays` -> thời hạn gói.
- `status` -> badge trạng thái gói.
- `startDate` -> ngày bắt đầu, fallback `Chưa kích hoạt`.
- `endDate` -> ngày kết thúc, fallback `Chưa có ngày kết thúc`.
- `autoRenew` -> `Có` hoặc `Không`.
- `usedQuota` -> số lượt đã dùng.
- `remainingQuota` -> số lượt còn lại.
- `subscriptionPlan.maxQuota` -> tổng quota.

Mapping plan card ở trang subscribe:

- `planName` -> tiêu đề card.
- `description` -> mô tả card.
- `price` -> giá VND hoặc `Miễn phí`.
- `durationDays` -> số ngày sử dụng.
- `maxQuota` -> số lượt phân tích tối đa.
- `planType` -> loại gói.
- `active` -> badge `Đang mở` hoặc `Đã tắt`.

## 10. Xử lý trạng thái gói

Đã thêm component:

```text
src/components/billing/SubscriptionStatusBadge.tsx
```

Mapping trạng thái:

- `PENDING` -> `Chờ thanh toán`
- `ACTIVE` -> `Đang hoạt động`
- `EXPIRED` -> `Đã hết hạn`
- `CANCELLED` -> `Đã hủy`
- Trạng thái khác -> `Không xác định`

Ghi chú quan trọng: `PENDING` không được hiển thị như gói đã active. UI vẫn hiển thị rõ đây là trạng thái chờ thanh toán/kích hoạt.

## 11. Xử lý quota

Billing page tính phần trăm sử dụng quota theo công thức:

```ts
const percent = maxQuota > 0
  ? Math.round((usedQuota / maxQuota) * 100)
  : 0;
```

UI hiển thị:

- `Đã sử dụng`
- `Còn lại`
- `Tổng quota`
- Progress bar theo `usedQuota / subscriptionPlan.maxQuota`

Không dùng lại `BillingUsage` cũ để biểu diễn current plan, vì backend response có shape khác.

## 12. Xử lý loading, empty state và error

Billing page:

- Loading: hiển thị skeleton card.
- No plan: hiển thị empty state và nút chọn gói.
- Unauthorized: hiển thị thông báo phiên đăng nhập hết hạn.
- Lỗi backend/network: hiển thị thông báo không thể tải gói hiện tại và nút thử lại.

Subscribe page:

- Loading: hiển thị skeleton plan card.
- Empty list: hiển thị empty state nếu backend không trả plan.
- Không chọn plan: không cho submit và hiển thị thông báo.
- Plan inactive: không cho đăng ký và hiển thị thông báo.
- Submit đang chạy: disable nút xác nhận.
- Unauthorized: hiển thị thông báo phiên đăng nhập hết hạn.
- Lỗi đăng ký: hiển thị thông báo thân thiện, không đưa stack trace lên UI.

Các lỗi kỹ thuật vẫn được log bằng `console.error` để debug.

## 13. Cách chạy frontend

Các lệnh đã kiểm tra:

```bash
cd frontend
npm install
npm run build
```

Kết quả:

- `npm install`: dependency tree đã up to date.
- `npm run build`: build thành công.
- Vite có cảnh báo chunk JS lớn hơn 500 kB, đây là warning tối ưu bundle, không phải lỗi build.

Dev server:

```bash
npm run dev
```

Trong môi trường kiểm tra hiện tại, `http://localhost:5173/billing` và `http://localhost:5173/billing/subscribe` đều trả HTTP `200`.

## 14. Cách kiểm thử thủ công

Kiểm thử Billing:

1. Đăng nhập vào frontend.
2. Mở:

```text
http://localhost:5173/billing
```

3. Kiểm tra request `GET /api/v1/customer-plans/me`.
4. Nếu customer có gói, kiểm tra tên gói, trạng thái, giá, quota và ngày tháng.
5. Nếu customer chưa có gói, kiểm tra empty state và nút chọn gói.

Kiểm thử Subscribe:

1. Mở:

```text
http://localhost:5173/billing/subscribe
```

2. Kiểm tra request `GET /api/v1/subscription-plans`.
3. Chọn một gói active.
4. Chọn phương thức thanh toán, mặc định là `VNPAY`.
5. Mở modal xác nhận.
6. Xác nhận đăng ký.
7. Kiểm tra request subscribe gửi body:

```json
{
  "subscriptionPlanId": 2,
  "paymentMethod": "VNPAY"
}
```

8. Sau khi thành công, kiểm tra frontend gọi lại `GET /api/v1/customer-plans/me`.

## 15. Lưu ý về endpoint subcribe/subscribe

Biến môi trường mặc định hiện đang dùng đúng theo yêu cầu task:

```env
VITE_CUSTOMER_PLAN_SUBSCRIBE_API=/api/v1/customer-plans/subcribe
```

Tuy nhiên, báo cáo phân tích backend trước đó ghi nhận source backend hiện tại có thể đang expose:

```http
POST /api/v1/customer-plans/subscribe
```

Frontend đã được thiết kế để đổi path chỉ bằng env. Nếu backend thực tế dùng `/subscribe`, chỉ cần đổi:

```env
VITE_CUSTOMER_PLAN_SUBSCRIBE_API=/api/v1/customer-plans/subscribe
```

Không cần sửa component hoặc service.

## 16. Những phần chưa làm

- Chưa tích hợp payment gateway redirect như VNPAY URL.
- Chưa tích hợp API hóa đơn thật.
- Chưa tích hợp lịch sử giao dịch thanh toán.
- Chưa tự động điều hướng sang cổng thanh toán sau khi tạo transaction.
- Chưa thêm sidebar item riêng cho `/billing/subscribe`; trang này được truy cập qua CTA từ Billing.
- Chưa thêm test tự động cho subscription UI.

## 17. Đề xuất cải thiện tiếp theo

- Xác nhận và thống nhất endpoint backend là `/subcribe` hay `/subscribe`.
- Nếu backend hỗ trợ payment URL, thêm bước redirect hoặc nút tiếp tục thanh toán sau khi nhận `latestTransactionId`.
- Thêm API hóa đơn thật để thay thế `invoices` mock.
- Thêm hook `useCustomerPlan` và `useSubscriptionPlans` nếu các màn hình khác cũng cần dùng dữ liệu này.
- Thêm unit test cho service parse lỗi và trạng thái no-plan.
- Thêm trạng thái quota cạn để dẫn user tới nâng cấp gói.

## 18. Kết luận

Frontend hiện đã có luồng subscription cơ bản: xem current plan ở `/billing`, chọn và đăng ký gói ở `/billing/subscribe`, refresh current plan sau khi đăng ký, và toàn bộ endpoint path được cấu hình qua env.

Billing page không còn hiển thị `Enterprise Platinum` và giá `$4,200` như gói hiện tại thật. Thay vào đó, dữ liệu gói và quota được lấy từ backend `/me` khi backend sẵn sàng và user đã đăng nhập.

Phần hóa đơn và payment gateway vẫn chưa được tích hợp API thật trong task này, nên invoice UI hiện chỉ còn là dữ liệu mẫu được ghi chú rõ.
