package com.analyzer.api.repository;

import com.analyzer.api.entity.LegalTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegalTicketMessageRepository extends JpaRepository<LegalTicketMessage, String> {

    List<LegalTicketMessage> findByTicket_IdOrderByCreatedAtAsc(String ticketId);
}
