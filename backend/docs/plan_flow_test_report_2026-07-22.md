# Báo cáo kiểm tra Plan Flow - 2026-07-22

## Phạm vi

Đối chiếu backend hiện tại với `backend/docs/Plan_Flow_Test_Cases.md` (Free/Standard/Premium, VNPay,
Upgrade/Downgrade, Billing, Admin Plan). Báo cáo phân biệt rõ: (a) đã sửa code + có automated test xác nhận,
(b) có cơ chế code từ trước nhưng chưa có automated test riêng, (c) chưa code — vẫn là gap thật. Các case cần
VNPay sandbox thật, AI service thật, hoặc trình duyệt thật không được tự động xem là PASS.

## Kết quả automated test

Lệnh chạy:

```powershell
.\mvnw.cmd clean test
```

```text
Tests run: 86, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Trong đó:

```text
Unit test (Mockito, không DB thật):        78 PASS
E2E test (Testcontainers Postgres thật):    8 PASS  -- com.analyzer.api.e2e.PlanFlowE2ETest
```

E2E chạy trên Postgres thật (không mock) vì các cơ chế được test — `pg_advisory_xact_lock`, unique partial
index, chữ ký HMAC VNPay — không thể giả lập đúng bằng H2/Mockito.

## Đã sửa code + có automated test xác nhận (phiên làm việc này)

| Mục tài liệu | Nội dung | Đã làm |
|---|---|---|
| Mục 19 #1 | Cancel: hủy ngay hay giữ đến endDate? | **Chốt: giữ quyền đến hết chu kỳ.** `CustomerPlanServiceImpl.cancelPlan()` viết lại thành graceful (schedule downgrade về FREE), tách riêng `cancelPlanAndActivateFree()` (hủy ngay) chỉ dùng cho refund. |
| PLAN-CAN-01, 02, 06, 09 | Cancel giữ nguyên quyền lợi, idempotent, hết mismatch UI "hủy gia hạn" vs backend hủy ngay | Cover bằng test `e2e05_cancelKeepsPremiumEntitlementUntilEndOfPaidCycle` + unit test `CustomerPlanServiceImplTest` (5 case mới) |
| Mục 19 #2 | Usage "tháng" tính theo mốc nào? | **Chốt: theo chu kỳ CustomerPlan** (`billingCycleStartAt/EndAt`), không phải tháng lịch. Sửa `SubscriptionQuotaServiceImpl.getCurrentUsage()`. |
| Mục 19 #3, PLAN-QD-03 | Upload/OCR fail có tiêu quota phân tích không? | **Chốt: không.** Quota phân tích chỉ đếm document `status=READY` theo `processedAt`, không đếm theo `uploadedAt` nữa. |
| Mục 19 #6, PLAN-UP-08/09, PLAN-ADM-05/06, PLAN-UI-10 | Admin sửa giá/quota giữa chu kỳ có ảnh hưởng user đang ACTIVE không? | **Chốt: không, dùng snapshot.** Thêm 13 cột `*Snapshot` trên `CustomerPlan` + `CustomerPlanSnapshotHelper`, áp dụng tại `subscribe()`. `/me` không còn re-fetch plan sống theo id (đã bỏ `getPlanById`, dùng `toResponse(SubscriptionPlan)` trực tiếp từ effective view). |
| Mục 19 #12 | Snapshot tại subscribe hay tại callback? | **Chốt: tại subscribe()`**, cùng lúc với `PaymentTransaction.amount`. |
| Mục 19 #8, PLAN-DOWN-09 | Scheduled plan bị disable trước effectiveAt | **Chốt: fallback về FREE.** `CustomerPlanExpiryHelper.applyExpiryOrScheduledChange()` kiểm tra `scheduled.getActive()` trước khi áp dụng. |
| PLAN-DOWN-03/04/05/06 (hardening) | Lost-update khi 2 request chạm cùng CustomerPlan ngay sau endDate | Thêm `findByIdForUpdate` (row lock) trước khi mutate trong `getActiveOrHandleExpiry()`/`expireDuePlans()`. |
| E2E-PLAN-01 → 08 | 8 kịch bản E2E ưu tiên | **Cả 8 đã tự động hóa**, xem `PlanFlowE2ETest.java`. |

## Đã có cơ chế code từ trước, xác nhận lại qua rà soát (không phải sửa mới)

Nhóm P0 mục 4 (cả 4), idempotency VNPay (PLAN-PAY-03..10), concurrency quota qua `UserQuotaLock`
(PLAN-QW-02, PLAN-QD-02, PLAN-QA-02, PLAN-QE-02, PLAN-QDR-02), trust-server-not-client (PLAN-SUB-08, PLAN-SEC-02),
IDOR 403 (PLAN-SUB-12, PLAN-SEC-01), unique constraint DB cho plan name/type kèm race (PLAN-ADM-03), và
DataInitializer chỉ seed khi DB rỗng (PLAN-SEED-01/02/03) — đều đã có logic đúng trong code, được E2E test
gián tiếp xác nhận lại (không phải phát hiện mới).

## Bug production thật phát hiện qua quá trình viết E2E (không unit test nào bắt được)

1. **Flyway chưa từng chạy thật ở bất kỳ môi trường nào** — thiếu dependency `spring-boot-flyway` (Spring Boot
   4.0.6 tách module này ra riêng). `spring.flyway.*` bị bỏ qua âm thầm, kể cả production. **Đã fix** trong `pom.xml`.
2. **2 cặp file migration trùng version** (`V20260721_01`, `V20260721_05` mỗi cái từng có 2 file, đã commit sẵn
   bởi 2 người khác nhau) — Flyway sẽ crash ngay khi khởi động một khi (1) được fix. **Đã fix** bằng cách rename
   đúng 1 file (`V20260721_01__ticket_message_idempotency.sql` → `_06`), không đụng nội dung/tên file nào khác
   đã commit.
3. **`SubscriptionQuotaServiceImpl.getCurrentPlan()`/`getCurrentUsage()` là `readOnly=true`** nhưng có thể kích
   hoạt ghi (row lock xử lý hết hạn) — Postgres từ chối `SELECT FOR UPDATE` trong transaction read-only. **Đã fix.**

Đã rà soát an toàn deploy toàn bộ 18 file migration: mọi thao tác đều `IF NOT EXISTS`/có backfill trước
`NOT NULL`/dùng `NOT VALID` cho constraint — an toàn cho lần đầu Flyway thật sự chạy trên DB server đã có
dữ liệu (baseline-on-migrate không xóa dữ liệu, đúng như `backend/docs/database-migrations.md` mô tả).

## Case trong test plan chưa đạt / còn treo theo code hiện tại

| ID / mục | Kết quả | Nguyên nhân |
|---|---|---|
| Mục 19 #7 | **CHƯA CODE** | Không có timeout/TTL cho `CustomerPlan`/`PaymentTransaction` ở trạng thái `PENDING` bị bỏ dở; cũng chưa có policy cho nhiều PENDING cùng plan. Gap thật, cần chốt và code riêng. |
| Mục 19 #4, PLAN-QS-03 | RỦI RO THẤP, chưa policy tường minh | `sumFileSizeByUserIdAndStatusNot` tính theo status hiện tại nên không double-count, nhưng chưa có test riêng cho soft-delete rồi restore nhiều lần. |
| Mục 19 #5, PLAN-QT-02/04 | MỘT PHẦN | Chỉ cộng `SYSTEM_PROMPT_TOKEN_OVERHEAD` cố định vào ước lượng trước khi gọi AI; RAG context, retry, streaming chưa được ước lượng — ghi rõ trong code là cố ý đơn giản hóa để tránh chặn nhầm request. |
| PLAN-PAY-13 | CHƯA KIỂM TRA RIÊNG | Double URL encoding / tham số trùng tên dựa vào hành vi mặc định của Spring `@RequestParam Map`, chưa có test riêng xác nhận không bypass được checksum. |
| PLAN-UI-01..09 | NGOÀI PHẠM VI | Toàn bộ là frontend, phiên này chỉ sửa backend. |

## Case chưa thể kết luận, cần test trên server/staging/VNPay sandbox thật

| Nhóm | Cần kiểm tra |
|---|---|
| VNPay thật | Toàn bộ callback trong E2E hiện dùng chữ ký HMAC tự ký (giả lập), chưa test với VNPay sandbox thật (URL thật, `vnp_BankCode`, redirect thật). |
| AI service thật | Upload/OCR pipeline (`WorkspaceServiceImpl.uploadDocument`) gọi HTTP sang Python AI service — E2E bỏ qua bước này (insert thẳng document `READY` để test quota), chưa test với AI service thật đang chạy. |
| Deploy lần đầu | Đây là lần đầu Flyway thật sự chạy hết 18 migration trên môi trường có DB — nên test trên bản sao/staging DB trước khi chạm production, kiểm tra `flyway_schema_history` sau khi khởi động. |
| Docker build | Rebuild lại image backend để `spring-boot-flyway` (dependency mới) được đóng gói — chưa test trên chính docker-compose thật (chỉ test qua Testcontainers). |
| Frontend | Toàn bộ UI mục 15 (Billing/Pricing hiển thị đúng snapshot vs plan mới, cảnh báo cancel giữ quyền đến endDate thay vì mất ngay) chưa được cập nhật/test — copy hiện tại của FE có thể không khớp hành vi graceful-cancel mới. |

## 8 luồng E2E ưu tiên (mục 18) — trạng thái

Khác với báo cáo ticket flow trước đó (liệt kê để test tay), **cả 8 luồng này đã được tự động hóa** trong
`PlanFlowE2ETest.java`, chạy PASS trên Postgres thật:

1. E2E-PLAN-01 — User mới không có CustomerPlan → Free fallback → vượt quota workspace/document → PASS.
2. E2E-PLAN-02 — Free mua Standard → PENDING → VNPay callback lặp → chỉ 1 Standard ACTIVE, idempotent → PASS.
3. E2E-PLAN-03 — Standard dùng 20 analysis/500k token → upgrade Premium → usage giữ nguyên → PASS.
4. E2E-PLAN-04 — Premium schedule Standard → qua endDate → quota service hỏi trước → Standard ACTIVE, không fallback Free → PASS.
5. E2E-PLAN-05 — Premium còn 20 ngày → cancel → vẫn upload 20MB/tạo expert ticket được (theo quyết định mới, khác kỳ vọng cũ trong file test case) → PASS.
6. E2E-PLAN-06 — 20 callback VNPay đồng thời → 1 activation duy nhất → PASS.
7. E2E-PLAN-07 — Free còn 1 slot upload → 50 request đồng thời → tối đa 1 thành công → PASS.
8. E2E-PLAN-08 — Admin sửa quota Premium → user cũ giữ snapshot, user mới dùng version mới → PASS.

## Kết luận gửi team

- Build + test: **86/86 PASS** (78 unit + 8 E2E Testcontainers).
- 6/12 quyết định nghiệp vụ mục 19 đã chốt và code xong trong phiên này (#1, #2, #3, #6, #8, #12); còn treo
  thật sự: **#7 (PENDING timeout — chưa code gì)**, #4 và #5 (đã hoạt động đúng nhưng chưa test/policy tường minh riêng).
- Phát hiện + fix 3 bug production nghiêm trọng liên quan Flyway (chưa từng chạy thật, version trùng, transaction
  read-only xung đột lock) — **bắt buộc rebuild + kiểm tra `flyway_schema_history` trước khi deploy**, khuyến
  nghị chạy trên staging/bản sao DB trước khi chạm production vì đây là lần đầu toàn bộ chuỗi migration chạy thật.
- Chưa test với VNPay sandbox thật và AI service thật — E2E hiện tại tự ký callback/tự insert document để cô lập
  khỏi hạ tầng ngoài, cần thêm một vòng test tay trên staging có đủ 2 dịch vụ ngoài trước khi release.
- Frontend chưa được cập nhật cho hành vi cancel mới (giữ quyền đến endDate) — cần phối hợp FE trước khi thông báo
  tính năng cho user, tránh lặp lại mismatch UI/backend như PLAN-CAN-09 đã ghi nhận.
