# Kế Hoạch Triển Khai Chi Tiết Các Tính Năng Bổ Sung (Backend)

Tài liệu này mô tả chi tiết phương án thiết kế cơ sở dữ liệu, các API endpoints, cấu trúc dịch vụ và phân quyền để triển khai 10 tính năng mới được yêu cầu cho phần Backend.

---

## Danh Sách Tính Năng Cần Triển Khai

1. **Admin tạo tài khoản cho Expert và Expert có thể đổi mật khẩu**
2. **Chức năng đổi mật khẩu cho User và Expert**
3. **Chức năng xác nhận email chính chủ cho User**
4. **Expert đăng ký/được tạo phải có thông tin Chuyên ngành (specialty) & Lĩnh vực luật (legalDomain) phục vụ hiển thị khi Admin phân công (assign)**
5. **Lưu trữ tài liệu upload vật lý & Cho phép Admin tải lại tài liệu gốc**
6. **Gửi email thông báo khi tài liệu được ingest (xử lý RAG) thành công**
7. **Khách hàng đánh giá (rating/comment) câu trả lời của LLM & Admin có thể xem được**
8. **Chia sẻ liên kết cuộc hội thoại (Share Chat Session) bằng token**
9. **Liên kết chia sẻ chỉ cho phép xem lịch sử cuộc hội thoại (Read-only), không hỗ trợ gửi thêm prompt**
10. **Chỉ Admin và Expert có quyền truy cập vào liên kết chia sẻ cuộc hội thoại này**

---

## 1. Thiết Kế Cơ Sở Dữ Liệu (Database Changes)

Cần cập nhật các bảng dữ liệu hiện có trong PostgreSQL để hỗ trợ lưu trữ thông tin mới:

### Bảng `users` (Cập nhật thực thể `User.java`)
*   `email_verified` (Boolean, default: `false`): Trạng thái xác thực email chính chủ.
*   `email_verification_token` (String, nullable): Token bảo mật gửi qua email để xác thực tài khoản.
*   `email_verification_token_expiry` (Timestamp, nullable): Thời gian hết hạn của token xác thực.
*   `specialty` (String, nullable): Chuyên ngành chính của Expert (ví dụ: Tranh chấp hợp đồng, Sở hữu trí tuệ).
*   `legal_domain` (String, nullable): Ngành luật (ví dụ: Luật Dân sự, Luật Doanh nghiệp).
*   `description` (Text, nullable): Mô tả chi tiết về kinh nghiệm làm việc của Expert.
*   `must_change_password` (Boolean, default: `false`): `true` khi tài khoản đang dùng mật khẩu tạm thời (Admin tạo/reset) và chưa tự đổi.
*   `password_reset_deadline` (Timestamp, nullable): Hạn chót phải đổi mật khẩu; quá hạn mà `must_change_password` vẫn `true` thì tài khoản bị khóa tự động.

### Bảng `chat_message_feedbacks` (Thực thể `ChatMessageFeedback.java`)
Bảng mới để quản lý đánh giá thumbs up/down của người dùng đối với các phản hồi của AI:
*   `id` (VARCHAR(50), Primary Key)
*   `chat_message_id` (VARCHAR(50), Foreign Key đến `chat_messages.id`, **UNIQUE** — mỗi tin nhắn chỉ có 1 đánh giá, đánh giá lại sẽ upsert)
*   `rating` (VARCHAR, lưu enum `FeedbackRating` dạng String): `THUMBS_UP` hoặc `THUMBS_DOWN`
*   `reasons` (Text, nullable): Danh sách enum `FeedbackReason` nối bằng dấu phẩy (vd `"INCORRECT,INCOMPLETE"`), chỉ có giá trị khi `rating = THUMBS_DOWN`
*   `comment` (Text, nullable): Bình luận phản hồi chi tiết từ user, dùng thêm khi chọn lý do `OTHER`
*   `created_at` / `updated_at` (Timestamp): Thời điểm gửi/cập nhật đánh giá

### Bảng `chat_sessions` (Cập nhật thực thể `ChatSession.java`)
Thêm các cột phục vụ tính năng chia sẻ cuộc hội thoại:
*   `is_shared` (Boolean, default: `false`): Trạng thái chia sẻ công khai nội bộ.
*   `share_token` (VARCHAR(100), nullable, unique): Token định danh phiên chia sẻ ngẫu nhiên (UUID).
*   `shared_at` (Timestamp, nullable): Thời điểm tạo liên kết chia sẻ.

---

## 2. Thiết Kế Các API Endpoints & Logic Xử Lý

### Flow 1: Xác Thực & Đổi Mật Khẩu (Tính năng 1, 2, 3)

#### API Đổi Mật Khẩu (Dành cho cả User và Expert)
*   **Endpoint:** `POST /api/v1/users/change-password`
*   **Phân quyền:** `@PreAuthorize("isAuthenticated()")`
*   **Payload:**
    ```json
    {
      "oldPassword": "mật_khẩu_cũ",
      "newPassword": "mật_khẩu_mới_an_toàn",
      "confirmNewPassword": "mật_khẩu_mới_an_toàn"
    }
    ```
*   **Logic:**
    1. Lấy thông tin user hiện tại qua token đăng nhập.
    2. Kiểm tra khớp mật khẩu cũ (`passwordEncoder.matches`).
    3. Kiểm tra độ mạnh mật khẩu mới và xác nhận mật khẩu mới.
    4. Mã hóa mật khẩu mới và cập nhật vào DB.

#### API Admin Tạo Tài Khoản Expert
*   **Endpoint:** `POST /api/v1/admin/users/experts`
*   **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Payload:** Admin **không nhập password** — hệ thống tự gán mật khẩu mặc định.
    ```json
    {
      "firstName": "Nguyễn",
      "lastName": "Văn B",
      "email": "expert.b@example.com",
      "specialty": "Tư vấn doanh nghiệp",
      "legalDomain": "Luật Thương mại",
      "description": "5 năm kinh nghiệm giải quyết tranh chấp kinh doanh"
    }
    ```
*   **Logic** ([UserServiceImpl.java:createExpertUser](../src/main/java/com/analyzer/api/service/impl/UserServiceImpl.java)):
    1. Kiểm tra email đã tồn tại hay chưa (chuẩn hóa `trim().toLowerCase()`).
    2. Mật khẩu luôn được gán cứng là `12345678` (mã hóa bcrypt trước khi lưu), tạo user mới với vai trò `EXPERT`.
    3. Đặt `email_verified = true` ngay do Admin tạo chủ động; đặt `must_change_password = true` và `password_reset_deadline = now() + 7 ngày`.
    4. Gửi email cho Expert (`EmailService.sendExpertAccountCreatedEmailAsync`, chạy `@Async`) chứa: email đăng nhập, mật khẩu tạm thời `12345678`, link `/login`, và cảnh báo phải đổi mật khẩu trong vòng 7 ngày kẻo bị khóa.
    5. Trả về thông tin Expert vừa tạo.
*   **Cơ chế tự khóa nếu quá hạn đổi mật khẩu** ([ExpertPasswordDeadlineScheduler.java](../src/main/java/com/analyzer/api/scheduler/ExpertPasswordDeadlineScheduler.java)):
    - Job chạy mỗi giờ (`@Scheduled(fixedRate = 3_600_000)`), tìm mọi user có `must_change_password = true`, `active = true` và `password_reset_deadline` đã qua → đặt `active = false` (khóa tài khoản).
    - Khi Expert tự đổi mật khẩu thành công qua `POST /api/v1/users/change-password`, backend tự xóa cờ `must_change_password` và `password_reset_deadline` — thoát khỏi diện bị khóa.
    - Tài khoản bị khóa sẽ nhận lỗi `403` khi login (tái sử dụng cơ chế `DisabledException` sẵn có của Spring Security cho `active = false`).

#### API Admin Gửi Lại Thông Tin Đăng Nhập / Mở Khóa Expert
*   **Endpoint:** `POST /api/v1/admin/users/experts/resend-activation`
*   **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Payload:**
    ```json
    { "email": "expert.b@example.com" }
    ```
*   **Logic:** Tìm Expert theo email (404 nếu không có, 403 nếu không phải role `EXPERT`) → reset mật khẩu về `12345678`, đặt lại `must_change_password = true`, `password_reset_deadline = now() + 7 ngày`, `active = true` (mở khóa nếu đang bị khóa) → gửi lại email thông tin đăng nhập. Dùng cho cả 2 trường hợp: Expert quên mật khẩu, hoặc tài khoản đã bị khóa do quá hạn.

#### API Xác Nhận Email User
*   **Endpoint:** `GET /api/v1/auth/verify-email?token=xxx`
*   **Phân quyền:** Public (Không yêu cầu đăng nhập)
*   **Logic:**
    1. Kiểm tra token trong DB xem có tồn tại và còn hạn hay không.
    2. Nếu hợp lệ, cập nhật `email_verified = true` và kích hoạt tài khoản `active = true`.
    3. Trả về thông báo xác thực thành công.
*   *Lưu ý:* Khi User đăng ký ở API `/register`, hệ thống sẽ tự động tạo mã token ngẫu nhiên, lưu vào DB cùng hạn sử dụng (ví dụ: 24h) và gửi link kèm token này tới hòm thư của User. Trạng thái `active` sẽ ở mức `false` cho tới khi xác thực (hoặc tùy quy tắc nghiệp vụ).

---

### Flow 2: Phân Công Yêu Cầu Tư Vấn & Thông Tin Expert (Tính năng 4)

#### API Lấy Danh Sách Expert Phục Vụ Assign
*   **Endpoint:** `GET /api/v1/admin/users/experts`
*   **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Logic:**
    1. Lấy tất cả người dùng hoạt động (`active = true`) có vai trò `EXPERT`.
    2. Trả về thông tin chi tiết bao gồm `id`, `firstName`, `lastName`, `email`, `specialty`, `legalDomain` và `description`.
    3. *Thay đổi:* Frontend sẽ chuyển sang gọi API này thay vì dùng API chung `/users` rồi tự lọc ở client để tối ưu hiệu năng và hiển thị đầy đủ thông tin chuyên môn của Expert.

---

### Flow 3: Lưu Trữ File & Tải Lại Tài Liệu Gốc (Tính năng 5)

#### API Admin/Expert Tải File Gốc Của Ticket
*   **Endpoint:** `GET /api/v1/admin/documents/{documentId}/download`
*   **Phân quyền:** `@PreAuthorize("hasAnyRole('ADMIN', 'EXPERT')")`
*   **Logic:**
    1. Tìm thông tin tài liệu trong bảng `documents` qua `documentId`.
    2. Đọc file vật lý tại `filePath` lưu trên ổ đĩa server.
    3. Trả về dữ liệu dưới dạng Binary Stream (`org.springframework.core.io.Resource`).
    4. Đặt header `Content-Disposition: attachment; filename="tên_file_gốc"`.

---

### Flow 4: Gửi Email Thông Báo Ingest Thành Công (Tính năng 6)

#### Cấu Hình Mail & Service
*   **Dependency:** Thêm `spring-boot-starter-mail` vào `pom.xml`.
*   **Configuration:** Cấu hình SMTP (Gmail, SendGrid hoặc Mailgun) trong `application.properties`.
*   **Logic Tích Hợp:**
    1. Khi Python AI service phân tích xong tài liệu và gọi callback API `/api/internal/documents/{documentId}/processing-result` với trạng thái `READY`.
    2. Backend cập nhật trạng thái document thành `READY`.
    3. Gọi `EmailService.sendIngestionSuccessEmail(userEmail, originalFileName)` chạy bất đồng bộ (`@Async`) để tránh chặn tiến trình chính.

---

### Flow 5: User Đánh Giá Phản Hồi LLM (Tính năng 7)

> **Đính chính:** Bản trước của tài liệu này từng ghi nhầm rằng API `POST /api/v1/chat-messages/{messageId}/feedback` không tồn tại và bị thay thế hoàn toàn bởi hệ thống Survey/AI Report bên dưới. Thực tế **có 2 hệ thống feedback độc lập cùng tồn tại** trong backend, phục vụ 2 mục đích khác nhau:
> 1. **Chat Message Rating** (`ChatMessageController.java` + `AdminChatFeedbackController.java`) — đúng là tính năng 7 (User đánh giá câu trả lời AI, Admin xem được). Vừa được cập nhật sang model thumbs up/down + preset reason (xem chi tiết ngay bên dưới).
> 2. **Feedback Survey / AI Report** (`FeedbackController.java`) — một hệ thống khác, tổng quát hơn, dùng cho khảo sát do Admin định nghĩa và báo cáo lỗi có workflow xử lý. Không liên quan trực tiếp đến việc rate từng câu trả lời AI. FE (`feedback.service.ts`, `AdminFeedbackPage.tsx`) đã tích hợp đúng phần này rồi, giữ nguyên không đổi.

#### API User Đánh Giá Tin Nhắn AI (Thumbs Up/Down)
*   **Endpoint:** `POST /api/v1/chat-messages/{messageId}/feedback`
*   **Phân quyền:** `@PreAuthorize("hasRole('CUSTOMER')")`
*   **Payload:**
    ```json
    {
      "rating": "THUMBS_DOWN",
      "reasons": ["INCORRECT", "INCOMPLETE"],
      "comment": "Câu trả lời trích dẫn sai điều luật"
    }
    ```
    *   `rating`: enum `FeedbackRating` — `THUMBS_UP` | `THUMBS_DOWN` (bắt buộc).
    *   `reasons`: danh sách enum `FeedbackReason` — `INCORRECT`, `WRONG_CITATION`, `INCOMPLETE`, `NOT_HELPFUL`, `POOR_PHRASING`, `OTHER`. Chỉ có ý nghĩa khi `rating = THUMBS_DOWN`; FE hiển thị các chip này (giống ChatGPT) khi user bấm thumbs down. Nếu `rating = THUMBS_UP`, backend tự bỏ qua `reasons` gửi lên (luôn lưu `null`).
    *   `comment`: optional, tối đa 2000 ký tự, dùng khi chọn lý do `OTHER` hoặc muốn ghi chú thêm.
*   **Logic** ([ChatMessageServiceImpl.java:submitFeedback](../src/main/java/com/analyzer/api/service/impl/ChatMessageServiceImpl.java)):
    1. Tin nhắn `messageId` phải tồn tại và có `role = ASSISTANT` — không cho đánh giá tin nhắn của chính user gửi lên.
    2. Chỉ chủ sở hữu cuộc hội thoại (`chatMessage.getUser().getId() == currentUserId`) mới được đánh giá — sai sẽ trả `403`.
    3. Upsert theo `chatMessageId` (unique constraint `uk_chat_message_feedback_message`): mỗi tin nhắn chỉ có **1** bản ghi đánh giá; đánh giá lại sẽ ghi đè bản cũ (đổi ý từ up sang down vẫn được).
    4. Vì `chatMessageId` gắn liền với `chatMessage.getUser()`, hệ thống luôn biết chính xác ai là người đánh giá — không cần cột `submittedById` riêng.

#### API Admin Xem Danh Sách Đánh Giá Tin Nhắn AI
*   **Endpoint:** `GET /api/v1/admin/chat-messages/feedback?rating={THUMBS_UP|THUMBS_DOWN}&page=&size=`
*   **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Logic:** Trả về danh sách phân trang, mỗi item gồm `chatMessageId`, đoạn trích nội dung tin nhắn AI, `rating`, `reasons`, `comment`, và **`submittedById`/`submittedByName`** (danh tính khách hàng đã đánh giá, lấy từ `chatMessage.getUser()`).

---

#### API Admin Tạo/Sửa/Xem Feedback Survey
*   **Endpoint:** `POST /api/v1/admin/feedback/surveys`, `PUT /api/v1/admin/feedback/surveys/{id}`, `GET /api/v1/admin/feedback/surveys`
*   **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Logic:** CRUD cho khảo sát feedback do Admin định nghĩa (câu hỏi, cấu trúc khảo sát), phân trang khi list.

#### API User Gửi Phản Hồi Khảo Sát
*   **Endpoint:** `POST /api/v1/feedback/surveys/{id}/responses`
*   **Phân quyền:** `@PreAuthorize("isAuthenticated()")`
*   **Payload:** theo `SubmitSurveyResponseRequest` (đáp án cho từng câu hỏi của survey).

#### API Gửi Báo Cáo Về Câu Trả Lời AI (Thay thế cho "đánh giá rating")
*   **Endpoint:** `POST /api/v1/feedback/ai-reports`
*   **Phân quyền:** `@PreAuthorize("isAuthenticated()")`
*   **Payload:**
    ```json
    {
      "reportType": "INCORRECT_ANSWER",
      "sourceType": "CHAT_MESSAGE",
      "sourceReferenceId": "<chatMessageId>",
      "summary": "Câu trả lời trích dẫn sai điều luật",
      "detailsJson": "{...}",
      "submittedById": 12,
      "workspaceId": 3
    }
    ```
*   **Logic:**
    1. Lưu bản ghi `AiReport` với trạng thái mặc định `OPEN` (enum `AiReportStatus`: `OPEN` → ... → `RESOLVED`).
    2. `sourceType`/`sourceReferenceId` là cặp giá trị tự do (không ràng buộc khóa ngoại cứng tới `chat_messages`), cho phép tái sử dụng cho các loại nguồn khác trong tương lai.

#### API Admin Xem Danh Sách/Chi Tiết Báo Cáo AI
*   **Endpoint:** `GET /api/v1/admin/feedback/ai-reports`, `GET /api/v1/admin/feedback/ai-reports/{id}`
*   **Phân quyền:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Logic:** Truy vấn danh sách/chi tiết `AiReport`, hỗ trợ phân trang.

---

### Flow 6: Chia Sẻ Phiên Chat Cho Admin/Expert (Tính năng 8, 9, 10)

#### API Tạo Link Chia Sẻ Phiên Chat
*   **Endpoint:** `POST /api/v1/chat-sessions/{chatSessionId}/share`
*   **Phân quyền:** `@PreAuthorize("hasRole('CUSTOMER')")` (Chỉ chủ sở hữu phiên chat mới được chia sẻ)
*   **Logic:**
    1. Tìm phiên chat của user.
    2. Sinh `share_token` (UUID) ngẫu nhiên và lưu trạng thái `is_shared = true`.
    3. Trả về đường dẫn chia sẻ (ví dụ: `/shared/chat/{share_token}`).

#### API Lấy Thông Tin Phiên Chat Chia Sẻ (Xem Chi Tiết)
*   **Endpoint:** `GET /api/v1/shared/chat/{shareToken}`
*   **Phân quyền:** `@PreAuthorize("hasAnyRole('ADMIN', 'EXPERT')")`
*   *Lưu ý quan trọng:* API này **chỉ cho phép đọc lịch sử tin nhắn** và thông tin session.
*   Mọi API gửi tin nhắn (như `POST /api/v1/chat-sessions/{chatSessionId}/messages`) vẫn chặn nghiêm ngặt và không hỗ trợ thực thi thông qua link chia sẻ. Do đó Expert/Admin chỉ có thể đọc nội dung hội thoại chứ không thể chat tiếp thay cho user.

---

## 3. Kế Hoạch Kiểm Thử (Verification Plan)

### Kiểm Thử Tự Động (Unit/Integration Tests)
*   **UserServiceTest:** Kiểm thử luồng đổi mật khẩu và luồng Admin tạo tài khoản Expert (đảm bảo mật khẩu lưu trong DB được mã hóa bcrypt).
*   **DocumentDownloadTest:** Kiểm tra API tải file gốc đảm bảo trả về đúng mã trạng thái HTTP 200, Content-Type, và Header Content-Disposition.
*   **ChatSessionShareTest:** Kiểm thử API truy cập qua `shareToken` với vai trò `EXPERT`/`ADMIN` (thành công) và vai trò `CUSTOMER` khác (thất bại).
*   **EmailVerificationTest:** Kiểm thử gửi email xác thực bằng mock mail server (GreenMail).

---

## 4. Các Edge Cases & Phương Án Xử Lý (Edge Cases & Resolutions)

Dưới đây là các trường hợp biên và ngoại lệ có nguy cơ gây lỗi API hoặc rò rỉ dữ liệu cần đặc biệt lưu ý khi lập trình backend:

### 4.1. Đổi mật khẩu (`/users/change-password`)
*   **Mật khẩu trống trong DB (đăng nhập bằng OAuth2):** Nếu người dùng liên kết tài khoản qua Google/Facebook mà chưa đặt mật khẩu, hàm `passwordEncoder.matches` sẽ luôn trả về sai.
    *   *Phương án:* Kiểm tra nếu mật khẩu hiện tại trong DB là `null` hoặc rỗng thì cho phép thiết lập mật khẩu mới trực tiếp mà không cần so khớp mật khẩu cũ.
*   **Mật khẩu mới trùng mật khẩu cũ:** Người dùng chọn đổi sang mật khẩu giống hệt.
    *   *Phương án:* Kiểm tra `!passwordEncoder.matches(newPassword, oldPassword)` trước khi cập nhật.
*   **Tấn công gán ID giả mạo:** Hacker gửi payload chứa `userId` của nạn nhân.
    *   *Phương án:* Lấy ID tài khoản thực hiện yêu cầu trực tiếp từ `AuthenticationContext` của Spring Security, tuyệt đối không nhận từ request body hay URL path.

### 4.2. Admin tạo Expert (`/admin/users/experts`)
*   **Trùng email do định dạng hoa/thường:** Admin nhập email có ký tự hoa (ví dụ: `Expert.A@example.com`) trùng với tài khoản sẵn có `expert.a@example.com`.
    *   *Phương án:* Luôn thực hiện `.trim().toLowerCase()` trên email trước khi kiểm tra tồn tại (`existsByEmail`) và lưu xuống DB để tránh lỗi Unique Constraint từ PostgreSQL.
*   **Mật khẩu mặc định `12345678` là cố định, ai cũng đoán được:** Rủi ro bảo mật nếu Expert không đổi mật khẩu kịp thời và có kẻ khác biết email của họ.
    *   *Phương án:* Đây chính là lý do bắt buộc `must_change_password` + `password_reset_deadline` (7 ngày) + tự động khóa nếu quá hạn — không để mật khẩu mặc định tồn tại vô thời hạn.
*   **Email gửi thông tin đăng nhập bị lỗi/không tới nơi (SMTP down):** Expert không nhận được mật khẩu tạm thời, không thể đăng nhập, và cũng không tự đổi được mật khẩu trước hạn 7 ngày.
    *   *Phương án:* Admin dùng API `POST /admin/users/experts/resend-activation` để gửi lại (reset lại `password_reset_deadline` một lượt 7 ngày mới), không cần tạo lại tài khoản.
*   **Tài khoản đã bị khóa do quá hạn, Expert muốn được cấp lại quyền truy cập:** Admin cần mở khóa mà không phải xóa/tạo lại account (mất lịch sử ticket đã gán).
    *   *Phương án:* API `resend-activation` cũng đặt `active = true`, dùng chung cho cả 2 tình huống "gửi lại vì lỗi email" và "mở khóa vì quá hạn".

### 4.3. Xác thực Email (`/auth/verify-email`)
*   **Người dùng nhấp link nhiều lần hoặc Web Scanner quét link tự động:** Phần mềm quét email kiểm tra link trước, làm token bị xóa/đánh dấu đã sử dụng. Khi người dùng bấm thật sẽ gặp lỗi.
    *   *Phương án:* Nếu tài khoản tương ứng với token đã được xác thực trước đó (`email_verified = true`), lập tức trả về thông báo xác thực thành công thay vì ném lỗi token không hợp lệ.
*   **Token hết hạn:** Token đã hết hạn sử dụng nhưng vẫn cố kích hoạt.
    *   *Phương án:* Kiểm tra thời gian hết hạn của token, nếu quá hạn thì thông báo lỗi và cung cấp nút "Gửi lại email xác thực".

### 4.4. Tải file tài liệu gốc (`/admin/documents/{documentId}/download`)
*   **Tấn công Path Traversal:** Tên file chứa ký tự điều hướng thư mục (ví dụ: `../../etc/passwd`).
    *   *Phương án:* Sử dụng `Path.normalize()` và kiểm tra đường dẫn tuyệt đối đảm bảo tệp tin tải xuống phải nằm trong thư mục gốc được chỉ định (`uploadRoot`).
*   **Mất file vật lý trên ổ đĩa server:** DB có bản ghi nhưng tệp tin đã bị xóa/mất trên server.
    *   *Phương án:* Sử dụng `Files.exists(filePath)` trước khi gửi file, nếu tệp tin không tồn tại, trả về lỗi HTTP 404 (Resource Not Found) kèm thông báo rõ ràng thay vì để server báo lỗi 500.
*   **Rò rỉ tài liệu giữa các Expert:** Expert A đoán `documentId` của Expert B để tải tài liệu nhạy cảm của khách hàng khác.
    *   *Phương án:* Phân quyền chặt chẽ: `ADMIN` được tải mọi file; `EXPERT` chỉ được tải file đính kèm của Ticket mà mình đang được phân công (`ticket.assignedLawyerId == currentUserId`).

### 4.5. Gửi email Ingest thành công
*   **Lỗi kết nối SMTP Server:** Mail server bị ngắt kết nối hoặc cấu hình sai tài khoản gửi thư.
    *   *Phương án:* Đặt tác vụ gửi mail chạy bất đồng bộ với annotation `@Async` và bọc trong khối `try-catch` riêng biệt để lỗi gửi mail không gây rollback giao dịch cập nhật trạng thái Document thành `READY` trong luồng chính.

### 4.6a. Đánh giá tin nhắn AI (`/chat-messages/{messageId}/feedback`)
*   **Đánh giá tin nhắn nhiều lần:** User đổi ý, bấm lại thumbs khác hoặc chọn lại reasons.
    *   *Phương án:* Upsert theo unique constraint `chat_message_id` — bản ghi cũ bị ghi đè, không tạo dòng mới.
*   **Gửi `reasons` kèm `THUMBS_UP`:** FE gửi nhầm reasons dù rating là up.
    *   *Phương án:* Backend luôn ép `reasons = null` khi `rating != THUMBS_DOWN`, bất kể client gửi gì lên.
*   **Đánh giá tin nhắn không thuộc quyền sở hữu:** User cố tình đánh giá tin nhắn trong phiên chat của user khác.
    *   *Phương án:* Kiểm tra `chatMessage.getUser().getId().equals(currentUserId)`, sai thì trả `403`.
*   **Đánh giá tin nhắn của chính User:** User đánh giá tin nhắn do chính mình gửi đi thay vì câu trả lời của AI.
    *   *Phương án:* Chỉ cho phép đánh giá nếu tin nhắn có `role == ChatMessageRole.ASSISTANT`.

### 4.6b. Báo cáo câu trả lời AI (`/feedback/ai-reports`)
*   **Spam báo cáo liên tiếp trên cùng một tin nhắn:** Người dùng gửi nhiều báo cáo cho cùng một `sourceReferenceId`.
    *   *Phương án:* Vì `sourceType`/`sourceReferenceId` là cặp giá trị tự do không có ràng buộc Unique ở tầng DB, cần validate ở service (hoặc bổ sung constraint) nếu muốn chặn/gộp báo cáo trùng lặp cho cùng nguồn.
*   **Báo cáo tin nhắn không thuộc quyền sở hữu:** User cố tình báo cáo tin nhắn trong phiên chat của user khác.
    *   *Phương án:* Khi `sourceType = CHAT_MESSAGE`, kiểm tra quyền sở hữu phiên chat trước khi lưu báo cáo (`chatMessage.getChatSession().getUser().getId().equals(currentUserId)`).
*   **Báo cáo tin nhắn của chính User:** User báo cáo tin nhắn do chính mình gửi đi thay vì câu trả lời của AI.
    *   *Phương án:* Chỉ cho phép gửi báo cáo nếu tin nhắn nguồn có `role == ChatMessageRole.ASSISTANT`.

### 4.7. Chia sẻ phiên chat (`/shared/chat/{shareToken}`)
*   **Truy cập liên kết đã hủy chia sẻ:** Phiên chat được đổi sang trạng thái dừng chia sẻ (`is_shared = false`).
    *   *Phương án:* Truy vấn DB phải kiểm tra đồng thời cả hai điều kiện: `share_token` khớp và `is_shared == true`.
*   **Rò rỉ thông tin ra ngoài Internet:** Người không đăng nhập hoặc người dùng thường (`CUSTOMER` khác) cố tình mở link chia sẻ.
    *   *Phương án:* API bắt buộc phải chặn phân quyền `@PreAuthorize("hasAnyRole('ADMIN', 'EXPERT')")` để đảm bảo chỉ quản trị viên hoặc chuyên gia được cấp quyền mới có thể đọc dữ liệu cuộc trò chuyện.

