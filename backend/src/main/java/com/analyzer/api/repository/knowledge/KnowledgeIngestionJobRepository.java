package com.analyzer.api.repository.knowledge;

import com.analyzer.api.entity.KnowledgeIngestionJob;
import com.analyzer.api.enums.KnowledgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeIngestionJobRepository extends JpaRepository<KnowledgeIngestionJob, String> {

    Optional<KnowledgeIngestionJob> findByRequestId(String requestId);

    List<KnowledgeIngestionJob> findByStatus(KnowledgeStatus status);
}
