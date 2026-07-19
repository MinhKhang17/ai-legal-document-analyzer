package com.analyzer.api.dto.legalticket;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttachmentPolicyResponse {
    private long maxAttachmentSizeKb;
    private int maxAttachmentsPerMessage;
    private int maxAttachmentsPerTicket;
    private List<String> allowedMimeTypes;
}
