# Backend — Known Issues / Tech Debt

Tổng hợp các bug/vấn đề còn tồn đọng phát hiện được trong quá trình làm việc trên backend, tính đến 2026-07-21. Không phải danh sách đầy đủ toàn bộ codebase — chỉ những gì đã được kiểm tra trực tiếp (chủ yếu quanh luồng ticket/doanh thu và một đợt rà soát entity tổng thể).

---

## 1. Luồng ticket & chia lợi nhuận (`LegalTicket` / `ExpertRevenueService`)

### Đã fix (2026-07-21)
| # | Vấn đề | Fix |
| :-- | :--- | :--- |
| 1 | Admin đóng ticket (`POST /admin/tickets/{id}/close`) bỏ qua hoàn toàn bước Lawyer resolve, vẫn kích hoạt tính hoa hồng | Chặn 409 `TICKET_NOT_RESOLVED_BY_EXPERT` nếu ticket có `assignedLawyer` nhưng chưa `RESOLVED` |
| 2 | `assignLawyer` không kiểm tra `lawyerId` có role `EXPERT` | Chặn 409 `USER_IS_NOT_EXPERT` |
| 3 | `reassignLawyer` không reset dữ liệu tài chính, tiền/hoa hồng của lawyer cũ bị tính nhầm cho lawyer mới | Chặn 409 `CANNOT_REASSIGN_TICKET_WITH_PAYMENT_SET`; thêm `POST /admin/tickets/{id}/expert-payment/reset` để admin chủ động xoá (chặn nếu đã `PAID`) |

Chi tiết: `backend/docs/expert_revenue_api.md`.

### Còn tồn đọng
| # | Vấn đề | Ghi chú |
| :-- | :--- | :--- |
| 4 | Ticket `RESOLVED` → `REOPENED` → resolve lại: `commissionRate` khoá từ lần đầu, không tách được tiền/công theo từng vòng xử lý | Cần quyết định nghiệp vụ: có cho phép tính hoa hồng riêng mỗi vòng reopen không, hay giữ nguyên (1 ticket = 1 lần tính, bất kể reopen bao nhiêu lần) |
| 5 | `RefundRequest` (hoàn tiền cho khách) không liên kết ngược để tự động đảo `expertPaymentStatus`/`expertPayout` khi ticket bị hoàn tiền | Nếu khách được hoàn tiền sau khi expert đã được `PAID`, hệ thống không cảnh báo/thu hồi — rủi ro thất thoát nếu không có quy trình thủ công đối soát |
| 6 | `updatePayment` (`PATCH .../expert-payment`) không khoá ticket sau khi đã `PAID` — admin vẫn sửa được `consultationFee`/set ngược về `UNPAID` | Khác với `resetFinancials` (đã chặn khi `PAID`), đường `updatePayment` gốc **chưa** có khoá tương tự. Cân nhắc: chặn sửa khi đang `PAID`, bắt buộc phải qua 1 luồng "điều chỉnh có lý do" riêng nếu cần sửa sau khi đã trả |
| 7 | Không có API GET lịch sử audit log riêng cho từng ticket | Dữ liệu đã ghi đủ vào `ticket_audit_logs` (`TICKET_CREATED`, `EXPERT_PAYMENT_UPDATED`, `EXPERT_PAYMENT_RESET`...) nhưng chưa expose qua REST — muốn xem phải query DB trực tiếp |
| 8 | Không có ví/wallet hay tích hợp cổng thanh toán để tự động trả tiền cho Expert | Toàn bộ việc chuyển tiền vẫn làm tay ngoài hệ thống; `expertPaymentStatus` chỉ là bút toán ghi nhận |
| 9 | `consultationFee` không có trần, không đối chiếu với bảng giá/gói dịch vụ nào | Admin có thể nhập sai số (vd gõ nhầm thêm số 0) mà không có cảnh báo |

---

## 2. Thiết kế entity — rà soát tổng thể (phát hiện, chưa xử lý)

| Vấn đề | Chi tiết |
| :--- | :--- |
| Hai cơ chế "share" song song cho hội thoại | `ChatSession` tự có field `isShared`/`shareToken`/`shareAccessLevel`, trong khi `ConversationShare` là entity riêng biệt gắn vào `LegalTicket` (token hash, hết hạn, thu hồi). Chưa rõ field share trên `ChatSession` có còn được dùng thực tế hay là tàn dư thiết kế cũ. |
| Trùng lặp field ingest giữa `KnowledgeBaseVersion` và `KnowledgeIngestionJob` | `KnowledgeBaseVersion` tự lưu `ingestStatus`/`ingestedAt`/`ingestErrorMessage`; `KnowledgeIngestionJob` lưu thêm `status`/`progressPercent` riêng cho từng lần chạy — rủi ro lệch dữ liệu nếu 2 nơi không đồng bộ. |
| Modeling không nhất quán trong nhóm ticket | `TicketContextSnapshot` dùng FK thật (`@OneToOne LegalTicket`); `TicketAuditLog` và `TicketAttachment` dùng `ticketId` kiểu `String` thường, không có ràng buộc toàn vẹn dữ liệu ở DB — có thể ghi `ticketId` trỏ tới ticket không tồn tại. |

---

## 3. Tính năng khác

| Vấn đề | Chi tiết |
| :--- | :--- |
| Quên mật khẩu — Frontend chưa làm | Backend đầy đủ (`POST /auth/forgot-password`, `/auth/reset-password`, gửi email link `/reset-password?token=...`). Frontend chỉ có link `href="#forgot"` chết ở `LoginPage.tsx`, chưa có route/trang nào xử lý — **link trong email hiện sẽ 404**. |

---

## 4. Quy ước migration — không nhất quán giữa doc và thực tế

`backend/docs/database-migrations.md` ghi rõ: *"Never edit an already-applied migration."* Nhưng để `EntityMigrationCoverageTest` pass (test bắt buộc mọi bảng/cột entity phải có trong đúng 2 file `V20260717_00__complete_entity_baseline.sql` và `V20260720_02__complete_entity_column_reconciliation.sql`), thực tế bắt buộc phải **sửa trực tiếp 2 file này** mỗi khi thêm entity/cột mới — đây là điều team trước đó cũng đã làm nhiều lần (nhiều cột của các feature trước cũng nằm rải rác thiếu trong 2 file này, đã phát hiện và vá lại trong phiên làm việc này: `users.forgot_password_*` (3 cột), `chat_sessions.share_access_level`).

**Rủi ro:** nếu môi trường nào đó đã từng chạy Flyway qua 2 file này trước khi bị sửa, checksum sẽ lệch (Flyway validate fail) khi deploy bản mới, trừ khi có `flyway repair` hoặc baseline lại. Cần team thống nhất chính thức 1 trong 2 hướng:
- (a) Tiếp tục coi 2 file baseline/reconciliation là "living rollup" được phép sửa (như đang làm), và đảm bảo mọi môi trường đều `flyway repair` khi pull code mới có sửa 2 file này.
- (b) Đổi cách `EntityMigrationCoverageTest` kiểm tra (quét toàn bộ migration thay vì chỉ 2 file cố định), để không cần sửa file cũ nữa — đúng với tinh thần "never edit an already-applied migration".

---

## 5. Đối chiếu tài liệu đã cập nhật (2026-07-21)

- ✅ `db/migration` — `V20260721_03__add_commission_fields_and_revenue_settings.sql` (mới) + đã vá `V20260717_00`/`V20260720_02` để `EntityMigrationCoverageTest` pass.
- ✅ `backend/docs/expert_revenue_api.md` — cập nhật đầy đủ entity, 6 endpoint, logic `applyCommissionSnapshot`, edge case, business rules.
- ✅ `docs/frontend-flows/12_doanh_thu_expert.txt` — cập nhật breaking change (phân trang), 3 API admin mới, hành vi reassign/reset mới.
- ✅ `docs/frontend-flows/05_admin_duyet_va_phan_cong_ticket.txt` — thêm edge case 409 mới cho assign/reassign-lawyer.
- ✅ `docs/frontend-flows/00_INDEX.txt` — đã thêm mục flow 12 vào danh sách.
