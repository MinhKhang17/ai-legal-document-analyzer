package com.analyzer.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contract_version_no", columnNames = {"contract_id", "version_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractVersion {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private UserContract contract;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by_id")
    private User generatedBy;

    @Column(name = "generated_by_ai", nullable = false)
    @Builder.Default
    private Boolean generatedByAi = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_job_id")
    private ContractGenerationJob generationJob;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
