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
