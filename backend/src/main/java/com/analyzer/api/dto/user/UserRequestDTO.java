package com.analyzer.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request payload for registering a new user")
public class UserRequestDTO {

    @NotBlank(message = "Tên không được để trống")
    @Schema(description = "First name of the user", example = "Nguyen")
    private String firstName;

    @NotBlank(message = "Họ và tên đệm không được để trống")
    @Schema(description = "Last name of the user", example = "Van A")
    private String lastName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email is invalid")
    @Schema(description = "Email address", example = "vana@example.com")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Password must have at least 8 characters")
    @Schema(description = "Password", example = "12345678")
    private String password;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    @Schema(description = "Password confirmation", example = "12345678")
    private String confirmPassword;

    @AssertTrue(message = "You must accept the terms")
    @Schema(description = "User accepts terms and conditions", example = "true")
    private Boolean acceptedTerms;
}
