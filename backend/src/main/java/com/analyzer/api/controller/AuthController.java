package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.JwtResponseDTO;
import com.analyzer.api.dto.LoginRequestDTO;
import com.analyzer.api.dto.UserRequestDTO;
import com.analyzer.api.dto.UserResponseDTO;
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
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> register(
            @RequestBody UserRequestDTO request) {
        UserResponseDTO user = userService.createUser(request);
        return new ResponseEntity<>(ApiResponseDTO.created("Tạo tài khoản thành công", user), HttpStatus.CREATED);
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
}