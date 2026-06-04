package com.analyzer.api.service;

import com.analyzer.api.dto.JwtResponseDTO;
import com.analyzer.api.dto.LoginRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    /**
     * Authenticate user, generate access + refresh tokens.
     * Access token returned in response body, refresh token set in HttpOnly cookie.
     */
    JwtResponseDTO login(LoginRequestDTO loginRequest, HttpServletResponse response);

    /**
     * Refresh access token using refresh token from HttpOnly cookie.
     * Old refresh token is revoked, new one issued (token rotation).
     */
    JwtResponseDTO refreshToken(HttpServletRequest request, HttpServletResponse response);

    /**
     * Logout user: revoke all refresh tokens and clear refresh token cookie.
     */
    void logout(HttpServletRequest request, HttpServletResponse response);
}
