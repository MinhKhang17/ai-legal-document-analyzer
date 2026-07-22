package com.analyzer.api.service.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.*;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.revenue.EarlyPayoutRequestRepository;
import com.analyzer.api.repository.revenue.ExpertPayoutTransactionRepository;
import com.analyzer.api.repository.revenue.ExpertRevenueStatementRepository;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.EarlyPayoutService;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.FinancialAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class EarlyPayoutServiceImpl implements EarlyPayoutService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<EarlyPayoutStatus> OPEN=Set.of(EarlyPayoutStatus.PENDING_ADMIN_REVIEW,EarlyPayoutStatus.NEED_MORE_INFO,EarlyPayoutStatus.EXPERT_RESPONDED,EarlyPayoutStatus.APPROVED,EarlyPayoutStatus.PAYMENT_PENDING);
    private final EarlyPayoutRequestRepository requestRepository;
    private final ExpertRevenueStatementRepository statementRepository;
    private final ExpertPayoutTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SystemNotificationRepository notificationRepository;
    private final EmailService emailService;
    private final FinancialAuditService audit;

    @Override
    @Transactional
    public RevenuePayrollDtos.EarlyPayout create(Long expertId,RevenuePayrollDtos.CreateEarlyPayout input){
        ExpertRevenueStatement statement=statementRepository.lockById(input.statementId()).orElseThrow(()->new ResourceNotFoundException("REVENUE_STATEMENT_NOT_FOUND"));
        if(!statement.getExpert().getId().equals(expertId))throw new ForbiddenException("REVENUE_STATEMENT_ACCESS_DENIED");
        if(statement.getStatus()!=RevenueStatementStatus.CONFIRMED)throw new ConflictException("PERIOD_NOT_CONFIRMED");
        if(requestRepository.existsByExpertIdAndPeriodIdAndStatusIn(expertId,statement.getPeriod().getId(),OPEN))throw new ConflictException("EARLY_PAYOUT_ALREADY_OPEN");
        BigDecimal available=available(statement,null); if(input.requestedAmount().compareTo(available)>0)throw new ConflictException("EARLY_PAYOUT_AMOUNT_EXCEEDED");
        EarlyPayoutRequest request=EarlyPayoutRequest.builder().expert(statement.getExpert()).period(statement.getPeriod()).statement(statement).requestedAmount(money(input.requestedAmount())).eligibleAmountSnapshot(available).reason(input.reason().trim()).expertNote(input.expertNote()).idempotencyKey(input.idempotencyKey()).build();
        try{request=requestRepository.saveAndFlush(request);}catch(DataIntegrityViolationException ex){throw new ConflictException("EARLY_PAYOUT_ALREADY_OPEN");}
        audit.record("EARLY_PAYOUT_REQUESTED",statement.getExpert(),"EARLY_PAYOUT_REQUEST",request.getId(),null,amountJson(request.getRequestedAmount()),request.getReason(),input.idempotencyKey());
        notifyAdmins(request); return dto(request);
    }

    @Override @Transactional(readOnly=true) public PageResponse<RevenuePayrollDtos.EarlyPayout> expertList(Long id,int page,int size){var p=requestRepository.findByExpertIdOrderByRequestedAtDesc(id,PageRequest.of(page,Math.min(Math.max(size,1),100)));return page(p);}
    @Override @Transactional(readOnly=true) public RevenuePayrollDtos.EarlyPayout expertDetail(Long expertId,String id){return dto(requestRepository.findByIdAndExpertId(id,expertId).orElseThrow(()->new ResourceNotFoundException("EARLY_PAYOUT_NOT_FOUND")));}
    @Override @Transactional(readOnly=true) public PageResponse<RevenuePayrollDtos.EarlyPayout> adminList(int page,int size){return page(requestRepository.findAllByOrderByRequestedAtDesc(PageRequest.of(page,Math.min(Math.max(size,1),100))));}
    @Override @Transactional(readOnly=true) public RevenuePayrollDtos.EarlyPayout adminDetail(String id){return dto(requestRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("EARLY_PAYOUT_NOT_FOUND")));}

    @Override @Transactional public RevenuePayrollDtos.EarlyPayout cancel(Long expertId,String id){EarlyPayoutRequest r=lock(id);owned(r,expertId);if(!Set.of(EarlyPayoutStatus.PENDING_ADMIN_REVIEW,EarlyPayoutStatus.NEED_MORE_INFO,EarlyPayoutStatus.EXPERT_RESPONDED).contains(r.getStatus()))throw new ConflictException("INVALID_STATUS_TRANSITION");r.setStatus(EarlyPayoutStatus.CANCELLED);return dto(r);}
    @Override @Transactional public RevenuePayrollDtos.EarlyPayout reply(Long expertId,String id,RevenuePayrollDtos.EarlyPayoutNote input){EarlyPayoutRequest r=lock(id);owned(r,expertId);if(r.getStatus()!=EarlyPayoutStatus.NEED_MORE_INFO)throw new ConflictException("INVALID_STATUS_TRANSITION");r.setExpertNote(input.note());r.setStatus(EarlyPayoutStatus.EXPERT_RESPONDED);notifyAdmins(r);return dto(r);}
    @Override @Transactional public RevenuePayrollDtos.EarlyPayout requestMoreInfo(Long adminId,String id,RevenuePayrollDtos.EarlyPayoutNote input){EarlyPayoutRequest r=lock(id);if(!Set.of(EarlyPayoutStatus.PENDING_ADMIN_REVIEW,EarlyPayoutStatus.EXPERT_RESPONDED).contains(r.getStatus()))throw new ConflictException("INVALID_STATUS_TRANSITION");r.setStatus(EarlyPayoutStatus.NEED_MORE_INFO);r.setAdminNote(input.note());review(r,user(adminId));notifyExpert(r,"EARLY_PAYOUT_MORE_INFO","Admin cần bổ sung thông tin");return dto(r);}
    @Override @Transactional public RevenuePayrollDtos.EarlyPayout approve(Long adminId,String id,RevenuePayrollDtos.ApproveEarlyPayout input){
        EarlyPayoutRequest r=lock(id);if(!Set.of(EarlyPayoutStatus.PENDING_ADMIN_REVIEW,EarlyPayoutStatus.EXPERT_RESPONDED).contains(r.getStatus()))throw new ConflictException("INVALID_STATUS_TRANSITION");
        ExpertRevenueStatement s=statementRepository.lockById(r.getStatement().getId()).orElseThrow();BigDecimal available=available(s,r.getId());BigDecimal amount=money(input.approvedAmount());
        if(amount.compareTo(r.getRequestedAmount())>0||amount.compareTo(available)>0)throw new ConflictException("INSUFFICIENT_AVAILABLE_BALANCE");
        r.setApprovedAmount(amount);r.setAdminNote(input.adminNote());r.setStatus(EarlyPayoutStatus.APPROVED);r.setApprovedAt(LocalDateTime.now(ZONE));review(r,user(adminId));
        audit.record("EARLY_PAYOUT_APPROVED",r.getReviewedBy(),"EARLY_PAYOUT_REQUEST",r.getId(),null,amountJson(amount),input.adminNote(),r.getId());notifyExpert(r,"EARLY_PAYOUT_APPROVED","Yêu cầu ứng doanh thu đã được duyệt");return dto(r);
    }
    @Override @Transactional public RevenuePayrollDtos.EarlyPayout reject(Long adminId,String id,RevenuePayrollDtos.RejectEarlyPayout input){EarlyPayoutRequest r=lock(id);if(!Set.of(EarlyPayoutStatus.PENDING_ADMIN_REVIEW,EarlyPayoutStatus.EXPERT_RESPONDED,EarlyPayoutStatus.NEED_MORE_INFO).contains(r.getStatus()))throw new ConflictException("INVALID_STATUS_TRANSITION");r.setStatus(EarlyPayoutStatus.REJECTED);r.setAdminNote(input.reason());r.setRejectedAt(LocalDateTime.now(ZONE));review(r,user(adminId));audit.record("EARLY_PAYOUT_REJECTED",r.getReviewedBy(),"EARLY_PAYOUT_REQUEST",r.getId(),null,"{\"status\":\"REJECTED\"}",input.reason(),r.getId());notifyExpert(r,"EARLY_PAYOUT_REJECTED","Yêu cầu ứng doanh thu bị từ chối");return dto(r);}
    @Override @Transactional public RevenuePayrollDtos.EarlyPayout markPending(Long adminId,String id){EarlyPayoutRequest r=lock(id);if(r.getStatus()!=EarlyPayoutStatus.APPROVED)throw new ConflictException("INVALID_STATUS_TRANSITION");r.setStatus(EarlyPayoutStatus.PAYMENT_PENDING);review(r,user(adminId));audit.record("EARLY_PAYOUT_PAYMENT_PENDING",r.getReviewedBy(),"EARLY_PAYOUT_REQUEST",r.getId(),null,"{\"status\":\"PAYMENT_PENDING\"}",null,r.getId());notifyExpert(r,"EARLY_PAYOUT_PAYMENT_PENDING","Khoản ứng đang chờ chuyển tiền");return dto(r);}
    @Override @Transactional public RevenuePayrollDtos.EarlyPayout markPaid(Long adminId,String id,RevenuePayrollDtos.MarkPayoutPaid input){
        EarlyPayoutRequest r=lock(id);if(r.getStatus()==EarlyPayoutStatus.PAID)return dto(r);if(r.getStatus()!=EarlyPayoutStatus.PAYMENT_PENDING)throw new ConflictException("INVALID_STATUS_TRANSITION");
        ExpertRevenueStatement s=statementRepository.lockById(r.getStatement().getId()).orElseThrow();BigDecimal amount=r.getApprovedAmount();if(amount==null)throw new ConflictException("INVALID_STATUS_TRANSITION");
        if(s.getRemainingAmount().compareTo(amount)<0)throw new ConflictException("INSUFFICIENT_AVAILABLE_BALANCE");User admin=user(adminId);
        try{transactionRepository.saveAndFlush(ExpertPayoutTransaction.builder().expert(r.getExpert()).statement(s).earlyPayoutRequest(r).amount(amount).type(PayoutTransactionType.EARLY).status(PayoutTransactionStatus.PAID).paidAt(LocalDateTime.now(ZONE)).paymentReference(input.paymentReference()).paidBy(admin).idempotencyKey(input.idempotencyKey()).build());}catch(DataIntegrityViolationException ex){if(transactionRepository.findByEarlyPayoutRequestId(r.getId()).isPresent())return dto(r);throw new ConflictException("PAYOUT_ALREADY_PAID");}
        r.setStatus(EarlyPayoutStatus.PAID);r.setPaidAt(LocalDateTime.now(ZONE));r.setPaymentReference(input.paymentReference());s.setPaidAmount(s.getPaidAmount().add(amount));s.setRemainingAmount(s.getFinalPayout().subtract(s.getPaidAmount()));s.setStatus(s.getRemainingAmount().signum()==0?RevenueStatementStatus.PAID:RevenueStatementStatus.PARTIALLY_PAID);if(s.getStatus()==RevenueStatementStatus.PAID&&s.getPaidAt()==null)s.setPaidAt(r.getPaidAt());if(statementRepository.findByPeriodIdOrderByExpertNameSnapshotAsc(s.getPeriod().getId()).stream().allMatch(x->x.getStatus()==RevenueStatementStatus.PAID))s.getPeriod().setStatus(RevenuePeriodStatus.PAID);
        audit.record("EARLY_PAYOUT_PAID",admin,"EARLY_PAYOUT_REQUEST",r.getId(),null,"{\"amount\":\""+amount+"\",\"paymentReference\":\""+safe(input.paymentReference())+"\"}",null,input.idempotencyKey());notifyExpert(r,"EARLY_PAYOUT_PAID","Khoản ứng doanh thu đã được chuyển");return dto(r);
    }

    private BigDecimal available(ExpertRevenueStatement s,String exclude){BigDecimal reserved=requestRepository.reserved(s.getId(),OPEN);if(exclude!=null){EarlyPayoutRequest r=requestRepository.findById(exclude).orElse(null);if(r!=null&&OPEN.contains(r.getStatus()))reserved=reserved.subtract(Optional.ofNullable(r.getApprovedAmount()).orElse(r.getRequestedAmount()));}return s.getFinalPayout().subtract(s.getPaidAmount()).subtract(reserved).max(BigDecimal.ZERO);}
    private void notifyAdmins(EarlyPayoutRequest r){for(User a:userRepository.findAllByRole_NameAndActiveTrue(RoleName.ADMIN))notify(a,"EARLY_PAYOUT_REQUESTED",r,"Yêu cầu ứng doanh thu "+r.getRequestCode());}
    private void notifyExpert(EarlyPayoutRequest r,String type,String message){notify(r.getExpert(),type,r,message);emailService.sendFinancialEmail(r.getExpert().getEmail(),message,"Mã yêu cầu: "+r.getRequestCode()+"\nTrạng thái: "+r.getStatus());}
    private void notify(User recipient,String type,EarlyPayoutRequest r,String message){String key=type+":"+r.getId()+":"+recipient.getId()+":"+r.getVersion();if(!notificationRepository.existsByDedupKey(key))notificationRepository.save(SystemNotification.builder().type(type).recipientUser(recipient).title(message).message("Kỳ "+r.getPeriod().getPeriodCode()+", số tiền "+Optional.ofNullable(r.getApprovedAmount()).orElse(r.getRequestedAmount())).entityType("EARLY_PAYOUT_REQUEST").entityId(r.getId()).dedupKey(key).build());}
    private void review(EarlyPayoutRequest r,User admin){r.setReviewedAt(LocalDateTime.now(ZONE));r.setReviewedBy(admin);}
    private void owned(EarlyPayoutRequest r,Long id){if(!r.getExpert().getId().equals(id))throw new ForbiddenException("EARLY_PAYOUT_ACCESS_DENIED");}
    private EarlyPayoutRequest lock(String id){return requestRepository.lockById(id).orElseThrow(()->new ResourceNotFoundException("EARLY_PAYOUT_NOT_FOUND"));}
    private User user(Long id){return userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("USER_NOT_FOUND"));}
    private BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}
    private String amountJson(BigDecimal a){return "{\"amount\":\""+a+"\"}";} private String safe(String s){return s.replace("\\","_").replace("\"","_");}
    private PageResponse<RevenuePayrollDtos.EarlyPayout> page(org.springframework.data.domain.Page<EarlyPayoutRequest> p){return PageResponse.<RevenuePayrollDtos.EarlyPayout>builder().items(p.getContent().stream().map(this::dto).toList()).page(p.getNumber()).size(p.getSize()).totalItems(p.getTotalElements()).totalPages(p.getTotalPages()).build();}
    private RevenuePayrollDtos.EarlyPayout dto(EarlyPayoutRequest r){return new RevenuePayrollDtos.EarlyPayout(r.getId(),r.getRequestCode(),r.getExpert().getId(),(r.getExpert().getFirstName()+" "+r.getExpert().getLastName()).trim(),r.getPeriod().getId(),r.getPeriod().getPeriodCode(),r.getStatement().getId(),r.getRequestedAmount(),r.getEligibleAmountSnapshot(),r.getApprovedAmount(),available(r.getStatement(),r.getId()),r.getReason(),r.getExpertNote(),r.getAdminNote(),r.getStatus(),r.getRequestedAt(),r.getReviewedAt(),r.getReviewedBy()==null?null:r.getReviewedBy().getId(),r.getApprovedAt(),r.getRejectedAt(),r.getPaidAt(),r.getPaymentReference(),r.getVersion()==null?0:r.getVersion());}
}
