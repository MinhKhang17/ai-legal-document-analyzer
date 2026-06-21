# Báo cáo phân tích Billing & Subscription Frontend

## 1. Tổng quan

Báo cáo này phân tích phần frontend hiện tại liên quan đến billing, gói dịch vụ, quota, thanh toán, hóa đơn và khả năng mua hoặc đăng ký gói trong dự án LexiGuard AI.

Kết luận chính:

- Frontend hiện tại có route `/billing`.
- Trang `/billing` đang hiển thị giao diện billing/usage/hóa đơn bằng dữ liệu giả/tĩnh.
- Chưa tìm thấy trong frontend hiện tại việc gọi API `GET /api/v1/subscription-plans`.
- Chưa tìm thấy trong frontend hiện tại việc gọi API `GET /api/v1/customer-plans/me`.
- Chưa tìm thấy trong frontend hiện tại việc gọi API `POST /api/v1/customer-plans/subcribe`.
- Chưa tìm thấy trong frontend hiện tại luồng mua gói, chọn gói, xác nhận đăng ký gói hoặc nâng cấp gói thật.
- Có một nút "Quản lý phương thức thanh toán" trên trang Billing, nhưng chưa có `onClick`, chưa mở modal và chưa gọi API.
- Có một nút "Nâng cấp ngay" trong trang Reports, nhưng đây là nội dung tĩnh, không kết nối đến luồng mua gói.

## 2. Các màn hình liên quan đến billing/subscription

Các màn hình và thành phần liên quan đã kiểm tra:

- `src/pages/billing/BillingPage.tsx`: trang Billing chính.
- `src/components/billing/BillingUsageCard.tsx`: thẻ hiển thị usage/quota dạng `used / limit`.
- `src/pages/reports/ReportsPage.tsx`: có nút nâng cấp tĩnh trong khối giới hạn/tỷ lệ, nhưng không phải luồng mua gói.
- `src/layouts/Sidebar.tsx`: có menu đến `/billing`.
- `src/layouts/Topbar.tsx`: có label route `/billing`.
- `src/utils/i18n.ts`: có chuỗi dịch cho Billing, bao gồm `billing.enterprisePlan`, `billing.managePaymentMethod`, `billing.invoices`.
- `src/api/mockData.ts`: chứa dữ liệu giả cho `billingUsage` và `invoices`.

Chưa tìm thấy trong frontend hiện tại màn hình pricing, màn hình chọn gói, modal xác nhận mua gói hoặc trang thanh toán riêng cho subscription.

## 3. Route và điều hướng

Frontend hiện tại có route Billing:

- File: `src/routes/router.tsx`
- Route: `/billing`
- Component: `BillingPage`
- Route nằm trong `AuthenticatedRoute` và `AppShell`, nghĩa là người dùng cần đi qua layout đã xác thực của ứng dụng.

Menu sidebar có mục Billing:

- File: `src/layouts/Sidebar.tsx`
- Mục menu: `to: '/billing'`
- Label i18n: `nav.billing`
- Icon: `Receipt`
- Nhóm menu: `system`

Topbar cũng nhận diện route `/billing`:

- File: `src/layouts/Topbar.tsx`
- Mapping route: `prefix: '/billing'`, `key: 'nav.billing'`

Chuỗi dịch:

- File: `src/utils/i18n.ts`
- Tiếng Anh: `nav.billing` là `Billing`
- Tiếng Việt: `nav.billing` là `Thanh toán`

## 4. Phân tích trang Billing hiện tại

### 4.1. File source liên quan

Các file trực tiếp liên quan đến trang Billing:

- `src/pages/billing/BillingPage.tsx`
- `src/components/billing/BillingUsageCard.tsx`
- `src/types/billing.ts`
- `src/api/mockData.ts`
- `src/utils/i18n.ts`
- `src/utils/format.ts`
- `src/components/common/Button.tsx`
- `src/components/common/Card.tsx`
- `src/components/common/DataTable.tsx`
- `src/components/common/StatusBadge.tsx`
- `src/components/common/ProgressBar.tsx`

### 4.2. UI hiện tại đang hiển thị gì

Trang `BillingPage` hiện đang hiển thị các phần sau:

1. Header trang
   - Tiêu đề lấy từ `billing.title`.
   - Mô tả lấy từ `billing.subtitle`.
   - Có nút `billing.managePaymentMethod`, tiếng Việt là "Quản lý phương thức thanh toán".
   - Nút này chưa có handler `onClick`.

2. Khu vực usage
   - Render danh sách `billingUsage`.
   - Mỗi item dùng `BillingUsageCard`.
   - Mỗi card hiển thị `label`, `used`, `limit`, `unit`, phần trăm và progress bar.

3. Khu vực gói hiện tại dạng card
   - Tên gói lấy từ `billing.enterprisePlan`.
   - Trong file i18n hiện đang là `Enterprise Platinum`.
   - Giá hiển thị trực tiếp trong component là `$4,200`.
   - Chu kỳ lấy từ `billing.perMonth`.
   - Có badge `SOC 2 ready` và `Legal AI models`.

4. Khu vực hóa đơn
   - Render bảng `invoices`.
   - Cột gồm invoice id, date, amount, status và action.
   - Nút tải hóa đơn có icon `Download`, nhưng chưa có handler tải file thật.

### 4.3. Dữ liệu đang lấy từ đâu

Trang Billing đang import dữ liệu trực tiếp từ:

- `src/api/mockData.ts`

Cụ thể:

- `billingUsage`
- `invoices`

Không có `useEffect`, không có React Query, không có hook riêng và không có lời gọi `fetch`/API trong `BillingPage`.

### 4.4. Có dùng mock/static data không

Có.

Dữ liệu usage hiện tại là dữ liệu giả trong `src/api/mockData.ts`:

- `AI Requests`: `1284 / 5000 requests`
- `Token Usage`: `842000 / 2000000 tokens`
- `Secure Storage`: `2.4 / 4 TB`
- `Seats`: `24 / 50 users`

Dữ liệu hóa đơn hiện tại cũng là dữ liệu giả trong `src/api/mockData.ts`:

- `INV-2024-101`, amount `4200`
- `INV-2024-091`, amount `4200`
- `INV-2024-081`, amount `3900`

Tên gói `Enterprise Platinum` đến từ `src/utils/i18n.ts`, không đến từ backend.

Giá `$4,200` được viết trực tiếp trong `src/pages/billing/BillingPage.tsx`, không đến từ backend.

### 4.5. Có gọi API thật không

Không.

Chưa tìm thấy trong frontend hiện tại việc gọi API thật cho Billing hoặc Subscription trong các file đã kiểm tra.

Các API backend sau chưa được gọi trong frontend:

- `GET /api/v1/subscription-plans`
- `POST /api/v1/customer-plans/subcribe`
- `GET /api/v1/customer-plans/me`

## 5. Phân tích luồng mua/đăng ký gói

### 5.1. Có màn hình chọn gói không

Chưa tìm thấy trong frontend hiện tại.

Trang Billing chỉ có một card gói tĩnh `Enterprise Platinum`. Không có danh sách nhiều gói, không có pricing table, không có plan card lấy từ backend và không có UI để chọn giữa `Gói Miễn Phí`, `Gói Tiêu Chuẩn`, `Gói Cao Cấp` hoặc các gói tương tự.

### 5.2. Có nút mua gói/đăng ký gói/nâng cấp gói không

Chưa tìm thấy trong trang Billing hiện tại.

Các nút liên quan đã thấy:

- `Quản lý phương thức thanh toán` trong `BillingPage`: chưa có handler và không phải nút đăng ký gói.
- Nút tải hóa đơn trong bảng invoice: chưa có handler và không liên quan đến mua gói.
- `Nâng cấp ngay` trong `ReportsPage`: là nút tĩnh trong một card khác, chưa có handler và chưa điều hướng đến `/billing`.

Do đó, chưa có nút mua gói, đăng ký gói hoặc nâng cấp gói thật.

### 5.3. Có modal hoặc form xác nhận mua gói không

Chưa tìm thấy trong frontend hiện tại.

Không thấy modal chọn phương thức thanh toán, form xác nhận, form chọn gói, dialog xác nhận đăng ký, hoặc bước review trước khi gọi API.

### 5.4. Có gọi API đăng ký gói không

Không.

Chưa tìm thấy trong frontend hiện tại việc gọi:

- `POST /api/v1/customer-plans/subcribe`
- `POST /api/v1/customer-plans/subscribe`

Lưu ý: báo cáo/backend source hiện có trong repository cho thấy backend hiện tại đang khai báo `/api/v1/customer-plans/subscribe`, còn đề bài nghiệp vụ nhắc `/api/v1/customer-plans/subcribe`. Trước khi tích hợp thật cần thống nhất endpoint chính xác với backend.

### 5.5. Kết luận hiện tại frontend đã có chỗ mua gói chưa

Chưa có.

Frontend hiện tại có trang Billing để xem usage, gói hiện tại dạng tĩnh và hóa đơn giả, nhưng chưa có luồng mua/đăng ký/nâng cấp gói thật. Nếu muốn người dùng mua gói thật, cần bổ sung UI chọn gói, xác nhận mua, API client, state loading/error/success và refresh gói hiện tại sau khi đăng ký.

## 6. Phân tích API client hiện tại

Các file API/client đã kiểm tra:

- `src/config/api.ts`
- `src/api/authApi.ts`
- `src/services/auth.service.ts`
- `src/api/aiApi.ts`
- `src/services/ai.service.ts`
- `src/api/mockApi.ts`
- `src/api/mockData.ts`

Hiện trạng:

- `src/config/api.ts` đã có `API_BASE_URL` đọc từ `VITE_API_BASE_URL`.
- `src/config/api.ts` đã có `buildApiUrl(endpoint)` để ghép URL backend chính.
- `API_ENDPOINTS` hiện có nhóm `auth` và `ai`.
- Chưa có nhóm endpoint cho `subscription`, `customerPlan`, `billing` hoặc `payment`.
- `authApi.ts` và `auth.service.ts` chỉ xử lý đăng nhập, đăng ký, refresh, lấy user hiện tại.
- `aiApi.ts` và `ai.service.ts` chỉ xử lý AI service.
- `mockApi.ts` không cung cấp API giả cho BillingPage; BillingPage import trực tiếp `billingUsage` và `invoices` từ `mockData.ts`.

Chưa tìm thấy trong frontend hiện tại API client cho:

- Lấy danh sách gói dịch vụ.
- Lấy gói hiện tại của customer.
- Đăng ký gói.
- Tạo giao dịch thanh toán.
- Lấy URL thanh toán.
- Tải hóa đơn thật.

## 7. Phân tích state management/hooks liên quan

Các file đã kiểm tra:

- `src/store/AppStore.tsx`
- `src/hooks/useI18n.ts`
- Các page và component billing liên quan.

Hiện trạng:

- `AppStore` quản lý trạng thái ứng dụng chung như theme, language, sidebar và auth user.
- Chưa có state global cho billing, subscription plan, customer plan, quota hoặc payment.
- `BillingPage` không có local state cho loading/error/data.
- Không thấy hook như `useSubscriptionPlans`, `useCustomerPlan`, `useBilling`, `usePayment`.
- Không thấy cache hoặc polling cho quota.

Do đó, hiện chưa có state management phục vụ luồng mua gói hoặc hiển thị gói thật từ backend.

## 8. Phân tích type/interface liên quan

File liên quan:

- `src/types/billing.ts`
- `src/types/status.ts`

`src/types/billing.ts` hiện chỉ có:

```ts
export interface BillingUsage {
  id: string;
  label: string;
  used: number;
  limit: number;
  unit: string;
}

export interface Invoice {
  id: string;
  date: string;
  amount: number;
  status: Status;
}
```

Chưa tìm thấy trong frontend hiện tại các interface sau:

- `SubscriptionPlan`
- `CustomerPlan`
- `SubscribeCustomerPlanRequest`
- `PaymentMethod`
- `PlanStatus`
- `ApiResponse<SubscriptionPlan[]>`
- `ApiResponse<CustomerPlan>`

`src/types/status.ts` có nhiều trạng thái dạng chữ thường như `pending`, `active`, `failed`, nhưng chưa có enum/type riêng cho trạng thái backend dạng chữ hoa như `PENDING`, `ACTIVE`, `EXPIRED`, `CANCELLED`.

Vì vậy, nếu tích hợp backend subscription API sau này, cần bổ sung type mới thay vì tái sử dụng trực tiếp `BillingUsage` hoặc `Invoice`.

## 9. Mapping với backend APIs

### 9.1. GET /api/v1/subscription-plans

Trạng thái hiện tại trong frontend:

- Chưa tìm thấy trong frontend hiện tại.
- Trang Billing chưa gọi API này.
- Chưa có API client function tương ứng.
- Chưa có type `SubscriptionPlan`.
- Chưa có UI render danh sách gói từ backend.

Mapping đề xuất sau này:

- Dùng API này để lấy danh sách gói dịch vụ.
- Hiển thị các field:
  - `planName`
  - `description`
  - `price`
  - `durationDays`
  - `maxQuota`
  - `planType`
  - `active`
- Chỉ nên hiển thị hoặc cho đăng ký các gói `active = true`.
- Có thể thay card tĩnh `Enterprise Platinum` bằng danh sách gói thật hoặc bổ sung khu vực chọn gói bên dưới gói hiện tại.

### 9.2. POST /api/v1/customer-plans/subcribe

Trạng thái hiện tại trong frontend:

- Chưa tìm thấy trong frontend hiện tại.
- Chưa có nút đăng ký gói gọi API này.
- Chưa có modal xác nhận.
- Chưa có API client function.
- Chưa có request type.

Lưu ý rất quan trọng về endpoint:

- Theo yêu cầu nghiệp vụ ở task này, endpoint được nhắc là `POST /api/v1/customer-plans/subcribe`.
- Theo báo cáo/source backend hiện có trong repository, backend hiện tại đang khai báo `POST /api/v1/customer-plans/subscribe`.
- Frontend cần xác nhận lại với backend trước khi tích hợp thật. Nếu backend chưa map `/subcribe`, gọi endpoint này có thể thất bại.

Request body theo báo cáo/backend source hiện có:

```ts
{
  subscriptionPlanId: number;
  paymentMethod: PaymentMethod;
}
```

Các `paymentMethod` backend hiện có thể hỗ trợ theo báo cáo backend:

- `CASH`
- `BANK_TRANSFER`
- `MOMO`
- `VNPAY`
- `CREDIT_CARD`

Mapping đề xuất sau này:

- Gọi khi người dùng chọn gói và xác nhận đăng ký.
- Sau khi đăng ký thành công, gọi lại `GET /api/v1/customer-plans/me`.
- Nếu backend trả `status = PENDING`, UI không nên hiển thị như gói đã kích hoạt hoàn toàn.
- Nếu backend trả `latestTransactionId` hoặc `latestTransactionCode`, UI có thể dùng để hiển thị giao dịch hoặc chuyển tiếp sang bước thanh toán nếu backend hỗ trợ.

### 9.3. GET /api/v1/customer-plans/me

Trạng thái hiện tại trong frontend:

- Chưa tìm thấy trong frontend hiện tại.
- Trang Billing chưa gọi API này.
- Chưa có API client function tương ứng.
- Chưa có type `CustomerPlan`.
- Chưa hiển thị quota thật từ backend.

Mapping đề xuất sau này:

- Gọi khi user mở `/billing`.
- Dùng để hiển thị gói hiện tại, trạng thái và quota.
- Các field nên map lên UI:
  - `status`: trạng thái gói hiện tại.
  - `autoRenew`: có tự gia hạn hay không.
  - `startDate`: ngày bắt đầu, có thể `null`.
  - `endDate`: ngày kết thúc, có thể `null`.
  - `usedQuota`: số lượt đã dùng.
  - `remainingQuota`: số lượt còn lại.
  - `subscriptionPlan.maxQuota`: tổng quota của gói.
  - `subscriptionPlan.planName`: tên gói.
  - `subscriptionPlan.planType`: loại gói.
  - `subscriptionPlan.price`: giá.
  - `subscriptionPlan.durationDays`: thời hạn gói.
  - `latestTransactionId`: id giao dịch gần nhất nếu backend trả.
  - `latestTransactionCode`: mã giao dịch gần nhất nếu backend trả.

## 10. Các phần còn thiếu để frontend mua gói được

Các phần chưa có trong frontend hiện tại:

- Chưa có API client lấy danh sách gói.
- Chưa có API client lấy gói hiện tại.
- Chưa có API client đăng ký gói.
- Chưa có biến môi trường endpoint cho subscription/customer plan.
- Chưa có type `SubscriptionPlan`.
- Chưa có type `CustomerPlan`.
- Chưa có type request đăng ký gói.
- Chưa có type `PaymentMethod`.
- Chưa có UI danh sách gói.
- Chưa có UI chọn gói.
- Chưa có nút "Mua gói", "Đăng ký gói" hoặc "Nâng cấp" thật trong Billing.
- Chưa có modal xác nhận mua gói.
- Chưa có UI chọn phương thức thanh toán.
- Chưa có trạng thái loading khi đăng ký.
- Chưa có trạng thái success/error khi đăng ký.
- Chưa có xử lý unauthorized cho API subscription.
- Chưa có xử lý validation error từ backend.
- Chưa có hiển thị `PENDING`, `ACTIVE`, `EXPIRED` theo dữ liệu backend.
- Chưa có hiển thị quota thật theo `usedQuota`, `remainingQuota`, `maxQuota`.
- Chưa có refresh current plan sau khi đăng ký.
- Chưa có điều hướng sang payment gateway nếu chọn phương thức cần thanh toán online.

## 11. Đề xuất luồng UI cần bổ sung

Luồng đề xuất cho tương lai:

1. User mở `/billing`.
2. Frontend gọi `GET /api/v1/customer-plans/me`.
3. Frontend hiển thị gói hiện tại, trạng thái và quota.
4. Frontend gọi `GET /api/v1/subscription-plans`.
5. Frontend hiển thị danh sách gói có thể đăng ký.
6. User bấm "Mua gói", "Đăng ký gói" hoặc "Nâng cấp".
7. Frontend mở modal xác nhận.
8. User chọn hoặc xác nhận phương thức thanh toán.
9. Frontend gọi API đăng ký gói.
10. Frontend hiển thị thông báo thành công hoặc lỗi.
11. Frontend gọi lại `GET /api/v1/customer-plans/me`.
12. Frontend cập nhật gói hiện tại và quota.

Các trạng thái UI nên xử lý:

- Customer chưa có gói: hiển thị trạng thái trống và mời chọn gói.
- Current plan `PENDING`: hiển thị là đang chờ thanh toán hoặc chờ kích hoạt, không coi là gói đã active hoàn toàn.
- Current plan `ACTIVE`: hiển thị gói đang dùng và quota.
- Current plan hết hạn: hiển thị cảnh báo cần gia hạn hoặc mua lại.
- Customer đã dùng hết quota: hiển thị cảnh báo hết lượt và CTA nâng cấp/mua thêm.
- API trả unauthorized: điều hướng đăng nhập hoặc hiển thị thông báo phiên đăng nhập hết hạn.
- API trả validation error: hiển thị lỗi theo field hoặc thông báo ngắn dễ hiểu.
- Backend không chạy hoặc lỗi mạng: hiển thị thông báo không thể kết nối đến máy chủ.

## 12. Đề xuất API client cần bổ sung

Không triển khai trong task này. Đây chỉ là đề xuất cho lần tích hợp sau.

Nên tạo file theo cấu trúc hiện có, ví dụ:

- `src/api/subscriptionApi.ts`
- hoặc `src/services/subscription.service.ts`

Các function nên có:

```ts
getSubscriptionPlans(): Promise<ApiResponse<SubscriptionPlan[]>>

getMyCustomerPlan(): Promise<ApiResponse<CustomerPlan>>

subscribeCustomerPlan(
  payload: SubscribeCustomerPlanRequest,
): Promise<ApiResponse<CustomerPlan>>
```

Tên function frontend nên dùng `subscribeCustomerPlan` cho dễ hiểu. Tuy nhiên endpoint backend cần gọi đúng path thật. Nếu backend giữ lỗi chính tả `subcribe`, function vẫn có thể tên sạch là `subscribeCustomerPlan` nhưng URL phải trỏ đến `/api/v1/customer-plans/subcribe`.

Nếu project muốn đặt tên function bám sát backend path thì có thể dùng `subcribeCustomerPlan`, nhưng cách này dễ lan truyền lỗi chính tả vào frontend.

## 13. Đề xuất TypeScript interfaces

Đề xuất type cho lần tích hợp sau:

```ts
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface SubscriptionPlan {
  id: number;
  planName: string;
  planType: string;
  description: string;
  price: number;
  durationDays: number;
  maxQuota: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type CustomerPlanStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'EXPIRED'
  | 'CANCELLED';

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
  status: CustomerPlanStatus | string;
  autoRenew: boolean;
  startDate: string | null;
  endDate: string | null;
  usedQuota: number;
  remainingQuota: number;
  latestTransactionId: number | null;
  latestTransactionCode: string | null;
  createdAt: string;
  updatedAt: string;
  subscriptionPlan: SubscriptionPlan;
}
```

Ghi chú:

- `status` nên cho phép `string` dự phòng nếu backend bổ sung trạng thái mới.
- `latestTransactionId` và `latestTransactionCode` có thể là `null`.
- `startDate` và `endDate` có thể là `null`, đặc biệt khi gói đang `PENDING`.
- Không nên dùng lại `BillingUsage` để biểu diễn `CustomerPlan`, vì shape dữ liệu backend khác hoàn toàn.

## 14. Đề xuất biến môi trường

Frontend hiện tại là Vite và đã có:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Nên tái sử dụng `VITE_API_BASE_URL`, không hard-code `http://localhost:8080` trong component hoặc service.

Đề xuất bổ sung endpoint path trong `.env` và `.env.example` khi tích hợp thật:

```env
VITE_SUBSCRIPTION_PLANS_API=/api/v1/subscription-plans
VITE_CUSTOMER_PLAN_SUBSCRIBE_API=/api/v1/customer-plans/subscribe
VITE_CUSTOMER_PLAN_ME_API=/api/v1/customer-plans/me
```

Nếu backend xác nhận endpoint bắt buộc là `subcribe`, biến môi trường nên là:

```env
VITE_CUSTOMER_PLAN_SUBSCRIBE_API=/api/v1/customer-plans/subcribe
```

Không nên tạo thêm `VITE_BACKEND_API_BASE_URL` nếu `VITE_API_BASE_URL` vẫn là base URL backend chính của project, vì sẽ gây trùng trách nhiệm cấu hình.

## 15. Rủi ro khi tích hợp

Các rủi ro cần chú ý:

- Endpoint đăng ký đang có khác biệt giữa yêu cầu nghiệp vụ `subcribe` và tài liệu/source backend hiện có `subscribe`.
- Trang Billing hiện tại dùng dữ liệu giả trực tiếp, nên khi thay sang API thật cần xử lý loading/error để tránh màn hình trống.
- `BillingUsage` hiện dùng `used` và `limit`, trong khi backend trả `usedQuota`, `remainingQuota`, `subscriptionPlan.maxQuota`.
- Trạng thái backend là chữ hoa như `PENDING`, trong khi type `Status` frontend hiện tại chủ yếu là chữ thường.
- Gói `PENDING` không nên hiển thị như gói đã active.
- Nếu backend trả `startDate` hoặc `endDate` là `null`, UI cần format an toàn.
- Nếu user chưa đăng ký gói, backend có thể trả lỗi thay vì `data = null`; UI cần có empty state.
- Nếu thiếu token hoặc token hết hạn, API có thể trả unauthorized; UI cần chuyển về login hoặc hiển thị thông báo phiên hết hạn.
- Nút "Quản lý phương thức thanh toán" hiện chưa có hành vi, cần quyết định nó dùng để đổi payment method hay mở lịch sử thanh toán.
- Hóa đơn hiện là dữ liệu giả; nếu tích hợp hóa đơn thật có thể cần API riêng ngoài ba API subscription đã nêu.
- Nếu tích hợp thanh toán online như VNPAY, có thể cần thêm API tạo payment URL hoặc redirect sau khi đăng ký.

## 16. Kết luận

Frontend hiện tại có trang `/billing`, có menu "Thanh toán" và có giao diện hiển thị usage, gói hiện tại và hóa đơn. Tuy nhiên toàn bộ dữ liệu Billing đang là dữ liệu giả/tĩnh, không lấy từ backend subscription APIs.

Chưa tìm thấy trong frontend hiện tại nơi mua hoặc đăng ký gói thật. Chưa có màn hình chọn gói, chưa có nút đăng ký gói thật, chưa có modal xác nhận mua, chưa có API client cho subscription/customer plan và chưa có xử lý trạng thái gói/quota từ backend.

Để frontend mua gói thật được, bước tiếp theo nên là bổ sung API client dùng `VITE_API_BASE_URL`, thêm endpoint path qua `.env`, tạo type `SubscriptionPlan` và `CustomerPlan`, sau đó nâng cấp trang `/billing` để gọi `GET /api/v1/customer-plans/me`, `GET /api/v1/subscription-plans` và gọi API đăng ký gói sau khi user xác nhận. Trước khi làm, cần thống nhất chính xác endpoint đăng ký là `/api/v1/customer-plans/subcribe` hay `/api/v1/customer-plans/subscribe`.
