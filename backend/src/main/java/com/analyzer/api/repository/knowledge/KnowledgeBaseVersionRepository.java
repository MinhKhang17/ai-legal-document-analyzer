package com.analyzer.api.repository.knowledge;

import com.analyzer.api.entity.KnowledgeBaseVersion;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeBaseVersionRepository extends JpaRepository<KnowledgeBaseVersion, String> {

    List<KnowledgeBaseVersion> findByKnowledgeBaseEntryIdOrderByVersionNoDesc(String knowledgeBaseEntryId);

    Optional<KnowledgeBaseVersion> findByKnowledgeBaseEntryIdAndVersionNo(String knowledgeBaseEntryId, Integer versionNo);

    List<KnowledgeBaseVersion> findByStatus(KnowledgeStatus status);

    Optional<KnowledgeBaseVersion> findFirstBySourceFileHashOrderByCreatedAtDesc(String sourceFileHash);

    Optional<KnowledgeBaseVersion> findFirstByNeo4jDocumentId(String neo4jDocumentId);

    Optional<KnowledgeBaseVersion> findFirstByOriginalFileNameIgnoreCaseOrderByCreatedAtDesc(String originalFileName);

    List<KnowledgeBaseVersion> findByStatusAndVisibilityAndActiveTrueOrderByCreatedAtDesc(
            KnowledgeStatus status, KnowledgeVisibility visibility);
}
