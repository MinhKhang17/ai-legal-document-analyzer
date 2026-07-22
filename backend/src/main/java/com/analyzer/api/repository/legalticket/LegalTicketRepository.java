package com.analyzer.api.repository.legalticket;

import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.LegalTicketType;
import com.analyzer.api.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;

@Repository
public interface LegalTicketRepository extends JpaRepository<LegalTicket, String> {

    Optional<LegalTicket> findByIdAndDeletedFalse(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from LegalTicket ticket join fetch ticket.createdBy where ticket.id = :id and ticket.deleted = false")
    Optional<LegalTicket> findByIdForPaymentUpdate(@Param("id") String id);

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

    @EntityGraph(attributePaths = {"workspace", "document", "createdBy", "proposedExpert"})
    Page<LegalTicket> findByProposedExpertIdAndStatusInAndDeletedFalse(
            Long expertId, Collection<LegalTicketStatus> statuses, Pageable pageable);

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

    @EntityGraph(attributePaths = {"assignedLawyer"})
    List<LegalTicket> findByStatusInAndAssignedLawyerIsNotNullAndDeletedFalse(Collection<LegalTicketStatus> statuses);

    List<LegalTicket> findByStatusInAndDeletedFalse(Collection<LegalTicketStatus> statuses);

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

    long countByCreatedByIdAndTicketTypeInAndDeletedFalseAndStatusNotInAndCreatedAtBetween(
            Long createdById,
            Collection<LegalTicketType> ticketTypes,
            Collection<LegalTicketStatus> excludedStatuses,
            LocalDateTime start,
            LocalDateTime end);
}
