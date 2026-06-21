# Báo cáo audit API AI trong frontend

Ngày kiểm tra: 2026-06-21  
Thư mục kiểm tra: `frontend`

## 1. Tổng quan kết quả kiểm tra

| Hạng mục | Kết luận |
| --- | --- |
| `VITE_AI_KNOWLEDGE_QUERY_PATH=/knowledge/query-v2` | Vẫn tồn tại trong `.env` và `.env.example`, nhưng không thấy được đọc hoặc gọi trong `frontend/src`. |
| `VITE_AI_CONTRACT_UPLOAD_PATH=/v2/contracts/upload` | Vẫn tồn tại trong `.env` và `.env.example`, nhưng không thấy được đọc hoặc gọi trong `frontend/src`. |
| Request thật tới hai endpoint | Không tìm thấy `fetch`, `FormData`, `axios`, API client, service, hook, store, context, route, page hoặc component nào gọi hai path này. |
| Dấu vết tích hợp AI còn lại trong source | Có `frontend/src/api/aiApi.ts`, nhưng file này chỉ re-export từ `../services/ai.service` và `../types/aiService`; hai file đích hiện không tồn tại. |
| AI base URL | `frontend/src/config/api.ts` vẫn đọc bắt buộc `VITE_AI_SERVICE_BASE_URL`, dù chưa có helper build URL hoặc service AI đang dùng nó. |
| Typecheck đúng phạm vi app | `npx tsc -p tsconfig.app.json --noEmit` lỗi vì `frontend/src/api/aiApi.ts` import module không tồn tại. |

Kết luận ngắn: hai API path vẫn còn trong cấu hình môi trường, nhưng không còn đường gọi runtime trong `frontend/src`. Phần tích hợp AI hiện tại là cấu hình/dấu vết code mồ côi, không phải luồng API hoạt động.

## 2. Phạm vi đã kiểm tra

Đã kiểm tra:

| Nhóm | Trạng thái |
| --- | --- |
| `frontend/src/**/*` | Đã kiểm tra 94 file. |
| `frontend/.env` | Có tồn tại, đã kiểm tra. |
| `frontend/.env.example` | Có tồn tại, đã kiểm tra. |
| `frontend/.env.local` | Không tồn tại. |
| `frontend/.env.development` | Không tồn tại. |
| `frontend/.env.production` | Không tồn tại. |
| `frontend/src/vite-env.d.ts` | Chỉ có `/// <reference types="vite/client" />`; không khai báo type riêng cho env AI. |
| API client/service/config | Đã kiểm tra `src/api`, `src/services`, `src/config`. |
| Hooks, store, routes, pages, components | Đã kiểm tra bằng search rộng và đọc trực tiếp các file liên quan upload/chat/knowledge/contract. |
| Test files | Không tìm thấy file `test`, `spec`, hoặc `__tests__` trong `frontend/src`. |

Ngoài phạm vi runtime source, có tài liệu cũ `frontend/FRONTEND_AI_API_INTEGRATION_REPORT.md` vẫn nhắc đến `POST /v2/contracts/upload`, `POST /knowledge/query-v2` và helper `buildAiServiceUrl`. Chưa xác nhận tài liệu đó còn đúng với source hiện tại, vì helper/service tương ứng không còn trong `frontend/src`.

## 3. Danh sách file liên quan

| File | Function/component/liên quan | Nhận xét |
| --- | --- | --- |
| `frontend/.env` | `VITE_AI_SERVICE_BASE_URL`, `VITE_AI_KNOWLEDGE_QUERY_PATH`, `VITE_AI_CONTRACT_UPLOAD_PATH` | Vẫn khai báo đầy đủ hai path cần audit. |
| `frontend/.env.example` | `VITE_AI_SERVICE_BASE_URL`, `VITE_AI_KNOWLEDGE_QUERY_PATH`, `VITE_AI_CONTRACT_UPLOAD_PATH` | Vẫn khai báo đầy đủ; riêng `VITE_CUSTOMER_PLAN_SUBSCRIBE_API` đang khác `.env` (`subcribe` vs `subscribe`), không trực tiếp liên quan AI. |
| `frontend/src/config/api.ts` | `getRequiredEnvValue`, `AI_SERVICE_BASE_URL`, `buildApiUrl`, `API_ENDPOINTS` | Chỉ đọc `VITE_AI_SERVICE_BASE_URL`; không đọc hai biến path AI. |
| `frontend/src/api/aiApi.ts` | `queryLegalKnowledge`, `uploadContractForRiskAnalysis`, `AiServiceRequestError` | File re-export mồ côi, import từ module không tồn tại. |
| `frontend/src/services/auth.service.ts` | `requestJson`, `postJson`, `getJson`, `login`, `register`, `refreshAccessToken`, `getCurrentUser` | Có `fetch(buildApiUrl(...))`, nhưng chỉ dùng backend auth endpoints. |
| `frontend/src/services/subscription.service.ts` | `requestJson`, `getSubscriptionPlans`, `getMyCustomerPlan`, `subscribeCustomerPlan` | Có `fetch(buildApiUrl(...))`, nhưng chỉ dùng subscription endpoints. |
| `frontend/src/pages/upload/UploadPage.tsx` | `UploadPage` | Upload UI dùng `FileUploadZone` và state local `processing`; không gọi upload contract API. |
| `frontend/src/components/upload/FileUploadZone.tsx` | `FileUploadZone`, `handleDrop`, `onFakeUpload` | Chỉ gọi callback giả lập `onFakeUpload`; không tạo `FormData`, không gọi network. |
| `frontend/src/components/upload/ProcessingTimeline.tsx` | `ProcessingTimeline` | Timeline tĩnh/local; không gọi API. |
| `frontend/src/pages/documents/DocumentsPage.tsx` | `DocumentsPage` | Dùng `documents` từ `mockData`; `FileUploadZone onFakeUpload`. |
| `frontend/src/pages/chat/LegalChatPage.tsx` | `LegalChatPage` | Dùng `chatThreads` và `documents` từ `mockData`; truyền vào `LegalChatPanel`. |
| `frontend/src/components/editor/LegalChatPanel.tsx` | `LegalChatPanel`, `createAssistantMessage`, `sendMessage` | Sinh câu trả lời local từ input; không gọi `/knowledge/query-v2`. |
| `frontend/src/pages/chat/ChatHistoryPage.tsx` | `ChatHistoryPage` | Dùng `chatThreads` từ `mockData`. |
| `frontend/src/pages/knowledge-base/KnowledgeBasePage.tsx` | `KnowledgeBasePage` | Dùng `knowledgeArticles` từ `mockData`; nút upload/reindex không gọi API. |
| `frontend/src/pages/knowledge-base/KnowledgeBaseDetailPage.tsx` | `KnowledgeBaseDetailPage` | Dùng `knowledgeArticles` từ `mockData`; nút replace/re-index không gọi API. |
| `frontend/src/api/mockData.ts` | `documents`, `riskFindings`, `chatThreads`, `knowledgeArticles` | Dữ liệu giả đang cấp cho các màn hình AI/upload/knowledge. |
| `frontend/src/api/mockApi.ts` | `mockApi.listDocuments`, `listChatThreads`, `listKnowledgeArticles`, `listRiskFindings` | Mock API wrapper tồn tại nhưng không thấy nơi nào import `mockApi`. |
| `frontend/src/routes/router.tsx` | route `/upload`, `/chat`, `/knowledge-base` | Chỉ route tới page; không cấu hình API endpoint. |

## 4. Phân tích API `VITE_AI_KNOWLEDGE_QUERY_PATH=/knowledge/query-v2`

| Điểm kiểm tra | Kết quả |
| --- | --- |
| Có trong env không? | Có trong `frontend/.env` và `frontend/.env.example`. |
| Có trong `frontend/src` dưới dạng exact string không? | Không tìm thấy `/knowledge/query-v2`, `query-v2`, hoặc `VITE_AI_KNOWLEDGE_QUERY_PATH` trong `frontend/src`. |
| Có đọc qua `import.meta.env` không? | Không. `frontend/src/config/api.ts` chỉ đọc `VITE_AI_SERVICE_BASE_URL`, không đọc path knowledge query. |
| Có API client/service gọi gián tiếp không? | Không thấy service/helper build AI URL. |
| Có component chat gọi không? | Không. `LegalChatPanel.sendMessage()` chỉ append user message và `createAssistantMessage(...)` local. |
| Có knowledge-base page gọi không? | Không. `KnowledgeBasePage` và `KnowledgeBaseDetailPage` dùng `knowledgeArticles` từ `mockData`. |

Chi tiết theo file:

- `frontend/src/components/editor/LegalChatPanel.tsx`
  - `sendMessage()` lấy input, tạo `userMessage`, rồi gọi `createAssistantMessage(query, language)`.
  - `createAssistantMessage()` tự sinh nội dung trả lời và citation local.
  - Không có `fetch`, không import `aiApi`, không import `queryLegalKnowledge`.
- `frontend/src/pages/chat/LegalChatPage.tsx`
  - `LegalChatPage()` lấy `thread = chatThreads[0]` từ `frontend/src/api/mockData.ts`.
  - Không truyền service/API callback vào `LegalChatPanel`.
- `frontend/src/pages/knowledge-base/KnowledgeBasePage.tsx`
  - `KnowledgeBasePage()` render table từ `knowledgeArticles`.
  - Nút `Re-index All`, `Upload Legal Source` chỉ là UI button.
- `frontend/src/pages/knowledge-base/KnowledgeBaseDetailPage.tsx`
  - `KnowledgeBaseDetailPage()` lấy article bằng `useParams()` rồi lookup trong `knowledgeArticles`.
  - Nút `Replace`, `Re-index`, `Mark Outdated` không có handler gọi API.

Kết luận riêng cho knowledge query: endpoint vẫn nằm trong env/template, nhưng source hiện tại không dùng endpoint này. Nếu mục tiêu là gỡ bỏ hoàn toàn API knowledge query khỏi frontend, cần xóa env var và dọn dấu vết `aiApi` mồ côi. Nếu mục tiêu là khôi phục tích hợp, cần tạo lại service/type và nối `LegalChatPanel` hoặc page chat vào API thật.

## 5. Phân tích API `VITE_AI_CONTRACT_UPLOAD_PATH=/v2/contracts/upload`

| Điểm kiểm tra | Kết quả |
| --- | --- |
| Có trong env không? | Có trong `frontend/.env` và `frontend/.env.example`. |
| Có trong `frontend/src` dưới dạng exact string không? | Không tìm thấy `/v2/contracts/upload`, `contracts/upload`, hoặc `VITE_AI_CONTRACT_UPLOAD_PATH` trong `frontend/src`. |
| Có đọc qua `import.meta.env` không? | Không. |
| Có dùng `FormData`/multipart upload không? | Không tìm thấy `FormData` trong upload/contract-related source. |
| Có component upload gọi API không? | Không. Upload chỉ kích hoạt state local qua `onFakeUpload`. |
| Có service/API client gọi upload không? | Không có service upload contract đang tồn tại. |

Chi tiết theo file:

- `frontend/src/pages/upload/UploadPage.tsx`
  - `UploadPage()` giữ state `processing` và `progress`.
  - `FileUploadZone onFakeUpload={() => setProcessing(true)}` chỉ bật UI processing giả lập.
  - Danh sách queue dùng `documents.slice(1, 3)` từ `mockData`.
- `frontend/src/components/upload/FileUploadZone.tsx`
  - `handleDrop()` chỉ `preventDefault()`, `setDragging(false)`, rồi gọi `onFakeUpload?.()`.
  - Button select files cũng gọi `onFakeUpload`.
  - Không có input file thật, không đọc file, không tạo `FormData`, không gọi `uploadContractForRiskAnalysis`.
- `frontend/src/pages/documents/DocumentsPage.tsx`
  - `FileUploadZone onFakeUpload={() => setFakeUploadActive(true)}`.
  - Processing flow là UI local.
- `frontend/src/pages/editor/RiskReviewPage.tsx` và `frontend/src/components/editor/RiskReviewPanel.tsx`
  - Risk review hiển thị `riskFindings` từ `mockData`.
  - Không gọi contract upload/risk analysis API.

Kết luận riêng cho contract upload: endpoint vẫn nằm trong env/template, nhưng không có luồng upload thật trong source hiện tại.

## 6. Các endpoint hardcode hoặc biến môi trường liên quan

| Nguồn | Key/path | Được source dùng? | Ghi chú |
| --- | --- | --- | --- |
| `.env` | `VITE_AI_SERVICE_BASE_URL=http://localhost:8000` | Có đọc trong `src/config/api.ts` | Được đọc bắt buộc khi import config, nhưng chưa dùng để build URL AI. |
| `.env` | `VITE_AI_KNOWLEDGE_QUERY_PATH=/knowledge/query-v2` | Không thấy dùng | Env path còn dư nếu frontend không còn tích hợp AI query. |
| `.env` | `VITE_AI_CONTRACT_UPLOAD_PATH=/v2/contracts/upload` | Không thấy dùng | Env path còn dư nếu frontend không còn upload AI service. |
| `.env.example` | `VITE_AI_SERVICE_BASE_URL=http://localhost:8000` | Có thể dùng làm template | Source chỉ đọc base URL. |
| `.env.example` | `VITE_AI_KNOWLEDGE_QUERY_PATH=/knowledge/query-v2` | Không thấy dùng | Template vẫn quảng bá endpoint cũ. |
| `.env.example` | `VITE_AI_CONTRACT_UPLOAD_PATH=/v2/contracts/upload` | Không thấy dùng | Template vẫn quảng bá endpoint cũ. |
| `src/config/api.ts` | `AI_SERVICE_BASE_URL` | Có export | Không có `buildAiServiceUrl` trong source hiện tại. |
| `src/config/api.ts` | `API_ENDPOINTS.auth.*` | Có dùng | Dùng bởi `auth.service.ts`. |
| `src/config/api.ts` | `API_ENDPOINTS.subscription.*` | Có dùng | Dùng bởi `subscription.service.ts`. |
| `src/services/auth.service.ts` | `fetch(buildApiUrl(endpointPath), ...)` | Có dùng | Chỉ auth backend API. |
| `src/services/subscription.service.ts` | `fetch(buildApiUrl(endpointPath), ...)` | Có dùng | Chỉ subscription backend API. |

Các route frontend liên quan về mặt UI nhưng không phải API endpoint:

| Route | Component | Ghi chú |
| --- | --- | --- |
| `/upload` | `UploadPage` | Upload giả lập, không gọi `/v2/contracts/upload`. |
| `/documents` | `DocumentsPage` | Dùng `mockData`, upload giả lập. |
| `/chat` | `LegalChatPage` + `LegalChatPanel` | Chat local, không gọi `/knowledge/query-v2`. |
| `/chat/history` | `ChatHistoryPage` | Dùng `mockData`. |
| `/knowledge-base` | `KnowledgeBasePage` | Dùng `mockData`. |
| `/knowledge-base/:id` | `KnowledgeBaseDetailPage` | Dùng `mockData`. |

## 7. Code thừa, code chết hoặc trùng lặp

| Mức độ | File | Vấn đề | Bằng chứng |
| --- | --- | --- | --- |
| Cao | `frontend/src/api/aiApi.ts` | Re-export API AI nhưng import từ module không tồn tại. | `../services/ai.service` và `../types/aiService` không tồn tại; `npx tsc -p tsconfig.app.json --noEmit` báo TS2307. |
| Cao | `frontend/src/api/aiApi.ts` | Không thấy file nào trong `frontend/src` import `aiApi`. | Search `aiApi`, `queryLegalKnowledge`, `uploadContractForRiskAnalysis` chỉ trả về chính `aiApi.ts`. |
| Trung bình | `frontend/.env`, `frontend/.env.example` | Hai biến path AI vẫn tồn tại nhưng không được đọc bởi source. | Không có match trong `frontend/src` cho `VITE_AI_KNOWLEDGE_QUERY_PATH` hoặc `VITE_AI_CONTRACT_UPLOAD_PATH`. |
| Trung bình | `frontend/src/config/api.ts` | `AI_SERVICE_BASE_URL` bị đọc bắt buộc nhưng chưa có service AI sử dụng. | `AI_SERVICE_BASE_URL = getRequiredEnvValue('VITE_AI_SERVICE_BASE_URL')`; không có import/usage khác trong `src`. |
| Trung bình | `frontend/src/api/mockApi.ts` | Mock API wrapper có `listDocuments`, `listChatThreads`, `listKnowledgeArticles`, nhưng pages đang import `mockData` trực tiếp. | Không thấy nơi nào import `mockApi`. |
| Thấp | `frontend/src/components/upload/FileUploadZone.tsx` | Prop `onFakeUpload` thể hiện luồng giả lập còn tồn tại. | Được dùng ở `UploadPage` và `DocumentsPage`; không có upload thật. |
| Thấp | `frontend/src/components/editor/LegalChatPanel.tsx` | `createAssistantMessage()` là trả lời AI giả lập local. | Không gọi API, chỉ tạo nội dung từ `query.slice(0, 80)`. |

Ghi chú typecheck:

- `npm run typecheck` hiện chạy `tsc --noEmit` từ root `tsconfig.json` dạng project references và không phát hiện lỗi `aiApi.ts`.
- Lệnh kiểm tra đúng app source là `npx tsc -p tsconfig.app.json --noEmit`; lệnh này phát hiện:
  - `src/api/aiApi.ts(6,8): error TS2307: Cannot find module '../services/ai.service'`
  - `src/api/aiApi.ts(14,8): error TS2307: Cannot find module '../types/aiService'`

## 8. Đề xuất chỉnh sửa theo mức ưu tiên

| Ưu tiên | Đề xuất | Khi nào nên làm |
| --- | --- | --- |
| P0 | Quyết định trạng thái sản phẩm: gỡ bỏ hẳn tích hợp AI query/upload hay khôi phục tích hợp thật. | Cần làm trước để tránh nửa cấu hình, nửa mock. |
| P0 nếu gỡ bỏ | Xóa `VITE_AI_KNOWLEDGE_QUERY_PATH`, `VITE_AI_CONTRACT_UPLOAD_PATH` khỏi `.env`/`.env.example`; xóa hoặc lưu trữ `src/api/aiApi.ts`. | Khi hai endpoint không còn thuộc frontend. |
| P0 nếu khôi phục | Tạo lại `src/services/ai.service.ts` và `src/types/aiService.ts`; đọc hai env path trong config AI riêng; nối `LegalChatPanel` vào `queryLegalKnowledge` và `FileUploadZone`/`UploadPage` vào `uploadContractForRiskAnalysis`. | Khi frontend cần gọi AI service thật. |
| P1 | Tách `AI_SERVICE_BASE_URL` khỏi `src/config/api.ts` chung hoặc chỉ đọc trong module AI service. | Tránh app auth/subscription phụ thuộc env AI không dùng. |
| P1 | Sửa script `typecheck` để kiểm tra project references/app source đúng cách, ví dụ `tsc -b --noEmit` hoặc kiểm tra riêng `tsconfig.app.json`. | Để CI phát hiện import mồ côi như `aiApi.ts`. |
| P2 | Đổi tên/đánh dấu rõ luồng mock: `onFakeUpload`, `createAssistantMessage`, mock data pages. | Giảm nhầm lẫn giữa UI demo và API tích hợp thật. |
| P2 | Nếu tiếp tục dùng mock layer, cân nhắc dùng thống nhất `mockApi` thay vì pages import trực tiếp `mockData`. | Giảm trùng lặp pattern khi chuyển sang API thật. |

## 9. Checklist sau kiểm tra

- [x] Đã search exact string: `VITE_AI_KNOWLEDGE_QUERY_PATH`, `/knowledge/query-v2`, `query-v2`, `VITE_AI_CONTRACT_UPLOAD_PATH`, `/v2/contracts/upload`, `contracts/upload`.
- [x] Đã search gián tiếp: `import.meta.env`, `VITE_`, `AI_SERVICE_BASE_URL`, `fetch`, `axios`, `FormData`, `upload`, `knowledge`, `contract`, `chat`, `query`.
- [x] Đã kiểm tra `frontend/.env` và `frontend/.env.example`.
- [x] Đã xác nhận `.env.local`, `.env.development`, `.env.production` không tồn tại trong `frontend`.
- [x] Đã kiểm tra `frontend/src/vite-env.d.ts`.
- [x] Đã kiểm tra API/config/service: `src/api`, `src/services`, `src/config`.
- [x] Đã kiểm tra upload-related code: `UploadPage`, `DocumentsPage`, `FileUploadZone`, `ProcessingTimeline`.
- [x] Đã kiểm tra chat/AI-related code: `LegalChatPage`, `LegalChatPanel`, `ChatHistoryPage`.
- [x] Đã kiểm tra knowledge-base code: `KnowledgeBasePage`, `KnowledgeBaseDetailPage`.
- [x] Đã kiểm tra contract/risk-related code: `DocumentDetailPage`, `EditorPage`, `RiskReviewPage`, `RiskReviewPanel`, `DocumentPreview`.
- [x] Đã kiểm tra routes tới upload/chat/knowledge.
- [x] Đã kiểm tra test files; không tìm thấy test/spec trong `frontend/src`.
- [x] Đã chạy `npx tsc -p tsconfig.app.json --noEmit` để xác nhận lỗi import AI mồ côi.
- [ ] Chưa xác nhận cấu hình ngoài repo/CI/CD/runtime server có override env khác hay không.
- [ ] Chưa xác nhận backend/AI service hiện còn cung cấp hai endpoint này hay không; audit này chỉ kết luận frontend.

## 10. Kết luận cuối cùng

Frontend hiện vẫn còn nhắc tới hai API path:

- `VITE_AI_KNOWLEDGE_QUERY_PATH=/knowledge/query-v2`
- `VITE_AI_CONTRACT_UPLOAD_PATH=/v2/contracts/upload`

Tuy nhiên, hai path này chỉ còn trong `frontend/.env` và `frontend/.env.example`. Trong `frontend/src`, không tìm thấy code nào đọc hai biến này, build URL từ chúng, hoặc gọi trực tiếp/gián tiếp tới `/knowledge/query-v2` hay `/v2/contracts/upload`.

Luồng chat, upload hợp đồng, knowledge base và risk review hiện đang chạy bằng `mockData` hoặc state local. File `frontend/src/api/aiApi.ts` là dấu vết tích hợp AI chưa hoàn chỉnh: nó re-export các hàm `queryLegalKnowledge` và `uploadContractForRiskAnalysis`, nhưng service/type phía sau không tồn tại và cũng không có nơi nào import file này.

Kết luận cuối cùng: nếu mục tiêu là loại bỏ hai API path khỏi frontend, vẫn cần dọn `.env`, `.env.example`, `src/api/aiApi.ts`, và cân nhắc `AI_SERVICE_BASE_URL` trong `src/config/api.ts`. Nếu mục tiêu là dùng lại hai API này, frontend hiện chưa tích hợp thực tế và cần khôi phục service/type/helper trước khi nối vào UI.
