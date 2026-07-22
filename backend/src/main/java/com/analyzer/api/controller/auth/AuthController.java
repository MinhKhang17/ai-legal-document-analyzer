package com.analyzer.api.controller.auth;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.auth.JwtResponse;
import com.analyzer.api.dto.auth.LoginRequest;
import com.analyzer.api.dto.user.UserRequest;
import com.analyzer.api.dto.user.UserResponse;
import com.analyzer.api.dto.auth.ResendVerificationEmailRequest;
import com.analyzer.api.dto.auth.RegistrationResponse;
import com.analyzer.api.dto.auth.ForgotPasswordRequest;
import com.analyzer.api.dto.auth.ResetPasswordRequest;
import com.analyzer.api.service.auth.AuthService;
import com.analyzer.api.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(
            summary = "Register user",
            description = "Creates a new user account in the system"
    )
    public ResponseEntity<ApiResponseDTO<RegistrationResponse>> register(
            @Valid @RequestBody UserRequest request,
            HttpServletRequest httpRequest) {
        RegistrationResponse user = userService.createUser(
                request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return new ResponseEntity<>(ApiResponseDTO.created("Tạo tài khoản thành công", user), HttpStatus.CREATED);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Returns a generic response to prevent email enumeration.")
    public ResponseEntity<ApiResponseDTO<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        userService.requestPasswordReset(request.getEmail());
        return new ResponseEntity<>(ApiResponseDTO.accepted(
                "Neu email ton tai, huong dan dat lai mat khau se duoc gui", null), HttpStatus.ACCEPTED);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with a one-time token")
    public ResponseEntity<ApiResponseDTO<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ResponseEntity.ok(ApiResponseDTO.success("Dat lai mat khau thanh cong"));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login user",
            description = "Authenticate with email/password. Returns Access Token in response body. " +
                    "Sets Refresh Token in HttpOnly Secure Cookie."
    )
    public ResponseEntity<ApiResponseDTO<JwtResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletResponse response) {

        JwtResponse jwtResponse = authService.login(loginRequest, response);
        return ResponseEntity.ok(ApiResponseDTO.success("Đăng nhập thành công", jwtResponse));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Reads Refresh Token from HttpOnly Cookie, validates it against PostgreSQL, " +
                    "generates a new Access Token (in body) and a new Refresh Token (in cookie). " +
                    "Old Refresh Token is revoked (token rotation)."
    )
    public ResponseEntity<ApiResponseDTO<JwtResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        JwtResponse jwtResponse = authService.refreshToken(request, response);
        return ResponseEntity.ok(ApiResponseDTO.success("Token đã được refresh thành công", jwtResponse));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout user",
            description = "Revokes all Refresh Tokens in database and clears the Refresh Token cookie."
    )
    public ResponseEntity<ApiResponseDTO<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponseDTO.success("Đăng xuất thành công"));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current authenticated user",
            description = "Get details of the currently authenticated user based on the JWT Bearer Token."
    )
    public ResponseEntity<ApiResponseDTO<UserResponse>> getCurrentUser() {
        UserResponse currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin người dùng thành công", currentUser));
    }

    @GetMapping("/verify-email")
    @Operation(
            summary = "Verify email",
            description = "Verifies a user's email using the token sent at registration and activates the account."
    )
    public ResponseEntity<ApiResponseDTO<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponseDTO.success("CONFIRMATION_SUCCESS"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification", description = "Issues a fresh verification link when the account exists and is still unverified.")
    public ResponseEntity<ApiResponseDTO<RegistrationResponse>> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequest request, HttpServletRequest httpRequest) {
        RegistrationResponse result = authService.resendVerificationEmail(request.getEmail(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponseDTO.success("Nếu email hợp lệ, liên kết xác thực mới đã được xử lý", result));
    }
}
