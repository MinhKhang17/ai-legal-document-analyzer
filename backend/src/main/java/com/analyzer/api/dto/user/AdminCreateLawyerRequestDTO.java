package com.analyzer.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "Mat khau khong duoc de trong")
    @Size(min = 8, message = "Password must have at least 8 characters")
    @Schema(description = "Password for the lawyer account", example = "Lawyer@123")
    private String password;

    @Schema(description = "Whether the account is active", example = "true", defaultValue = "true")
    private Boolean active = true;
}
