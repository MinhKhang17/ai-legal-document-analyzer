package com.analyzer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request payload for user login")
public class LoginRequestDTO {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email is invalid")
    @Schema(description = "Email address used for login", example = "vantay@example.com")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Schema(description = "Password", example = "12345678")
    private String password;
}