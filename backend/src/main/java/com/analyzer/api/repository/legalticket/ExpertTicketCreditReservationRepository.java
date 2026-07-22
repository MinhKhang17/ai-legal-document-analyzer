package com.analyzer.api.repository.legalticket;

import com.analyzer.api.entity.ExpertTicketCreditReservation;
import com.analyzer.api.enums.TicketQuotaReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface ExpertTicketCreditReservationRepository extends JpaRepository<ExpertTicketCreditReservation, String> {
    Optional<ExpertTicketCreditReservation> findByTicket_Id(String ticketId);
    long countByUser_IdAndQuotaCycleAndStatusIn(Long userId, String quotaCycle,
                                                Collection<TicketQuotaReservationStatus> statuses);
}
