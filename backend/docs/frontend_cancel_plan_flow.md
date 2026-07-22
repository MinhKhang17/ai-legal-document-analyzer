# Frontend flow - Hủy gia hạn gói dịch vụ

## Mục tiêu

Phân biệt rõ hai nghiệp vụ:

- **Hủy gia hạn:** user vẫn sử dụng gói trả phí đã mua cho tới khi hết hạn hoặc dùng hết quota.
- **Hoàn tiền toàn phần:** gói trả phí có thể bị hủy ngay và user được chuyển về Free.

## API hủy gia hạn

```http
PUT /api/v1/subscriptions/my-plan/{customerPlanId}/cancel
```

Sau khi hủy thành công, backend vẫn trả gói hiện tại với các giá trị chính:

```json
{
  "subscriptionPlan": {
    "planType": "PREMIUM"
  },
  "status": "ACTIVE",
  "usedQuota": 50,
  "remainingQuota": 50,
  "autoRenew": false,
  "scheduledSubscriptionPlan": {
    "planType": "FREE"
  },
  "planChangeEffectiveAt": "2026-08-20T10:00:00"
}
```

## Yêu cầu hiển thị frontend

Sau khi user bấm **Hủy gói**:

1. Không đổi badge gói hiện tại sang `FREE` ngay.
2. Tiếp tục hiển thị Plus/Premium và quota hiện tại, ví dụ `50/100`.
3. Đổi nút hoặc nhãn trạng thái thành **Đã hủy gia hạn**.
4. Hiển thị thông báo: **Gói vẫn dùng được đến ngày {planChangeEffectiveAt}**.
5. Nếu có `scheduledSubscriptionPlan`, hiển thị: **Sau đó sẽ chuyển sang gói Free**.
6. Sau khi API cancel thành công, gọi lại API lấy `my-plan` để đồng bộ dữ liệu.
7. Chỉ đổi giao diện sang Free khi backend thực sự trả:

```text
subscriptionPlan.planType = FREE
```

## Trường hợp chuyển về Free

Backend tự chuyển gói hiện tại sang Free khi xảy ra một trong hai điều kiện:

- Đã đến `planChangeEffectiveAt`/ngày hết hạn của gói trả phí.
- User đã sử dụng hết quota của gói trả phí.

Khi chuyển sang Free, backend mở chu kỳ quota Free mới. Frontend chỉ cần refresh `my-plan`, không tự tính hoặc
tự đổi gói dựa trên quota ở client.

## Không dùng chung UI với hoàn tiền

| Nghiệp vụ | Gói trả phí sau thao tác | Cách hiển thị |
|---|---|---|
| Hủy gia hạn | Vẫn `ACTIVE` đến hết hạn/hết quota | Hiển thị gói hiện tại và nhãn `Đã hủy gia hạn` |
| Hoàn tiền toàn phần | Có thể chuyển Free ngay | Hiển thị theo `subscriptionPlan` backend trả về |

Frontend không được suy luận rằng `autoRenew=false` đồng nghĩa user đang dùng Free.

## Checklist test FE

- [ ] Premium đang dùng `50/100`, hủy gói vẫn hiển thị Premium `50/100`.
- [ ] Sau khi hủy, `autoRenew=false` và nút hủy không tiếp tục gửi request lặp lại.
- [ ] Hiển thị đúng ngày chuyển gói từ `planChangeEffectiveAt`.
- [ ] Refresh trang sau khi hủy vẫn hiển thị gói trả phí hiện tại.
- [ ] Khi backend trả `subscriptionPlan.planType=FREE`, UI chuyển sang Free và hiển thị quota Free mới.
- [ ] Luồng hoàn tiền toàn phần vẫn cập nhật Free ngay nếu response backend trả Free.
