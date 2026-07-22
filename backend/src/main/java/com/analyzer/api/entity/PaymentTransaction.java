package com.analyzer.api.entity;

import com.analyzer.api.enums.PaymentMethod;
import com.analyzer.api.enums.PaymentPurpose;
import com.analyzer.api.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id")
    private SubscriptionPlan subscriptionPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_ticket_id")
    private LegalTicket legalTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_plan_id", nullable = true)
    private CustomerPlan customerPlan;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_purpose", nullable = false)
    @Builder.Default
    private PaymentPurpose paymentPurpose = PaymentPurpose.SUBSCRIPTION;

    @Column(name = "transaction_code", nullable = false, unique = true)
    private String transactionCode;

    @Column(name = "payment_url", length = 2048)
    private String paymentUrl;

    @Column(name = "gateway_transaction_no")
    private String gatewayTransactionNo;

    @Column(name = "gateway_response_code")
    private String gatewayResponseCode;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
