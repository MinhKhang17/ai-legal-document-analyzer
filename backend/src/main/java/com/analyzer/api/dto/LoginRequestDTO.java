package com.analyzer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request payload for user login")
public class LoginRequestDTO {
    @Schema(description = "Username or email", example = "johndoe")
    private String username;

    @Schema(description = "Password", example = "SecureP@ssw0rd")
    private String password;
}
