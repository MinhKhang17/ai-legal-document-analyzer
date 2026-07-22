package com.analyzer.api.service.revenue.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.*;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.ExpertRevenueStatement;
import com.analyzer.api.entity.ExpertRevenueStatementItem;
import com.analyzer.api.enums.ExpertPaymentStatus;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.enums.RevenueStatementStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.legalticket.LegalTicketRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.revenue.ExpertRevenueStatementRepository;
import com.analyzer.api.repository.revenue.ExpertRevenueStatementItemRepository;
import com.analyzer.api.service.revenue.ExpertRevenueService;
import com.analyzer.api.service.revenue.RevenuePayrollService;
import com.analyzer.api.service.revenue.RevenueSettingService;
import com.analyzer.api.service.legalticket.TicketCollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExpertRevenueServiceImpl implements ExpertRevenueService {
    private final LegalTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RevenueSettingService revenueSettingService;
    private final TicketCollaborationService collaborationService;
    private final RevenuePayrollService revenuePayrollService;
    private final ExpertRevenueStatementRepository statementRepository;
    private final ExpertRevenueStatementItemRepository statementItemRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ExpertRevenueServiceImpl(LegalTicketRepository ticketRepository, UserRepository userRepository,
            RevenueSettingService revenueSettingService, TicketCollaborationService collaborationService,
            RevenuePayrollService revenuePayrollService, ExpertRevenueStatementRepository statementRepository,
            ExpertRevenueStatementItemRepository statementItemRepository) {
        this.ticketRepository=ticketRepository; this.userRepository=userRepository; this.revenueSettingService=revenueSettingService;
        this.collaborationService=collaborationService; this.revenuePayrollService=revenuePayrollService;
        this.statementRepository=statementRepository; this.statementItemRepository=statementItemRepository;
    }
    /** Compatibility constructor retained for existing unit tests and external wiring. */
    public ExpertRevenueServiceImpl(LegalTicketRepository ticketRepository, UserRepository userRepository,
            RevenueSettingService revenueSettingService, TicketCollaborationService collaborationService) {
        this(ticketRepository,userRepository,revenueSettingService,collaborationService,null,null,null);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpertRevenueSummaryResponse getSummary(Long expertId) {
        if(statementRepository!=null){
            var statements=statementRepository.findByExpertIdOrderByPeriodStartDateDesc(expertId,PageRequest.of(0,1000)).getContent();
            long resolved=statements.stream().mapToLong(ExpertRevenueStatement::getTicketCount).sum();
            return ExpertRevenueSummaryResponse.builder().assignedTicketCount(ticketRepository.findByAssignedLawyerIdAndDeletedFalse(expertId).size()).resolvedTicketCount(resolved)
                .paidTicketCount(statements.stream().filter(s->s.getStatus()==RevenueStatementStatus.PAID).mapToLong(ExpertRevenueStatement::getTicketCount).sum())
                .pendingPaymentTicketCount(statements.stream().filter(s->s.getStatus()==RevenueStatementStatus.PAYMENT_PENDING).mapToLong(ExpertRevenueStatement::getTicketCount).sum())
                .totalRevenue(sumStatements(statements,ExpertRevenueStatement::getGrossConsultationFee)).paidRevenue(sumStatements(statements,ExpertRevenueStatement::getPaidAmount))
                .pendingRevenue(sumStatements(statements,ExpertRevenueStatement::getRemainingAmount)).totalPlatformFee(sumStatements(statements,ExpertRevenueStatement::getTotalPlatformFee))
                .totalExpertPayout(sumStatements(statements,ExpertRevenueStatement::getFinalPayout)).build();
        }
        List<LegalTicket> tickets = ticketRepository.findByAssignedLawyerIdAndDeletedFalse(expertId);
        return ExpertRevenueSummaryResponse.builder()
                .assignedTicketCount(tickets.size())
                .resolvedTicketCount(tickets.stream().filter(this::isResolved).count())
                .paidTicketCount(tickets.stream().filter(this::isResolved)
                        .filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PAID).count())
                .pendingPaymentTicketCount(tickets.stream().filter(this::isResolved)
                        .filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PENDING).count())
                .totalRevenue(sum(tickets, null, LegalTicket::getConsultationFee))
                .paidRevenue(sum(tickets, ExpertPaymentStatus.PAID, LegalTicket::getConsultationFee))
                .pendingRevenue(sum(tickets, ExpertPaymentStatus.PENDING, LegalTicket::getConsultationFee))
                .totalPlatformFee(sum(tickets, null, LegalTicket::getPlatformFee))
                .totalExpertPayout(sum(tickets, null, LegalTicket::getExpertPayout))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpertRevenueTicketResponse> getTickets(Long expertId, int page, int size) {
        if(statementRepository!=null&&statementItemRepository!=null){
            var statements=statementRepository.findByExpertIdOrderByPeriodStartDateDesc(expertId,PageRequest.of(0,1000)).getContent();
            var rows=statements.stream().flatMap(s->statementItemRepository.findByStatementIdOrderByRecognizedAtAsc(s.getId()).stream().map(i->toResponse(i,s))).sorted(java.util.Comparator.comparing(ExpertRevenueTicketResponse::getResolvedAt,java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))).toList();
            int safeSize=Math.min(Math.max(size,1),100),from=Math.min(Math.max(page,0)*safeSize,rows.size()),to=Math.min(from+safeSize,rows.size());
            return PageResponse.<ExpertRevenueTicketResponse>builder().items(rows.subList(from,to)).page(Math.max(page,0)).size(safeSize).totalItems(rows.size()).totalPages((rows.size()+safeSize-1)/safeSize).build();
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LegalTicket> pageResult = ticketRepository.findByAssignedLawyerIdAndDeletedFalse(expertId, pageable);

        return PageResponse.<ExpertRevenueTicketResponse>builder()
                .items(pageResult.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalItems(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public ExpertRevenueTicketResponse updatePayment(String ticketId, Long adminId, UpdateExpertPaymentRequest request) {
        LegalTicket ticket = ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getAssignedLawyer() == null) {
            throw new ConflictException("Ticket has not been assigned to an expert");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));

        BigDecimal previousPayout = ticket.getExpertPayout();
        ticket.setConsultationFee(request.getConsultationFee());
        ticket.setExpertPaymentStatus(request.getPaymentStatus());
        ticket.setExpertPaidAt(request.getPaymentStatus() == ExpertPaymentStatus.PAID
                ? LocalDateTime.now() : null);
        applyCommissionSnapshot(ticket);
        if(revenuePayrollService!=null) revenuePayrollService.reconcileTicketFinancialChange(ticket, previousPayout, admin, "Admin updated consultation fee");

        LegalTicket saved = ticketRepository.save(ticket);
        collaborationService.auditTicket(saved, admin, "EXPERT_PAYMENT_UPDATED",
                "{\"consultationFee\":\"" + request.getConsultationFee() + "\",\"paymentStatus\":\""
                        + request.getPaymentStatus() + "\"}");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ExpertRevenueTicketResponse resetFinancials(String ticketId, Long adminId) {
        LegalTicket ticket = ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getExpertPaymentStatus() == ExpertPaymentStatus.PAID) {
            throw new ConflictException("CANNOT_RESET_PAID_TICKET");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));

        BigDecimal previousPayout = ticket.getExpertPayout();
        ticket.setConsultationFee(BigDecimal.ZERO);
        ticket.setExpertPaymentStatus(ExpertPaymentStatus.UNPAID);
        ticket.setExpertPaidAt(null);
        ticket.setCommissionRate(null);
        ticket.setPlatformFee(null);
        ticket.setExpertPayout(null);
        if(revenuePayrollService!=null) revenuePayrollService.reconcileTicketFinancialChange(ticket, previousPayout, admin, "Admin reset ticket financials");

        LegalTicket saved = ticketRepository.save(ticket);
        collaborationService.auditTicket(saved, admin, "EXPERT_PAYMENT_RESET", "{}");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRevenueOverviewResponse getAdminOverview() {
        if(statementRepository!=null){
            var statements=statementRepository.findAll();
            var byExpert=statements.stream().collect(Collectors.groupingBy(s->s.getExpert().getId())).entrySet().stream().map(e->ExpertRevenueBreakdownItem.builder().expertId(e.getKey()).expertName(e.getValue().getFirst().getExpertNameSnapshot()).ticketCount(e.getValue().stream().mapToLong(ExpertRevenueStatement::getTicketCount).sum()).totalConsultationFee(sumStatements(e.getValue(),ExpertRevenueStatement::getGrossConsultationFee)).totalExpertPayout(sumStatements(e.getValue(),ExpertRevenueStatement::getFinalPayout)).build()).toList();
            return AdminRevenueOverviewResponse.builder().totalTicketCount(statements.stream().mapToLong(ExpertRevenueStatement::getTicketCount).sum()).paidTicketCount(statements.stream().filter(s->s.getStatus()==RevenueStatementStatus.PAID).mapToLong(ExpertRevenueStatement::getTicketCount).sum()).pendingPaymentTicketCount(statements.stream().filter(s->s.getStatus()==RevenueStatementStatus.PAYMENT_PENDING).mapToLong(ExpertRevenueStatement::getTicketCount).sum()).totalConsultationFee(sumStatements(statements,ExpertRevenueStatement::getGrossConsultationFee)).totalPlatformFee(sumStatements(statements,ExpertRevenueStatement::getTotalPlatformFee)).totalExpertPayout(sumStatements(statements,ExpertRevenueStatement::getFinalPayout)).byExpert(byExpert).build();
        }
        List<LegalTicket> tickets = ticketRepository.findByStatusInAndAssignedLawyerIsNotNullAndDeletedFalse(
                List.of(LegalTicketStatus.RESOLVED, LegalTicketStatus.CLOSED));

        var byExpert = tickets.stream()
                .collect(Collectors.groupingBy(t -> t.getAssignedLawyer().getId()))
                .entrySet().stream()
                .map(entry -> {
                    List<LegalTicket> expertTickets = entry.getValue();
                    User expert = expertTickets.get(0).getAssignedLawyer();
                    return ExpertRevenueBreakdownItem.builder()
                            .expertId(expert.getId())
                            .expertName((expert.getFirstName() + " " + expert.getLastName()).trim())
                            .ticketCount(expertTickets.size())
                            .totalConsultationFee(sum(expertTickets, null, LegalTicket::getConsultationFee))
                            .totalExpertPayout(sum(expertTickets, null, LegalTicket::getExpertPayout))
                            .build();
                })
                .collect(Collectors.toList());

        return AdminRevenueOverviewResponse.builder()
                .totalTicketCount(tickets.size())
                .paidTicketCount(tickets.stream().filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PAID).count())
                .pendingPaymentTicketCount(tickets.stream().filter(t -> t.getExpertPaymentStatus() == ExpertPaymentStatus.PENDING).count())
                .totalConsultationFee(sum(tickets, null, LegalTicket::getConsultationFee))
                .totalPlatformFee(sum(tickets, null, LegalTicket::getPlatformFee))
                .totalExpertPayout(sum(tickets, null, LegalTicket::getExpertPayout))
                .byExpert(byExpert)
                .build();
    }

    @Override
    public void applyCommissionSnapshot(LegalTicket ticket) {
        if (ticket.getAssignedLawyer() == null) {
            return;
        }
        if (ticket.getCommissionRate() == null) {
            ticket.setCommissionRate(revenueSettingService.getCurrentRate());
        }
        BigDecimal fee;
        if (ticket.getPricingType() == com.analyzer.api.enums.TicketPricingType.PLAN_INCLUDED
                && ticket.getInternalTicketValue() != null) {
            fee = ticket.getInternalTicketValue();
        } else if (ticket.getUserPrice() != null) {
            fee = ticket.getUserPrice();
        } else {
            fee = ticket.getConsultationFee() == null ? BigDecimal.ZERO : ticket.getConsultationFee();
        }
        ticket.setConsultationFee(fee);
        BigDecimal platformFee = fee.multiply(ticket.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
        ticket.setPlatformFee(platformFee);
        ticket.setExpertPayout(fee.subtract(platformFee));
    }

    private BigDecimal sum(List<LegalTicket> tickets, ExpertPaymentStatus status,
                            java.util.function.Function<LegalTicket, BigDecimal> extractor) {
        return tickets.stream()
                .filter(this::isResolved)
                .filter(t -> status == null || t.getExpertPaymentStatus() == status)
                .map(extractor)
                .map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isResolved(LegalTicket ticket) {
        return ticket.getStatus() == LegalTicketStatus.RESOLVED || ticket.getStatus() == LegalTicketStatus.CLOSED;
    }

    private ExpertRevenueTicketResponse toResponse(LegalTicket ticket) {
        return ExpertRevenueTicketResponse.builder().ticketId(ticket.getId()).ticketCode(ticket.getTicketCode())
                .ticketStatus(ticket.getStatus()).consultationFee(ticket.getConsultationFee())
                .commissionRate(ticket.getCommissionRate())
                .platformFee(ticket.getPlatformFee())
                .expertPayout(ticket.getExpertPayout())
                .paymentStatus(ticket.getExpertPaymentStatus()).resolvedAt(ticket.getResolvedAt())
                .paidAt(ticket.getExpertPaidAt()).build();
    }

    private ExpertRevenueTicketResponse toResponse(ExpertRevenueStatementItem item,ExpertRevenueStatement statement){return ExpertRevenueTicketResponse.builder().ticketId(item.getTicket().getId()).ticketCode(item.getTicketCode()).ticketStatus(item.getTicketStatusSnapshot()).consultationFee(item.getConsultationFee()).commissionRate(item.getCommissionRateSnapshot()).platformFee(item.getPlatformFee()).expertPayout(item.getExpertPayout()).paymentStatus(statement.getStatus()==RevenueStatementStatus.PAID?ExpertPaymentStatus.PAID:statement.getStatus()==RevenueStatementStatus.PAYMENT_PENDING?ExpertPaymentStatus.PENDING:ExpertPaymentStatus.UNPAID).resolvedAt(item.getRecognizedAt()).paidAt(statement.getPaidAt()).build();}
    private BigDecimal sumStatements(List<ExpertRevenueStatement> statements,java.util.function.Function<ExpertRevenueStatement,BigDecimal> extractor){return statements.stream().map(extractor).map(v->v==null?BigDecimal.ZERO:v).reduce(BigDecimal.ZERO,BigDecimal::add);}
}
