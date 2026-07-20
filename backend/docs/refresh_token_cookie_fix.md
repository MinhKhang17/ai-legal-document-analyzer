# Fix: Refresh token cookie bị chặn cross-site (F5 / share link bắt đăng nhập lại)

## Triệu chứng

- User đăng nhập thành công, nhưng F5 lại trang thì bị đá về `/login` với thông báo
  "Bạn cần đăng nhập để truy cập trang này."
- Console browser log `POST /api/v1/auth/refresh` trả về `400 (Bad Request)`.
- Môi trường tái hiện: frontend `https://ai-legal-document-analyzer-eta.vercel.app`,
  backend `https://studying-organisations-stayed-delete.trycloudflare.com` (2 domain khác nhau).

## Nguyên nhân gốc

Refresh token được lưu trong HttpOnly cookie, set tại `login`/`refresh` (token rotation) và
xóa tại `logout`. Cookie này được tạo bởi
[`JwtTokenProvider.createRefreshTokenCookie`](../src/main/java/com/analyzer/api/security/JwtTokenProvider.java)
với cấu hình cũ:

```java
cookie.setHttpOnly(true);
cookie.setSecure(false); // TODO: Set to true in production with HTTPS
cookie.setPath("/api/v1/auth");
```

Không set `SameSite` → trình duyệt áp dụng mặc định `SameSite=Lax`.

Frontend và backend nằm trên **2 domain khác nhau (cross-site)**. Cookie `SameSite=Lax`
**không được trình duyệt đính kèm** vào các request cross-site không phải GET
top-level navigation — bao gồm cả `fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })`
mà frontend gọi để lấy access token mới sau khi F5 (access token chỉ giữ trong bộ nhớ JS, mất khi reload).

Luồng lỗi:

1. Login thành công → backend `Set-Cookie` refresh token, nhưng do `SameSite=Lax` nên trình
   duyệt sẽ không gửi lại cookie này cho request cross-site tiếp theo.
2. F5 trang → access token trong JS memory mất → frontend gọi `POST /auth/refresh` để tự
   động đăng nhập lại bằng refresh token.
3. Request này là cross-site POST → cookie **không được đính kèm**.
4. `AuthServiceImpl.refreshToken()` không đọc được cookie → ném `RuntimeException("Refresh token không tìm thấy trong cookie")`
   → `GlobalExceptionHandler` map thành `400 Bad Request`.
5. Frontend coi 400 là chưa đăng nhập → redirect về `/login`.

Đây cũng là một phần nguyên nhân của lỗi "share link bắt đăng nhập lại dù đã đăng nhập":
người dùng vẫn đang có phiên hợp lệ nhưng cookie refresh không được gửi kèm nên mọi lần
reload/điều hướng cross-site đều bị coi là chưa xác thực.

### Luồng khác cũng bị ảnh hưởng: thanh toán VNPay

`PaymentTransactionController.handleVnPayReturn` (`GET /api/v1/payment-transactions/vnpay-return`)
xử lý callback từ VNPay rồi trả **302 redirect** (top-level navigation) sang trang frontend
`app.frontend.payment-result-url` (vd. `/billing/payment-result`). Trình duyệt load lại trang
frontend từ đầu — giống hệt F5 — nên frontend cũng phải gọi `POST /auth/refresh` để khôi phục
phiên đăng nhập, và bị chặn bởi đúng cơ chế `SameSite=Lax` nói trên. Kết quả: user thanh toán
xong, được redirect về trang kết quả, nhưng bị bắt đăng nhập lại.

Fix cookie ở trên (`SameSite=None; Secure=true`) giải quyết luôn trường hợp này, không cần sửa
gì thêm ở `PaymentTransactionController`. Bất kỳ luồng nào có kiểu "full page load rồi tự động
refresh session" (F5, redirect ngoài về, mở tab mới, share link) đều dùng chung cơ chế
`/auth/refresh` này nên đều được fix cùng lúc.

## Fix

`SameSite=None` là giá trị duy nhất cho phép trình duyệt gửi cookie trong request cross-site,
và theo spec, `SameSite=None` **bắt buộc phải đi kèm `Secure=true`** (chỉ gửi qua HTTPS).

File: [`JwtTokenProvider.java`](../src/main/java/com/analyzer/api/security/JwtTokenProvider.java)

```java
public Cookie createRefreshTokenCookie(String refreshToken) {
    Cookie cookie = new Cookie(refreshCookieName, refreshToken);
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setAttribute("SameSite", "None");
    cookie.setPath("/api/v1/auth");
    cookie.setMaxAge((int) (refreshTokenExpirationMs / 1000));
    return cookie;
}

public Cookie createDeleteRefreshTokenCookie() {
    Cookie cookie = new Cookie(refreshCookieName, "");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setAttribute("SameSite", "None");
    cookie.setPath("/api/v1/auth");
    cookie.setMaxAge(0);
    return cookie;
}
```

`Cookie.setAttribute(String, String)` khả dụng vì project dùng Servlet API 6.1
(kéo theo bởi `spring-boot-starter-parent` 4.0.6) — API cũ hơn (Servlet 6.0 trở xuống) không
có method này, phải build `Set-Cookie` header thủ công hoặc dùng `ResponseCookie` của Spring.

Cả 3 nơi gọi cookie đều dùng chung 2 method trên nên fix áp dụng toàn bộ luồng:

- `AuthServiceImpl.login()` — set cookie lần đầu.
- `AuthServiceImpl.refreshToken()` — set cookie mới khi rotate token.
- `AuthServiceImpl.logout()` — xóa cookie.

## Ảnh hưởng tới local dev

`Secure=true` không phá vỡ dev local qua `http://localhost:5173`
(cấu hình mặc định trong `application.yml` → `app.cors.allowed-origins`), vì Chrome/Firefox
coi `localhost` là "potentially trustworthy origin" ngay cả khi chạy qua HTTP, nên cookie
`Secure` vẫn được set/gửi bình thường trên localhost.

## Việc cần làm thêm (chưa nằm trong fix này)

- `GET /api/v1/shared/chat/{shareToken}` (`SharedChatSessionController`) hiện bắt buộc
  `@PreAuthorize("hasAnyRole('ADMIN','EXPERT')")` và không dùng `shareToken` làm cơ chế xác
  thực độc lập (dù entity `ConversationShare` đã có sẵn `expiresAt`/`revokedAt`). Đây là một
  bug khác, cần tách riêng luồng auth cho share link thay vì bắt buộc JWT + role.
- Xác nhận `CORS_ALLOWED_ORIGINS` trên môi trường production/staging trỏ đúng domain frontend
  thật (Vercel) để tránh bị CORS chặn song song với fix cookie này.
