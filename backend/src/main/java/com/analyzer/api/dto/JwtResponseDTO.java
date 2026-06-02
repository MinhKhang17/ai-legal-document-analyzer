package com.analyzer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Response payload after successful login")
public class JwtResponseDTO {
    @Schema(description = "JWT Token", example = "eyJhbGciOiJIUzI1NiIsInR...")
    private String token;
    
    @Schema(description = "Token Type", example = "Bearer")
    private String type = "Bearer";
    
    @Schema(description = "User ID", example = "1")
    private Long id;
    
    @Schema(description = "Username", example = "johndoe")
    private String username;
    
    @Schema(description = "Email", example = "johndoe@example.com")
    private String email;
    
    @Schema(description = "Role", example = "ROLE_USER")
    private String role;

    public JwtResponseDTO(String token, Long id, String username, String email, String role) {
        this.token = token;
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
    }
}
