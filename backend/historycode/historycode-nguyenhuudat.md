# History Code - Nguyen Huu Dat

**Date:** 2026-07-22 (Ngày 22 tháng 7 năm 2026)

## Tasks Completed:
- **Rà soát toàn diện `backend/docs/Plan_Flow_Test_Cases.md` đối chiếu source code thật** (không sửa code ở vòng đầu): xác nhận cả 4 rủi ro P0 ở mục 4 (hai API current plan không đồng nhất, scheduled downgrade xử lý khác nhau giữa service, VNPay return/IPN race, quota bypass bằng concurrency) đều có cơ chế xử lý; phát hiện mục 5 (snapshot/version plan) và phần lớn mục 19 (quyết định nghiệp vụ) **chưa được implement**.
- **Chốt và code 4 quyết định nghiệp vụ ở mục 19 theo lựa chọn của leader**:
  - **Cancel plan chuyển sang graceful** (giữ quyền đến hết chu kỳ đã trả, chỉ tắt auto-renew): viết lại `CustomerPlanServiceImpl.cancelPlan()` để `scheduleSubscriptionPlan = FREE` + `planChangeEffectiveAt = endDate` thay vì set `CANCELLED` ngay; tách riêng `cancelPlanAndActivateFree()` (hủy ngay lập tức) chỉ dùng cho luồng refund (`RefundServiceImpl`) — nơi cắt quyền ngay là đúng vì tiền đã hoàn.
  - **Usage "theo tháng" đổi sang chu kỳ CustomerPlan** (`billingCycleStartAt`/`billingCycleEndAt`) thay vì tháng lịch, trong `SubscriptionQuotaServiceImpl.getCurrentUsage()`.
  - **Snapshot giá/quota tại thời điểm subscribe()`**: thêm entity mới `CustomerPlanSnapshotHelper` (`service/support/`), 13 cột `*Snapshot` trên `CustomerPlan` (migration `V20260722_01`), áp dụng ở `subscribe()`, khi scheduled downgrade có hiệu lực (`CustomerPlanExpiryHelper`), và khi refund tạo lại gói Free — đảm bảo Admin sửa giá/quota gói master không ảnh hưởng ngược user đang trong chu kỳ đã mua.
  - **Analysis quota chỉ tính document đã phân tích thành công** (`status = READY`, theo `processedAt`) thay vì đếm mọi upload — thêm `DocumentRepository.countByUserIdAndSourceTypeAndStatusAndProcessedAtBetween`.
- **Fix thêm 2 gap phát hiện trong lúc code**: `CustomerPlanExpiryHelper.applyExpiryOrScheduledChange()` kiểm tra `scheduled.getActive()`, fallback về FREE nếu plan đã lên lịch downgrade/cancel-vào bị Admin disable trước `effectiveAt` (PLAN-DOWN-09); hardening row-lock (`findByIdForUpdate`) trong `getActiveOrHandleExpiry()`/`expireDuePlans()` để tránh lost-update khi nhiều request đụng cùng lúc ngay sau `endDate`.
- **Viết bộ E2E test thật cho 8 kịch bản mục 18** (`backend/src/test/java/com/analyzer/api/e2e/`), dùng **Testcontainers Postgres** (không mock) vì cơ chế đang test (`pg_advisory_xact_lock`, unique partial index, VNPay HMAC) không thể giả lập đúng bằng H2/Mockito. Thêm dependency `spring-boot-testcontainers`/`testcontainers-postgresql`/`testcontainers-junit-jupiter` vào `pom.xml`.
- **Phát hiện và fix 3 bug production thật qua quá trình chạy E2E** (không unit test nào bắt được):
  1. **Flyway chưa từng thực sự chạy ở bất kỳ môi trường nào**: Spring Boot 4.0.6 tách auto-config Flyway ra module riêng `spring-boot-flyway`; project chỉ khai báo `flyway-core`/`flyway-database-postgresql` trực tiếp (thiếu module cầu nối) nên `spring.flyway.*` bị bỏ qua hoàn toàn, kể cả production. Đã thêm đúng dependency vào `pom.xml`.
  2. **2 cặp file migration trùng version** (`V20260721_01` và `V20260721_05` mỗi cái có 2 file, đã commit sẵn từ trước bởi 2 người khác nhau) khiến Flyway crash ngay khi khởi động một khi đã fix (1) ở trên. Xử lý bằng cách chỉ rename 1 file (`V20260721_01__ticket_message_idempotency.sql` → `_06`) — không đụng nội dung/tên bất kỳ file nào khác đã commit, tránh rủi ro checksum cho teammate đã từng chạy migration cũ.
  3. **`SubscriptionQuotaServiceImpl.getCurrentPlan()`/`getCurrentUsage()` đánh dấu `readOnly = true`** nhưng có thể kích hoạt ghi (lock hết hạn/scheduled downgrade qua `CustomerPlanExpiryHelper`) — Postgres từ chối `SELECT ... FOR UPDATE` trong transaction read-only, đổi cả 2 (và 1 overload liên quan) sang `@Transactional` thường.
- **Sửa `EntityMigrationCoverageTest`** để quét toàn bộ thư mục `db/migration` thay vì 2 file cố định (baseline + reconciliation) — tránh việc cột mới bắt buộc phải sửa vào file người khác đã commit; cập nhật `backend/docs/database-migrations.md` cho khớp hành vi mới.
- **Rà soát an toàn deploy toàn bộ 18 file migration**: xác nhận mọi `CREATE TABLE`/`ADD COLUMN` đều `IF NOT EXISTS`, mọi `SET NOT NULL` có backfill trước, mọi `ADD CONSTRAINT`/FK đều bọc `DO $$ ... EXCEPTION/IF NOT EXISTS $$` hoặc `NOT VALID` — an toàn cho lần đầu Flyway thật sự chạy trên DB server đã tồn tại dữ liệu (baseline-on-migrate không xóa dữ liệu).
- Sửa 2 unit test bị lỗi biên dịch từ trước (không liên quan phần việc trên, phát hiện khi chạy `mvn test` toàn bộ): `CustomerPlanServiceImplTest` (constructor cũ thiếu 3 dependency mới + assertion theo hành vi cancel cũ), `ExpertLegalTicketServiceImplTest` (thiếu mock `ExpertRevenueService`).
- Build & test cuối cùng: `mvn clean compile` **BUILD SUCCESS**, `mvn test` **86/86 PASS** (78 unit + 8 E2E Testcontainers).
- Ghi báo cáo chi tiết tại `backend/docs/plan_flow_test_report_2026-07-22.md`.

---

**Date:** 2026-07-18 (Ngày 18 tháng 7 năm 2026)

## Tasks Completed:
- **Sửa lỗi đặt tên API tạo Expert**: Đổi `POST /api/v1/admin/users/expert` (số ít) thành `POST /api/v1/admin/users/experts` (số nhiều) trong `AdminUserController` để nhất quán với `GET /api/v1/admin/users/experts` cùng resource — tránh 2 method khác path cho cùng 1 collection.
- **Chẩn đoán nguyên nhân "click link xác thực email vẫn không đăng nhập được"**: Xác nhận backend (`AuthController.verifyEmail`, `AuthServiceImpl.verifyEmail`) hoạt động đúng, nhưng **frontend hoàn toàn chưa có route/trang `/verify-email`** — khi click link, route guard redirect thẳng về `/login` do chưa đăng nhập, token trên query string bị bỏ qua hoàn toàn, tài khoản không bao giờ được verify thật (`active`/`emailVerified` vẫn `false`). Đây là phần việc còn thiếu bên FE, chưa code do phạm vi công việc tập trung backend + docs.
- **Rà soát và sửa nhiều sai lệch giữa `backend/docs/new_features_implementation_plan.md` / `docs/frontend-flows/*` với code thật**:
  - Sửa endpoint danh sách Expert bị ghi sai (`/admin/experts` → đúng là `/admin/users/experts`) ở cả 2 nhóm tài liệu.
  - Phát hiện tài liệu tính năng 7 (đánh giá câu trả lời AI) mô tả một API rating 1-5 sao tại `/chat-messages/{messageId}/feedback` — lần khảo sát đầu tưởng API này không tồn tại và đã bị thay thế bởi hệ thống Survey/AI Report (`FeedbackController`), nhưng rà soát kỹ hơn xác nhận **API rating thật sự đã tồn tại sẵn** ở `ChatMessageController` + `AdminChatFeedbackController` + entity `ChatMessageFeedback`, chỉ do bỏ sót khi khảo sát lần đầu (chưa đọc hết `ChatMessageController.java`). Đã đính chính lại toàn bộ nội dung docs liên quan.
- **Redesign tính năng đánh giá câu trả lời AI sang mô hình thumbs up/down (kiểu ChatGPT) kèm preset lý do**:
  - Thêm enum `FeedbackRating` (`THUMBS_UP` / `THUMBS_DOWN`) và `FeedbackReason` (`INCORRECT`, `WRONG_CITATION`, `INCOMPLETE`, `NOT_HELPFUL`, `POOR_PHRASING`, `OTHER`).
  - Đổi `ChatMessageFeedback.rating` từ `Integer(1-5)` sang enum `FeedbackRating` (`@Enumerated(STRING)`), thêm cột `reasons` (Text, các mã enum nối dấu phẩy, chỉ có giá trị khi `rating = THUMBS_DOWN`).
  - Bổ sung `submittedById` / `submittedByName` vào `ChatMessageFeedbackResponse` (suy ra từ `chatMessage.getUser()`, không cần thêm cột riêng) để Admin biết chính xác khách hàng nào đã đánh giá.
  - Cập nhật `ChatMessageServiceImpl.submitFeedback()` (tự ép `reasons = null` khi `rating = THUMBS_UP` bất kể client gửi gì), `AdminChatFeedbackServiceImpl`, `ChatMessageFeedbackRepository`, `AdminChatFeedbackController` tương ứng.
- **Cải tiến luồng Admin tạo tài khoản Expert theo yêu cầu mật khẩu mặc định + tự khóa sau 7 ngày**:
  - Bỏ hẳn field `password` khỏi `AdminCreateLawyerRequestDTO` — Admin không tự nhập mật khẩu.
  - Mật khẩu mặc định gán cứng `12345678` (mã hóa bcrypt trước khi lưu), thêm `User.mustChangePassword` (boolean) và `User.passwordResetDeadline` (LocalDateTime), đặt hạn 7 ngày kể từ lúc tạo/reset tài khoản.
  - Gửi email thông tin đăng nhập cho Expert qua `EmailService.sendExpertAccountCreatedEmailAsync` (mật khẩu tạm thời + cảnh báo hạn đổi mật khẩu 7 ngày, nếu không sẽ bị khóa).
  - Thêm `ExpertPasswordDeadlineScheduler` (`@Scheduled(fixedRate = 3_600_000)`, bật kèm `@EnableScheduling` ở `LegalAnalyzerApplication`) tự động khóa (`active = false`) tài khoản Expert quá hạn chưa đổi mật khẩu.
  - `UserServiceImpl.changePassword()` tự xóa cờ `mustChangePassword`/`passwordResetDeadline` khi đổi mật khẩu thành công, giúp tài khoản thoát diện bị khóa.
  - Thêm API mới `POST /api/v1/admin/users/experts/resend-activation` (payload chỉ `email`) để Admin gửi lại mật khẩu tạm thời và/hoặc mở khóa tài khoản Expert mà không cần xóa/tạo lại (giữ nguyên lịch sử ticket đã gán).
  - Cập nhật `UserMapper` thêm `@Mapping(target = "mustChangePassword", ignore = true)` và tương tự cho `passwordResetDeadline` để loại bỏ warning MapStruct "Unmapped target properties" khi map từ `UserRequestDTO` (đăng ký Customer) sang `User`.
- **Kiểm tra toàn diện lại cả 10 tính năng theo yêu cầu ban đầu** (đối chiếu code backend thật ↔ `new_features_implementation_plan.md` ↔ toàn bộ `docs/frontend-flows/*`): xác nhận backend đã hoàn thiện 10/10 tính năng, tài liệu đã khớp 100% với code sau khi sửa thêm 1 chỗ mô tả schema DB của bảng feedback còn ghi kiểu cũ (`rating Integer` → sửa thành lưu enum dạng String + bổ sung cột `reasons`); xác nhận phía frontend hiện chưa build UI cho bất kỳ tính năng nào trong 10 tính năng trên (đúng như các mục đã được đánh dấu `[MỚI]` trong docs).
- Build Maven (`.\mvnw.cmd clean compile` và `.\mvnw.cmd test-compile`) đạt `BUILD SUCCESS` sau mỗi vòng thay đổi, không phát sinh lỗi biên dịch.

---

**Date:** 2026-07-17 (Ngày 17 tháng 7 năm 2026)

## Tasks Completed:
- **Triển khai 10 tính năng bổ sung Backend theo đặc tả `backend/docs/new_features_implementation_plan.md`** (thiết kế qua Plan Mode, được duyệt trước khi code, tuân thủ kiến trúc layer sẵn có entity → repository → service/impl → mapper → controller):
  - **Cập nhật Entity/Schema**: `User` bổ sung `emailVerified`, `emailVerificationToken`, `emailVerificationTokenExpiry`, `specialty`, `legalDomain`, `description`; `ChatSession` bổ sung `isShared`, `shareToken`, `sharedAt`; tạo mới `@Entity ChatMessageFeedback` (unique theo `chat_message_id`, upsert 1 feedback/tin nhắn).
  - **Đổi mật khẩu**: `POST /api/v1/users/change-password` (`@PreAuthorize("isAuthenticated()")`), lấy user từ SecurityContext (chống IDOR), xử lý edge case mật khẩu rỗng (OAuth), trùng mật khẩu cũ.
  - **Admin quản lý Expert**: `POST /api/v1/admin/users/expert` (tạo tài khoản Expert active + verified ngay, chuẩn hóa email `trim().toLowerCase()` chống trùng do hoa/thường) và `GET /api/v1/admin/experts` (danh sách Expert active kèm `specialty`/`legalDomain`/`description` phục vụ giao diện phân công).
  - **Xác thực email bắt buộc trước khi đăng nhập**: Đăng ký (`/auth/register`) giờ set `active=false`, `emailVerified=false`, sinh token UUID hết hạn 24h và gửi email xác thực. `GET /api/v1/auth/verify-email?token=` (public, idempotent, xử lý token hết hạn qua `ExpiredVerificationTokenException`). Sửa `UserDetailsImpl.isEnabled()` để phản ánh đúng `user.active` (trước đó luôn `true`, không có tác dụng chặn), đăng ký thêm handler `DisabledException` → 403 trong `GlobalExceptionHandler`. 3 tài khoản demo seed sẵn trong `DataInitializer` được set `emailVerified=true` để không bị khóa.
  - **Hạ tầng gửi Email**: Thêm dependency `spring-boot-starter-mail`, tạo `EmailService`/`EmailServiceImpl` (dùng `JavaMailSender`, chạy `@Async`, tự log cảnh báo và bỏ qua thay vì throw nếu chưa cấu hình SMTP hoặc gửi lỗi — không làm rollback giao dịch chính), bật `@EnableAsync` ở `LegalAnalyzerApplication`. Hook gửi email thông báo khi tài liệu ingest xong (`WorkspaceServiceImpl.updateProcessingResult`, trạng thái `READY`).
  - **Tải tài liệu gốc cho Admin/Expert**: `GET /api/v1/admin/documents/{documentId}/download` (`AdminDocumentController` mới) — Admin tải mọi file, Expert chỉ tải được file thuộc ticket đang được phân công (`document.legalTicket.assignedLawyer.id == currentUserId`), kiểm tra `Files.exists()` trả 404 rõ ràng thay vì lỗi 500 khi mất file vật lý.
  - **Đánh giá câu trả lời AI**: `POST /api/v1/chat-messages/{messageId}/feedback` (chỉ rate được tin nhắn `role=ASSISTANT`, thuộc sở hữu chính mình, upsert theo `chatMessageId`) và `GET /api/v1/admin/chat-messages/feedback` (Admin xem, filter theo `rating`, phân trang) qua service mới `AdminChatFeedbackService`.
  - **Chia sẻ Chat Session Read-only**: `POST /api/v1/chat-sessions/{chatSessionId}/share` (Customer sở hữu, sinh/tái sử dụng `shareToken`) và `GET /api/v1/shared/chat/{shareToken}` (`SharedChatSessionController` mới, chỉ `ADMIN`/`EXPERT`, kiểm tra đồng thời `shareToken` khớp và `isShared=true`); endpoint gửi tin nhắn giữ nguyên `@PreAuthorize("hasRole('CUSTOMER')")` nên tự động chặn gửi tiếp qua link chia sẻ.
  - Build Maven (`.\mvnw.cmd -q -DskipTests compile`) đạt `BUILD SUCCESS`, không phát sinh lỗi biên dịch/MapStruct.
- **Phát hiện và sửa lỗi nghiêm trọng về cấu hình `.env`**: Thư viện `spring-dotenv 5.1.0` **không nạp được file `.env` vào Spring Environment** khi chạy cùng Spring Boot 4.0.6 (âm thầm, không log lỗi) — toàn bộ config trước giờ chạy bằng default cứng trong `application.yml`, chỉ "trùng hợp" đúng với các biến DB vì giá trị `.env` giống hệt default. Phát hiện qua việc `MAIL_FROM`/`SMTP_USERNAME` (không có default) bị rỗng khi gửi email xác thực dù `.env` đã điền đúng. Đã xác minh nguyên nhân bằng cách set thẳng biến môi trường OS (mail gửi được ngay) rồi so sánh với chạy chỉ dựa `.env` (mail bị skip). **Khắc phục**: thêm `spring.config.import: "optional:file:.env[.properties]"` vào `application.yml` — dùng cơ chế native của Spring Boot để nạp `.env` theo định dạng properties, không phụ thuộc thư viện thứ 3 nữa. Đã verify lại: restart chỉ dựa vào `.env`, đăng ký thử, log không còn cảnh báo "Mail is not configured".
- Cấu hình SMTP Gmail thật trong `backend/.env` (không commit lên git vì đã khớp pattern `*.env` trong `backend/.gitignore`) và test gửi email thành công qua `smtp.gmail.com:587`.

---

**Date:** 2026-06-27 (Ngày 27 tháng 6 năm 2026)

## Tasks Completed:
- **Triển khai Business Logic & Tái cấu trúc Phase 2 Backend (Developer 1 Modules)**:
  - **Phân tích Kiến trúc & Thiết lập Kế hoạch**:
    - Nghiên cứu hợp đồng API Phase 2 từ commit của Leader và xây dựng tài liệu phân chia công việc `phase2_task_allocation.md` cho 2 lập trình viên backend, giúp cách ly độc lập theo domain package và triệt tiêu xung đột git.
    - Lập bản Kế hoạch triển khai chi tiết `implementation_plan.md` cho 4 module nghiệp vụ chính.
  - **Triển khai Module Lawyer Ticket Operations**:
    - Tạo mới `TicketConversationServiceImpl` xử lý trao đổi tin nhắn giữa chuyên gia và khách hàng (`chatWithUser`), cập nhật thời gian phản hồi `lastLawyerMessageAt` và truy xuất lịch sử hội thoại (`getChatHistory`).
    - Tạo mới `TicketFileServiceImpl` quản lý danh sách và tải lên tài liệu chuyên gia đính kèm ticket với `DocumentPurpose.LAWYER_ATTACHMENT`.
    - Cập nhật `LawyerTicketController` gắn bảo mật `@PreAuthorize("hasRole('EXPERT')")` đồng bộ với vai trò `EXPERT` trong hệ thống.
  - **Triển khai Module Admin Ticket Management**:
    - Tạo mới `AdminTicketManagementServiceImpl` triển khai các chức năng cho Admin xem tóm tắt AI (`viewAiSummary`), xem file người dùng (`viewUserFiles`), xem lịch sử chat, phân công (`assignLawyer`) và tái phân công chuyên gia (`reassignLawyer`).
    - Cập nhật `AdminTicketManagementController` và bổ sung thêm endpoint từ chối ticket (`POST /api/v1/admin/tickets/{id}/reject`).
  - **Triển khai Module AI Features & Citations Integration**:
    - Tạo mới `AiFeatureServiceImpl` trả về đánh giá rủi ro AI (`AiRiskAssessmentResponse`) và tóm tắt ticket.
    - Tạo mới `AiCitationServiceImpl` cùng MapStruct mapper `AiFeatureMapper` để truy xuất danh sách trích dẫn nguồn pháp lý (`AiCitation`) đính kèm theo Ticket hoặc Chat Message.
    - Cập nhật `AiFeatureController` hoàn thiện các REST endpoint phục vụ AI metadata.
  - **Triển khai Module Chat Session Memory & Context**:
    - Tạo mới `ChatMemoryServiceImpl` xử lý đọc tóm tắt (`summary`), bộ nhớ (`memoryJson`), và nạp ngữ cảnh (`contextJson`) tự động tăng `contextVersion`.
    - Cập nhật `ChatSessionContextController` hoàn thiện các API quản lý bộ nhớ chat.
  - **Tái cấu trúc (Refactoring) & Loại bỏ API trùng lặp**:
    - Tái cấu trúc `LegalTicketController` cũ thành controller độc quyền dành cho Khách hàng (`Customer`), xóa bỏ hoàn toàn các endpoint Admin và Expert bị trùng lặp.
    - Xóa bỏ các controller cũ trùng lặp `SubscriptionPlanController` và `CustomerPlanController`, hợp nhất toàn bộ luồng quản lý gói cước, đăng ký, sử dụng và hoàn tiền vào `SubscriptionManagementController` duy nhất tại đường dẫn `/api/v1/subscriptions/...`.
    - Tạo mới các Service implementation `SubscriptionUsageServiceImpl` và `RefundServiceImpl` để cung cấp đủ Spring Beans giúp hệ thống khởi chạy mượt mà.
  - **Kiểm thử và Biên dịch (Build Verification)**:
    - Sửa đổi và chuẩn hóa toàn bộ các điểm khác biệt về Enum (`LegalTicketStatus`, `LegalTicketMessageType`, `SuggestionType`, `UserActionHint`) và phương thức getter trên Entity (`uploadedAt`, `question`, `customerNote`).
    - Chạy đóng gói tự động qua Maven Wrapper (`.\mvnw.cmd clean package -DskipTests`) đạt kết quả **`BUILD SUCCESS`** 100%.

---

**Date:** 2026-06-24 (Ngày 24 tháng 6 năm 2026)

## Tasks Completed:
- **Thiết kế và Cài đặt tính năng Legal Ticket (Hỗ trợ Tư vấn Pháp lý từ Chuyên gia)**:
  - **Cập nhật & Tạo mới Enums**:
    - Cập nhật `LegalTicketStatus` gồm 11 trạng thái vòng đời đầy đủ: `DRAFT`, `PENDING_ADMIN_REVIEW`, `REJECTED_BY_ADMIN`, `ASSIGNED_TO_LAWYER`, `IN_REVIEW`, `NEED_MORE_INFO`, `CUSTOMER_RESPONDED`, `RESOLVED`, `CLOSED`, `CANCELLED`, `REOPENED`.
    - Tạo mới `LegalTicketMessageType` để hỗ trợ phân loại luồng tin nhắn trao đổi: `CUSTOMER_REPLY`, `EXPERT_REQUEST_MORE_INFO`, `EXPERT_RESPONSE`, `ADMIN_NOTE`, `SYSTEM`.
  - **Triển khai Thực thể JPA (Entities) & Repositories**:
    - Tạo mới `@Entity` `LegalTicket` kế thừa đầy đủ cơ chế Soft Delete, Optimistic Locking (`@Version`), Audit Timestamps và ràng buộc Unique key trên cặp `(request_id, created_by_id)`.
    - Tạo mới `@Entity` `LegalTicketMessage` lưu trữ lịch sử trao đổi giữa khách hàng, admin và chuyên gia.
    - Cấu hình `LegalTicketRepository` sử dụng `@EntityGraph` để nạp trước (Eager Fetch) các quan hệ `workspace`, `document`, `createdBy`, và `assignedLawyer`, giải quyết triệt để lỗi N+1 Query.
    - Bổ sung `findByRequestId` trong `ChatMessageRepository` và `findByTicket_IdOrderByCreatedAtAsc` trong `LegalTicketMessageRepository`.
  - **Xây dựng DTOs và MapStruct Mapper**:
    - Chuẩn hóa DTO tạo mới và phản hồi: `CreateLegalTicketRequest` và `LegalTicketResponse` chứa đầy đủ thông tin snapshot phân tích từ AI.
    - Xây dựng các DTO hành động: `AssignLawyerRequest`, `RejectLegalTicketRequest`, `ResolveLegalTicketRequest`, `RequestMoreInfoRequest`, `CustomerTicketReplyRequest`, `CloseLegalTicketRequest`, `CancelLegalTicketRequest`, `ReopenLegalTicketRequest`.
    - Triển khai `LegalTicketMapper` sử dụng MapStruct để chuyển đổi tự động và an toàn giữa Entity và DTO.
  - **Triển khai Business Logic & Phân quyền Endpoint**:
    - **`LegalTicketService`**: Xử lý logic tạo ticket từ phía Customer (kiểm tra quyền sở hữu Workspace, xác thực gói dịch vụ Active và từ chối các tài khoản Free, kiểm tra trùng lặp ticket), hủy ticket, đóng ticket, phản hồi của Customer, và mở lại ticket trong vòng 7 ngày.
    - **`AdminTicketAssignmentService`**: Xử lý nghiệp vụ phân công/tái phân công chuyên gia (`assignLawyer` / `reassignLawyer`) dựa trên `AssignLawyerRequest`, tích hợp ghi nhận lịch sử và ghi chú bảo mật.
    - **`ExpertLegalTicketService`**: Phục vụ các thao tác xử lý của chuyên gia gồm tiếp nhận rà soát (`startReview`), yêu cầu bổ sung tài liệu (`requestMoreInfo`), và hoàn tất trả lời câu hỏi (`resolveTicket`).
    - **`LegalTicketController`**: Định nghĩa đầy đủ các REST API endpoints với tiền tố `/api/v1/...`, tích hợp kiểm tra phân quyền bảo mật `@PreAuthorize` tương ứng cho từng vai trò `CUSTOMER`, `ADMIN`, `EXPERT`.
  - **Tối ưu hóa Xử lý Lỗi & Exception**:
    - Tạo mới các custom exceptions: `ConflictException` (phục vụ lỗi Conflict 409 khi tạo trùng ticket hoặc chuyển trạng thái không hợp lệ) và `TooManyRequestsException` (mã 429).
    - Cấu hình các exception handlers tương ứng trong `GlobalExceptionHandler.java`.
  - **Kiểm tra và Xác minh Hệ thống**:
    - Biên dịch và chạy thử hệ thống qua Maven wrapper (`./mvnw clean compile`) thành công 100% không phát sinh lỗi biên dịch.

---

**Date:** 2026-06-21 (Ngày 21 tháng 6 năm 2026)

## Tasks Completed:
- **Thiết kế và Cài đặt Thực thể Chat AI theo Workspace**:
  - Tạo mới các `@Entity`: `ChatSession` (ID kiểu String định dạng `chat_...`, quan hệ `@ManyToOne` với `User` và `Workspace`) và `ChatMessage` (ID kiểu String, quan hệ `@ManyToOne` với `ChatSession` và `User`).
  - Thiết lập các enum đi kèm: `ChatSessionStatus`, `ChatMessageRole`, `ChatMessageType`, `ChatMessageStatus`.
  - Tạo các interface `ChatSessionRepository` và `ChatMessageRepository` hỗ trợ truy vấn.
- **Triển khai các API quản lý Chat Session**:
  - **`POST /api/v1/workspaces/{workspaceId}/chat-sessions`**: Tạo cuộc hội thoại mới cho Workspace thuộc người dùng hiện tại (lấy từ Security Context). Ràng buộc validation tiêu đề (`title` không trống, tối đa 255 ký tự).
  - **`GET /api/v1/workspaces/{workspaceId}/chat-sessions`**: Lấy danh sách cuộc hội thoại phân trang, lọc theo trạng thái (`status`), sắp xếp theo `lastMessageAt DESC` và `createdAt DESC`. Sử dụng DTO phân trang generic mới tạo `PageResponse<T>`.
  - **`GET /api/v1/chat-sessions/{chatSessionId}`**: Lấy chi tiết thông tin một cuộc hội thoại cụ thể, chặn truy cập nếu cuộc hội thoại đã bị xóa (`DELETED`).
  - Đảm bảo kiểm tra phân quyền sở hữu Workspace/ChatSession chặt chẽ (`403 Forbidden`) và trạng thái tồn tại (`404 Not Found`).
- **Chuẩn hóa Route & Tài liệu Swagger**:
  - Chuyển cấu hình ánh xạ route của `ChatSessionController` sang mức độ method với các đường dẫn tuyệt đối dạng `/api/v1/...`, giúp loại bỏ hiển thị trùng lặp trên giao diện Swagger/OpenAPI.
- **Khắc phục lỗi phân tích cú pháp JSON**:
  - Cấu hình thuộc tính `spring.jackson.parser.allow-unquoted-control-chars: true` trong `application.yml` cho phép Jackson xử lý các chuỗi JSON chứa ký tự điều khiển thô chưa escape (như ký tự xuống dòng `\n` - code 10), khắc phục hoàn toàn lỗi `JSON parse error: Illegal unquoted character` khi gọi API (ví dụ `/api/v1/auth/register`).
- **Triển khai các API gửi tin nhắn Chat/RAG**:
  - **`POST /api/v1/workspaces/{workspaceId}/messages`**: Gửi câu hỏi đầu tiên trong Workspace. Tự động tạo một `ChatSession` mặc định (nếu chưa có), lưu tin nhắn của User, gọi Python AI Service để truy vấn RAG và lưu tin nhắn phản hồi của Assistant.
  - **`POST /api/v1/chat-sessions/{chatSessionId}/messages`**: Gửi tin nhắn tiếp theo trong cuộc hội thoại hiện có. Lưu tin nhắn User, gọi Python AI và lưu tin nhắn phản hồi Assistant.
  - **Tích hợp Python AI Client (`PythonAiClient.java`)**: Tạo client kết nối Python AI service qua `/internal/rag/query` bằng Java `HttpClient`, cấu hình connect/read timeout và ném exception tương ứng khi AI Service lỗi hoặc quá hạn.
  - **Xử lý các tình huống lỗi Chat/RAG**: Triển khai các custom exception (`NoReadyDocumentsException`, `AiServiceUnavailableException`, `AiServiceTimeoutException`, `InvalidMessageException`) và đăng ký handler trong `GlobalExceptionHandler` để trả về đúng cấu trúc payload lỗi (400, 403, 404, 503, 504) như yêu cầu.
  - Thiết kế các DTO phục vụ gửi nhận tin nhắn: `SendMessageRequest`, `ChatMessageResponse`, `SendMessageResponse`, `RagQueryRequest`, `RagQueryResponse`.
- **Triển khai các API đọc tin nhắn**:
  - **`GET /api/v1/chat-sessions/{chatSessionId}/messages`**: Lấy toàn bộ lịch sử tin nhắn trong một ChatSession cụ thể. Có hỗ trợ phân trang (`page`, `size`) và tự động sắp xếp theo thứ tự thời gian tăng dần (`createdAt ASC`).
  - **`GET /api/v1/chat-messages/{messageId}`**: Lấy thông tin chi tiết của một tin nhắn cụ thể bao gồm các siêu dữ liệu (`requestId`, `aiModel`, `tokens`, `errorMessage`).
  - Đảm bảo kiểm tra phân quyền sở hữu chặt chẽ (`403 Forbidden`) đối với người dùng hiện tại và kiểm tra trạng thái hoạt động của cuộc hội thoại để chặn truy xuất từ các cuộc hội thoại đã bị xóa (`400 Bad Request`).
- **Triển khai các API quản lý ChatSession (Cập nhật & Xóa mềm)**:
  - **`PUT /api/v1/chat-sessions/{chatSessionId}`**: Cập nhật tiêu đề (`title`) của `ChatSession`. Thực hiện kiểm tra quyền sở hữu, chặn cập nhật đối với cuộc hội thoại đã bị xóa, tự động trim tiêu đề, đồng thời validate không trống và độ dài tối đa 255 ký tự (ném custom exception `InvalidTitleException` để trả về định dạng lỗi chuẩn).
  - **`DELETE /api/v1/chat-sessions/{chatSessionId}`**: Thực hiện xóa mềm cuộc hội thoại bằng cách cập nhật trường trạng thái `status` thành `DELETED` và ghi lại mốc thời gian xóa tại trường `updatedAt`. Kiểm tra các ràng buộc bảo mật và trạng thái cuộc hội thoại trước khi xóa (trả về `DeleteChatSessionResponse`).
- **Khắc phục lỗi khởi động ApplicationContext do thiếu Bean `ObjectMapper`**:
  - Khởi tạo trực tiếp instance `ObjectMapper` trong `PythonAiClient.java` (tương tự như cách làm ở `WorkspaceServiceImpl` và `AuthEntryPointJwt`), giúp loại bỏ lỗi dependency injection khi Spring Boot khởi động do không tìm thấy bean `ObjectMapper` được tự động cấu hình.
- **Tách và phân loại cấu trúc Custom Exceptions**:
  - Phân chia các custom exceptions trong package `com.analyzer.api.exception` thành 5 thư mục con chuyên biệt nhằm tăng tính tường minh và cấu trúc sạch cho dự án:
    - `common`: `ResourceNotFoundException`, `ForbiddenException`
    - `workspace`: `WorkspaceDeletedException`, `NoReadyDocumentsException`, `DocumentProcessingDispatchException`
    - `chat`: `DeletedChatSessionException`, `InvalidMessageException`, `InvalidTitleException`
    - `ai`: `AiServiceUnavailableException`, `AiServiceTimeoutException`
    - `validation`: `InvalidPageException`, `InvalidSizeException`, `InvalidStatusException`
  - Cập nhật toàn bộ các câu lệnh import, tham chiếu gói (package references) tương ứng tại các Service, Controller và Client của hệ thống, đồng thời đăng ký gói mới vào `GlobalExceptionHandler` để đảm bảo hệ thống vận hành ổn định.
- **Cập nhật DTO `RagQueryRequest` gọi Python AI**:
  - Thêm các annotation `@JsonProperty` cho tất cả thuộc tính của `RagQueryRequest` nhằm đảm bảo serialization tự động chuyển đổi định dạng camelCase sang snake_case (gồm `request_id`, `user_id`, `workspace_id`, `question`, `top_k_checklist`, `top_k_user_chunks_per_checklist`, `top_k_knowledge_chunks`).
  - Loại bỏ hoàn toàn trường `chatSessionId` khỏi DTO để tránh gửi dư thừa thông tin sang AI Service.
  - Cấu hình các tham số RAG mới trong `ChatMessageServiceImpl` gồm `topKChecklist(10)` và `topKUserChunksPerChecklist(3)` đồng thời giữ `topKKnowledgeChunks(5)`.

---

**Date:** 2026-06-20 (Ngày 20 tháng 6 năm 2026)

## Tasks Completed:
- **Thiết lập và Đồng bộ hóa Quan hệ Thực thể JPA**:
  - **`Workspace`**: Chuyển đổi trường `userId` kiểu `String` thành mối quan hệ `@ManyToOne` kiểu `User` (`private User user`), đồng bộ kiểu dữ liệu trường ID của User (`Long`).
  - **`Document`**: Thiết lập quan hệ `@ManyToOne` liên kết với `Workspace` (`private Workspace workspace`) và `User` (`private User user`) thay cho các trường `String` `workspaceId` và `userId` trước đó.
  - Cập nhật các lớp Repository tương ứng (`WorkspaceRepository`, `DocumentRepository`) để nhận kiểu dữ liệu ID của `User` là `Long` khi truy vấn.
  - Cập nhật lớp Service (`WorkspaceServiceImpl`) để lưu trữ dữ liệu, truy vấn và trả về kết quả khớp với cấu trúc thực thể mới.
- **Chuẩn hóa Endpoint URL**:
  - Chuẩn hóa base mapping của `WorkspaceController` từ việc hỗ trợ đa đường dẫn `{"/api/workspaces", "/api/v1/workspaces"}` về chỉ hỗ trợ duy nhất `"/api/v1/workspaces"` để đồng nhất với định dạng phiên bản `/api/v1` của toàn bộ hệ thống.
  - Xác minh các API Controller khác (`UserController`, `SubscriptionPlanController`, `PaymentTransactionController`, `CustomerPlanController`, `AuthController`) đều đang tuân thủ đúng chuẩn `/api/v1`.
- **Phân quyền và Bảo mật Endpoint Workspace & Document**:
  - Thay đổi mức độ phân quyền từ `@PreAuthorize("isAuthenticated()")` sang `@PreAuthorize("hasRole('CUSTOMER')")` cho toàn bộ các endpoint trong `WorkspaceController` (bao gồm tạo workspace, lấy danh sách documents và upload document), đảm bảo chỉ những người dùng có vai trò **CUSTOMER** mới có quyền thao tác và sử dụng.
- **Đánh giá luồng tích hợp với Python AI Service**:
  - Rà soát toàn bộ luồng tích hợp gọi tệp tin từ Spring Boot sang Python AI Service (`POST /internal/documents/process`) và callback cập nhật kết quả (`POST /api/internal/documents/{documentId}/processing-result`).
  - Xác nhận luồng tích hợp đồng bộ cơ bản đã được cài đặt hoạt động đầy đủ trong dự án.
  - Lập báo cáo đánh giá kiến trúc, chỉ ra các điểm rủi ro tiềm ẩn (nghẽn connection pool, chặn luồng, cấu hình shared volume Docker) và đưa ra các đề xuất cải tiến tối ưu (xử lý bất đồng bộ, dùng Message Broker).

---

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
