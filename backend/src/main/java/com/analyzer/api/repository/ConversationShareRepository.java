package com.analyzer.api.repository;

import com.analyzer.api.entity.ConversationShare;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConversationShareRepository extends JpaRepository<ConversationShare, String> {
    Optional<ConversationShare> findByShareTokenHash(String hash);
    Optional<ConversationShare> findByIdAndTicket_Id(String id, String ticketId);
}
