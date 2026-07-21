package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.*;
import com.analyzer.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service @RequiredArgsConstructor
public class RevenuePayrollService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final RevenuePeriodRepository periodRepository;
    private final ExpertRevenueStatementRepository statementRepository;
    private final ExpertRevenueStatementItemRepository itemRepository;
    private final RevenueAdjustmentRepository adjustmentRepository;
    private final ExpertPayoutTransactionRepository payoutRepository;
    private final UserRepository userRepository;
    private final LegalTicketRepository ticketRepository;
    private final CommissionPolicyManagementService commission;
    private final FinancialAuditService audit;

    @Transactional
    public void recognizeResolvedTicket(LegalTicket ticket){
        if(ticket.getAssignedLawyer()==null||ticket.getResolvedAt()==null||itemRepository.existsByTicketId(ticket.getId())) return;
        LocalDate recognizedDate=ticket.getResolvedAt().atZone(ZONE).toLocalDate();
        RevenuePeriod foundPeriod=findOrCreatePeriod(recognizedDate);
        RevenuePeriod period=periodRepository.lockById(foundPeriod.getId()).orElse(foundPeriod);
        BigDecimal fee=money(ticket.getConsultationFee()); BigDecimal rate=commission.rateFor(recognizedDate);
        BigDecimal platform=fee.multiply(rate).setScale(2,RoundingMode.HALF_UP); BigDecimal payout=fee.subtract(platform);
        ticket.setCommissionRate(rate); ticket.setPlatformFee(platform); ticket.setExpertPayout(payout);
        if(period.getStatus()==RevenuePeriodStatus.CLOSED||period.getStatus()==RevenuePeriodStatus.PAID){
            RevenuePeriod current=findOrCreatePeriod(LocalDate.now(ZONE));
            createAdjustmentInternal(period,current,ticket.getAssignedLawyer(),ticket,RevenueAdjustmentType.FEE_INCREASE,payout,"Late recognition after period close",ticket.getAssignedLawyer());
            return;
        }
        ExpertRevenueStatement statement=statementRepository.findByPeriodIdAndExpertId(period.getId(),ticket.getAssignedLawyer().getId()).orElseGet(()->statementRepository.save(newStatement(period,ticket.getAssignedLawyer())));
        try{ itemRepository.saveAndFlush(ExpertRevenueStatementItem.builder().statement(statement).ticket(ticket).ticketCode(Optional.ofNullable(ticket.getTicketCode()).orElse(ticket.getId())).consultationFee(fee).commissionRateSnapshot(rate).platformFee(platform).expertPayout(payout).recognizedAt(ticket.getResolvedAt()).assignedExpertIdSnapshot(ticket.getAssignedLawyer().getId()).ticketStatusSnapshot(LegalTicketStatus.RESOLVED).build()); }
        catch(DataIntegrityViolationException ignored){ return; }
        recalculate(statement);
    }

    @Transactional
    public void reconcileTicketFinancialChange(LegalTicket ticket,BigDecimal previousPayout,User actor,String reason){
        ExpertRevenueStatementItem item=itemRepository.findByTicketId(ticket.getId()).orElse(null); if(item==null)return;
        BigDecimal newPayout=money(ticket.getExpertPayout()); RevenuePeriod original=item.getStatement().getPeriod();
        if(original.getStatus()==RevenuePeriodStatus.OPEN||original.getStatus()==RevenuePeriodStatus.CALCULATING){
            BigDecimal fee=money(ticket.getConsultationFee()),rate=ticket.getCommissionRate()==null?item.getCommissionRateSnapshot():ticket.getCommissionRate();BigDecimal platform=fee.multiply(rate).setScale(2,RoundingMode.HALF_UP);
            item.setConsultationFee(fee);item.setCommissionRateSnapshot(rate);item.setPlatformFee(platform);item.setExpertPayout(fee.subtract(platform));recalculate(item.getStatement());recalculatePeriod(original);return;
        }
        BigDecimal delta=newPayout.subtract(money(previousPayout)); if(delta.signum()==0)return;
        RevenuePeriod current=findOrCreatePeriod(LocalDate.now(ZONE));RevenueAdjustmentType type=delta.signum()>0?RevenueAdjustmentType.FEE_INCREASE:RevenueAdjustmentType.FEE_DECREASE;
        createAdjustmentInternal(original,current,item.getStatement().getExpert(),ticket,type,delta,reason,actor);
    }

    @Transactional public RevenuePayrollDtos.Period ensureCurrentPeriod(){ return periodDto(findOrCreatePeriod(LocalDate.now(ZONE))); }
    @Transactional public void generateDraftStatements(){ findOrCreatePeriod(LocalDate.now(ZONE)); ticketRepository.findByStatusInAndAssignedLawyerIsNotNullAndDeletedFalse(List.of(LegalTicketStatus.RESOLVED,LegalTicketStatus.CLOSED)).forEach(this::recognizeResolvedTicket); }
    @Transactional(readOnly=true) public PageResponse<RevenuePayrollDtos.Period> periods(int page,int size){ var p=periodRepository.findAllByOrderByStartDateDesc(PageRequest.of(page,Math.min(Math.max(size,1),100))); return PageResponse.<RevenuePayrollDtos.Period>builder().items(p.getContent().stream().map(this::periodDto).toList()).page(p.getNumber()).size(p.getSize()).totalItems(p.getTotalElements()).totalPages(p.getTotalPages()).build(); }
    @Transactional(readOnly=true) public RevenuePayrollDtos.Period period(String id){ return periodDto(requirePeriod(id)); }
    @Transactional(readOnly=true) public List<RevenuePayrollDtos.Statement> periodStatements(String id){ requirePeriod(id); return statementRepository.findByPeriodIdOrderByExpertNameSnapshotAsc(id).stream().map(s->statementDto(s,false)).toList(); }
    @Transactional(readOnly=true) public RevenuePayrollDtos.Statement adminStatement(String id){ return statementDto(requireStatement(id),true); }
    @Transactional(readOnly=true) public PageResponse<RevenuePayrollDtos.Statement> expertStatements(Long expertId,int page,int size){ var p=statementRepository.findByExpertIdOrderByPeriodStartDateDesc(expertId,PageRequest.of(page,Math.min(Math.max(size,1),100))); return PageResponse.<RevenuePayrollDtos.Statement>builder().items(p.getContent().stream().map(s->statementDto(s,false)).toList()).page(p.getNumber()).size(p.getSize()).totalItems(p.getTotalElements()).totalPages(p.getTotalPages()).build(); }
    @Transactional(readOnly=true) public RevenuePayrollDtos.Statement expertStatement(Long expertId,String id){ var s=requireStatement(id); if(!s.getExpert().getId().equals(expertId))throw new ForbiddenException("REVENUE_STATEMENT_ACCESS_DENIED"); return statementDto(s,true); }

    @Transactional
    public RevenuePayrollDtos.Period calculate(String periodId,Long adminId){
        RevenuePeriod period=periodRepository.lockById(periodId).orElseThrow(()->new ResourceNotFoundException("REVENUE_PERIOD_NOT_FOUND"));
        if(period.getStatus()==RevenuePeriodStatus.CLOSED||period.getStatus()==RevenuePeriodStatus.PAID) throw new ConflictException("PERIOD_ALREADY_CLOSED");
        period.setStatus(RevenuePeriodStatus.CALCULATING);
        statementRepository.findByPeriodIdOrderByExpertNameSnapshotAsc(periodId).forEach(this::recalculate);
        recalculatePeriod(period);
        audit.record("REVENUE_PERIOD_CALCULATED",user(adminId),"REVENUE_PERIOD",periodId,null,totalsJson(period),null,periodId);
        return periodDto(period);
    }

    @Transactional
    public RevenuePayrollDtos.Period close(String periodId,Long adminId){
        RevenuePeriod period=periodRepository.lockById(periodId).orElseThrow(()->new ResourceNotFoundException("REVENUE_PERIOD_NOT_FOUND"));
        if(period.getStatus()==RevenuePeriodStatus.CLOSED||period.getStatus()==RevenuePeriodStatus.PAID) return periodDto(period);
        if(period.getStatus()!=RevenuePeriodStatus.CALCULATING) throw new ConflictException("PERIOD_NOT_CALCULATED");
        LocalDateTime now=LocalDateTime.now(ZONE);
        for(ExpertRevenueStatement s:statementRepository.findByPeriodIdOrderByExpertNameSnapshotAsc(periodId)){
            recalculate(s); s.setStatus(RevenueStatementStatus.CONFIRMED); if(s.getConfirmedAt()==null)s.setConfirmedAt(now);
        }
        recalculatePeriod(period); period.setStatus(RevenuePeriodStatus.CLOSED); period.setClosedAt(now); period.setClosedBy(user(adminId));
        audit.record("REVENUE_PERIOD_CLOSED",period.getClosedBy(),"REVENUE_PERIOD",periodId,null,totalsJson(period),null,periodId);
        return periodDto(period);
    }

    @Transactional
    public RevenuePayrollDtos.Adjustment addAdjustment(String periodId,Long adminId,RevenuePayrollDtos.CreateAdjustment input){
        RevenuePeriod applied=periodRepository.lockById(periodId).orElseThrow(()->new ResourceNotFoundException("REVENUE_PERIOD_NOT_FOUND"));
        if(applied.getStatus()==RevenuePeriodStatus.CLOSED||applied.getStatus()==RevenuePeriodStatus.PAID) throw new ConflictException("PERIOD_ALREADY_CLOSED");
        User expert=user(input.expertId()); LegalTicket ticket=input.ticketId()==null?null:ticketRepository.findById(input.ticketId()).orElseThrow(()->new ResourceNotFoundException("TICKET_NOT_FOUND"));
        RevenuePeriod original=ticket==null?null:itemRepository.findByTicketId(ticket.getId()).map(i->i.getStatement().getPeriod()).orElse(null);
        RevenueAdjustment a=createAdjustmentInternal(original,applied,expert,ticket,input.type(),input.amount(),input.reason(),user(adminId));
        return adjustmentDto(a);
    }

    @Transactional
    public RevenuePayrollDtos.Statement markRegularPaymentPending(String statementId,Long adminId){
        ExpertRevenueStatement s=statementRepository.lockById(statementId).orElseThrow(()->new ResourceNotFoundException("REVENUE_STATEMENT_NOT_FOUND"));
        if(!Set.of(RevenueStatementStatus.CONFIRMED,RevenueStatementStatus.PARTIALLY_PAID).contains(s.getStatus())) throw new ConflictException("PERIOD_NOT_CONFIRMED");
        s.setStatus(RevenueStatementStatus.PAYMENT_PENDING); audit.record("EARLY_PAYOUT_PAYMENT_PENDING",user(adminId),"REVENUE_STATEMENT",s.getId(),null,"{\"status\":\"PAYMENT_PENDING\"}",null,s.getId()); return statementDto(s,true);
    }

    @Transactional
    public RevenuePayrollDtos.Statement markRegularPaid(String statementId,Long adminId,RevenuePayrollDtos.MarkStatementPayment input){
        ExpertRevenueStatement s=statementRepository.lockById(statementId).orElseThrow(()->new ResourceNotFoundException("REVENUE_STATEMENT_NOT_FOUND"));
        if(s.getStatus()==RevenueStatementStatus.PAID) return statementDto(s,true);
        if(s.getStatus()!=RevenueStatementStatus.PAYMENT_PENDING) throw new ConflictException("INVALID_STATUS_TRANSITION");
        BigDecimal amount=s.getRemainingAmount(); if(amount.signum()<0)throw new ConflictException("INSUFFICIENT_AVAILABLE_BALANCE");
        if(amount.signum()>0) payoutRepository.save(ExpertPayoutTransaction.builder().expert(s.getExpert()).statement(s).amount(amount).type(PayoutTransactionType.REGULAR).status(PayoutTransactionStatus.PAID).paidAt(LocalDateTime.now(ZONE)).paymentReference(input.paymentReference()).paidBy(user(adminId)).idempotencyKey(input.idempotencyKey()).build());
        s.setPaidAmount(s.getPaidAmount().add(amount));s.setRemainingAmount(BigDecimal.ZERO);s.setStatus(RevenueStatementStatus.PAID);if(s.getPaidAt()==null)s.setPaidAt(LocalDateTime.now(ZONE));s.setPaymentReference(input.paymentReference());
        updatePeriodPaidStatus(s.getPeriod()); return statementDto(s,true);
    }

    private RevenueAdjustment createAdjustmentInternal(RevenuePeriod original,RevenuePeriod applied,User expert,LegalTicket ticket,RevenueAdjustmentType type,BigDecimal amount,String reason,User actor){
        RevenueAdjustment a=adjustmentRepository.save(RevenueAdjustment.builder().originalPeriod(original).appliedPeriod(applied).expert(expert).ticket(ticket).type(type).amount(amount.setScale(2,RoundingMode.HALF_UP)).reason(reason).createdBy(actor).build());
        ExpertRevenueStatement s=statementRepository.findByPeriodIdAndExpertId(applied.getId(),expert.getId()).orElseGet(()->statementRepository.save(newStatement(applied,expert))); recalculate(s); recalculatePeriod(applied);
        audit.record("REVENUE_ADJUSTMENT_CREATED",actor,"REVENUE_ADJUSTMENT",a.getId(),null,"{\"amount\":\""+a.getAmount()+"\",\"expertId\":"+expert.getId()+"}",reason,a.getId()); return a;
    }
    private RevenuePeriod findOrCreatePeriod(LocalDate date){
        Optional<RevenuePeriod> found=periodRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date,date);
        if(found.isPresent())return found.get();
        LocalDate start=date.withDayOfMonth(1),end=date.with(TemporalAdjusters.lastDayOfMonth());
        try{return periodRepository.saveAndFlush(RevenuePeriod.builder().periodCode(start.toString().substring(0,7)).startDate(start).endDate(end).cutoffAt(end.plusDays(1).atStartOfDay()).build());}
        catch(DataIntegrityViolationException e){return periodRepository.findByPeriodCode(start.toString().substring(0,7)).orElseThrow(()->e);}
    }
    private ExpertRevenueStatement newStatement(RevenuePeriod p,User e){String name=(e.getFirstName()+" "+e.getLastName()).trim();return ExpertRevenueStatement.builder().period(p).expert(e).expertNameSnapshot(name).build();}
    private void recalculate(ExpertRevenueStatement s){
        List<ExpertRevenueStatementItem> items=itemRepository.findByStatementIdOrderByRecognizedAtAsc(s.getId());
        BigDecimal gross=sumItems(items,ExpertRevenueStatementItem::getConsultationFee),platform=sumItems(items,ExpertRevenueStatementItem::getPlatformFee),payout=sumItems(items,ExpertRevenueStatementItem::getExpertPayout),adjust=adjustmentRepository.sumFor(s.getPeriod().getId(),s.getExpert().getId());
        s.setTicketCount(items.size());s.setGrossConsultationFee(gross);s.setTotalPlatformFee(platform);s.setTotalExpertPayout(payout);s.setAdjustmentAmount(adjust);s.setFinalPayout(payout.add(adjust));s.setRemainingAmount(s.getFinalPayout().subtract(s.getPaidAmount()));
    }
    private void recalculatePeriod(RevenuePeriod p){List<ExpertRevenueStatement> all=statementRepository.findByPeriodIdOrderByExpertNameSnapshotAsc(p.getId());p.setTotalGross(sumStatements(all,ExpertRevenueStatement::getGrossConsultationFee));p.setTotalPlatformFee(sumStatements(all,ExpertRevenueStatement::getTotalPlatformFee));p.setTotalExpertPayout(sumStatements(all,ExpertRevenueStatement::getTotalExpertPayout));p.setTotalAdjustments(sumStatements(all,ExpertRevenueStatement::getAdjustmentAmount));p.setTotalFinalPayout(sumStatements(all,ExpertRevenueStatement::getFinalPayout));}
    private void updatePeriodPaidStatus(RevenuePeriod p){if(statementRepository.findByPeriodIdOrderByExpertNameSnapshotAsc(p.getId()).stream().allMatch(s->s.getStatus()==RevenueStatementStatus.PAID))p.setStatus(RevenuePeriodStatus.PAID);}
    private BigDecimal sumItems(List<ExpertRevenueStatementItem> l,java.util.function.Function<ExpertRevenueStatementItem,BigDecimal> f){return l.stream().map(f).map(this::money).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private BigDecimal sumStatements(List<ExpertRevenueStatement> l,java.util.function.Function<ExpertRevenueStatement,BigDecimal> f){return l.stream().map(f).map(this::money).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private BigDecimal money(BigDecimal v){return v==null?BigDecimal.ZERO:v.setScale(2,RoundingMode.HALF_UP);}
    private RevenuePeriod requirePeriod(String id){return periodRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("REVENUE_PERIOD_NOT_FOUND"));}
    private ExpertRevenueStatement requireStatement(String id){return statementRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("REVENUE_STATEMENT_NOT_FOUND"));}
    private User user(Long id){return userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("USER_NOT_FOUND"));}
    RevenuePayrollDtos.Period periodDto(RevenuePeriod p){return new RevenuePayrollDtos.Period(p.getId(),p.getPeriodCode(),p.getStartDate(),p.getEndDate(),p.getCutoffAt(),p.getStatus(),p.getClosedAt(),p.getTotalGross(),p.getTotalPlatformFee(),p.getTotalExpertPayout(),p.getTotalAdjustments(),p.getTotalFinalPayout(),p.getVersion()==null?0:p.getVersion());}
    RevenuePayrollDtos.Statement statementDto(ExpertRevenueStatement s,boolean details){return new RevenuePayrollDtos.Statement(s.getId(),periodDto(s.getPeriod()),s.getExpert().getId(),s.getExpertNameSnapshot(),s.getTicketCount(),s.getGrossConsultationFee(),s.getTotalPlatformFee(),s.getTotalExpertPayout(),s.getAdjustmentAmount(),s.getFinalPayout(),s.getPaidAmount(),s.getRemainingAmount(),s.getStatus(),s.getGeneratedAt(),s.getConfirmedAt(),s.getPaidAt(),s.getPaymentReference(),s.getVersion()==null?0:s.getVersion(),details?itemRepository.findByStatementIdOrderByRecognizedAtAsc(s.getId()).stream().map(this::itemDto).toList():List.of(),details?adjustmentRepository.findByAppliedPeriodIdAndExpertIdOrderByCreatedAtAsc(s.getPeriod().getId(),s.getExpert().getId()).stream().map(this::adjustmentDto).toList():List.of(),details?payoutRepository.findByStatementIdOrderByPaidAtAsc(s.getId()).stream().map(this::payoutDto).toList():List.of());}
    private RevenuePayrollDtos.Item itemDto(ExpertRevenueStatementItem i){return new RevenuePayrollDtos.Item(i.getId(),i.getTicket().getId(),i.getTicketCode(),i.getConsultationFee(),i.getCommissionRateSnapshot(),i.getPlatformFee(),i.getExpertPayout(),i.getRecognizedAt(),i.getAssignedExpertIdSnapshot(),i.getTicketStatusSnapshot());}
    private RevenuePayrollDtos.Adjustment adjustmentDto(RevenueAdjustment a){return new RevenuePayrollDtos.Adjustment(a.getId(),a.getOriginalPeriod()==null?null:a.getOriginalPeriod().getId(),a.getAppliedPeriod().getId(),a.getExpert().getId(),a.getTicket()==null?null:a.getTicket().getId(),a.getType(),a.getAmount(),a.getReason(),a.getCreatedBy().getId(),a.getCreatedAt());}
    private RevenuePayrollDtos.Payout payoutDto(ExpertPayoutTransaction p){return new RevenuePayrollDtos.Payout(p.getId(),p.getExpert().getId(),p.getStatement().getId(),p.getEarlyPayoutRequest()==null?null:p.getEarlyPayoutRequest().getId(),p.getAmount(),p.getType(),p.getStatus(),p.getPaidAt(),p.getPaymentReference(),p.getPaidBy()==null?null:p.getPaidBy().getId());}
    private String totalsJson(RevenuePeriod p){return "{\"gross\":\""+p.getTotalGross()+"\",\"finalPayout\":\""+p.getTotalFinalPayout()+"\"}";}
}
