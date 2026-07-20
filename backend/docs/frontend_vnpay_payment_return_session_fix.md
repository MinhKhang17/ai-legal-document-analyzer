# Frontend fix: giữ phiên đăng nhập sau khi VNPAY redirect về

## Hiện tượng

1. Customer đăng nhập và chọn mua gói.
2. Frontend chuyển trình duyệt sang trang VNPAY.
3. Thanh toán thành công, VNPAY gọi backend return URL.
4. Backend xử lý giao dịch rồi redirect sang `/billing/payment-result`.
5. Frontend tải lại từ đầu và đưa customer về `/login` thay vì hiển thị kết quả hoặc trang chủ.

Backend đã xử lý callback và cập nhật giao dịch thành công. Lỗi xảy ra khi frontend khôi phục
phiên đăng nhập sau một full-page navigation.

## Nguyên nhân

- Frontend hiện giữ access token trong biến JavaScript tại `src/services/authSession.ts`.
- `window.location.assign(paymentUrl)` trong `SubscribePlanPage.tsx` và `BillingPage.tsx` rời khỏi
  ứng dụng, nên access token trong memory bị mất.
- Khi VNPAY redirect về, `AppStoreProvider` gọi `POST /api/v1/auth/refresh` để khôi phục phiên.
- Nếu refresh cookie không được gửi do môi trường cross-site hoặc chính sách trình duyệt,
  `refreshSession()` gọi `clearAuthState()`.
- `/billing/payment-result` đang được bọc bởi `CustomerRoute`, vì vậy auth guard chuyển user về
  `/login` trước khi `PaymentResultPage` có thể hiển thị kết quả.

## Phạm vi sửa frontend

Các file chính:

- `src/services/authSession.ts`
- `src/store/AppStore.tsx`
- `src/pages/billing/SubscribePlanPage.tsx`
- `src/pages/billing/BillingPage.tsx`
- `src/routes/router.tsx`
- Có thể thêm test cho auth session và payment-result route.

Không sửa logic xác thực chữ ký VNPAY hoặc trạng thái giao dịch ở backend.

## Hướng triển khai đề xuất

### 1. Lưu access token dự phòng trong `sessionStorage`

`sessionStorage` tồn tại qua redirect/reload trong cùng tab nhưng tự bị xóa khi tab đóng. Không sử
dụng `localStorage` cho token mới.

Trong `authSession.ts`, thêm key riêng, ví dụ:

```ts
const SESSION_ACCESS_TOKEN_KEY = "lexiguard.sessionAccessToken";
```

Yêu cầu cho các hàm:

- `setAccessToken(token)`:
  - vẫn cập nhật biến access token trong memory;
  - nếu token hợp lệ thì lưu thêm vào `window.sessionStorage`.
- `getAccessToken()` / `getStoredAccessToken()`:
  - ưu tiên token trong memory;
  - nếu memory trống thì đọc từ `sessionStorage` và hydrate lại memory.
- `clearAccessToken()`:
  - xóa cả token memory, key cũ `accessToken` trong `localStorage` và key mới trong
    `sessionStorage`.
- Không log token ra console và không đưa token vào query string/return URL.

### 2. Khôi phục phiên theo đúng thứ tự

Trong `AppStore.refreshSession()`:

1. Đọc token từ memory/sessionStorage.
2. Nếu có token, gọi `GET /auth/me`.
3. Nếu `/auth/me` thành công, hydrate user và kết thúc.
4. Chỉ khi token thiếu hoặc trả `401/403` mới gọi `POST /auth/refresh` với
   `credentials: "include"`.
5. Chỉ xóa toàn bộ auth state khi cả access token và refresh token đều không dùng được.

Lỗi mạng tạm thời hoặc `5xx` không nên bị coi ngay là logout; nên hiển thị trạng thái thử lại.

### 3. Giữ route kết quả thanh toán

`/billing/payment-result` có thể tiếp tục dùng `CustomerRoute` sau khi bước 1 và 2 hoàn tất.
Auth guard phải chờ `isAuthReady === true`, không redirect trong lúc `refreshSession()` đang chạy.

Khi không khôi phục được phiên, redirect login cần giữ đầy đủ URL quay lại:

```ts
state: {
  from: `${location.pathname}${location.search}`,
}
```

Sau khi login thành công, frontend quay lại `state.from` để customer vẫn xem được kết quả giao dịch.

### 4. Điều hướng sau khi thanh toán thành công

Trong `PaymentResultPage`:

- Khi trạng thái là `SUCCESS`, hiển thị nút `Về trang chủ` hoặc `Xem gói hiện tại`.
- `Về trang chủ` điều hướng đến `/dashboard`.
- Không tự động chuyển về `/login` hoặc tự động logout.
- Vẫn tải giao dịch bằng API `GET /api/v1/payment-transactions/me`; không tin hoàn toàn các query
  parameter do trình duyệt nhận từ VNPAY.

### 5. Kiểm tra cấu hình deploy

Backend environment phải trỏ đúng frontend deploy:

```env
FRONTEND_PAYMENT_RESULT_URL=https://<frontend-domain>/billing/payment-result
```

Không để giá trị `http://localhost:5173/billing/payment-result` trên môi trường deploy.

Frontend phải gọi đúng backend HTTPS và giữ `credentials: "include"` cho login/refresh/logout.
CORS backend phải cho phép chính xác frontend origin và cho phép credentials.

## Checklist test frontend

### Luồng thành công

1. Đăng nhập customer trên frontend deploy.
2. Mua gói bằng VNPAY và hoàn thành thanh toán.
3. Xác nhận URL quay về đúng `/billing/payment-result?...` trên frontend deploy.
4. Xác nhận không bị chuyển sang `/login`.
5. Trang kết quả gọi được API giao dịch và hiển thị `SUCCESS`.
6. Nhấn `Về trang chủ` và xác nhận vào `/dashboard` với phiên đăng nhập còn nguyên.

### Các case cần test thêm

- Hủy thanh toán tại VNPAY rồi quay về.
- VNPAY trả giao dịch thất bại hoặc pending.
- Access token hết hạn nhưng refresh cookie còn hợp lệ.
- Refresh cookie bị chặn nhưng access token trong `sessionStorage` còn hợp lệ.
- Cả access token và refresh token đều hết hạn: chuyển login và sau login quay lại đúng payment URL.
- F5 trực tiếp tại `/billing/payment-result`.
- Mở payment result ở tab khác: không được dùng token từ tab gốc vì `sessionStorage` tách theo tab;
  trường hợp này phải dựa vào refresh cookie hoặc yêu cầu login rồi quay lại URL kết quả.

## Tiêu chí hoàn thành

- Thanh toán VNPAY thành công không làm customer mất phiên trong cùng tab.
- Payment result hiển thị dựa trên giao dịch lấy từ backend.
- Nếu bắt buộc login lại, frontend giữ query string và quay lại đúng trang kết quả sau login.
- Không lưu access token mới trong `localStorage`, URL, log hoặc analytics.
- Không thay đổi logic thanh toán và xác minh callback của backend.
