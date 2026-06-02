package com.analyzer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Response payload containing user details")
public class UserResponseDTO {
    
    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "Username of the user", example = "johndoe")
    private String username;

    @Schema(description = "Email address", example = "johndoe@example.com")
    private String email;

    @Schema(description = "Status of the user account", example = "true")
    private boolean active;
}
