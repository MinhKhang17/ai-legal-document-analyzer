# Báo cáo loại bỏ UI AI retrieval và kiểm tra i18n frontend

## 1. Tổng quan yêu cầu

Đã thực hiện dọn dẹp trong phạm vi frontend: chỉ loại bỏ câu mô tả phụ về việc bản ghi không được dùng cho truy xuất AI. Toàn bộ chức năng Active/Inactive của Knowledge Base được giữ lại, đồng thời kiểm tra cơ chế i18n EN/VI, các chuỗi hiển thị còn hardcode và tính đồng nhất khóa locale.

## 2. Phạm vi đã kiểm tra

- Toàn bộ `frontend/src`, gồm pages, layouts, components, hook, store, service và utility i18n.
- Các màn Knowledge Base: danh sách, chi tiết và danh sách tài liệu đã ingest.
- Expert Revenue tại `frontend/src/pages/lawyer/LawyerRevenuePage.tsx`; trang này đã dùng hoàn toàn `expertRevenue.*`, `table.status`, `common.refresh`, enum `expertRevenue.payment.*` và định dạng locale theo `language`.
- Các vị trí phát hiện chuỗi giao diện rẽ nhánh theo `language === "vi"`: hồ sơ, phản hồi AI quản trị, chat, admin console, xác thực email, hoàn tiền email và tài liệu.
- Các thông báo lỗi nhận từ API qua `frontend/src/services/http.ts`.

## 3. Danh sách đầy đủ các tệp đã chỉnh sửa

- `frontend/src/utils/i18n.ts`
- `frontend/src/services/http.ts`
- `frontend/src/services/chat.service.ts`
- `frontend/src/services/subscription.service.ts`
- `frontend/src/pages/knowledge-base/KnowledgeBasePage.tsx`
- `frontend/src/pages/knowledge-base/KnowledgeBaseDetailPage.tsx`
- `frontend/src/components/knowledge-base/KnowledgeBaseIngestedDocumentsCard.tsx`
- `frontend/src/pages/lawyer/LawyerRevenuePage.tsx` đã được kiểm tra, không cần sửa vì đã i18n đầy đủ.
- `frontend/src/pages/settings/ProfilePage.tsx`
- `frontend/src/components/admin/AdminAiFeedbackPanel.tsx`
- `frontend/src/pages/admin/AdminConsolePage.tsx`
- `frontend/src/pages/admin/AdminFeedbackPage.tsx`
- `frontend/src/components/chat/ChatMessageFeedbackControls.tsx`
- `frontend/src/pages/chat/LegalChatPage.tsx`
- `frontend/src/pages/auth/VerifyEmailPage.tsx`
- `frontend/src/pages/billing/RefundEmailConfirmationPage.tsx`
- `frontend/src/pages/documents/DocumentsPage.tsx`
- `FRONTEND_AI_RETRIEVAL_REMOVAL_AND_I18N_FULL_AUDIT_REPORT.md`

## 4. Thành phần mô tả truy xuất AI đã bị xóa

- Chỉ xóa câu mô tả phụ từng xuất hiện bên dưới badge status của Knowledge Base khi bản ghi không active.
- Đã điều chỉnh layout status để không còn dòng trống sau khi bỏ câu này.
- Không xóa card, bảng, hàng, trạng thái hay thao tác Knowledge Base nào.

Đã khôi phục và giữ nguyên cột Active/Inactive, badge, filter, `activeFilter`, `setActiveFilter`, tham số request `active`, trạng thái tại trang chi tiết, bảng phiên bản và `KnowledgeBaseIngestedDocumentsCard`. Trường `active` trong type/API response vẫn nguyên vẹn và frontend tiếp tục dùng nó để hiển thị/lọc.

## 5. Các khóa i18n đã xóa hoặc làm sạch

Đã xóa khỏi cả hai dictionary EN/VI khóa chỉ dành cho câu mô tả phụ:

- `knowledge.inactiveForRetrieval`
- `knowledge.privateInactiveForAi`

Các khóa còn lại nhưng từng có nội dung retrieval được viết lại trung tính:

- `knowledge.uploadEntrySubtitle`
- `knowledge.unpublishSuccess`
- `knowledge.guidePublish`

## 6. Lỗi i18n đã phát hiện

- Fallback cũ của `translate` dùng English khi thiếu bản dịch, sau đó trả trực tiếp key; điều này có thể làm lộ sai ngôn ngữ hoặc key. Hàm nay chỉ tra dictionary ngôn ngữ hiện hành, ghi lỗi phát triển và trả chuỗi rỗng khi thiếu khóa.
- Thông báo API từng có thể hiển thị nguyên văn nội dung backend (có thể là tiếng Việt) khi UI đang ở EN.
- Profile, Admin AI Feedback, Admin Console, Legal Chat, Verify Email, Refund Email Confirmation và Documents có chuỗi UI hardcode/rẽ nhánh thủ công.
- Một số list/column được tạo từ chuỗi trực tiếp; các vị trí phát hiện đã chuyển sang `t(...)`, nên render lại theo context ngôn ngữ.

## 7. Chuỗi hardcode đã được chuyển sang i18n

- Namespace mới/hoàn thiện: `profile.*`, `feedback.admin.*`, `errors.*`.
- Khóa hỗ trợ: `documents.deleting`, `documents.deleteAria`, `chat.inputPlaceholderWithDocuments`, `chat.feedback.saveError`, `knowledge.pendingExtraction`.
- Khóa trạng thái/liên kết: `auth.verifyEmail.expired`, `auth.verifyEmail.alreadyUsed`, `auth.verifyEmail.invalidToken`, `refund.emailConfirmation.used`.
- Thao tác quản trị: `admin.users.deactivate`, `admin.users.deactivateSuccess`, `admin.users.deactivateError`, `admin.users.restore`, `admin.users.restoreSuccess`, `admin.users.restoreError`.

## 8. Trang và component đã kiểm tra

- Pages trong `frontend/src/pages/**`, đặc biệt Knowledge Base, Expert Revenue, Admin, Billing/Refund, Settings/Profile, Auth, Documents và Chat.
- Reusable components trong `frontend/src/components/**`, đặc biệt Knowledge Base, Admin AI Feedback và Chat feedback.
- Sidebar, topbar, pagination, table, modal, toast và status được đối chiếu với dictionary hiện có.
- Các enum hiển thị dùng map key như `knowledge.status.*`, `knowledge.visibility.*`, `expertRevenue.payment.*`, `contracts.status.*`, `feedback.*`, `refund.*` và `legalTickets.*`.

## 9. Chi tiết tại trang Expert Revenue

`LawyerRevenuePage.tsx` đã không chứa chuỗi Việt/Anh hardcode. Tiêu đề, mô tả, nút làm mới, metric, bảng, empty state, lỗi và thông báo payout đều lấy từ `expertRevenue.*`/`common.*`; trạng thái thanh toán map từ giá trị raw `UNPAID`, `PENDING`, `PAID` sang `expertRevenue.payment.*`. Locale định dạng tiền và thời gian đổi theo EN/VI ngay khi context đổi.

## 10. Xử lý trạng thái và enum từ API

Giá trị raw API không bị đổi. Component chuyển giá trị máy đọc được sang nhãn qua translation key tập trung, ví dụ `t(\`expertRevenue.payment.${item.paymentStatus}\`)`, `getLegalTicketStatusLabel(item.ticketStatus, t)` và map Knowledge Base status/visibility. Không có enum nào được dịch trước khi gửi request.

## 11. Đồng bộ chuyển đổi EN/VI

`AppStore` lưu `lexiguard.language` trong localStorage, khởi tạo lại từ giá trị hợp lệ, cập nhật `document.documentElement.lang`, title và meta description. `useI18n` tạo `t` phụ thuộc `language`, nên component/context render lại tức thì. `getRuntimeLanguage` được dùng riêng cho thông báo lỗi request phát sinh ngoài React để đảm bảo cũng đọc ngôn ngữ đã lưu tại thời điểm lỗi.

## 12. Kiểm tra đồng nhất locale

Script PowerShell trích key từ hai dictionary `en` và `vi` trong `frontend/src/utils/i18n.ts`, so sánh hai chiều và kiểm tra duplicate. Kết quả:

- `en_count=2061`
- `vi_count=2061`
- Không có key chỉ tồn tại ở EN.
- Không có key chỉ tồn tại ở VI.
- Không có duplicate key trong từng dictionary.

## 13. Kết quả lint

Lệnh `npm run lint` trong `frontend` chạy thành công. Script hiện hành gọi `tsc --noEmit`.

## 14. Kết quả typecheck

Lệnh `npm run typecheck` chạy thành công (`tsc --noEmit`).

## 15. Kết quả test

`frontend/package.json` không định nghĩa script `test` hoặc framework test; không có lệnh test frontend để chạy mà không tự tạo lệnh ngoài cấu hình dự án.

## 16. Kết quả production build

Lệnh `npm run build` thành công: `tsc -b && vite build`; Vite transform 1764 modules và tạo bundle production. Có cảnh báo bundle `index` lớn hơn 500 kB, đây là cảnh báo tối ưu chunk có sẵn, không phải lỗi build.

## 17. Lỗi tồn tại từ trước

Không có lỗi lint/typecheck/build còn lại. Cảnh báo kích thước chunk Vite được ghi nhận ở mục build và không thay đổi trong phạm vi task.

## 18. Xác nhận phạm vi backend và AI service

Không có tệp nào trong `backend/**` hoặc `ai-service/**` bị chỉnh sửa. API response field `active`/các field Knowledge Base khác không bị xóa hoặc đổi tên.

## 19. Các lệnh kiểm tra đã chạy

```powershell
npm run lint
npm run typecheck
npm run build
rg -n -i "used...ai|ai...retrieval|..." frontend/src
rg -n "knowledge.ingestedDocuments.active|activeFilter|active: activeFilter" frontend/src
git diff --check
git diff --name-only
```

Ngoài ra đã chạy script PowerShell đối chiếu key EN/VI nêu tại mục 12.

## 20. Kết luận và checklist nghiệm thu

- [x] Cột Active/Inactive, badge, filter và query parameter `active` của Knowledge Base đã được giữ lại/khôi phục.
- [x] Không còn câu mô tả phụ về việc bản ghi không được dùng cho truy xuất AI.
- [x] Không đổi backend, AI service, API contract hay logic retrieval server-side.
- [x] Locale EN/VI có số key và cấu trúc tương ứng bằng nhau.
- [x] Các chuỗi UI hardcode phát hiện trong các page/component liên quan đã chuyển sang i18n.
- [x] Lỗi API không còn hiển thị trực tiếp message backend sai ngôn ngữ.
- [x] Expert Revenue dùng key i18n và enum mapping EN/VI.
- [x] Lint, typecheck và production build đều thành công.
- [x] `git diff --check` không báo lỗi whitespace; các tệp thay đổi thuộc `frontend/src/**` và báo cáo bắt buộc ở repository root.
