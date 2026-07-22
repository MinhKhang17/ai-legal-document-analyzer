package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.*;
import com.analyzer.api.repository.*;
import com.analyzer.api.service.CommissionPolicyManagementService;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.FinancialAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class CommissionPolicyManagementServiceImpl implements CommissionPolicyManagementService {
    static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<CommissionPolicyStatus> LIVE=Set.of(CommissionPolicyStatus.ACTIVE,CommissionPolicyStatus.SCHEDULED);
    private final CommissionPolicyRepository policyRepository;
    private final CommissionPolicyChangeRequestRepository requestRepository;
    private final CommissionPolicyExpertNotificationRepository notificationRepository;
    private final SystemNotificationRepository systemNotificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final FinancialAuditService audit;
    @Value("${app.mail.frontend-base-url:http://localhost:5173}") private String frontendBaseUrl;

    @Override
    @Transactional(readOnly=true)
    public BigDecimal rateFor(LocalDate date){
        var scheduled=policyRepository.applicable(date,List.of(CommissionPolicyStatus.SCHEDULED));
        if(scheduled.size()>1) throw new ConflictException("COMMISSION_POLICY_INTEGRITY_ERROR");
        if(scheduled.size()==1) return scheduled.getFirst().getRate();
        var matches=policyRepository.applicable(date,List.of(CommissionPolicyStatus.ACTIVE,CommissionPolicyStatus.EXPIRED));
        if(matches.size()!=1) throw new ConflictException(matches.isEmpty()?"COMMISSION_POLICY_NOT_FOUND":"COMMISSION_POLICY_INTEGRITY_ERROR");
        return matches.getFirst().getRate();
    }

    @Override @Transactional(readOnly=true) public List<RevenuePayrollDtos.Policy> listPolicies(){ return policyRepository.findAllByOrderByEffectiveFromDesc().stream().map(this::policyDto).toList(); }

    @Override
    @Transactional
    public RevenuePayrollDtos.ChangeRequest requestChange(Long adminId,RevenuePayrollDtos.CreateCommissionChange input){
        User admin=user(adminId); LocalDate today=LocalDate.now(ZONE); LocalDate effective=input.applicationType()==CommissionApplicationType.NEXT_MONTH
            ?today.withDayOfMonth(1).plusMonths(1):nextQuarter(today);
        if(policyRepository.existsByStatus(CommissionPolicyStatus.SCHEDULED)||policyRepository.existsByEffectiveFromAndStatusIn(effective,LIVE)) throw new ConflictException("COMMISSION_POLICY_OVERLAP");
        String token=token();
        CommissionPolicyChangeRequest request=CommissionPolicyChangeRequest.builder().oldRateSnapshot(rateFor(today)).newRate(input.newRate())
            .applicationType(input.applicationType()).effectiveFrom(effective).reason(input.reason().trim()).requestedBy(admin)
            .tokenHash(hash(token)).tokenExpiresAt(LocalDateTime.now(ZONE).plusMinutes(30)).build();
        request=requestRepository.save(request);
        audit.record("COMMISSION_CHANGE_REQUESTED",admin,"COMMISSION_CHANGE_REQUEST",request.getId(),null,
            jsonRate(input.newRate(),effective),input.reason(),request.getId());
        sendVerification(request,token);
        return requestDto(request);
    }

    @Override
    @Transactional
    public RevenuePayrollDtos.ChangeRequest resend(Long adminId,String id){
        CommissionPolicyChangeRequest request=requestRepository.lockById(id).orElseThrow(()->new ResourceNotFoundException("COMMISSION_CHANGE_REQUEST_NOT_FOUND"));
        if(!request.getRequestedBy().getId().equals(adminId)) throw new ForbiddenException("COMMISSION_REQUEST_OWNER_REQUIRED");
        if(request.getStatus()!=CommissionChangeRequestStatus.PENDING_EMAIL_VERIFICATION) throw new ConflictException("POLICY_ALREADY_VERIFIED");
        String token=token(); request.setTokenHash(hash(token)); request.setTokenExpiresAt(LocalDateTime.now(ZONE).plusMinutes(30));
        sendVerification(request,token); return requestDto(request);
    }

    @Override
    @Transactional
    public RevenuePayrollDtos.Policy verify(Long adminId,String requestId,String rawToken){
        CommissionPolicyChangeRequest request=requestRepository.lockById(requestId).orElseThrow(()->new ResourceNotFoundException("COMMISSION_CHANGE_REQUEST_NOT_FOUND"));
        if(!request.getRequestedBy().getId().equals(adminId)) throw new ForbiddenException("COMMISSION_REQUEST_OWNER_REQUIRED");
        if(request.getStatus()!=CommissionChangeRequestStatus.PENDING_EMAIL_VERIFICATION) throw new ConflictException("POLICY_ALREADY_VERIFIED");
        if(request.getTokenExpiresAt()==null||!request.getTokenExpiresAt().isAfter(LocalDateTime.now(ZONE))){ request.setStatus(CommissionChangeRequestStatus.EXPIRED); throw new ConflictException("VERIFICATION_TOKEN_EXPIRED"); }
        if(!MessageDigest.isEqual(hash(rawToken).getBytes(StandardCharsets.UTF_8),request.getTokenHash().getBytes(StandardCharsets.UTF_8))) throw new ConflictException("INVALID_VERIFICATION_TOKEN");
        if(policyRepository.existsByStatus(CommissionPolicyStatus.SCHEDULED)||policyRepository.existsByEffectiveFromAndStatusIn(request.getEffectiveFrom(),LIVE)) throw new ConflictException("COMMISSION_POLICY_OVERLAP");
        request.setStatus(CommissionChangeRequestStatus.VERIFIED); request.setVerifiedAt(LocalDateTime.now(ZONE)); request.setTokenHash(null); request.setTokenExpiresAt(null);
        CommissionPolicy policy=CommissionPolicy.builder().rate(request.getNewRate()).effectiveFrom(request.getEffectiveFrom())
            .status(CommissionPolicyStatus.SCHEDULED).reason(request.getReason()).sourceChangeRequestId(request.getId()).createdBy(request.getRequestedBy()).build();
        try { policy=policyRepository.saveAndFlush(policy); } catch(DataIntegrityViolationException ex){ throw new ConflictException("COMMISSION_POLICY_OVERLAP"); }
        request.setStatus(CommissionChangeRequestStatus.SCHEDULED);
        audit.record("COMMISSION_EMAIL_VERIFIED",request.getRequestedBy(),"COMMISSION_CHANGE_REQUEST",request.getId(),null,jsonRate(request.getNewRate(),request.getEffectiveFrom()),request.getReason(),request.getId());
        audit.record("COMMISSION_POLICY_SCHEDULED",request.getRequestedBy(),"COMMISSION_POLICY",policy.getId(),null,jsonRate(policy.getRate(),policy.getEffectiveFrom()),policy.getReason(),request.getId());
        notifyExperts(policy,false); return policyDto(policy);
    }

    @Override
    @Transactional
    public RevenuePayrollDtos.Policy cancel(Long adminId,String policyId,String reason){
        CommissionPolicy policy=policyRepository.lockById(policyId).orElseThrow(()->new ResourceNotFoundException("COMMISSION_POLICY_NOT_FOUND"));
        if(policy.getStatus()!=CommissionPolicyStatus.SCHEDULED) throw new ConflictException("INVALID_STATUS_TRANSITION");
        policy.setStatus(CommissionPolicyStatus.CANCELLED); User admin=user(adminId);
        requestRepository.findById(policy.getSourceChangeRequestId()).ifPresent(r->r.setStatus(CommissionChangeRequestStatus.CANCELLED));
        audit.record("COMMISSION_POLICY_CANCELLED",admin,"COMMISSION_POLICY",policy.getId(),null,"{\"status\":\"CANCELLED\"}",reason,policy.getSourceChangeRequestId());
        notifyExperts(policy,true); return policyDto(policy);
    }

    @Override
    @Transactional
    public void activateDuePolicies(){
        LocalDate today=LocalDate.now(ZONE);
        for(CommissionPolicy candidate:policyRepository.findByStatusAndEffectiveFromLessThanEqual(CommissionPolicyStatus.SCHEDULED,today)){
            CommissionPolicy scheduled=policyRepository.lockById(candidate.getId()).orElse(null);
            if(scheduled==null||scheduled.getStatus()!=CommissionPolicyStatus.SCHEDULED) continue;
            var current=policyRepository.applicable(today,List.of(CommissionPolicyStatus.ACTIVE));
            if(current.size()>1) throw new ConflictException("COMMISSION_POLICY_INTEGRITY_ERROR");
            current.stream().filter(p->!p.getId().equals(scheduled.getId())).forEach(p->{ p.setStatus(CommissionPolicyStatus.EXPIRED); p.setEffectiveTo(scheduled.getEffectiveFrom().minusDays(1)); });
            scheduled.setStatus(CommissionPolicyStatus.ACTIVE); scheduled.setActivatedAt(LocalDateTime.now(ZONE));
            audit.record("COMMISSION_POLICY_ACTIVATED",null,"COMMISSION_POLICY",scheduled.getId(),null,jsonRate(scheduled.getRate(),scheduled.getEffectiveFrom()),scheduled.getReason(),scheduled.getSourceChangeRequestId());
        }
    }

    @Override @Transactional(readOnly=true) public List<RevenuePayrollDtos.PolicyNotification> expertNotifications(Long expertId){ return notificationRepository.findByExpertIdOrderByIdDesc(expertId).stream().map(this::notificationDto).toList(); }
    @Override @Transactional public RevenuePayrollDtos.PolicyNotification readNotification(Long expertId,Long id){ var n=notificationRepository.findByIdAndExpertId(id,expertId).orElseThrow(()->new ResourceNotFoundException("POLICY_NOTIFICATION_NOT_FOUND")); if(n.getReadAt()==null)n.setReadAt(LocalDateTime.now(ZONE)); return notificationDto(n); }
    @Override @Transactional public void retryFailed(){ notificationRepository.findByStatusIn(List.of(NotificationDeliveryStatus.FAILED,NotificationDeliveryStatus.PENDING)).forEach(n->deliver(n,false)); }

    private void notifyExperts(CommissionPolicy policy,boolean cancelled){
        for(User expert:userRepository.findAllByRole_NameAndActiveTrue(RoleName.EXPERT)){
            CommissionPolicyExpertNotification n=notificationRepository.findAll().stream().filter(x->x.getPolicy().getId().equals(policy.getId())&&x.getExpert().getId().equals(expert.getId())).findFirst().orElseGet(()->notificationRepository.save(CommissionPolicyExpertNotification.builder().policy(policy).expert(expert).expertEmailSnapshot(expert.getEmail()).status(NotificationDeliveryStatus.PENDING).build()));
            deliver(n,cancelled);
            String type=cancelled?"COMMISSION_POLICY_CANCELLED":"COMMISSION_POLICY_SCHEDULED";
            String key=type+":"+policy.getId()+":"+expert.getId();
            if(!systemNotificationRepository.existsByDedupKey(key)) systemNotificationRepository.save(SystemNotification.builder().type(type).recipientUser(expert).title(cancelled?"Chính sách hoa hồng đã hủy":"Chính sách hoa hồng mới").message("Tỷ lệ "+policy.getRate().multiply(BigDecimal.valueOf(100))+"% hiệu lực từ "+policy.getEffectiveFrom()).entityType("COMMISSION_POLICY").entityId(policy.getId()).dedupKey(key).build());
        }
    }
    private void deliver(CommissionPolicyExpertNotification n,boolean cancelled){ boolean ok=emailService.sendFinancialEmail(n.getExpertEmailSnapshot(),cancelled?"Hủy chính sách hoa hồng":"Chính sách hoa hồng mới", "Tỷ lệ: "+n.getPolicy().getRate()+"\nHiệu lực: "+n.getPolicy().getEffectiveFrom()+"\nTrạng thái: "+(cancelled?"CANCELLED":"SCHEDULED")); n.setRetryCount(n.getRetryCount()+1); n.setStatus(ok?NotificationDeliveryStatus.SENT:NotificationDeliveryStatus.FAILED); if(ok)n.setSentAt(LocalDateTime.now(ZONE));else n.setFailedAt(LocalDateTime.now(ZONE)); }
    private void sendVerification(CommissionPolicyChangeRequest r,String token){ emailService.sendFinancialEmail(r.getRequestedBy().getEmail(),"Xác nhận thay đổi hoa hồng LexiGuard","Mở trang xác nhận (30 phút): "+frontendBaseUrl+"/admin/revenue/commission/verify?requestId="+r.getId()+"&token="+token+"\nTỷ lệ mới: "+r.getNewRate()+"\nHiệu lực: "+r.getEffectiveFrom()); }
    private User user(Long id){ return userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("USER_NOT_FOUND")); }
    private LocalDate nextQuarter(LocalDate d){ int month=((d.getMonthValue()-1)/3+1)*3+1; return month>12?LocalDate.of(d.getYear()+1,1,1):LocalDate.of(d.getYear(),month,1); }
    private String token(){ byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private String hash(String value){ try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private String jsonRate(BigDecimal rate,LocalDate date){ return "{\"rate\":\""+rate+"\",\"effectiveFrom\":\""+date+"\"}"; }
    private RevenuePayrollDtos.Policy policyDto(CommissionPolicy p){ return new RevenuePayrollDtos.Policy(p.getId(),p.getRate(),p.getEffectiveFrom(),p.getEffectiveTo(),p.getStatus(),p.getReason(),p.getSourceChangeRequestId(),p.getCreatedBy()==null?null:p.getCreatedBy().getId(),p.getCreatedAt(),p.getActivatedAt(),p.getVersion()==null?0:p.getVersion()); }
    private RevenuePayrollDtos.ChangeRequest requestDto(CommissionPolicyChangeRequest r){ return new RevenuePayrollDtos.ChangeRequest(r.getId(),r.getOldRateSnapshot(),r.getNewRate(),r.getApplicationType(),r.getEffectiveFrom(),r.getReason(),r.getStatus(),r.getRequestedBy().getId(),r.getRequestedAt(),r.getTokenExpiresAt(),r.getVerifiedAt(),r.getVersion()==null?0:r.getVersion()); }
    private RevenuePayrollDtos.PolicyNotification notificationDto(CommissionPolicyExpertNotification n){ return new RevenuePayrollDtos.PolicyNotification(n.getId(),n.getPolicy().getId(),n.getPolicy().getRate(),n.getPolicy().getEffectiveFrom(),n.getStatus(),n.getRetryCount(),n.getSentAt(),n.getFailedAt(),n.getReadAt()); }
}
