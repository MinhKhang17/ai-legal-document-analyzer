# Kế Hoạch Phân Chia Task Phase 2 Backend (Dành cho 2 Lập Trình Viên)

Tài liệu này hướng dẫn cách phân chia 8 module của Phase 2 cho **2 Developer** trong nhóm Backend nhằm tối ưu hóa tiến độ, đảm bảo khối lượng công việc cân bằng và **tối thiểu hóa nguy cơ xung đột code (git merge conflict)**.

---

## 🛡️ Nguyên Tắc Triển Khai Tránh Merge Conflict

1. **Phân chia độc lập theo Package Service & Controller**: 
   - Mỗi Developer chịu trách nhiệm chính trên các package `service/impl/<domain>` và `controller/<domain>` riêng biệt.
   - Tránh việc 2 người cùng sửa một class Controller hoặc Service Impl cùng một lúc.
2. **Quản lý Entity & Repository dùng chung**:
   - Các entity cốt lõi dùng chung (như `Document`, `LegalTicket`, `ChatMessage`) đã được leader bổ sung field trong commit `78d9d7134b84215540f9aaa52b059bf8b22ad0f6`. 
   - Nếu cần thay đổi hoặc bổ sung logic JPA Query trong Repository, hãy thông báo trước với người còn lại hoặc tạo nhánh PR riêng.
3. **Quy trình làm việc (Workflow)**:
   - Làm việc trên các nhánh tính năng riêng: `feature/dev1-<module>` và `feature/dev2-<module>`.
   - Viết MapStruct Mapper riêng cho từng domain (ví dụ: `ContractMapper`, `KnowledgeMapper` thuộc Dev 2; `LawyerTicketMapper`, `AiFeatureMapper` thuộc Dev 1).

---

## 👤 Developer 1: AI Assistant, Chat & Legal Ticket Operations
> **Trọng tâm**: Nghiệp vụ tương tác người dùng - luật sư - admin, tính năng hội thoại AI và trích dẫn tri thức.

### 📋 Các Module Chịu Trách Nhiệm (4 Modules):

#### 1. Module Lawyer (Luật sư xử lý Ticket)
* **Packages & Controllers**: `controller/lawyer/LawyerTicketController.java`
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.lawyer.TicketConversationService` -> Tạo `TicketConversationServiceImpl`
  * `com.analyzer.api.service.lawyer.TicketFileService` -> Tạo `TicketFileServiceImpl`
* **Nhiệm vụ chính**:
  * Triển khai API cho luật sư xem ticket được phân công, chat trực tiếp với người dùng.
  * Xử lý upload/tải tài liệu đính kèm giữa luật sư và khách hàng.
  * Hoàn thiện logic đóng ticket (`closeReason`).

#### 2. Module Admin Ticket Management (Admin quản lý & phân công Ticket)
* **Packages & Controllers**: `controller/admin/AdminTicketManagementController.java`
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.admin.AdminTicketManagementService` -> Tạo `AdminTicketManagementServiceImpl`
* **Nhiệm vụ chính**:
  * Triển khai API cho Admin duyệt danh sách ticket, xem tóm tắt ticket do AI tạo, xem lịch sử chat.
  * Triển khai chức năng phân công luật sư (`assign-lawyer`) và phân công lại (`reassign-lawyer`).

#### 3. Module AI Features Integration (Tích hợp tính năng AI & Trích dẫn)
* **Packages & Controllers**: `controller/ai/AiFeatureController.java`
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.ai.AiFeatureService` -> Tạo `AiFeatureServiceImpl`
  * `com.analyzer.api.service.ai.AiCitationService` -> Tạo `AiCitationServiceImpl`
* **Nhiệm vụ chính**:
  * Triển khai API trả về đánh giá rủi ro AI (`AiRiskAssessmentResponse`) và tóm tắt ticket.
  * Quản lý và truy vấn danh sách trích dẫn nguồn pháp lý (`AiCitation`) đính kèm vào ticket hoặc message.

#### 4. Module Chat Session Enhancements (Quản lý Ngữ cảnh & Bộ nhớ Chat)
* **Packages & Controllers**: `controller/chatsession/ChatSessionContextController.java`
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.chatsession.ChatMemoryService` -> Tạo `ChatMemoryServiceImpl`
  * Cập nhật/bổ sung logic cho `ChatSessionServiceImpl` và `ChatMessageServiceImpl` hiện có.
* **Nhiệm vụ chính**:
  * Quản lý và lưu vết bộ nhớ hội thoại (`memoryJson`), ngữ cảnh (`contextJson`) và tóm tắt cuộc hội thoại (`summary`).

---

## 👤 Developer 2: Contracts, Knowledge Base, Subscriptions & Feedback
> **Trọng tâm**: Nghiệp vụ quản lý tài liệu hợp đồng, nạp dữ liệu RAG tri thức, gói dịch vụ & xử lý phản hồi.

### 📋 Các Module Chịu Trách Nhiệm (4 Modules):

#### 1. Module Contract Management (Quản lý Hợp đồng)
* **Packages & Controllers**: `controller/contract/ContractManagementController.java`
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.contract.ContractTemplateService` -> Tạo `ContractTemplateServiceImpl`
  * `com.analyzer.api.service.contract.ContractGenerationService` -> Tạo `ContractGenerationServiceImpl`
  * `com.analyzer.api.service.contract.UserContractService` -> Tạo `UserContractServiceImpl`
  * `com.analyzer.api.service.contract.ContractVersionService` -> Tạo `ContractVersionServiceImpl`
* **Nhiệm vụ chính**:
  * Triển khai CRUD template hợp đồng.
  * Triển khai luồng tạo job sinh hợp đồng (`ContractGenerationJob`).
  * Quản lý hợp đồng của người dùng, lưu trữ vết các phiên bản (`ContractVersion`) và chức năng revert phiên bản.

#### 2. Module Knowledge Base Management (Quản lý Cơ sở Tri thức RAG)
* **Packages & Controllers**: `controller/knowledge/KnowledgeBaseManagementController.java` (hoặc admin management)
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.knowledge.KnowledgeUploadService` -> Tạo `KnowledgeUploadServiceImpl`
  * `com.analyzer.api.service.knowledge.KnowledgeIngestionService` -> Tạo `KnowledgeIngestionServiceImpl`
  * `com.analyzer.api.service.knowledge.KnowledgeReviewService` -> Tạo `KnowledgeReviewServiceImpl`
  * `com.analyzer.api.service.knowledge.KnowledgePublicationService` -> Tạo `KnowledgePublicationServiceImpl`
  * `com.analyzer.api.service.knowledge.KnowledgeArchiveService` -> Tạo `KnowledgeArchiveServiceImpl`
* **Nhiệm vụ chính**:
  * Xử lý luồng upload file tri thức, quản lý các version tri thức (`KnowledgeBaseVersion`).
  * Triển khai các trạng thái duyệt (Review), xuất bản (Publish - để AI query) và lưu trữ (Archive).

#### 3. Module Subscription & Refund (Quản lý Gói dịch vụ & Hoàn tiền)
* **Packages & Controllers**: `controller/subscription/SubscriptionManagementController.java`
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.subscription.SubscriptionUsageService` -> Tạo `SubscriptionUsageServiceImpl`
  * `com.analyzer.api.service.subscription.RefundService` -> Tạo `RefundServiceImpl`
* **Nhiệm vụ chính**:
  * Triển khai theo dõi hạn ngạch sử dụng (`SubscriptionUsage`) cho các sự kiện AI query, sinh hợp đồng, tạo ticket.
  * Xử lý yêu cầu hoàn tiền (`RefundRequest`) từ khách hàng.

#### 4. Module User Feedback & AI Reports (Khảo sát Phản hồi & Báo cáo Lỗi AI)
* **Packages & Controllers**: `controller/feedback/FeedbackController.java`
* **Interfaces cần Implement**:
  * `com.analyzer.api.service.feedback.FeedbackSurveyService` -> Tạo `FeedbackSurveyServiceImpl`
  * `com.analyzer.api.service.feedback.FeedbackSurveyResponseService` -> Tạo `FeedbackSurveyResponseServiceImpl`
  * `com.analyzer.api.service.feedback.AiReportService` -> Tạo `AiReportServiceImpl`
* **Nhiệm vụ chính**:
  * Triển khai quản lý khảo sát feedback và lưu phản hồi của người dùng.
  * Triển khai chức năng gửi và quản lý báo cáo sự cố/lỗi AI (`AiReport`).

---

## 🔄 Các Điểm Giao Thoa Cần Phối Hợp (Sync Points)

Mặc dù việc phân chia package đã độc lập 90%, 2 Developer cần trao đổi khi làm việc với các phần sau:

1. **Entity `Document`**: 
   - Dev 1 dùng `Document` cho tài liệu đính kèm Ticket (`legalTicket`).
   - Dev 2 dùng `Document` làm nguồn tạo Hợp đồng (`sourceDocument`) và Nguồn nạp Tri thức.
   - *Lưu ý*: Cả 2 giữ nguyên các field đã định nghĩa, không tự ý sửa tên column.
2. **Tích hợp Python AI Client (`client/PythonAiClient.java`)**:
   - Dev 1 gọi AI Service để lấy summary chat/ticket & assessment rủi ro.
   - Dev 2 gọi AI Service để trigger sinh hợp đồng & ingest tri thức vào Vector DB.
   - *Lưu ý*: Mở rộng `PythonAiClient` bằng các method riêng biệt cho từng nghiệp vụ.

---

## 📌 Lộ Trình Khuyên Dùng (Recommended Timeline)

* **Tuần 1**: 
  - Dev 1: Hoàn thiện **Lawyer Module** + **Admin Ticket Management**.
  - Dev 2: Hoàn thiện **Contract Management** (CRUD Template & sinh hợp đồng cơ bản).
* **Tuần 2**:
  - Dev 1: Hoàn thiện **Chat Session Enhancements** + **AI Features Integration**.
  - Dev 2: Hoàn thiện **Knowledge Base Management** (Upload, Ingest, Publish flow).
* **Tuần 3**:
  - Dev 1: Kiểm thử tích hợp luồng Ticket & Chat.
  - Dev 2: Hoàn thiện **Subscription Usage & Refund** + **Feedback Survey & AI Reports**.
* **Tuần 4**:
  - Cả 2 nhóm phối hợp kiểm thử toàn hệ thống, tối ưu query JPA, xử lý Exception và chuẩn hóa OpenAPI Docs.
