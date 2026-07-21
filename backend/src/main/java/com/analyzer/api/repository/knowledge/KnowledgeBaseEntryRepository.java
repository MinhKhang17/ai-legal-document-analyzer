package com.analyzer.api.repository.knowledge;

import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.enums.KnowledgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseEntryRepository extends JpaRepository<KnowledgeBaseEntry, String>,
        JpaSpecificationExecutor<KnowledgeBaseEntry> {

    Optional<KnowledgeBaseEntry> findByCode(String code);

    Optional<KnowledgeBaseEntry> findFirstByTitleIgnoreCase(String title);

    List<KnowledgeBaseEntry> findByCurrentStatus(KnowledgeStatus status);

}
