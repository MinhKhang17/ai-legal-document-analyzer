package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.user.ChangePasswordRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user's details based on their ID")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> getUserById(
            @Parameter(description = "ID of the user to be retrieved") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDTO.success(userService.getUserById(id)));
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves a list of all registered users")
    public ResponseEntity<ApiResponseDTO<List<UserResponseDTO>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponseDTO.success(userService.getAllUsers()));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change password", description = "Change the password of the currently authenticated user (customer or expert).")
    public ResponseEntity<ApiResponseDTO<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request) {
        userService.changePassword(getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponseDTO.success("Đổi mật khẩu thành công"));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("Bạn chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getId();
        }
        throw new RuntimeException("Thông tin xác thực không hợp lệ");
    }
}
