package com.analyzer.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request payload for updating current user profile")
public class UpdateProfileRequest {

    @NotBlank(message = "Tên không được để trống")
    @Schema(description = "First name of the user", example = "John")
    private String firstName;

    @NotBlank(message = "Họ không được để trống")
    @Schema(description = "Last name of the user", example = "Doe")
    private String lastName;

    @Schema(description = "Expert's main specialty", example = "Tư vấn doanh nghiệp")
    private String specialty;

    @Schema(description = "Legal domain the user practices in", example = "Luật Thương mại")
    private String legalDomain;

    @Schema(description = "Description or bio of the user", example = "Chuyên gia pháp lý doanh nghiệp")
    private String description;
}
