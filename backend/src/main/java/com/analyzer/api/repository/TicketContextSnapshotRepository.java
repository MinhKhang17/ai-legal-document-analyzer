package com.analyzer.api.repository;

import com.analyzer.api.entity.TicketContextSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TicketContextSnapshotRepository extends JpaRepository<TicketContextSnapshot, String> {
    Optional<TicketContextSnapshot> findByTicket_Id(String ticketId);
}
