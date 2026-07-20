package com.analyzer.api.repository;

import com.analyzer.api.entity.LegalTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface LegalTicketMessageRepository extends JpaRepository<LegalTicketMessage, String> {

    List<LegalTicketMessage> findByTicket_IdOrderByCreatedAtAsc(String ticketId);
    Page<LegalTicketMessage> findByTicket_IdAndDeletedAtIsNullOrderByCreatedAtDesc(String ticketId, Pageable pageable);
}
