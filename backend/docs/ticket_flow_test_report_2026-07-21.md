# Báo cáo kiểm tra Ticket Flow - 2026-07-21

## Phạm vi

Đối chiếu backend hiện tại với `Ticket_Flow_Test_Cases.docx`, gồm customer, admin, expert, chat, file,
reopen, quota và refund request. Báo cáo phân biệt rõ kết quả automated test với kết quả đọc code; các case
cần nhiều phiên đăng nhập, trình duyệt, storage hoặc tải đồng thời không được tự động xem là PASS.

## Kết quả automated test

Lệnh chạy:

```powershell
.\mvnw.cmd surefire:test
```

```text
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Nhóm test trực tiếp cho ticket flow:

```text
AdminTicketManagementServiceImplTest: 5 PASS
ExpertLegalTicketServiceImplTest:      3 PASS
TicketCollaborationServiceTest:        4 PASS
TicketFileServiceImplTest:             3 PASS
Tổng:                                 15 PASS / 15
```

Các hành vi đã được automated test xác nhận:

- Admin assign expert cho ticket đang chờ duyệt và ticket chuyển sang `ASSIGNED_TO_LAWYER`.
- Ticket `REFUND_REQUEST` không được assign expert.
- Expert được assign có thể start review, request more info và resolve.
- Expert không được assign bị từ chối; ticket `CLOSED` không thể resolve lại.
- Attachment dùng luồng collaboration chặn extension thực thi và MIME không khớp chữ ký file.
- Share token được lưu dạng SHA-256 và link mặc định hết hạn khoảng 7 ngày.

## Case trong file Word chưa đạt theo code hiện tại

Các mục dưới đây là lỗi hoặc khoảng trống đã xác nhận bằng cách đọc đúng service/DTO đang được API sử dụng.

| ID trong test plan | Kết quả | Nguyên nhân |
|---|---|---|
| TCK-ASG-02 | FIXED LOCAL | Đã bắt buộc assignee có role `EXPERT` và đang active. |
| TCK-ASG-03 | FIXED LOCAL | Đã giới hạn assign/reassign vào các trạng thái ticket còn xử lý được. |
| TCK-RASG-03 | FIXED LOCAL | Reassign cùng expert trả response hiện tại, không ghi DB và không gửi notification. |
| TCK-RASG-04 | PASS | Team xác nhận điều kiện thanh toán của luồng hiện tại đã ổn. |
| TCK-CHAT-05 | FIXED LOCAL | Thêm `clientMessageId`, lookup idempotent và unique index theo ticket/sender/client ID. |
| TCK-FILE-02 | FIXED LOCAL | `TicketFileServiceImpl` giới hạn mặc định 5 MB qua `app.ticket-files.max-size-mb`. |
| TCK-FILE-03 | FIXED LOCAL | Kiểm tra magic bytes/nội dung thật so với MIME và extension. |
| TCK-FILE-05 | FIXED LOCAL | Kiểm tra độ dài Base64 trước khi decode và kiểm tra chính xác kích thước sau decode. |
| TCK-FILE-09 | FIXED LOCAL | Nếu lưu metadata DB lỗi, backend xóa file vật lý vừa ghi. |
| TCK-SEC-07 | FIXED LOCAL | Customer-facing service đã xóa `expert_internal_note` trước khi trả response. |
| TCK-Q-04, E2E-TCK-06 | FAIL/RISK | Check quota và tạo/count ticket không có lock hoặc thao tác atomic; request đồng thời có thể vượt quota. |
| TCK-Q-09, TCK-Q-11, E2E-TCK-08 | FAIL | Quota đọc trực tiếp từ `SubscriptionPlan` hiện tại, chưa snapshot quyền lợi vào `CustomerPlan`; sửa master plan có thể đổi quota giữa chu kỳ. |

Lưu ý: kiểm tra MIME đã PASS ở `TicketCollaborationService`, nhưng chưa PASS cho endpoint Base64 trong
`TicketFileServiceImpl`; đây là hai đường upload khác nhau.

## Case chưa thể kết luận, cần test trên server/browser

| Nhóm | Cần kiểm tra |
|---|---|
| Polling/chat | Hai user mở đồng thời, thứ tự message, refresh liên tục, mất mạng và retry. |
| Session/auth | F5 route ticket, token hết hạn, nhiều tab, customer/expert khác truy cập ticket hoặc file. |
| Upload/download | PDF, DOCX, image thật; visibility `CUSTOMER`/`INTERNAL`; file thiếu trên disk; tên file Unicode. |
| Email/notification | Email lỗi không rollback nghiệp vụ, gửi trùng, retry và link trong email. |
| Reopen | Đúng mốc 7 ngày, timezone server và xử lý sau khi reopen. |
| Legacy/migration | Chạy migration trên bản sao database server có dữ liệu cũ. |
| Performance | 20-100 request đồng thời cho create ticket, message, polling và quota. |

## 8 luồng E2E ưu tiên để team test deploy

1. Customer tạo `CONTACT_EXPERT` -> admin assign -> expert review -> request info -> customer reply -> expert resolve -> customer close.
2. Admin reassign expert; expert cũ phải mất quyền ngay, expert mới truy cập được.
3. Expert upload file customer-visible và internal; customer chỉ thấy/tải file được phép.
4. Customer đóng ticket rồi reopen trong 7 ngày; quá 7 ngày phải bị từ chối.
5. Refund request chỉ do admin xử lý và không tiêu hao quota expert.
6. Gửi đồng thời nhiều request tạo ticket khi quota còn 1; chỉ đúng số lượng quota được thành công.
7. Retry cùng thao tác create/message; không được sinh duplicate.
8. Admin sửa cấu hình plan; quyền lợi của gói đã mua không được thay đổi giữa chu kỳ nếu nghiệp vụ yêu cầu snapshot.

## Kết quả test trực tiếp bản deploy

URL: `https://ai-legal-document-analyzer-eta.vercel.app`

| Case | Kết quả |
|---|---|
| User login và vào dashboard | PASS |
| User mở danh sách ticket | PASS, load được 2 ticket |
| User mở ticket `REOPENED` | PASS, không bị chuyển về login |
| Admin login và mở danh sách/chi tiết ticket | PASS |
| Admin thử assign expert cho `REFUND_REQUEST` | PASS phía backend: trả `REFUND_TICKET_ADMIN_ONLY`, không thay đổi assignment |
| UI refund ticket | FE ISSUE: vẫn hiển thị combobox và nút phân công dù backend luôn từ chối |

Frontend cần ẩn toàn bộ khối phân công/phân công lại khi `ticket_type === REFUND_REQUEST`, thay vì để admin
thao tác rồi mới hiển thị lỗi backend.

## Kết luận gửi team

- Backend regression sau khi sửa local: **50/50 PASS**.
- Automated ticket tests hiện tại: **15/15 PASS**.
- Đã sửa local 3 nhóm quan trọng: assign sai role, assign sai trạng thái và lộ internal note.
- Các rủi ro ticket trong nhóm reassign/message/file nêu trên đã được sửa; quota đồng thời và plan snapshot vẫn là nhóm riêng.
- Bản deploy chỉ có các sửa trên sau khi team deploy backend mới.

Khi case server fail, ghi URL, role, ticket ID, HTTP status, response body, timestamp và request ID để đối chiếu log.
