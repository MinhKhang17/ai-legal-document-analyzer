package com.analyzer.api.service.subscription.impl;

import com.analyzer.api.dto.subscription.RefundRequestDTO;
import com.analyzer.api.dto.subscription.RefundResponseDTO;
import com.analyzer.api.dto.subscription.UpdateRefundStatusRequest;
import com.analyzer.api.entity.*;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.*;
import com.analyzer.api.repository.subscription.RefundRequestRepository;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.CustomerPlanService;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.subscription.RefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class RefundServiceImpl implements RefundService {

    private static final EnumSet<RefundStatus> IN_PROGRESS_STATUSES = EnumSet.of(
            RefundStatus.NEW, RefundStatus.ADMIN_REVIEWING, RefundStatus.WAITING_USER_BANK_INFO,
            RefundStatus.WAITING_EMAIL_CONFIRMATION, RefundStatus.EMAIL_CONFIRMED,
            RefundStatus.REFUND_REQUEST_CREATED, RefundStatus.REQUESTED, RefundStatus.APPROVED,
            RefundStatus.PROCESSING);

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LegalTicketRepository legalTicketRepository;
    private final LegalTicketMessageRepository legalTicketMessageRepository;
    private final EmailService emailService;
    private final CustomerPlanService customerPlanService;

    @Autowired
    public RefundServiceImpl(
            RefundRequestRepository refundRequestRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            CustomerPlanRepository customerPlanRepository,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            LegalTicketRepository legalTicketRepository,
            LegalTicketMessageRepository legalTicketMessageRepository,
            EmailService emailService,
            CustomerPlanService customerPlanService) {
        this.refundRequestRepository = refundRequestRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.customerPlanRepository = customerPlanRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.legalTicketRepository = legalTicketRepository;
        this.legalTicketMessageRepository = legalTicketMessageRepository;
        this.emailService = emailService;
        this.customerPlanService = customerPlanService;
    }

    /** Backward-compatible constructor retained for existing unit tests and integrations. */
    public RefundServiceImpl(
            RefundRequestRepository refundRequestRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            CustomerPlanRepository customerPlanRepository,
            UserRepository userRepository) {
        this(refundRequestRepository, paymentTransactionRepository, customerPlanRepository, userRepository,
                null, null, null, null, null);
    }

    public RefundServiceImpl(
            RefundRequestRepository refundRequestRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            CustomerPlanRepository customerPlanRepository,
            UserRepository userRepository,
            CustomerPlanService customerPlanService) {
        this(refundRequestRepository, paymentTransactionRepository, customerPlanRepository, userRepository,
                null, null, null, null, customerPlanService);
    }

    @Override
    @Transactional
    public RefundResponseDTO requestRefund(Long customerId, RefundRequestDTO request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND"));
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(request.getPaymentTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_TRANSACTION_NOT_FOUND"));
        if (!transaction.getCustomer().getId().equals(customerId)) throw new ForbiddenException("REFUND_INVOICE_ACCESS_DENIED");
        if (transaction.getPaymentStatus() != PaymentStatus.SUCCESS) throw new ConflictException("REFUND_REQUIRES_SUCCESSFUL_PAYMENT");
        validateInvoiceReference(request, transaction);
        if (refundRequestRepository.existsByPaymentTransactionIdAndStatusIn(transaction.getId(), IN_PROGRESS_STATUSES)) {
            throw new ConflictException("REFUND_ALREADY_IN_PROGRESS");
        }
        BigDecimal reserved = refundRequestRepository.sumReservedAmount(transaction.getId(), RefundStatus.REJECTED);
        BigDecimal remaining = transaction.getAmount().subtract(reserved);
        if (request.getAmount().compareTo(remaining) > 0) throw new ConflictException("REFUND_AMOUNT_EXCEEDS_REMAINING_PAYMENT");
        CustomerPlan customerPlan = validateCustomerPlan(customerId, request.getCustomerPlanId(), transaction);

        Workspace workspace = workspaceRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(customerId, "ACTIVE")
                .stream().findFirst().orElse(null);
        String reason = request.getRefundReason() != null && !request.getRefundReason().isBlank()
                ? request.getRefundReason().trim() : request.getReason().trim();
        LegalTicket ticket = legalTicketRepository.save(LegalTicket.builder()
                .requestId("refund_" + UUID.randomUUID().toString().replace("-", ""))
                .ticketType(LegalTicketType.REFUND_REQUEST)
                .recipientType(TicketRecipientType.ADMIN)
                .title("Yeu cau hoan tien " + transaction.getTransactionCode())
                .description(reason)
                .createdBy(customer)
                .workspace(workspace)
                .question(reason)
                .status(LegalTicketStatus.PENDING_ADMIN_REVIEW)
                .lastCustomerMessageAt(LocalDateTime.now())
                .build());
        legalTicketMessageRepository.save(LegalTicketMessage.builder().ticket(ticket).sender(customer)
                .senderRole(RoleName.CUSTOMER.name()).content(reason)
                .messageType(LegalTicketMessageType.CUSTOMER_REPLY).internalOnly(false).build());

        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        boolean hasBankInfo = hasText(request.getBankName()) && hasText(request.getAccountNumber())
                && hasText(request.getAccountHolderName());
        RefundRequest refund = refundRequestRepository.save(RefundRequest.builder()
                .paymentTransaction(transaction).customerPlan(customerPlan).requestedBy(customer).legalTicket(ticket)
                .reason(reason).status(hasBankInfo ? RefundStatus.WAITING_EMAIL_CONFIRMATION : RefundStatus.WAITING_USER_BANK_INFO)
                .amount(request.getAmount()).bankName(normalize(request.getBankName()))
                .accountNumber(normalize(request.getAccountNumber())).accountHolderName(normalize(request.getAccountHolderName()))
                .invoiceId(hasText(request.getInvoiceId()) ? request.getInvoiceId().trim() : transaction.getTransactionCode())
                .confirmationTokenHash(hasBankInfo ? sha256(rawToken) : null)
                .confirmationExpiresAt(hasBankInfo ? LocalDateTime.now().plusHours(24) : null)
                .adminNote(hasBankInfo ? "Waiting for user email confirmation" : "Waiting for user bank information").build());
        if (hasBankInfo) emailService.sendRefundConfirmationEmailAsync(customer.getEmail(), customer.getFirstName(), refund.getId(), rawToken);
        return toResponse(refund);
    }

    @Override @Transactional(readOnly = true)
    public RefundResponseDTO getRefund(Long id) {
        RefundRequest refund = requireRefund(id);
        if (!canViewRefund(refund)) throw new ForbiddenException("REFUND_ACCESS_DENIED");
        return toResponse(refund);
    }

    @Override @Transactional(readOnly = true)
    public List<RefundResponseDTO> getMyRefunds(Long customerId) {
        return refundRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(customerId).stream().map(this::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<RefundResponseDTO> getRefunds(RefundStatus status) {
        return (status == null ? refundRequestRepository.findAllByOrderByCreatedAtDesc()
                : refundRequestRepository.findByStatusOrderByCreatedAtDesc(status)).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public RefundResponseDTO confirmRefundEmail(String token) {
        String tokenHash = sha256(token);
        var refundWithActiveToken = refundRequestRepository.findByConfirmationTokenHash(tokenHash);
        if (refundWithActiveToken.isEmpty()) {
            if (refundRequestRepository.findByConfirmationUsedTokenHash(tokenHash).isPresent()) {
                throw new ConflictException("TOKEN_ALREADY_USED");
            }
            throw new ResourceNotFoundException("TOKEN_INVALID");
        }
        RefundRequest refund = refundWithActiveToken.get();
        if (refund.getConfirmationExpiresAt() == null || LocalDateTime.now().isAfter(refund.getConfirmationExpiresAt())) {
            throw new ConflictException("TOKEN_EXPIRED");
        }
        if (refund.getStatus() == RefundStatus.EMAIL_CONFIRMED || refund.getEmailConfirmedAt() != null) {
            throw new ConflictException("TOKEN_ALREADY_USED");
        }
        if (refund.getStatus() != RefundStatus.WAITING_EMAIL_CONFIRMATION) throw new ConflictException("REFUND_CONFIRMATION_NOT_ALLOWED");
        refund.setStatus(RefundStatus.EMAIL_CONFIRMED);
        refund.setEmailConfirmedAt(LocalDateTime.now());
        refund.setConfirmationUsedTokenHash(tokenHash);
        refund.setConfirmationTokenHash(null);
        refund.setAdminNote("Email confirmed; admin can create refund order");
        return toResponse(refundRequestRepository.save(refund));
    }

    @Override
    @Transactional
    public RefundResponseDTO updateRefundStatus(Long id, UpdateRefundStatusRequest request) {
        RefundRequest refund = requireRefund(id);
        validateTransition(refund, request.status());
        refund.setStatus(request.status());
        refund.setAdminNote(normalize(request.adminNote()));
        RefundRequest saved = refundRequestRepository.save(refund);
        if (request.status() == RefundStatus.REFUNDED || request.status() == RefundStatus.COMPLETED) completeRefund(saved);
        if (request.status() == RefundStatus.REFUNDED || request.status() == RefundStatus.REJECTED
                || request.status() == RefundStatus.CLOSED || request.status() == RefundStatus.COMPLETED) {
            LegalTicket ticket = saved.getLegalTicket();
            if (ticket != null) {
                ticket.setStatus(request.status() == RefundStatus.REJECTED ? LegalTicketStatus.REJECTED_BY_ADMIN : LegalTicketStatus.CLOSED);
                ticket.setClosedAt(LocalDateTime.now());
                legalTicketRepository.save(ticket);
            }
        }
        return toResponse(saved);
    }

    private void validateTransition(RefundRequest refund, RefundStatus target) {
        RefundStatus current = refund.getStatus();
        if (target == RefundStatus.REFUND_REQUEST_CREATED && refund.getEmailConfirmedAt() == null) {
            throw new ConflictException("REFUND_EMAIL_CONFIRMATION_REQUIRED");
        }
        boolean valid = switch (current) {
            case NEW -> target == RefundStatus.ADMIN_REVIEWING || target == RefundStatus.REJECTED;
            case ADMIN_REVIEWING -> target == RefundStatus.WAITING_USER_BANK_INFO || target == RefundStatus.WAITING_EMAIL_CONFIRMATION || target == RefundStatus.REJECTED;
            case WAITING_USER_BANK_INFO -> target == RefundStatus.WAITING_EMAIL_CONFIRMATION || target == RefundStatus.REJECTED;
            case WAITING_EMAIL_CONFIRMATION -> target == RefundStatus.REJECTED;
            case EMAIL_CONFIRMED -> target == RefundStatus.REFUND_REQUEST_CREATED || target == RefundStatus.REJECTED;
            case REFUND_REQUEST_CREATED -> target == RefundStatus.REFUNDED || target == RefundStatus.REJECTED;
            case REFUNDED -> target == RefundStatus.CLOSED;
            case REQUESTED -> target == RefundStatus.APPROVED || target == RefundStatus.REJECTED;
            case APPROVED -> target == RefundStatus.PROCESSING;
            case PROCESSING -> target == RefundStatus.COMPLETED;
            case REJECTED, CLOSED, COMPLETED -> false;
        };
        if (!valid) throw new ConflictException("INVALID_REFUND_STATUS_TRANSITION");
    }

    private void completeRefund(RefundRequest refund) {
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(refund.getPaymentTransaction().getId())
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_TRANSACTION_NOT_FOUND"));
        BigDecimal completedAmount = refundRequestRepository.sumAmountByStatus(transaction.getId(), refund.getStatus());
        if (completedAmount == null || completedAmount.compareTo(transaction.getAmount()) < 0) return;
        transaction.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentTransactionRepository.save(transaction);
        if (transaction.getCustomerPlan() != null) {
            CustomerPlan plan = transaction.getCustomerPlan();
            if (customerPlanService != null) {
                customerPlanService.cancelPlanAndActivateFree(transaction.getCustomer().getId(), plan.getId(),
                        "Refund " + transaction.getTransactionCode());
            } else {
                plan.setStatus(PlanStatus.CANCELLED); plan.setAutoRenew(false);
                plan.setCancelReason("Refund " + transaction.getTransactionCode());
                customerPlanRepository.save(plan);
            }
        }
    }

    private CustomerPlan validateCustomerPlan(Long customerId, Long requestedId, PaymentTransaction transaction) {
        CustomerPlan plan = transaction.getCustomerPlan();
        if (requestedId == null) return plan;
        CustomerPlan requested = customerPlanRepository.findById(requestedId)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_PLAN_NOT_FOUND"));
        if (!requested.getCustomer().getId().equals(customerId)) throw new ForbiddenException("CUSTOMER_PLAN_ACCESS_DENIED");
        if (plan == null || !plan.getId().equals(requested.getId())) throw new ConflictException("CUSTOMER_PLAN_PAYMENT_MISMATCH");
        return requested;
    }

    private void validateInvoiceReference(RefundRequestDTO request, PaymentTransaction transaction) {
        String code = transaction.getTransactionCode();
        String id = String.valueOf(transaction.getId());
        if (request.getInvoiceId() == null || request.getInvoiceId().isBlank()) return;
        if (!request.getInvoiceId().equalsIgnoreCase(code) && !request.getInvoiceId().equals(id)) {
            throw new ConflictException("REFUND_INVOICE_MISMATCH");
        }
        if (request.getTransactionId() != null && !request.getTransactionId().isBlank()
                && !request.getTransactionId().equalsIgnoreCase(code) && !request.getTransactionId().equals(id)) {
            throw new ConflictException("REFUND_TRANSACTION_MISMATCH");
        }
    }

    private RefundRequest requireRefund(Long id) {
        return refundRequestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("REFUND_NOT_FOUND"));
    }

    private RefundResponseDTO toResponse(RefundRequest refund) {
        return RefundResponseDTO.builder().id(refund.getId()).paymentTransactionId(refund.getPaymentTransaction().getId())
                .customerPlanId(refund.getCustomerPlan() == null ? null : refund.getCustomerPlan().getId())
                .requestedById(refund.getRequestedBy().getId()).reason(refund.getReason()).status(refund.getStatus())
                .amount(refund.getAmount()).adminNote(refund.getAdminNote())
                .legalTicketId(refund.getLegalTicket() == null ? null : refund.getLegalTicket().getId())
                .bankName(refund.getBankName()).accountNumber(refund.getAccountNumber())
                .accountHolderName(refund.getAccountHolderName()).invoiceId(refund.getInvoiceId())
                .confirmationExpiresAt(refund.getConfirmationExpiresAt()).emailConfirmedAt(refund.getEmailConfirmedAt())
                .emailConfirmed(refund.getEmailConfirmedAt() != null)
                .createdAt(refund.getCreatedAt()).updatedAt(refund.getUpdatedAt()).build();
    }

    private boolean canViewRefund(RefundRequest refund) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl details)) return false;
        boolean admin = details.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin || refund.getRequestedBy().getId().equals(details.getId());
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
