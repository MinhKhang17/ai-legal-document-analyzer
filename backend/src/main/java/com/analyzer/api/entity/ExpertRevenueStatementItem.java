package com.analyzer.api.entity;

import com.analyzer.api.enums.LegalTicketStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="expert_revenue_statement_items", uniqueConstraints=@UniqueConstraint(name="uk_statement_item_ticket", columnNames="ticket_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpertRevenueStatementItem {
    @Id private String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="statement_id", nullable=false) private ExpertRevenueStatement statement;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="ticket_id", nullable=false) private LegalTicket ticket;
    @Column(name="ticket_code", nullable=false) private String ticketCode;
    @Column(name="consultation_fee", precision=19, scale=2, nullable=false) private BigDecimal consultationFee;
    @Column(name="commission_rate_snapshot", precision=7, scale=6, nullable=false) private BigDecimal commissionRateSnapshot;
    @Column(name="platform_fee", precision=19, scale=2, nullable=false) private BigDecimal platformFee;
    @Column(name="expert_payout", precision=19, scale=2, nullable=false) private BigDecimal expertPayout;
    @Column(name="recognized_at", nullable=false) private LocalDateTime recognizedAt;
    @Column(name="assigned_expert_id_snapshot", nullable=false) private Long assignedExpertIdSnapshot;
    @Enumerated(EnumType.STRING) @Column(name="ticket_status_snapshot", nullable=false, length=40) private LegalTicketStatus ticketStatusSnapshot;
    @PrePersist void create(){ if(id==null) id="stmtitem_"+UUID.randomUUID().toString().replace("-",""); }
}
