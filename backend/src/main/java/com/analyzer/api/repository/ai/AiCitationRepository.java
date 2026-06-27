package com.analyzer.api.repository.ai;

import com.analyzer.api.entity.AiCitation;
import com.analyzer.api.enums.CitationSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCitationRepository extends JpaRepository<AiCitation, String> {

    List<AiCitation> findBySourceTypeAndSourceReferenceId(CitationSourceType sourceType, String sourceReferenceId);
}
