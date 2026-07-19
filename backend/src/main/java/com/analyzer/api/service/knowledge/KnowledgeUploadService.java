package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.knowledge.KnowledgeBaseVersionResponse;
import com.analyzer.api.dto.knowledge.UploadKnowledgeRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface KnowledgeUploadService {

    KnowledgeBaseVersionResponse upload(UploadKnowledgeRequest request);

    KnowledgeBaseVersionResponse storeSourceFile(String entryId, MultipartFile file) throws IOException;

    Resource loadSourceFile(String entryId);

    KnowledgeBaseVersionResponse getCurrentVersion(String entryId);
}
