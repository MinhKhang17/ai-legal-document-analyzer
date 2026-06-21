package com.analyzer.api.service.impl;

import com.analyzer.api.dto.auth.JwtResponseDTO;
import com.analyzer.api.dto.auth.LoginRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import com.analyzer.api.entity.RefreshToken;
import com.analyzer.api.entity.User;
import com.analyzer.api.mapper.UserMapper;
import com.analyzer.api.repository.RefreshTokenRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.security.JwtTokenProvider;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public JwtResponseDTO login(LoginRequestDTO loginRequest, HttpServletResponse response) {
        // 1. Authenticate credentials
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // 2. Generate Access Token (JWT)
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        // 3. Find user entity
        User user = userRepository.findByEmail(userDetails.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 4. Revoke all existing refresh tokens for this user
        refreshTokenRepository.revokeAllByUser(user);

        // 5. Generate and save new Refresh Token
        String refreshTokenStr = jwtTokenProvider.generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .expiryDate(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        // 6. Set Refresh Token in HttpOnly Cookie
        response.addCookie(jwtTokenProvider.createRefreshTokenCookie(refreshTokenStr));

        // 7. Build and return response (Access Token in body)
        String roleAuthority = userDetails.getAuthorities().stream()
                .findFirst()
                .map(item -> item.getAuthority())
                .orElse("ROLE_CUSTOMER");
        String roleName = roleAuthority.startsWith("ROLE_") ? roleAuthority.substring(5) : roleAuthority;

        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .role(roleName)
                .build();
    }

    @Override
    @Transactional
    public JwtResponseDTO refreshToken(HttpServletRequest request, HttpServletResponse response) {
        // 1. Read Refresh Token from HttpOnly Cookie
        String refreshTokenStr = jwtTokenProvider.getRefreshTokenFromCookies(request);
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            throw new RuntimeException("Refresh token không tìm thấy trong cookie");
        }

        // 2. Look up in database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new RuntimeException("Refresh token không hợp lệ"));

        // 3. Check if revoked
        if (refreshToken.isRevoked()) {
            throw new RuntimeException("Refresh token đã bị thu hồi");
        }

        // 4. Check if expired
        if (refreshToken.isExpired()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new RuntimeException("Refresh token đã hết hạn. Vui lòng đăng nhập lại");
        }

        User user = refreshToken.getUser();

        // 5. Revoke old refresh token (token rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // 6. Generate new Access Token
        String roleAuthority = "ROLE_" + user.getRole().getName().name();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), roleAuthority);

        // 7. Generate and save new Refresh Token
        String newRefreshTokenStr = jwtTokenProvider.generateRefreshToken();
        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenStr)
                .user(user)
                .expiryDate(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshToken);

        // 8. Set new Refresh Token in HttpOnly Cookie
        response.addCookie(jwtTokenProvider.createRefreshTokenCookie(newRefreshTokenStr));

        // 9. Return new Access Token in body
        return JwtResponseDTO.builder()
                .accessToken(newAccessToken)
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().getName().name())
                .build();
    }

    @Override
    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        // 1. Read Refresh Token from Cookie and revoke it
        String refreshTokenStr = jwtTokenProvider.getRefreshTokenFromCookies(request);
        if (refreshTokenStr != null) {
            refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(rt -> {
                // Revoke all tokens for this user
                refreshTokenRepository.revokeAllByUser(rt.getUser());
            });
        }

        // 2. Also try to revoke via authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl userDetails) {
            userRepository.findByEmail(userDetails.getEmail()).ifPresent(
                    refreshTokenRepository::revokeAllByUser);
        }

        // 3. Clear refresh token cookie
        response.addCookie(jwtTokenProvider.createDeleteRefreshTokenCookie());

        // 4. Clear security context
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();
        String email;
        if (principal instanceof UserDetailsImpl userDetails) {
            email = userDetails.getEmail();
        } else if (principal instanceof String strPrincipal) {
            email = strPrincipal;
        } else {
            throw new RuntimeException("Invalid authentication principal");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return userMapper.toResponseDTO(user);
    }
}
