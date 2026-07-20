# History Code - Claude (AI Assistant Session Log)

> File này do Claude tự viết để đọc lại nhanh ở các phiên sau, không phải nhật ký thao tác chi tiết như 2 file kia — chỉ ghi **kiến trúc đã nắm được**, **quyết định đã chốt**, **việc đã làm**, và **bẫy/gotcha** cần nhớ. Ưu tiên súc tích, tra cứu nhanh hơn là tường thuật.

---

## Bức tranh tổng thể dự án

Monorepo `legal-rag-platform`: `frontend/` (React+Vite+TS, Context state, fetch thuần, token RAM/cookie), `backend/` (Spring Boot 4.0.6, Java 21, PostgreSQL, `com.analyzer.api`), `ai-service/` (Python FastAPI, tự xây GraphRAG trên Neo4j, Gemini LLM, không dùng LangChain).

## Convention backend cần tuân thủ khi code tiếp

- Layer: `entity` → `repository` (Spring Data JPA) → `service` (interface) + `service/impl` → `mapper` (MapStruct, `componentModel = SPRING`) → `controller`. Có subpackage domain riêng: `admin`, `ai`, `chatsession`, `contract`, `feedback`, `knowledge`, `lawyer`, `subscription` ở cả `controller/`, `service/`, `service/impl/`.
- DTO chia theo domain trong `dto/<domain>/`. Response chung: `ApiResponseDTO<T>` (record, `.success()/.created()/.accepted()/.error()`), `PageResponse<T>` cho phân trang.
- Exception: mỗi domain 1 package (`exception/common`, `workspace`, `chat`, `ai`, `validation`, `auth`), extends `RuntimeException`, đăng ký handler thủ công trong `GlobalExceptionHandler` (KHÔNG dùng `@ResponseStatus` tự động).
- ID: aggregate nghiệp vụ dùng `String` tự sinh `@PrePersist` (`ticket_xxx`, `chat_xxx`, `msg_xxx`, `feedback_xxx`...); bảng tra cứu (`User`, `Role`) dùng `Long` identity.
- Lấy current user: **không có helper chung** — mỗi controller tự copy-paste `getCurrentUserId()` từ `SecurityContextHolder` + `UserDetailsImpl`. Đây là convention cố ý của dự án, cứ theo mà làm, đừng tự ý refactor thành shared util.
- `@PreAuthorize` role-based: `CUSTOMER`, `ADMIN`, `EXPERT`. File storage local qua `app.storage.upload-root` (`Path.normalize()` + check `startsWith` chống path traversal).
- DB dev: `ddl-auto: create-drop` — sửa entity là schema tự tạo lại, không cần viết migration script.

## Đã implement trong phiên 2026-07-17

Toàn bộ 10 tính năng trong `backend/docs/new_features_implementation_plan.md` — chi tiết đầy đủ đã ghi ở đầu `historycode-nguyenhuudat.md` (entry ngày 2026-07-17), không lặp lại ở đây. Tóm tắt 1 dòng: đổi mật khẩu, admin tạo Expert + list Expert, email verification bắt buộc trước login, EmailService (Gmail SMTP thật), email báo ingest xong, admin/expert tải file gốc, feedback đánh giá AI, share chat session read-only cho admin/expert.

## Quyết định nghiệp vụ đã chốt với user (đừng hỏi lại)

- **Email verification CHẶN đăng nhập**: register → `active=false` → login 403 (`DisabledException`) tới khi verify qua `GET /auth/verify-email?token=`. User đã chọn phương án này thay vì phương án "không chặn" (đã hỏi qua AskUserQuestion, user chọn "Chặn đăng nhập tới khi xác thực").
- **SMTP dùng Gmail thật**, không phải placeholder. User tự điền `backend/.env` (không dán secret qua chat). Credentials hiện tại: `huudatnguyen165@gmail.com` (lưu ý: KHÁC với email liên hệ chính `huudatnguyen15@gmail.com` — có số 6 dư, để ý khi debug).

## Gotcha quan trọng — đã tốn thời gian debug, nhớ kỹ

**`spring-dotenv:5.1.0` KHÔNG nạp được file `.env` vào Spring Environment khi chạy với Spring Boot 4.0.6** — lỗi âm thầm, không log gì cả. Toàn bộ config trước đó chạy bằng default cứng trong `application.yml`; chỉ "qua mặt" được vì giá trị `.env` cho DB trùng hệt default. Lộ ra khi `MAIL_FROM`/`SMTP_USERNAME` (không có default) rỗng dù `.env` đã điền đúng.

**Đã fix**: thêm vào `application.yml` (đầu block `spring:`):
```yaml
spring:
  config:
    import: "optional:file:.env[.properties]"
```
Đây là cơ chế native Spring Boot (ép đọc `.env` theo format properties), không phụ thuộc `spring-dotenv` nữa. Đã verify: restart chỉ dựa `.env` (không set OS env var tay) → gửi email thành công, log hết cảnh báo "Mail is not configured".

→ Nếu sau này thấy config nào đó trong `.env` "không có tác dụng" dù đã điền đúng, **kiểm tra ngay `spring.config.import` có còn nguyên trong `application.yml` không** trước khi nghi ngờ chỗ khác.

## Trạng thái cuối phiên

Backend đã bị **tắt theo yêu cầu user** (dừng process `mvnw spring-boot:run`). Muốn chạy lại: `cd backend && .\mvnw.cmd spring-boot:run` (Postgres local port 5432 phải đang chạy sẵn — không phải qua Docker, dự án đang dùng Postgres cài local của user).

## File liên quan cần đọc nếu tiếp tục việc này

- `backend/docs/new_features_implementation_plan.md` — đặc tả gốc 10 tính năng (đã implement xong hết).
- `backend/historycode/historycode-nguyenhuudat.md` — nhật ký chi tiết đầy đủ (entry đầu file, ngày mới nhất).
- `backend/.env` — chứa SMTP thật, KHÔNG được đọc to/in ra lại nội dung password trong response nếu không cần thiết.
