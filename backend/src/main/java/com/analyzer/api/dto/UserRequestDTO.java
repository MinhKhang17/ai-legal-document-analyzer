package com.analyzer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request payload for creating or updating a user")
public class UserRequestDTO {
    
    @Schema(description = "Username of the user", example = "johndoe")
    private String username;

    @Schema(description = "Email address", example = "johndoe@example.com")
    private String email;

    @Schema(description = "Password", example = "SecureP@ssw0rd")
    private String password;
}
