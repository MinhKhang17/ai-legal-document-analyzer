package com.analyzer.api.service.impl;

import com.analyzer.api.config.VnPayProperties;
import com.analyzer.api.dto.paymenttransaction.PaymentTransactionResponse;
import com.analyzer.api.dto.paymenttransaction.PaymentUrlResponse;
import com.analyzer.api.entity.CustomerPlan;
import com.analyzer.api.entity.PaymentTransaction;
import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PaymentStatus;
import com.analyzer.api.enums.PlanStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.exception.payment.VnPayCallbackException;
import com.analyzer.api.mapper.PaymentTransactionMapper;
import com.analyzer.api.repository.CustomerPlanRepository;
import com.analyzer.api.repository.PaymentTransactionRepository;
import com.analyzer.api.service.PaymentTransactionService;
import com.analyzer.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VNPAY_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal VNPAY_MIN_AMOUNT = BigDecimal.valueOf(5000);
    private static final BigDecimal VNPAY_MAX_AMOUNT = BigDecimal.valueOf(1_000_000_000);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPlanRepository customerPlanRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final VnPayProperties vnPayProperties;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getMyTransactions(Long customerId) {
        return paymentTransactionRepository.findByCustomerId(customerId).stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .map(paymentTransactionMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getAllTransactions() {
        return paymentTransactionRepository.findAll().stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .map(paymentTransactionMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentUrlResponse createVnPayPaymentUrl(Long transactionId, Long customerId, String clientIp) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Khong tim thay giao dich thanh toan voi id: " + transactionId));

        validateVnPayConfig();

        if (!transaction.getCustomer().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen thanh toan giao dich nay");
        }
        if (transaction.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new ConflictException("Giao dich nay khong su dung phuong thuc VNPAY");
        }
        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Giao dich nay da duoc xu ly truoc do");
        }
        if (!isValidVnPayAmount(transaction.getAmount())) {
            throw new RuntimeException("So tien thanh toan VNPAY phai tu 5,000 VND den duoi 1 ty VND");
        }

        String paymentUrl = createPaymentUrl(transaction, clientIp);
        transaction.setPaymentUrl(paymentUrl);
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        return new PaymentUrlResponse(
                savedTransaction.getId(),
                savedTransaction.getTransactionCode(),
                PaymentMethod.VNPAY.name(),
                paymentUrl,
                vnPayProperties.getReturnUrl());
    }

    @Override
    @Transactional
    public PaymentTransactionResponse handleVnPayCallback(Map<String, String> callbackParams) {
        if (!isValidSignature(callbackParams)) {
            throw new VnPayCallbackException("97", "Invalid signature");
        }

        String transactionCode = callbackParams.get("vnp_TxnRef");
        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionCodeForUpdate(transactionCode)
                .orElseThrow(() -> new VnPayCallbackException("01", "Order not found"));

        String expectedAmount = toVnPayAmount(transaction.getAmount());
        if (!expectedAmount.equals(callbackParams.get("vnp_Amount"))) {
            throw new VnPayCallbackException("04", "Invalid amount");
        }

        transaction.setGatewayTransactionNo(callbackParams.get("vnp_TransactionNo"));
        transaction.setGatewayResponseCode(callbackParams.get("vnp_ResponseCode"));

        String responseCode = callbackParams.get("vnp_ResponseCode");
        String transactionStatus = callbackParams.get("vnp_TransactionStatus");
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            transaction = markSuccess(transaction);
        } else {
            transaction = markFailed(transaction);
        }

        return paymentTransactionMapper.toResponseDTO(transaction);
    }

    private PaymentTransaction markSuccess(PaymentTransaction transaction) {
        if (transaction.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return transaction;
        }
        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new VnPayCallbackException("02", "Order already confirmed");
        }

        transaction.setPaymentStatus(PaymentStatus.SUCCESS);
        transaction.setPaidAt(AppClock.now());
        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        CustomerPlan customerPlanRef = transaction.getCustomerPlan();
        if (customerPlanRef != null) {
            // Lock the CustomerPlan row: a customer can switch their PENDING plan selection
            // (subscribe() reuses the same row) while an older transaction for a previous
            // selection is still in flight. Without this lock+check, that stale transaction
            // could activate the customer onto whatever plan the row currently references,
            // with an endDate computed from the *old* transaction's plan duration.
            CustomerPlan customerPlan = customerPlanRepository.findByIdForUpdate(customerPlanRef.getId())
                    .orElse(null);
            boolean staleSelection = customerPlan != null
                    && customerPlan.getSubscriptionPlan() != null
                    && transaction.getSubscriptionPlan() != null
                    && !customerPlan.getSubscriptionPlan().getId().equals(transaction.getSubscriptionPlan().getId());

            if (staleSelection) {
                log.warn("Payment {} succeeded for plan '{}' but the customer plan has since moved to '{}'; "
                                + "leaving entitlement untouched, flag for manual refund/reconciliation.",
                        transaction.getTransactionCode(),
                        transaction.getSubscriptionPlan().getPlanName(),
                        customerPlan.getSubscriptionPlan().getPlanName());
            } else if (customerPlan != null) {
                activateCustomerPlan(transaction, customerPlan);
            }
        }

        return savedTransaction;
    }

    private void activateCustomerPlan(PaymentTransaction transaction, CustomerPlan customerPlan) {
        Long customerId = transaction.getCustomer().getId();

        CustomerPlan oldActivePlan = customerPlanRepository.findByCustomerIdAndStatus(customerId, PlanStatus.ACTIVE)
                .orElse(null);
        if (oldActivePlan != null && !oldActivePlan.getId().equals(customerPlan.getId())) {
            customerPlan.setUsedQuota(oldActivePlan.getUsedQuota());
            customerPlan.setUsageStartAt(oldActivePlan.getUsageStartAt());
            customerPlan.setUsageEndAt(oldActivePlan.getUsageEndAt());
            customerPlan.setBillingCycleStartAt(oldActivePlan.getBillingCycleStartAt());
            customerPlan.setBillingCycleEndAt(oldActivePlan.getBillingCycleEndAt());
            oldActivePlan.setStatus(PlanStatus.EXPIRED);
            // Flush before activating the new row: a DB constraint enforces at most one
            // ACTIVE customer_plan per customer, and Hibernate may otherwise order the new
            // row's ACTIVE update before this row's EXPIRED update within the same flush.
            customerPlanRepository.saveAndFlush(oldActivePlan);
        }

        LocalDateTime now = AppClock.now();
        customerPlan.setStatus(PlanStatus.ACTIVE);
        customerPlan.setStartDate(now);
        if (transaction.getSubscriptionPlan() != null) {
            customerPlan.setEndDate(now.plusDays(transaction.getSubscriptionPlan().getDurationDays()));
        } else {
            customerPlan.setEndDate(now.plusDays(30));
        }
        customerPlanRepository.save(customerPlan);
    }

    private PaymentTransaction markFailed(PaymentTransaction transaction) {
        if (transaction.getPaymentStatus() == PaymentStatus.FAILED) {
            return transaction;
        }
        if (transaction.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new VnPayCallbackException("02", "Order already confirmed");
        }

        transaction.setPaymentStatus(PaymentStatus.FAILED);
        return paymentTransactionRepository.save(transaction);
    }

    private String createPaymentUrl(PaymentTransaction transaction, String clientIp) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", vnPayProperties.getVersion());
        params.put("vnp_Command", vnPayProperties.getCommand());
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", toVnPayAmount(transaction.getAmount()));
        params.put("vnp_CurrCode", vnPayProperties.getCurrCode());
        params.put("vnp_TxnRef", transaction.getTransactionCode());
        params.put("vnp_OrderInfo", "Thanh toan goi dich vu " + transaction.getSubscriptionPlan().getPlanName());
        params.put("vnp_OrderType", vnPayProperties.getOrderType());
        params.put("vnp_Locale", vnPayProperties.getLocale());
        params.put("vnp_ReturnUrl", vnPayProperties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        LocalDateTime vnPayTime = LocalDateTime.now(VNPAY_ZONE_ID);
        params.put("vnp_CreateDate", vnPayTime.format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", vnPayTime.plusMinutes(15).format(VNPAY_DATE_FORMAT));

        String query = buildHashData(params);
        logVnPayCredentialsForDebug();
        String secureHash = hmacSha512(vnPayProperties.getHashSecret(), query);
        return vnPayProperties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    private void logVnPayCredentialsForDebug() {
        if (!vnPayProperties.isDebugLogCredentials()) {
            return;
        }
        log.warn(
                "VNPAY DEBUG ONLY - tmnCode='{}', hashSecret='{}'. Disable VNPAY_DEBUG_LOG_CREDENTIALS after debugging.",
                vnPayProperties.getTmnCode(), vnPayProperties.getHashSecret());
    }

    private void validateVnPayConfig() {
        if (isMissingConfig(vnPayProperties.getTmnCode()) || isMissingConfig(vnPayProperties.getHashSecret())) {
            throw new RuntimeException("Chua cau hinh VNPAY_TMN_CODE hoac VNPAY_HASH_SECRET sandbox");
        }
    }

    private boolean isMissingConfig(String value) {
        return value == null || value.isBlank() || "CHANGE_ME".equals(value);
    }

    private boolean isValidSignature(Map<String, String> callbackParams) {
        String receivedHash = callbackParams.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        Map<String, String> signedParams = callbackParams.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, TreeMap::new));

        String calculatedHash = hmacSha512(vnPayProperties.getHashSecret(), buildHashData(signedParams));
        return calculatedHash.equalsIgnoreCase(receivedHash);
    }

    private String toVnPayAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private boolean isValidVnPayAmount(BigDecimal amount) {
        return amount != null
                && amount.compareTo(VNPAY_MIN_AMOUNT) >= 0
                && amount.compareTo(VNPAY_MAX_AMOUNT) < 0;
    }

    private String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String secretKey, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Khong the tao chu ky thanh toan VNPAY", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
