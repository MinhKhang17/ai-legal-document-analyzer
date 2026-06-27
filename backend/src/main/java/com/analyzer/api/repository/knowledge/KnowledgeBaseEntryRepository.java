package com.analyzer.api.repository.knowledge;

import com.analyzer.api.entity.KnowledgeBaseEntry;
import com.analyzer.api.enums.KnowledgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseEntryRepository extends JpaRepository<KnowledgeBaseEntry, String> {

    Optional<KnowledgeBaseEntry> findByCode(String code);

    List<KnowledgeBaseEntry> findByCurrentStatus(KnowledgeStatus status);
}
