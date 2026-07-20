package com.analyzer.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request payload for admin to resend/reactivate an expert account's temporary credentials")
public class ResendExpertActivationRequestDTO {

    @NotBlank(message = "Email khong duoc de trong")
    @Email(message = "Email is invalid")
    @Schema(description = "Email of the expert account to reactivate", example = "expert.b@example.com")
    private String email;
}
