package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.user.AdminCreateLawyerRequest;
import com.analyzer.api.dto.user.ResendExpertActivationRequest;
import com.analyzer.api.dto.user.UserResponse;
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
    public ResponseEntity<ApiResponseDTO<UserResponse>> createExpert(
            @Valid @RequestBody AdminCreateLawyerRequest request) {
        UserResponse user = userService.createExpertUser(request);
        return new ResponseEntity<>(
                ApiResponseDTO.created("Tạo tài khoản Expert thành công", user),
                HttpStatus.CREATED);
    }

    @GetMapping("/experts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List active experts", description = "Retrieves all active EXPERT users for assignment purposes.")
    public ResponseEntity<ApiResponseDTO<List<UserResponse>>> getActiveExperts() {
        return ResponseEntity.ok(ApiResponseDTO.success(userService.getActiveExperts()));
    }

    @PostMapping("/experts/resend-activation")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resend expert account activation",
            description = "Resets an expert account's password back to the default temporary password, unlocks it if it was locked for missing the password-change deadline, and resends the account-info email.")
    public ResponseEntity<ApiResponseDTO<Void>> resendExpertActivation(
            @Valid @RequestBody ResendExpertActivationRequest request) {
        userService.resendExpertActivation(request.getEmail());
        return ResponseEntity.ok(ApiResponseDTO.success("Đã gửi lại thông tin đăng nhập cho Expert"));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft delete user", description = "Deactivates (soft deletes) a user account.")
    public ResponseEntity<ApiResponseDTO<Void>> deleteUser(
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        Long currentAdminId = getCurrentUserId();
        userService.softDeleteUser(id, currentAdminId);
        return ResponseEntity.ok(ApiResponseDTO.success("Đã xóa (vô hiệu hóa) tài khoản người dùng thành công"));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore user", description = "Re-activates a soft-deleted user account.")
    public ResponseEntity<ApiResponseDTO<Void>> restoreUser(
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        userService.restoreUser(id);
        return ResponseEntity.ok(ApiResponseDTO.success("Đã khôi phục tài khoản người dùng thành công"));
    }

    private Long getCurrentUserId() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.analyzer.api.security.UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
