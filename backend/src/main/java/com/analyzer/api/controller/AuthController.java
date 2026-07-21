package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.auth.JwtResponseDTO;
import com.analyzer.api.dto.auth.LoginRequestDTO;
import com.analyzer.api.dto.user.UserRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import com.analyzer.api.dto.auth.ResendVerificationEmailRequestDTO;
import com.analyzer.api.dto.auth.RegistrationResponseDTO;
import com.analyzer.api.dto.auth.ForgotPasswordRequestDTO;
import com.analyzer.api.dto.auth.ResetPasswordRequestDTO;
import com.analyzer.api.service.AuthService;
import com.analyzer.api.service.UserService;
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
    public ResponseEntity<ApiResponseDTO<RegistrationResponseDTO>> register(
            @RequestBody UserRequestDTO request) {
        RegistrationResponseDTO user = userService.createUser(request);
        return new ResponseEntity<>(ApiResponseDTO.created("Tạo tài khoản thành công", user), HttpStatus.CREATED);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Returns a generic response to prevent email enumeration.")
    public ResponseEntity<ApiResponseDTO<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {
        userService.requestPasswordReset(request.getEmail());
        return new ResponseEntity<>(ApiResponseDTO.accepted(
                "Neu email ton tai, huong dan dat lai mat khau se duoc gui", null), HttpStatus.ACCEPTED);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with a one-time token")
    public ResponseEntity<ApiResponseDTO<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request) {
        userService.resetPassword(request);
        return ResponseEntity.ok(ApiResponseDTO.success("Dat lai mat khau thanh cong"));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login user",
            description = "Authenticate with email/password. Returns Access Token in response body. " +
                    "Sets Refresh Token in HttpOnly Secure Cookie."
    )
    public ResponseEntity<ApiResponseDTO<JwtResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequest,
            HttpServletResponse response) {

        JwtResponseDTO jwtResponse = authService.login(loginRequest, response);
        return ResponseEntity.ok(ApiResponseDTO.success("Đăng nhập thành công", jwtResponse));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Reads Refresh Token from HttpOnly Cookie, validates it against PostgreSQL, " +
                    "generates a new Access Token (in body) and a new Refresh Token (in cookie). " +
                    "Old Refresh Token is revoked (token rotation)."
    )
    public ResponseEntity<ApiResponseDTO<JwtResponseDTO>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        JwtResponseDTO jwtResponse = authService.refreshToken(request, response);
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
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> getCurrentUser() {
        UserResponseDTO currentUser = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponseDTO.success("Lấy thông tin người dùng thành công", currentUser));
    }

    @GetMapping("/verify-email")
    @Operation(
            summary = "Verify email",
            description = "Verifies a user's email using the token sent at registration and activates the account."
    )
    public ResponseEntity<ApiResponseDTO<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponseDTO.success("Xác thực email thành công"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification", description = "Issues a fresh verification link when the account exists and is still unverified.")
    public ResponseEntity<ApiResponseDTO<RegistrationResponseDTO>> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequestDTO request, HttpServletRequest httpRequest) {
        RegistrationResponseDTO result = authService.resendVerificationEmail(request.getEmail(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponseDTO.success("Nếu email hợp lệ, liên kết xác thực mới đã được xử lý", result));
    }
}
