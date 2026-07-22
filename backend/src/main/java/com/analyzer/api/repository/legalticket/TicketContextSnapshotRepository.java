package com.analyzer.api.repository.legalticket;

import com.analyzer.api.entity.TicketContextSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TicketContextSnapshotRepository extends JpaRepository<TicketContextSnapshot, String> {
    Optional<TicketContextSnapshot> findByTicket_Id(String ticketId);
}
