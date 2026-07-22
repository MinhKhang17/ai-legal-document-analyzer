package com.analyzer.api.entity;

import com.analyzer.api.enums.ContractGenerationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "contract_generation_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contract_generation_request", columnNames = "request_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ContractGenerationJob extends BaseEntity {

    @Id
    private String id;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ContractTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Document sourceDocument;

    @Column(name = "input_json", columnDefinition = "TEXT", nullable = false)
    private String inputJson;

    @Column(name = "prompt_snapshot", columnDefinition = "TEXT")
    private String promptSnapshot;

    @Column(name = "output_draft", columnDefinition = "TEXT")
    private String outputDraft;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractGenerationStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
