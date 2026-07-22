package com.analyzer.api.entity;

import com.analyzer.api.enums.KnowledgeScope;
import com.analyzer.api.enums.KnowledgeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "knowledge_base_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_knowledge_base_code", columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class KnowledgeBaseEntry extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KnowledgeScope scope;

    @Column(name = "current_version_no", nullable = false)
    @Builder.Default
    private Integer currentVersionNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private KnowledgeStatus currentStatus;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @OneToMany(mappedBy = "knowledgeBaseEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<KnowledgeBaseVersion> versions = new ArrayList<>();
}
