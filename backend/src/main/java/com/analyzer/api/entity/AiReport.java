package com.analyzer.api.entity;

import com.analyzer.api.enums.AiReportStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AiReport extends BaseEntity {

    @Id
    private String id;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_reference_id", nullable = false)
    private String sourceReferenceId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiReportStatus status;
}
