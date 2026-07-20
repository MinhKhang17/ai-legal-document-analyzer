package com.analyzer.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request payload for changing the current user's password")
public class ChangePasswordRequestDTO {

    @Schema(description = "Current password (omit only if the account has no password set yet)", example = "OldPass@123")
    private String oldPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, message = "Mật khẩu mới phải có ít nhất 8 ký tự")
    @Schema(description = "New password", example = "NewPass@123")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu mới không được để trống")
    @Schema(description = "New password confirmation", example = "NewPass@123")
    private String confirmNewPassword;
}
