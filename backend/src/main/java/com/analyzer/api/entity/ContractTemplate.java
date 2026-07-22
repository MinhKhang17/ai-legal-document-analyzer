package com.analyzer.api.entity;

import com.analyzer.api.enums.ContractTemplateStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contract_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contract_template_code", columnNames = "template_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ContractTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, unique = true)
    private String templateCode;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category;

    @Column
    private String jurisdiction;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContractTemplateStatus status = ContractTemplateStatus.ACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserContract> contracts = new ArrayList<>();
}
