package com.analyzer.api.repository.legalticket;

import com.analyzer.api.entity.ConversationShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ConversationShareRepository extends JpaRepository<ConversationShare, String> {
    Optional<ConversationShare> findByShareTokenHash(String hash);
    Optional<ConversationShare> findByIdAndTicket_Id(String id, String ticketId);
}
