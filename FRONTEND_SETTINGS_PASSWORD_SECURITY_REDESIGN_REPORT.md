# Báo cáo tách và thiết kế lại chức năng đổi mật khẩu

## 1. Mục tiêu chỉnh sửa

Trang Cài đặt ban đầu đặt lựa chọn giao diện, ngôn ngữ và form đổi mật khẩu trong cùng một màn hình. Form mật khẩu chỉ gồm ba input nằm ngang và một nút gửi, khiến chức năng bảo mật bị hòa lẫn với các tùy chọn giao diện, khó đọc trên màn hình hẹp và thiếu các trạng thái hỗ trợ rõ ràng.

Mục tiêu của thay đổi là giữ `/settings` tập trung vào tùy chọn chung, chuyển quản lý mật khẩu sang một khu vực bảo mật độc lập, đồng thời nâng cấp form thành giao diện tài khoản chuyên nghiệp nhưng không thay đổi API hay business logic.

## 2. Phạm vi thực hiện

- Chỉ thay đổi mã nguồn Frontend và tạo báo cáo này.
- Chỉ đọc Backend để xác nhận hợp đồng API đổi mật khẩu.
- Không thay đổi Backend, Backend configuration, API contract, database, authentication/authorization hoặc AI Service.
- Không thay đổi service `changePassword`, endpoint, header xác thực hoặc payload hiện hữu.
- Không thay đổi các chức năng chat, forgot-password, workspace hay các cải tiến Frontend có sẵn trong working tree.

## 3. Phân tích cấu trúc Settings ban đầu

`SettingsPage.tsx` trước đây đảm nhiệm đồng thời:

- lựa chọn Light/Dark/System;
- lựa chọn tiếng Việt/tiếng Anh;
- state của ba mật khẩu;
- validation đổi mật khẩu;
- gọi API và xử lý toast;
- reload trang sau khi đổi thành công.

Hai card giao diện/ngôn ngữ nằm trong grid hai cột. Bên dưới là một card mật khẩu, trong đó `md:grid-cols-3` ép mật khẩu hiện tại, mật khẩu mới và xác nhận vào cùng một hàng. Các input chưa có nút hiện/ẩn, lỗi validation chỉ xuất hiện dưới dạng toast chung và không gắn với từng trường.

Frontend đã dùng các route màn hình chức năng phẳng trong React Router và cùng nằm trong `AppShell`; dự án không có navigation tab/nested settings sẵn có.

## 4. Phương án tách chức năng bảo mật

Đã tạo route chuyên biệt:

```text
/settings/security
```

Route nằm trong nhánh đã xác thực của `AppShell`, cùng cấp với `/settings`. Cách này phù hợp pattern route hiện tại, hỗ trợ truy cập trực tiếp và browser refresh mà không tạo navigation system mới.

Trang `/settings` giữ theme/language và thay form cũ bằng thẻ điều hướng “Account security / Bảo mật tài khoản”. Banner bắt buộc đổi mật khẩu của tài khoản tạm thời được cập nhật để mở route mới. Khi đang ở chính trang bảo mật, banner toàn cục được ẩn để tránh lặp; thông báo tương ứng được trình bày ngay trong password card.

## 5. Các tệp đã thay đổi

- `frontend/src/pages/settings/SettingsPage.tsx`: loại bỏ state/form/API mật khẩu khỏi trang tùy chọn chung; thêm thẻ điều hướng bảo mật có icon, mô tả, mũi tên, hover và keyboard focus.
- `frontend/src/pages/settings/AccountSecurityPage.tsx` (mới): trang bảo mật tài khoản, password card, form responsive, validation theo trường, ba nút hiện/ẩn độc lập và các trạng thái submit/success/error.
- `frontend/src/routes/router.tsx`: đăng ký route `/settings/security` trong nhánh ứng dụng đã xác thực.
- `frontend/src/components/common/AppShell.tsx`: đổi liên kết “Change now / Đổi ngay” sang route bảo mật và tránh lặp banner trên chính trang này.
- `frontend/src/utils/i18n.ts`: thêm các nhãn, mô tả, helper và lỗi validation cần thiết bằng tiếng Anh/Việt. Những translation khác có sẵn được giữ nguyên.
- `FRONTEND_SETTINGS_PASSWORD_SECURITY_REDESIGN_REPORT.md` (mới): báo cáo hiện tại.

Không có tệp Backend, AI Service hoặc API service nào bị thay đổi.

## 6. Giao diện trang Cài đặt sau khi chỉnh sửa

Trang Cài đặt tiếp tục sử dụng `PageHeader`, page container và grid hiện hữu. Hai card sau được giữ nguyên hành vi:

- Appearance / Giao diện: Light, Dark, System.
- Language / Ngôn ngữ: Vietnamese, English.

Phần bên dưới được cân bằng bằng một navigation card toàn chiều rộng. Card có:

- icon `ShieldCheck`;
- tiêu đề Bảo mật tài khoản;
- mô tả ngắn;
- mũi tên điều hướng;
- trạng thái hover ở cả hai theme;
- focus outline rõ ràng;
- semantic `Link` đến `/settings/security`.

Không còn form mật khẩu hoặc khoảng trống bất hợp lý trên trang tùy chọn chung.

## 7. Thiết kế trang Bảo mật tài khoản

Trang mới dùng đúng `PageHeader`, `Card`, `Button`, typography và theme token của ứng dụng.

- Header: eyebrow “Cài đặt”, tiêu đề “Bảo mật tài khoản”, mô tả quản lý mật khẩu và nút quay lại Settings.
- Password card: tiêu đề “Đổi mật khẩu”, mô tả yêu cầu xác minh mật khẩu hiện tại và icon bảo mật.
- Nếu tài khoản đang dùng mật khẩu tạm, notice có icon, border/background warning và thời hạn thực từ user state.
- Mật khẩu hiện tại nằm ở một hàng riêng với chiều rộng đọc thoải mái.
- Mật khẩu mới và xác nhận dùng grid hai cột từ breakpoint `md`; tự xếp dọc trên màn hình nhỏ.
- Footer form có ghi chú an toàn và nút submit. Nút toàn chiều rộng trên mobile, trở về kích thước vừa phải trên desktop.
- Loading có spinner, text “Đang đổi mật khẩu…” và khóa toàn bộ input/nút để chặn request lặp.
- API error có inline alert và toast thân thiện; không hiển thị lỗi Backend thô.
- Success có inline status và toast; sau đó giữ nguyên hành vi reload 600 ms có sẵn để làm mới trạng thái tài khoản.

## 8. Chức năng ẩn và hiện mật khẩu

Ba field có state hoàn toàn độc lập:

- `showOldPassword`;
- `showNewPassword`;
- `showConfirmPassword`.

Mỗi field có button `type="button"`, icon `Eye`/`EyeOff`, `aria-pressed` và accessible label kết hợp hành động với tên field. Toggle một field không ảnh hưởng hai field còn lại và không submit form.

Giá trị input nằm trong state form riêng, không bị tạo lại khi đổi `type`. Vị trí selection/cursor được ghi nhận trước toggle và khôi phục ở frame tiếp theo. Sau API success, cả ba visibility state trở về hidden và các giá trị được xóa. Khi API thất bại, giá trị và visibility hiện tại được giữ nguyên.

## 9. Validation và tích hợp API

Hợp đồng API hiện hữu được giữ nguyên tuyệt đối:

```ts
changePassword({
  oldPassword,
  newPassword,
  confirmNewPassword,
})
```

- Endpoint vẫn là `POST /api/v1/users/change-password`.
- Vẫn dùng `buildAuthHeaders`, access token hiện hữu, `Content-Type: application/json` và `credentials: include`.
- Không sửa `user.service.ts` hoặc Backend DTO.

Validation Frontend khớp các rule đã tồn tại trong UI/Backend:

- mật khẩu hiện tại bắt buộc theo UX hiện hữu;
- mật khẩu mới bắt buộc;
- tối thiểu 8 ký tự theo Backend `@Size(min=8)`;
- xác nhận bắt buộc và phải khớp;
- mật khẩu mới phải khác mật khẩu hiện tại, đúng rule Backend.

Lỗi được đặt cạnh field tương ứng và nối với input qua `aria-describedby`. Chỉ khi validation hợp lệ mới gọi service. Không xóa form trước khi API xác nhận thành công.

## 10. Light Mode, Dark Mode và responsive

Giao diện dùng các token/class có sẵn như:

- `bg-white`, `bg-surface-container-low`, `text-on-surface`, `text-on-surface-variant`;
- `border-legal-border`, `text-primary`, `focus:ring-primary`;
- các variant `dark:bg-slate-*`, `dark:border-slate-*`, `dark:text-slate-*`, `dark:text-inverse-primary`;
- semantic warning, error và success styles đã quen thuộc trong dự án.

Responsive behavior:

- Desktop/laptop: current password một hàng, hai field mới song song, card tối đa 4xl.
- Tablet/mobile: hai field tự xếp dọc, page gutter do `DashboardLayout` quản lý.
- Footer xếp dọc đảo thứ tự trên mobile để nút dễ thao tác và toàn chiều rộng; desktop chuyển về hàng ngang.
- Input có `pr-11`, nên eye button không che nội dung.
- Các block dùng `min-w-0`, flex/grid responsive và không thêm fixed width gây horizontal overflow.

## 11. Đa ngôn ngữ và accessibility

- Tất cả nội dung mới nằm trong `i18n.ts`, có đủ tiếng Anh và tiếng Việt tự nhiên.
- Mỗi label nối đúng input qua `htmlFor`/`id`.
- Current password dùng `autocomplete="current-password"`.
- Hai field mới dùng `autocomplete="new-password"`.
- Form semantic hỗ trợ Enter để submit.
- Eye controls là semantic button, keyboard accessible, có focus outline và accessible label riêng theo field.
- Input invalid có `aria-invalid`; lỗi/helper có ID và `aria-describedby`.
- API error dùng `role="alert"`; success/temporary notice dùng `role="status"`, success có `aria-live="polite"`.
- Icon trang trí dùng `aria-hidden="true"`; trạng thái không chỉ truyền đạt bằng màu.
- Tab order: current password → toggle current → new password → toggle new → confirm → toggle confirm → submit.

## 12. Bảo mật phía Frontend

- Password state chỉ nằm local trong component.
- Không ghi password vào console, local storage, session storage, URL, query parameter hoặc global store.
- Không log payload hay raw Backend error.
- Submit bị disable khi đang gửi để tránh request đồng thời.
- Field chỉ được xóa sau API success; lỗi giữ nguyên dữ liệu để người dùng sửa.
- Sau success, password values bị xóa và visibility được reset về hidden.
- Frontend validation chỉ hỗ trợ UX; Backend vẫn quyết định tính hợp lệ cuối cùng.

## 13. Chức năng được giữ nguyên

- Logic và toast thay đổi theme giữ nguyên.
- Logic và toast thay đổi ngôn ngữ giữ nguyên.
- Login, logout, token storage, token refresh, protected routes, role và permission không thay đổi.
- `changePassword` service, endpoint và payload không thay đổi.
- Hành vi reload sau đổi mật khẩu thành công được giữ nguyên để cập nhật `mustChangePassword`.
- Sidebar, top header, profile menu và các màn hình không liên quan không bị thiết kế lại.
- Banner mật khẩu tạm tiếp tục xuất hiện ở ứng dụng, nhưng link đúng trang bảo mật mới.

## 14. Kết quả kiểm tra

### Lệnh đã chạy

| Lệnh | Trạng thái | Kết quả |
|---|---|---|
| `npm run typecheck` | PASS | `tsc --noEmit`, exit code 0, không lỗi/cảnh báo |
| `npm run lint` | PASS | Script dự án chạy `tsc --noEmit`, exit code 0; dự án không có ESLint script riêng |
| `npm run build` | PASS | `tsc -b && vite build`, exit code 0; 1.705 module được transform và production build hoàn tất |

Build có warning Vite về main chunk lớn hơn 500 kB (`index` khoảng 809,33 kB, gzip 206,89 kB). Đây là warning không chặn build và là vấn đề bundle chung đã tồn tại trước hạng mục.

`package.json` không có unit-test/component-test script nên không có lệnh test tương ứng để chạy.

### Kiểm tra route/runtime

- `GET http://127.0.0.1:4173/settings`: HTTP `200`, có SPA root.
- `GET http://127.0.0.1:4173/settings/security`: HTTP `200`, có SPA root; xác nhận direct navigation/refresh được Vite phục vụ.
- `git diff --check`: không phát hiện whitespace error.
- `git diff --numstat -- backend ai-service`: không có thay đổi Backend/AI Service.
- Audit source không tìm thấy `console`, local storage, session storage hoặc password trong URL ở phần Settings/service.
- Diff xác nhận `user.service.ts` không thay đổi; API contract vẫn nguyên bản.

### Kiểm tra hành vi qua mã nguồn

- Theme/language handlers và option components được giữ nguyên.
- Security entry là link thật đến route mới.
- Ba eye button có state độc lập, `type="button"`, field value không đổi và cursor được khôi phục.
- Validation required/minimum/mismatch/must-differ gắn đúng field.
- Loading khóa input và submit; error không xóa form; success xóa form và ẩn lại cả ba field.
- Light/Dark classes, mobile stack và desktop grid có mặt trong output build.

### Giới hạn kiểm thử trực quan và API thật

Đã thử kết nối công cụ trình duyệt tích hợp nhưng công cụ bị lỗi hạ tầng trước khi mở trang: `codex/sandbox-state-meta: missing field sandboxPolicy`. Vì vậy không tuyên bố đã kiểm tra hình ảnh Light/Dark, viewport hoặc browser console trực tiếp.

Không gửi request đổi mật khẩu thật vì thao tác này thay đổi credential tài khoản và không có thông tin đăng nhập thử nghiệm/ủy quyền cụ thể. Success/API-error được kiểm tra ở mức logic, typecheck và production build, không được khai là end-to-end pass.

## 15. Vấn đề còn tồn tại

- Chưa thể thực hiện visual/browser interaction test do lỗi công cụ nêu trên.
- Chưa thực hiện end-to-end password mutation với tài khoản thật; cần tài khoản test được cấp quyền và xác nhận trước khi thay đổi credential.
- Warning bundle size của Vite còn tồn tại, ngoài phạm vi redesign Settings.
- Lint script hiện chỉ chạy TypeScript compiler; dự án chưa có cấu hình ESLint riêng.

## 16. Kết luận

Chức năng quản lý mật khẩu đã được tách khỏi trang tùy chọn chung thành `/settings/security`. Settings chính vẫn cân bằng với hai card giao diện/ngôn ngữ và một entry bảo mật rõ ràng. Trang mới có cấu trúc form chuyên nghiệp, responsive, hỗ trợ Light/Dark, validation theo trường, accessibility và ba nút hiện/ẩn độc lập, đồng thời giữ nguyên service, payload, auth state và hành vi thành công hiện hữu.

Typecheck, lint và production build đều thành công. Không có Backend hoặc AI Service file nào bị thay đổi; các giới hạn kiểm thử trực quan và đổi mật khẩu end-to-end đã được ghi nhận minh bạch.
