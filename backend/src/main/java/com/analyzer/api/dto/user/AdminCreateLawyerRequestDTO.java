package com.analyzer.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request payload for admin to create a lawyer account")
public class AdminCreateLawyerRequestDTO {

    @NotBlank(message = "Ten khong duoc de trong")
    @Schema(description = "First name of the lawyer", example = "Nguyen")
    private String firstName;

    @NotBlank(message = "Ho va ten dem khong duoc de trong")
    @Schema(description = "Last name of the lawyer", example = "Van A")
    private String lastName;

    @NotBlank(message = "Email khong duoc de trong")
    @Email(message = "Email is invalid")
    @Schema(description = "Email address for the lawyer account", example = "lawyer.demo@lexiguard.ai")
    private String email;

    @Schema(description = "Whether the account is active", example = "true", defaultValue = "true")
    private Boolean active = true;

    @Schema(description = "Expert's main specialty", example = "Tư vấn doanh nghiệp")
    private String specialty;

    @Schema(description = "Legal domain the expert practices in", example = "Luật Thương mại")
    private String legalDomain;

    @Schema(description = "Free-text description of the expert's experience", example = "5 năm kinh nghiệm giải quyết tranh chấp kinh doanh")
    private String description;
}
