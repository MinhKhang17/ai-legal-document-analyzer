# Báo cáo tích hợp AI Service vào Frontend

## 1. Tổng quan

Đã tích hợp hai luồng gọi AI Service vào frontend LexiGuard AI mà không thay đổi cấu trúc chính, không thiết kế lại giao diện và không hard-code URL trong component.

- Trang upload tài liệu mới gọi API phân tích rủi ro hợp đồng.
- Khung chat pháp lý gọi API truy vấn kho tri thức pháp lý.
- URL nền và đường dẫn API được đọc từ biến môi trường Vite.
- Các lỗi kỹ thuật được ghi vào `console.error`, còn giao diện chỉ hiển thị thông báo thân thiện bằng tiếng Việt.

## 2. Các API đã tích hợp

- Upload/phân tích rủi ro hợp đồng: `POST /v2/contracts/upload`
- Chat/truy vấn dữ liệu pháp lý: `POST /knowledge/query-v2`

Hàm gọi API nằm trong `src/services/ai.service.ts` và được export lại qua `src/api/aiApi.ts`.

## 3. Biến môi trường đã thêm

Đã thêm vào file `.env` hiện có và tạo thêm `.env.example` để lưu convention cấu hình có thể commit:

```env
VITE_AI_SERVICE_BASE_URL=http://localhost:8000
VITE_AI_KNOWLEDGE_QUERY_PATH=/knowledge/query-v2
VITE_AI_CONTRACT_UPLOAD_PATH=/v2/contracts/upload
```

Các biến môi trường cũ như `VITE_API_BASE_URL` và nhóm `VITE_AUTH_*` được giữ nguyên.

## 4. File frontend đã chỉnh sửa

- `.env`: thêm biến môi trường cho AI Service trong môi trường local.
- `.env.example`: thêm file mẫu chứa các biến hiện có và nhóm biến AI Service.
- `src/config/api.ts`: thêm cấu hình AI Service và helper `buildAiServiceUrl`.
- `src/types/aiService.ts`: thêm interface cho request/response của upload và query.
- `src/services/ai.service.ts`: thêm client gọi `fetch` cho hai API AI Service.
- `src/api/aiApi.ts`: export lại hàm và type AI API theo convention hiện có.
- `src/components/upload/FileUploadZone.tsx`: hỗ trợ chọn file thật và kéo thả file thật, vẫn giữ callback mock cũ.
- `src/pages/upload/UploadPage.tsx`: kết nối upload file với `uploadContractForRiskAnalysis`.
- `src/components/editor/LegalChatPanel.tsx`: kết nối chat với `queryLegalKnowledge`.

## 5. Luồng upload tài liệu

Tại trang `/upload`, component `UploadPage` truyền callback `handleContractUpload` vào `FileUploadZone`.

Luồng xử lý:

1. Người dùng chọn hoặc kéo thả file.
2. Frontend kiểm tra phần mở rộng file: `pdf`, `docx`, `txt`.
3. Frontend tạo `title` từ loại tài liệu đang chọn và tên file.
4. Hàm `uploadContractForRiskAnalysis(file, { title })` gửi `multipart/form-data`.
5. Field gửi lên backend chỉ gồm:
   - `file`
   - `title`
6. Trong lúc chờ API, hàng đợi hiển thị trạng thái `processing` và thanh tiến độ.
7. Khi thành công, trang hiển thị thẻ kết quả phân tích rủi ro.

Dữ liệu response được map vào UI:

- `filename`, `title`: tiêu đề kết quả và tên file trong hàng đợi.
- `summary.clause_count`: số điều khoản.
- `summary.finding_count`: số phát hiện.
- `summary.high_risk_count`, `medium_risk_count`, `low_risk_count`: thống kê mức rủi ro.
- `knowledge_source_files`: số nguồn tri thức.
- `clauses[].severity`: badge mức rủi ro.
- `clauses[].confidence`: độ tin cậy.
- `clauses[].risk_concept`, `title`, `taxonomy`: tiêu đề phát hiện.
- `clauses[].explanation`: giải thích rủi ro.
- `clauses[].legal_basis`: cơ sở pháp lý.

## 6. Luồng chat với AI pháp lý

Tại trang `/chat`, component `LegalChatPage` vẫn dùng `LegalChatPanel`. Bên trong `LegalChatPanel`, khi người dùng gửi câu hỏi, frontend gọi:

```ts
queryLegalKnowledge({ query, top_k: 5 })
```

Luồng xử lý:

1. Người dùng nhập câu hỏi pháp lý.
2. Frontend thêm message người dùng vào khung chat.
3. Frontend gửi JSON body:

```json
{
  "query": "nội dung câu hỏi",
  "top_k": 5
}
```

4. Trong lúc chờ API, khung chat hiển thị trạng thái `Đang truy vấn AI Service...`.
5. Khi thành công, frontend ưu tiên hiển thị `answer_preview`.
6. Nếu `answer_preview` rỗng nhưng có `chunks`, frontend tạo fallback chỉ tóm tắt các đoạn dữ liệu liên quan, không tự bịa tư vấn pháp lý.
7. Nếu không có `chunks`, frontend hiển thị: `Không tìm thấy dữ liệu pháp lý phù hợp với câu hỏi này.`
8. `chunks` được map thành citation badge bằng `title`, `source`, `chunk_id` và `score` nếu có.

## 7. Cách chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend Vite mặc định chạy tại:

```text
http://localhost:5173
```

## 8. Cách chạy ai-service liên quan

Cách chạy bằng Docker Compose theo cấu trúc hiện có:

```bash
cd ai-service
docker compose up --build
```

Nếu chạy thủ công, lệnh trong Dockerfile tương ứng là:

```bash
cd ai-service
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Cần bảo đảm AI Service đang phục vụ hai endpoint:

- `POST /v2/contracts/upload`
- `POST /knowledge/query-v2`

## 9. Cách kiểm thử upload API

1. Chạy AI Service ở cổng `8000`.
2. Chạy frontend ở cổng `5173`.
3. Đăng nhập vào frontend nếu route guard yêu cầu phiên người dùng.
4. Mở `/upload`.
5. Chọn file `.pdf`, `.docx` hoặc `.txt`.
6. Kiểm tra hàng đợi hiển thị trạng thái đang xử lý.
7. Khi API trả kết quả, kiểm tra thẻ `Kết quả phân tích rủi ro`.
8. Nếu AI Service tắt, UI phải hiển thị: `Không thể kết nối tới AI Service. Vui lòng kiểm tra backend đã chạy chưa.`

## 10. Cách kiểm thử chat API

1. Chạy AI Service ở cổng `8000`.
2. Chạy frontend ở cổng `5173`.
3. Mở `/chat`.
4. Nhập câu hỏi pháp lý.
5. Bấm nút gửi.
6. Kiểm tra loading `Đang truy vấn AI Service...`.
7. Kiểm tra assistant message hiển thị `answer_preview` hoặc fallback từ `chunks`.
8. Kiểm tra citation badge nếu response có `chunks`.
9. Nếu không có dữ liệu phù hợp, UI phải hiển thị thông báo không tìm thấy dữ liệu pháp lý liên quan.

## 11. Lỗi thường gặp và cách xử lý

- AI Service chưa chạy: chạy `docker compose up --build` trong thư mục `ai-service`.
- Sai cổng AI Service: kiểm tra `VITE_AI_SERVICE_BASE_URL`.
- Sai endpoint: kiểm tra `VITE_AI_KNOWLEDGE_QUERY_PATH` và `VITE_AI_CONTRACT_UPLOAD_PATH`.
- File không hợp lệ: chỉ dùng `pdf`, `docx`, `txt`.
- Lỗi validation từ API: kiểm tra request body hoặc multipart field có đúng `query`, `top_k`, `file`, `title`.
- Không thấy kết quả chat đầy đủ: API `query-v2` là API truy xuất dữ liệu, có thể chỉ trả `chunks` và `answer_preview`, không nhất thiết trả lập luận pháp lý hoàn chỉnh.

## 12. Ghi chú kỹ thuật

- Frontend dùng `fetch`, không thêm thư viện HTTP mới.
- Upload API không set thủ công header `Content-Type` để trình duyệt tự sinh boundary cho `multipart/form-data`.
- Chat API gửi JSON với header `Content-Type: application/json`.
- Helper `buildAiServiceUrl` nối `VITE_AI_SERVICE_BASE_URL` với endpoint path từ env.
- `FileUploadZone` vẫn tương thích với luồng mock cũ qua `onFakeUpload`.
- Các lỗi kỹ thuật được log vào console để debug, nhưng UI chỉ hiển thị thông báo tiếng Việt.
- TypeScript đã được kiểm tra bằng `npm run typecheck`.
- Production build đã được kiểm tra bằng `npm run build`.

## 13. Những phần chưa làm hoặc cần cải thiện

- Chưa thêm persistence kết quả phân tích vào store hoặc backend chính vì frontend hiện tại đang dùng mock data cục bộ cho nhiều màn hình.
- Chưa tự chuyển kết quả upload sang trang risk review riêng; kết quả được hiển thị trực tiếp trên `/upload`.
- Chưa thêm progress thật theo server event vì API upload hiện được mô tả là request/response thông thường.
- Nếu AI Service đang chạy từ source hiện tại chỉ có `/health`, cần bật hoặc cập nhật router backend để phục vụ đúng hai endpoint trong báo cáo AI Service.
- Response shape thực tế có thể khác một chút so với báo cáo; các field optional đã được xử lý mềm để tránh vỡ UI.
