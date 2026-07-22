package com.analyzer.api.entity;

import com.analyzer.api.enums.AiQueryExecutionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_query_executions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_query_execution_request", columnNames = "request_id")
}, indexes = {
        @Index(name = "idx_ai_query_execution_user_status_created", columnList = "user_id,status,created_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AiQueryExecution extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false, length = 100)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "workspace_id", length = 255)
    private String workspaceId;

    @Column(name = "chat_session_id", length = 255)
    private String chatSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiQueryExecutionStatus status;

    @Column(name = "estimated_tokens", nullable = false)
    private Integer estimatedTokens;

    @Column(name = "actual_input_tokens")
    private Integer actualInputTokens;

    @Column(name = "actual_output_tokens")
    private Integer actualOutputTokens;

    @Column(name = "plan_type_snapshot", length = 50)
    private String planTypeSnapshot;

    @Column(name = "context_snapshot_json", columnDefinition = "TEXT")
    private String contextSnapshotJson;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Version
    private Long version;
}
