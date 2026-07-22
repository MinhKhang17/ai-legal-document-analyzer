package com.analyzer.api.service.impl;

import com.analyzer.api.dto.auth.JwtResponse;
import com.analyzer.api.dto.auth.LoginRequest;
import com.analyzer.api.dto.auth.RegistrationResponse;
import com.analyzer.api.dto.user.UserResponse;
import com.analyzer.api.entity.RefreshToken;
import com.analyzer.api.entity.User;
import com.analyzer.api.exception.auth.ExpiredVerificationTokenException;
import com.analyzer.api.exception.auth.InvalidRefreshTokenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.mapper.UserMapper;
import com.analyzer.api.repository.RefreshTokenRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.security.JwtTokenProvider;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.AuthService;
import com.analyzer.api.service.EmailService;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.TooManyRequestsException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final ConcurrentHashMap<String, LocalDateTime> resendCooldowns = new ConcurrentHashMap<>();

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final EmailService emailService;

    @Override
    @Transactional
    public JwtResponse login(LoginRequest loginRequest, HttpServletResponse response) {
        userRepository.findByEmail(loginRequest.getEmail().trim().toLowerCase()).ifPresent(user -> {
            if (!user.isEmailVerified()) throw new ForbiddenException("EMAIL_NOT_VERIFIED");
            if (!user.isActive()) throw new ForbiddenException("Tài khoản của bạn đã bị vô hiệu hóa hoặc ngừng hoạt động.");
        });
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

        return JwtResponse.builder()
                .accessToken(accessToken)
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .role(roleName)
                .build();
    }

    @Override
    @Transactional
    public JwtResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        // 1. Read Refresh Token from HttpOnly Cookie
        String refreshTokenStr = jwtTokenProvider.getRefreshTokenFromCookies(request);
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token không tìm thấy trong cookie");
        }

        // 2. Look up in database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token không hợp lệ"));

        // 3. Check if revoked
        if (refreshToken.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token đã bị thu hồi");
        }

        // 4. Check if expired
        if (refreshToken.isExpired()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new InvalidRefreshTokenException("Refresh token đã hết hạn. Vui lòng đăng nhập lại");
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
        return JwtResponse.builder()
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
    public UserResponse getCurrentUser() {
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

    @Override
    @Transactional
    public void verifyEmail(String token) {
        String tokenHash = sha256(token);
        var userWithActiveToken = userRepository.findByEmailVerificationToken(tokenHash);
        if (userWithActiveToken.isEmpty()) {
            if (userRepository.findByEmailVerificationLastUsedToken(tokenHash).isPresent()) {
                throw new ConflictException("TOKEN_ALREADY_USED");
            }
            throw new ResourceNotFoundException("TOKEN_INVALID");
        }
        User user = userWithActiveToken.get();

        if (user.isEmailVerified()) {
            return;
        }

        if (user.getEmailVerificationTokenExpiry() == null
                || user.getEmailVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ExpiredVerificationTokenException("TOKEN_EXPIRED");
        }

        user.setEmailVerified(true);
        user.setActive(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setEmailVerificationLastUsedToken(tokenHash);
        user.setEmailVerificationTokenUsedAt(LocalDateTime.now());
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public RegistrationResponse resendVerificationEmail(String email, String clientIp) {
        String normalized = email.trim().toLowerCase();
        String cooldownKey = sha256(normalized + "|" + clientIp);
        LocalDateTime previous = resendCooldowns.putIfAbsent(cooldownKey, LocalDateTime.now());
        if (previous != null && previous.plusSeconds(60).isAfter(LocalDateTime.now())) {
            throw new TooManyRequestsException("RESEND_VERIFICATION_COOLDOWN");
        }
        resendCooldowns.put(cooldownKey, LocalDateTime.now());
        userRepository.findByEmail(normalized).ifPresent(user -> {
            if (user.isEmailVerified()) {
                return;
            }
            String token = UUID.randomUUID().toString();
            user.setEmailVerificationToken(sha256(token));
            user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            user.setEmailVerificationRequestedAt(LocalDateTime.now());
            userRepository.save(user);
            boolean sent = emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), token);
            user.setEmailDeliveryStatus(sent ? "SENT" : "FAILED");
            userRepository.save(user);
        });
        return RegistrationResponse.builder().registrationStatus("PENDING_VERIFICATION")
                .emailDeliveryStatus("SENT").maskedEmail(maskEmail(normalized))
                .resendAvailableInSeconds(60).build();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(at, 0));
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
