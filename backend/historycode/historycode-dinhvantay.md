# History Code - Dinh Van Tay

# Update History - 2026-06-28

## Tasks Completed

- **Trien khai Phase 2 Backend - Developer 2 Modules**
  - Doc `docs/phase2_task_allocation.md` va thuc hien phan Developer 2: Contracts, Knowledge Base, Subscriptions & Feedback.
  - Giu nguyen entity dung chung `Document`, khong doi ten column/field de tranh conflict voi Developer 1.

- **Module Contract Management**
  - Tao cac service implementation:
    - `ContractTemplateServiceImpl`
    - `ContractGenerationServiceImpl`
    - `UserContractServiceImpl`
    - `ContractVersionServiceImpl`
  - Hoan thien CRUD template hop dong.
  - Trien khai luong tao job sinh hop dong `ContractGenerationJob` co idempotency theo `requestId`.
  - Trien khai luu hop dong nguoi dung, tao version dau tien va hash noi dung.
  - Trien khai danh sach hop dong cua user, xem chi tiet hop dong, xem lich su version va revert version.
  - Cap nhat `ContractManagementController` bo `501 Phase 2 contract only`, chuyen sang goi service that.
  - Bo sung endpoint luu hop dong:
    - `POST /api/v1/contracts`

- **Module Knowledge Base Management**
  - Tao cac service implementation:
    - `KnowledgeUploadServiceImpl`
    - `KnowledgeIngestionServiceImpl`
    - `KnowledgeReviewServiceImpl`
    - `KnowledgePublicationServiceImpl`
    - `KnowledgeArchiveServiceImpl`
  - Trien khai upload knowledge theo `code`, tao entry moi hoac version moi.
  - Trien khai ingest job `KnowledgeIngestionJob` theo `requestId`, cap nhat trang thai version/entry sang `INGESTED`.
  - Trien khai review voi cac decision `APPROVE`, `REQUEST_CHANGES`, `REJECT`.
  - Trien khai publish chi khi version da duoc `APPROVE`.
  - Trien khai archive version hien tai va set entry `active = false`.
  - Cap nhat `KnowledgeBaseManagementController` bo `501`, them list/get/detail/versions va cac endpoint upload/ingest/review/publish/archive.

- **Module Subscription & Refund**
  - Thay mock trong `SubscriptionUsageServiceImpl` bang truy van repository that.
  - Bo sung query phan trang usage theo customer:
    - `findByCustomerPlanCustomerIdOrderByCreatedAtDesc`
  - Thay mock trong `RefundServiceImpl` bang luu `RefundRequest` that.
  - Validate payment transaction thuoc dung customer truoc khi tao refund.
  - Validate customer plan thuoc dung customer neu request co `customerPlanId`.
  - Chan user xem refund cua nguoi khac; chi requester hoac ADMIN duoc xem.

- **Module User Feedback & AI Reports**
  - Tao cac service implementation:
    - `FeedbackSurveyServiceImpl`
    - `FeedbackSurveyResponseServiceImpl`
    - `AiReportServiceImpl`
  - Trien khai tao/cap nhat/list survey feedback.
  - Trien khai submit survey response khi survey dang `ACTIVE`.
  - Trien khai tao/list/get AI report voi trang thai mac dinh `OPEN`.
  - Cap nhat `FeedbackController` bo `501`, them phan quyen `@PreAuthorize`.

---

## Files Added / Updated

### Added
- `src/main/java/com/analyzer/api/service/contract/impl/ContractTemplateServiceImpl.java`
- `src/main/java/com/analyzer/api/service/contract/impl/ContractGenerationServiceImpl.java`
- `src/main/java/com/analyzer/api/service/contract/impl/UserContractServiceImpl.java`
- `src/main/java/com/analyzer/api/service/contract/impl/ContractVersionServiceImpl.java`
- `src/main/java/com/analyzer/api/service/contract/impl/ContractMappingSupport.java`
- `src/main/java/com/analyzer/api/service/contract/impl/CurrentUserSupport.java`
- `src/main/java/com/analyzer/api/service/knowledge/impl/KnowledgeUploadServiceImpl.java`
- `src/main/java/com/analyzer/api/service/knowledge/impl/KnowledgeIngestionServiceImpl.java`
- `src/main/java/com/analyzer/api/service/knowledge/impl/KnowledgeReviewServiceImpl.java`
- `src/main/java/com/analyzer/api/service/knowledge/impl/KnowledgePublicationServiceImpl.java`
- `src/main/java/com/analyzer/api/service/knowledge/impl/KnowledgeArchiveServiceImpl.java`
- `src/main/java/com/analyzer/api/service/knowledge/impl/KnowledgeMappingSupport.java`
- `src/main/java/com/analyzer/api/service/feedback/impl/FeedbackSurveyServiceImpl.java`
- `src/main/java/com/analyzer/api/service/feedback/impl/FeedbackSurveyResponseServiceImpl.java`
- `src/main/java/com/analyzer/api/service/feedback/impl/AiReportServiceImpl.java`
- `src/main/java/com/analyzer/api/service/feedback/impl/FeedbackMappingSupport.java`

### Updated
- `src/main/java/com/analyzer/api/controller/contract/ContractManagementController.java`
- `src/main/java/com/analyzer/api/controller/knowledge/KnowledgeBaseManagementController.java`
- `src/main/java/com/analyzer/api/controller/feedback/FeedbackController.java`
- `src/main/java/com/analyzer/api/repository/contract/UserContractRepository.java`
- `src/main/java/com/analyzer/api/repository/subscription/SubscriptionUsageRepository.java`
- `src/main/java/com/analyzer/api/repository/subscription/RefundRequestRepository.java`
- `src/main/java/com/analyzer/api/service/subscription/impl/SubscriptionUsageServiceImpl.java`
- `src/main/java/com/analyzer/api/service/subscription/impl/RefundServiceImpl.java`

---

## API Summary

### Contract

```http
POST /api/v1/contracts/templates
PUT  /api/v1/contracts/templates/{id}
GET  /api/v1/contracts/templates
POST /api/v1/contracts/generate
POST /api/v1/contracts
GET  /api/v1/contracts/my
GET  /api/v1/contracts/{id}
GET  /api/v1/contracts/{id}/versions
POST /api/v1/contracts/{id}/versions/{versionNo}/revert
```

### Knowledge Base

```http
POST /api/v1/admin/knowledge-base/upload
POST /api/v1/admin/knowledge-base/{id}/ingest
GET  /api/v1/admin/knowledge-base
GET  /api/v1/admin/knowledge-base/{id}
GET  /api/v1/admin/knowledge-base/{id}/versions
POST /api/v1/admin/knowledge-base/{id}/review
POST /api/v1/admin/knowledge-base/{id}/publish
POST /api/v1/admin/knowledge-base/{id}/archive
```

### Subscription & Refund

```http
GET  /api/v1/subscriptions/my-usage
POST /api/v1/subscriptions/refunds
GET  /api/v1/subscriptions/refunds/{id}
```

### Feedback & AI Reports

```http
POST /api/v1/admin/feedback/surveys
PUT  /api/v1/admin/feedback/surveys/{id}
GET  /api/v1/admin/feedback/surveys
POST /api/v1/feedback/surveys/{id}/responses
POST /api/v1/feedback/ai-reports
GET  /api/v1/admin/feedback/ai-reports
GET  /api/v1/admin/feedback/ai-reports/{id}
```

---

## Swagger Test JSON

### Contract Template - Create

```http
POST /api/v1/contracts/templates
```

```json
{
  "templateCode": "LABOR_CONTRACT_001",
  "name": "Mau hop dong lao dong",
  "description": "Template hop dong lao dong co ban",
  "category": "LABOR",
  "jurisdiction": "VN",
  "content": "HOP DONG LAO DONG\nBen A: {{companyName}}\nBen B: {{employeeName}}\nVi tri: {{position}}\nMuc luong: {{salary}}"
}
```

### Contract Template - Update

```http
PUT /api/v1/contracts/templates/{id}
```

```json
{
  "name": "Mau hop dong lao dong cap nhat",
  "description": "Template hop dong lao dong da cap nhat dieu khoan",
  "category": "LABOR",
  "jurisdiction": "VN",
  "content": "HOP DONG LAO DONG CAP NHAT\nBen A: {{companyName}}\nBen B: {{employeeName}}\nDieu khoan bao mat: {{confidentialityClause}}"
}
```

### Contract Generation - Create Job

```http
POST /api/v1/contracts/generate
```

```json
{
  "requestId": "req_contract_001",
  "workspaceId": 1,
  "templateId": 1,
  "sourceDocumentId": "doc_example",
  "inputJson": "{\"companyName\":\"Cong ty ABC\",\"employeeName\":\"Nguyen Van A\",\"position\":\"Developer\",\"salary\":\"20000000\"}"
}
```

**Note:** `sourceDocumentId` co the bo qua neu chua co document. `workspaceId` DTO hien la `Long`; neu database dang dung id workspace dang `ws_...` thi can dong bo lai DTO/service sau.

### User Contract - Save Contract

```http
POST /api/v1/contracts
```

```json
{
  "workspaceId": 1,
  "templateId": 1,
  "generationJobId": "cg_example",
  "sourceDocumentId": "doc_example",
  "title": "Hop dong lao dong Nguyen Van A",
  "contractType": "LABOR",
  "content": "Noi dung hop dong da sinh hoac da chinh sua tu nguoi dung"
}
```

**Note:** `templateId`, `generationJobId`, `sourceDocumentId` co the bo qua neu luu hop dong thu cong.

### Contract Version - Revert

```http
POST /api/v1/contracts/{id}/versions/{versionNo}/revert
```

```json
{
  "reason": "Khoi phuc ve phien ban on dinh truoc khi chinh sua"
}
```

### Knowledge Base - Upload

```http
POST /api/v1/admin/knowledge-base/upload
```

```json
{
  "code": "LAW_LABOR_2019",
  "title": "Bo luat Lao dong 2019",
  "category": "LABOR",
  "scope": "GLOBAL",
  "createdById": 1,
  "workspaceId": null,
  "extractedContent": "Noi dung trich xuat tu van ban phap luat ve lao dong...",
  "rawContent": "Raw text goc neu co"
}
```

### Knowledge Base - Ingest

```http
POST /api/v1/admin/knowledge-base/{id}/ingest
```

```json
{
  "requestId": "req_kb_ingest_001",
  "jobPayload": "{\"chunkSize\":1000,\"embeddingModel\":\"default\"}"
}
```

### Knowledge Base - Review

```http
POST /api/v1/admin/knowledge-base/{id}/review
```

```json
{
  "decision": "APPROVE",
  "note": "Noi dung da du dieu kien publish"
}
```

Gia tri `decision` hop le:

```text
APPROVE
REQUEST_CHANGES
REJECT
```

### Knowledge Base - Publish

```http
POST /api/v1/admin/knowledge-base/{id}/publish
```

```json
{
  "note": "Publish version da duoc review approve"
}
```

### Knowledge Base - Archive

```http
POST /api/v1/admin/knowledge-base/{id}/archive
```

```json
{
  "reason": "Van ban da het hieu luc hoac duoc thay the bang version moi"
}
```

### Refund - Create Request

```http
POST /api/v1/subscriptions/refunds
```

```json
{
  "paymentTransactionId": 1,
  "customerPlanId": 1,
  "reason": "Khach hang yeu cau hoan tien do chua su dung dich vu",
  "amount": 150000
}
```

**Note:** `paymentTransactionId` phai thuoc customer dang dang nhap. `customerPlanId` co the bo qua neu refund chi gan voi payment transaction.

### Feedback Survey - Create

```http
POST /api/v1/admin/feedback/surveys
```

```json
{
  "code": "SURVEY_CONTRACT_001",
  "title": "Khao sat trai nghiem tao hop dong",
  "description": "Danh gia chat luong tinh nang tao hop dong",
  "surveyType": "SATISFACTION",
  "targetType": "CONTRACT",
  "createdById": 1,
  "workspaceId": null
}
```

### Feedback Survey - Update / Activate

```http
PUT /api/v1/admin/feedback/surveys/{id}
```

```json
{
  "title": "Khao sat trai nghiem tao hop dong",
  "description": "Survey dang duoc kich hoat de nhan phan hoi",
  "surveyType": "SATISFACTION",
  "targetType": "CONTRACT",
  "status": "ACTIVE"
}
```

### Feedback Survey - Submit Response

```http
POST /api/v1/feedback/surveys/{id}/responses
```

```json
{
  "respondentId": 2,
  "sourceReferenceId": "contract_example",
  "rating": 5,
  "answerJson": "{\"easeOfUse\":5,\"quality\":4}",
  "comment": "Tinh nang tao hop dong de dung va noi dung kha day du"
}
```

### AI Report - Create

```http
POST /api/v1/feedback/ai-reports
```

```json
{
  "reportType": "INCORRECT_ANSWER",
  "sourceType": "CHAT_MESSAGE",
  "sourceReferenceId": "msg_example",
  "summary": "AI tra loi sai dieu khoan trong hop dong",
  "detailsJson": "{\"expected\":\"Dieu khoan dung\",\"actual\":\"Noi dung AI da tra loi\"}",
  "submittedById": 2,
  "workspaceId": null
}
```

---

## Verification


**Date:** 2026-06-20

## Tasks Completed

- **Tao module Workspace**
  - Them entity `Workspace` map voi bang `workspaces`.
  - Them cac field chinh: `id`, `userId`, `name`, `description`, `status`, `createdAt`, `updatedAt`.
  - Them lifecycle `@PrePersist`, `@PreUpdate` de tu dong cap nhat thoi gian tao va cap nhat.
  - Trang thai mac dinh khi tao workspace moi: `ACTIVE`.

- **Tao API tao Workspace**
  - Endpoint: `POST /api/workspaces`.
  - Ho tro them alias theo style project: `POST /api/v1/workspaces`.
  - Lay `userId` tu JWT thong qua `SecurityContext`.
  - Validate `name` khong duoc de trong bang `@NotBlank`.
  - Response tra ve object truc tiep gom: `workspaceId`, `name`, `description`, `status`, `createdAt`.

- **Tao API lay danh sach document trong Workspace**
  - Endpoint: `GET /api/workspaces/{workspaceId}/documents`.
  - Ho tro them alias: `GET /api/v1/workspaces/{workspaceId}/documents`.
  - Kiem tra workspace phai thuoc user hien tai va dang `ACTIVE`.
  - Chi lay document theo `workspaceId` va `userId`.
  - Khong tra document co status `DELETED`.

- **Cap nhat Entity Document theo spec moi**
  - Them entity `Document` map voi bang `documents`.
  - Bo sung day du cac field:
    - `id`
    - `workspaceId`
    - `userId`
    - `originalFileName`
    - `storedFileName`
    - `filePath`
    - `fileType`
    - `fileSize`
    - `sourceType`
    - `status`
    - `chunkCount`
    - `errorMessage`
    - `uploadedAt`
    - `processedAt`
    - `updatedAt`
  - `sourceType` mac dinh khi user upload file: `USER_DOCUMENT`.
  - Cac status ho tro theo nghiep vu: `UPLOADED`, `PROCESSING`, `READY`, `FAILED`, `DELETED`.

- **Tao API upload Document va goi Python AI Service**
  - Endpoint: `POST /api/workspaces/{workspaceId}/documents`.
  - Request dang `multipart/form-data`, field file: `file`.
  - Spring Boot luu file vao storage local:
    - `uploads/users/{userId}/workspaces/{workspaceId}/documents/{documentId}/...`
  - Tao record `Document` trong database.
  - Sau khi luu file, Spring Boot goi Python AI Service:
    - `POST {AI_SERVICE_BASE_URL}/internal/documents/process`
  - Body gui sang Python gom:
    - `jobId`
    - `documentId`
    - `workspaceId`
    - `userId`
    - `sourceType`
    - `fileName`
    - `fileType`
    - `filePath`
    - `callbackUrl`
  - Neu Python AI Service chua chay hoac goi that bai, document duoc cap nhat `FAILED` va luu `errorMessage`.

- **Tao API callback de Python bao ket qua xu ly**
  - Endpoint: `POST /api/internal/documents/{documentId}/processing-result`.
  - Cho phep Python AI Service goi endpoint nay khong can JWT.
  - Neu body co `status = READY`:
    - Cap nhat `Document.status = READY`.
    - Luu `chunkCount`.
    - Set `processedAt = LocalDateTime.now()`.
    - Xoa `errorMessage`.
  - Neu body co `status = FAILED`:
    - Cap nhat `Document.status = FAILED`.
    - Luu `chunkCount`.
    - Luu `errorMessage`.

- **Cap nhat Security**
  - Mo route internal callback:
    - `/api/internal/documents/*/processing-result`
  - Cac API workspace/document cua user van yeu cau JWT.

- **Cap nhat config**
  - Them cau hinh trong `application.yml`:
    - `app.api.callback-base-url`
    - `app.ai-service.base-url`
    - `app.storage.upload-root`
  - Co the override bang bien moi truong:
    - `APP_CALLBACK_BASE_URL`
    - `AI_SERVICE_BASE_URL`
    - `UPLOAD_ROOT`

---

## Files Added / Updated

### Entity
- `src/main/java/com/analyzer/api/entity/Workspace.java`
- `src/main/java/com/analyzer/api/entity/Document.java`

### DTO
- `src/main/java/com/analyzer/api/dto/workspace/WorkspaceRequestDTO.java`
- `src/main/java/com/analyzer/api/dto/workspace/WorkspaceResponseDTO.java`
- `src/main/java/com/analyzer/api/dto/document/DocumentResponseDTO.java`
- `src/main/java/com/analyzer/api/dto/document/ProcessDocumentRequestDTO.java`
- `src/main/java/com/analyzer/api/dto/document/ProcessingResultRequestDTO.java`

### Repository
- `src/main/java/com/analyzer/api/repository/WorkspaceRepository.java`
- `src/main/java/com/analyzer/api/repository/DocumentRepository.java`

### Service
- `src/main/java/com/analyzer/api/service/WorkspaceService.java`
- `src/main/java/com/analyzer/api/service/impl/WorkspaceServiceImpl.java`

### Controller
- `src/main/java/com/analyzer/api/controller/WorkspaceController.java`
- `src/main/java/com/analyzer/api/controller/InternalDocumentController.java`

### Config
- `src/main/java/com/analyzer/api/security/SecurityConfig.java`
- `src/main/resources/application.yml`

---

## API Summary

### 1. Create Workspace

**Endpoint**

```http
POST /api/workspaces
```

**Request**

```json
{
  "name": "Hop dong lao dong Cong ty ABC",
  "description": "Workspace dung de phan tich hop dong lao dong"
}
```

**Logic**

1. Lay user hien tai tu JWT.
2. Validate `name` khong rong.
3. Tao workspace moi.
4. Gan workspace voi `userId`.
5. Set `status = ACTIVE`.

### 2. List Documents In Workspace

**Endpoint**

```http
GET /api/workspaces/{workspaceId}/documents
```

**Logic**

1. Lay user hien tai tu JWT.
2. Kiem tra workspace co thuoc user hien tai khong.
3. Lay danh sach document theo `workspaceId` va `userId`.
4. Loai bo document co `status = DELETED`.

### 3. Upload Document

**Endpoint**

```http
POST /api/workspaces/{workspaceId}/documents
```

**Content-Type**

```http
multipart/form-data
```

**Form field**

```text
file
```

**Logic**

1. Lay user hien tai tu JWT.
2. Kiem tra workspace thuoc user hien tai.
3. Luu file vao storage.
4. Tao `Document` record.
5. Goi Python AI Service de xu ly file.

### 4. Python Processing Callback

**Endpoint**

```http
POST /api/internal/documents/{documentId}/processing-result
```

**Success Body**

```json
{
  "jobId": "job_001",
  "status": "READY",
  "chunkCount": 86,
  "errorMessage": null
}
```

**Failed Body**

```json
{
  "jobId": "job_001",
  "status": "FAILED",
  "chunkCount": 0,
  "errorMessage": "Cannot extract text from scanned PDF"
}
```

---

## Testing Notes

- Da chay compile/test:

```powershell
.\mvnw.cmd test
```

Ket qua:

```text
BUILD SUCCESS
```

- Da test Swagger/OpenAPI:
  - `/swagger-ui.html` mo duoc.
  - `/v3/api-docs` expose du cac endpoint workspace/document/callback.

- Da test bang tai khoan:

```json
{
  "email": "vananh@example.com",
  "password": "12345678"
}
```

- Cac case da test:
  - Login lay JWT: `200 OK`.
  - Tao workspace: `201 Created`.
  - Lay danh sach document trong workspace: `200 OK`.
  - Upload document: `201 Created`.
  - Callback `READY`: `200 OK`.
  - List documents sau callback: document co `status = READY`.
  - Validate workspace name rong: `400 Bad Request`.
  - Tao workspace khong co JWT: `401 Unauthorized`.

---

## Notes

- Neu Python AI Service chua chay o `AI_SERVICE_BASE_URL`, upload document van tao record va luu file, nhung status se thanh `FAILED` voi `errorMessage` tuong ung.
- Khi Python AI Service chay that, no se goi callback URL de cap nhat document thanh `READY` hoac `FAILED`.
- Trong qua trinh test, server local tren port `8080` duoc restart de Swagger load code moi.

---

# Update History - 2026-06-22

## Tasks Completed

- **Gop route Workspace ve mot chuan**
  - Bo alias `/api/workspaces`.
  - Chi giu prefix chinh: `/api/v1/workspaces`.
  - Swagger khong con hien duplicate endpoint workspace/document.

- **Chuan hoa response controller**
  - `WorkspaceController` tra ve `ApiResponseDTO` thay vi raw DTO/list.
  - `InternalDocumentController` tra ve `ApiResponseDTO<DocumentResponseDTO>`.
  - Them factory method:
    - `ApiResponseDTO.accepted(String message, T data)`
  - Upload document tra HTTP `202 Accepted` va body co `code = 202`.

- **Refactor DTO sang record**
  - Doi cac DTO sau sang Java `record`:
    - `WorkspaceRequestDTO`
    - `WorkspaceResponseDTO`
    - `DocumentResponseDTO`
    - `PaymentTransactionResponseDTO`
    - `PaymentUrlResponseDTO`
  - Cap nhat code tao DTO trong `WorkspaceServiceImpl` tu builder sang constructor record.
  - Cap nhat truy cap request workspace tu `request.getName()` / `request.getDescription()` sang `request.name()` / `request.description()`.

- **Refactor payment service**
  - Xoa service trung gian:
    - `PaymentService`
    - `PaymentServiceImpl`
    - `VnPayService`
  - Chi giu service chinh:
    - `PaymentTransactionService`
    - `PaymentTransactionServiceImpl`
  - Gop logic tao URL VNPAY, build hash data, verify signature, encode param vao `PaymentTransactionServiceImpl`.
  - Inject truc tiep `VnPayProperties` vao `PaymentTransactionServiceImpl`.
  - Thu muc `service/impl` chi con mot implementation payment: `PaymentTransactionServiceImpl`.

- **Sua encoding tieng Viet**
  - Them cau hinh Maven:
    - `project.build.sourceEncoding=UTF-8`
  - Sua cac message Swagger/API bi mojibake trong controller/DTO lien quan.

- **Kiem tra return URL VNPAY**
  - Xac nhan config van dung endpoint:
    - `/api/v1/payment-transactions/vnpay-return`
  - SecurityConfig da permit route return/ipn cua VNPAY.

---

## Files Added / Removed / Updated

### Added
- `src/main/java/com/analyzer/api/service/PaymentService.java` da tung duoc them trong qua trinh refactor, sau do da xoa de tranh trung service payment.
- `src/main/java/com/analyzer/api/service/impl/PaymentServiceImpl.java` da tung duoc them trong qua trinh refactor, sau do da xoa de chi giu `PaymentTransactionServiceImpl`.

### Removed
- `src/main/java/com/analyzer/api/service/VnPayService.java`
- `src/main/java/com/analyzer/api/service/PaymentService.java`
- `src/main/java/com/analyzer/api/service/impl/PaymentServiceImpl.java`

### Updated
- `pom.xml`
- `src/main/java/com/analyzer/api/dto/ApiResponseDTO.java`
- `src/main/java/com/analyzer/api/controller/WorkspaceController.java`
- `src/main/java/com/analyzer/api/controller/InternalDocumentController.java`
- `src/main/java/com/analyzer/api/controller/PaymentTransactionController.java`
- `src/main/java/com/analyzer/api/dto/workspace/WorkspaceRequestDTO.java`
- `src/main/java/com/analyzer/api/dto/workspace/WorkspaceResponseDTO.java`
- `src/main/java/com/analyzer/api/dto/document/DocumentResponseDTO.java`
- `src/main/java/com/analyzer/api/dto/paymenttransaction/PaymentTransactionResponseDTO.java`
- `src/main/java/com/analyzer/api/dto/paymenttransaction/PaymentUrlResponseDTO.java`
- `src/main/java/com/analyzer/api/service/impl/WorkspaceServiceImpl.java`
- `src/main/java/com/analyzer/api/service/impl/PaymentTransactionServiceImpl.java`

---

## API Changes

### Workspace

Workspace API chi dung prefix:

```http
/api/v1/workspaces
```

Khong con alias:

```http
/api/workspaces
```

### Upload Document

**Endpoint**

```http
POST /api/v1/workspaces/{workspaceId}/documents
```

**Response**

```json
{
  "code": 202,
  "message": "Upload document thanh cong, dang gui yeu cau xu ly",
  "data": {
    "documentId": "doc_xxx",
    "workspaceId": "ws_xxx",
    "originalFileName": "contract.docx",
    "fileType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "fileSize": 12345,
    "status": "PROCESSING",
    "uploadedAt": "2026-06-22T21:44:30"
  }
}
```

### Payment

Payment controller giu cac endpoint:

```http
GET  /api/v1/payment-transactions/me
GET  /api/v1/payment-transactions
POST /api/v1/payment-transactions/{id}/vnpay-url
GET  /api/v1/payment-transactions/vnpay-return
GET  /api/v1/payment-transactions/vnpay-ipn
PUT  /api/v1/payment-transactions/{id}/success
PUT  /api/v1/payment-transactions/{id}/failed
```

Logic VNPAY hien nam trong:

```text
PaymentTransactionServiceImpl
```

---

## Verification

- Da chay Maven voi JDK local:

```powershell
$env:JAVA_HOME='C:\Users\admin\.jdks\openjdk-26.0.1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd '-Dmaven.compiler.useIncrementalCompilation=false' test
```

- Ket qua:

```text
BUILD SUCCESS
```

- Luu y:
  - Project hien khong co test source nen Maven bao `No tests to run`.
  - Compile backend pass.
  - Con mot so thay doi ngoai backend khong thuoc phan nay:
    - `frontend/.env.example`
    - `frontend/vite.config.ts`
    - `ai-service/package-lock.json`
