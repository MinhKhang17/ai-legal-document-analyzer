package com.analyzer.api.repository;

import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

@Repository
public interface LegalTicketRepository extends JpaRepository<LegalTicket, String> {

    Optional<LegalTicket> findByIdAndDeletedFalse(String id);

    Optional<LegalTicket> findByRequestIdAndCreatedByIdAndDeletedFalse(String requestId, Long createdById);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByCreatedByIdAndDeletedFalse(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByCreatedByIdAndStatusAndDeletedFalse(Long userId, LegalTicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findAllByDeletedFalse(Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByStatusAndDeletedFalse(LegalTicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByAssignedLawyerIdAndDeletedFalse(Long lawyerId, Pageable pageable);

    List<LegalTicket> findByAssignedLawyerIdAndDeletedFalse(Long lawyerId);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByAssignedLawyerIdAndStatusAndDeletedFalse(Long lawyerId, LegalTicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByRiskLevelAndDeletedFalse(RiskLevel riskLevel, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByStatusAndRiskLevelAndDeletedFalse(LegalTicketStatus status, RiskLevel riskLevel, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByTicketTypeAndDeletedFalse(LegalTicketType ticketType, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByStatusAndTicketTypeAndDeletedFalse(LegalTicketStatus status, LegalTicketType ticketType, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByRiskLevelAndTicketTypeAndDeletedFalse(RiskLevel riskLevel, LegalTicketType ticketType, Pageable pageable);

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "assignedLawyer"})
    Page<LegalTicket> findByStatusAndRiskLevelAndTicketTypeAndDeletedFalse(LegalTicketStatus status, RiskLevel riskLevel, LegalTicketType ticketType, Pageable pageable);

    long countByCreatedByIdAndDeletedFalseAndCreatedAtBetween(Long createdById, LocalDateTime start, LocalDateTime end);

    long countByCreatedByIdAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
            Long createdById,
            Collection<LegalTicketStatus> excludedStatuses,
            LocalDateTime start,
            LocalDateTime end);

    long countByCreatedByIdAndTicketTypeAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
            Long createdById,
            LegalTicketType ticketType,
            Collection<LegalTicketStatus> excludedStatuses,
            LocalDateTime start,
            LocalDateTime end);
}
