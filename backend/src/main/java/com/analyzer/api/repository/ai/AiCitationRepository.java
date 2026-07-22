package com.analyzer.api.repository.ai;

import com.analyzer.api.entity.AiCitation;
import com.analyzer.api.enums.CitationSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiCitationRepository extends JpaRepository<AiCitation, String> {

    List<AiCitation> findBySourceTypeAndSourceReferenceId(CitationSourceType sourceType, String sourceReferenceId);

    List<AiCitation> findByLegalTicket_Id(String ticketId);

    List<AiCitation> findByChatMessage_Id(String chatMessageId);

    long countByChatMessage_Id(String chatMessageId);
}
