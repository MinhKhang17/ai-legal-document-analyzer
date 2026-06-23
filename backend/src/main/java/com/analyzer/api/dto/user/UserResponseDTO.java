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
}
