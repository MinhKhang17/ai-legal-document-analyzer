package com.analyzer.api.entity;

import com.analyzer.api.enums.FeedbackSurveyStatus;
import com.analyzer.api.enums.FeedbackSurveyType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "feedback_surveys", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_survey_code", columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FeedbackSurvey extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "survey_type", nullable = false)
    private FeedbackSurveyType surveyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackSurveyStatus status;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FeedbackSurveyResponse> responses = new ArrayList<>();
}
