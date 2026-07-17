package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.user.AdminCreateLawyerRequestDTO;
import com.analyzer.api.dto.user.ResendExpertActivationRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import com.analyzer.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "Endpoints for admin to create and browse expert accounts")
public class AdminUserController {

    private final UserService userService;

    @PostMapping("/experts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create expert account", description = "Creates an active, email-verified EXPERT user account for a lawyer.")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> createExpert(
            @Valid @RequestBody AdminCreateLawyerRequestDTO request) {
        UserResponseDTO user = userService.createExpertUser(request);
        return new ResponseEntity<>(
                ApiResponseDTO.created("Tạo tài khoản Expert thành công", user),
                HttpStatus.CREATED);
    }

    @GetMapping("/experts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List active experts", description = "Retrieves all active EXPERT users for assignment purposes.")
    public ResponseEntity<ApiResponseDTO<List<UserResponseDTO>>> getActiveExperts() {
        return ResponseEntity.ok(ApiResponseDTO.success(userService.getActiveExperts()));
    }

    @PostMapping("/experts/resend-activation")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resend expert account activation",
            description = "Resets an expert account's password back to the default temporary password, unlocks it if it was locked for missing the password-change deadline, and resends the account-info email.")
    public ResponseEntity<ApiResponseDTO<Void>> resendExpertActivation(
            @Valid @RequestBody ResendExpertActivationRequestDTO request) {
        userService.resendExpertActivation(request.getEmail());
        return ResponseEntity.ok(ApiResponseDTO.success("Đã gửi lại thông tin đăng nhập cho Expert"));
    }
}
