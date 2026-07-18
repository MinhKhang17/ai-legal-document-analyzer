package com.analyzer.api.dto.user;

import com.analyzer.api.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Response payload containing user details")
public class UserResponseDTO {

    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "First name of the user", example = "John")
    private String firstName;

    @Schema(description = "Last name of the user", example = "Doe")
    private String lastName;

    @Schema(description = "Email address", example = "johndoe@example.com")
    private String email;

    @Schema(description = "Role of the user", example = "CUSTOMER")
    private RoleName role;

    @Schema(description = "Status of the user account", example = "true")
    private boolean active;

    @Schema(description = "Whether the user's email has been verified", example = "true")
    private boolean emailVerified;

    @Schema(description = "Expert's main specialty (EXPERT role only)", example = "Tư vấn doanh nghiệp")
    private String specialty;

    @Schema(description = "Legal domain the expert practices in (EXPERT role only)", example = "Luật Thương mại")
    private String legalDomain;

    @Schema(description = "Free-text description of the expert's experience (EXPERT role only)", example = "5 năm kinh nghiệm giải quyết tranh chấp kinh doanh")
    private String description;
}
