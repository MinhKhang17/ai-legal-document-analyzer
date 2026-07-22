package com.analyzer.api.entity;

import com.analyzer.api.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserContract extends BaseEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ContractTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_job_id")
    private ContractGenerationJob generationJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Document sourceDocument;

    @Column(nullable = false)
    private String title;

    @Column(name = "contract_type", nullable = false)
    private String contractType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    @Column(name = "current_version_no", nullable = false)
    @Builder.Default
    private Integer currentVersionNo = 1;

    @Column(name = "current_content_hash")
    private String currentContentHash;

    @Column(name = "last_generated_at")
    private LocalDateTime lastGeneratedAt;

    @OneToMany(mappedBy = "contract", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContractVersion> versions = new ArrayList<>();
}
