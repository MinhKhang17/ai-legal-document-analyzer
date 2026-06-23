# History Code - Dinh Van Tay

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
