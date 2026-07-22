package com.analyzer.api.dto.auth;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegistrationResponse {
    private String registrationStatus;
    private String emailDeliveryStatus;
    private String maskedEmail;
    private int resendAvailableInSeconds;
}
