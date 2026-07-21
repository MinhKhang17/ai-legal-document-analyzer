package com.analyzer.api.dto.chatmessage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackSummaryResponse {
    private long total;
    private long likes;
    private long dislikes;
    private double likeRate;
    private double dislikeRate;
}
