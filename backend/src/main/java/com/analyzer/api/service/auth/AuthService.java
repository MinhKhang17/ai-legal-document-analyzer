package com.analyzer.api.service.auth;

import com.analyzer.api.dto.auth.JwtResponse;
import com.analyzer.api.dto.auth.LoginRequest;
import com.analyzer.api.dto.user.UserResponse;
import com.analyzer.api.dto.auth.RegistrationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    /**
     * Authenticate user, generate access + refresh tokens.
     * Access token returned in response body, refresh token set in HttpOnly cookie.
     */
    JwtResponse login(LoginRequest loginRequest, HttpServletResponse response);

    /**
     * Refresh access token using refresh token from HttpOnly cookie.
     * Old refresh token is revoked, new one issued (token rotation).
     */
    JwtResponse refreshToken(HttpServletRequest request, HttpServletResponse response);

    /**
     * Logout user: revoke all refresh tokens and clear refresh token cookie.
     */
    void logout(HttpServletRequest request, HttpServletResponse response);

    /**
     * Get the current authenticated user's details.
     */
    UserResponse getCurrentUser();

    /**
     * Verify a user's email using the token sent at registration.
     * Idempotent: verifying an already-verified account succeeds silently.
     */
    void verifyEmail(String token);

    RegistrationResponse resendVerificationEmail(String email, String clientIp);
}
