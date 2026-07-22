package com.analyzer.api.e2e;

import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.service.SubscriptionQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// Test-only bean (picked up by LegalAnalyzerApplication's component scan since it lives under
// com.analyzer.api) mirroring WorkspaceServiceImpl.uploadDocument()'s check-then-insert pattern
// as ONE transaction. Needed because the real uploadDocument() also makes an external AI-service
// HTTP call E2E tests can't depend on — but splitting the check and the insert into two separate
// top-level calls (two separate transactions) would release UserQuotaLock's advisory lock (held
// only until commit) between them, defeating the concurrency guarantee under test.
@Component
@RequiredArgsConstructor
class TestUploadAttemptService {

    private final SubscriptionQuotaService subscriptionQuotaService;
    private final DocumentRepository documentRepository;

    @Transactional
    public void checkAndRecordUpload(User user, Workspace workspace, long fileSizeBytes) {
        subscriptionQuotaService.checkCanUploadOrAnalyzeContract(user, workspace.getId(), fileSizeBytes);

        LocalDateTime now = LocalDateTime.now();
        Document document = Document.builder()
                .id("doc_" + UUID.randomUUID().toString().replace("-", ""))
                .workspace(workspace)
                .user(user)
                .originalFileName("contract.pdf")
                .storedFileName(UUID.randomUUID() + "_contract.pdf")
                .filePath("/tmp/" + UUID.randomUUID())
                .fileType("application/pdf")
                .fileSize(fileSizeBytes)
                .sourceType("USER_DOCUMENT")
                .contractTypeConfirmed(false)
                .status("READY")
                .chunkCount(1)
                .uploadedAt(now)
                .processedAt(now)
                .updatedAt(now)
                .build();
        documentRepository.save(document);
    }
}
