package com.analyzer.api.service.impl;

import com.analyzer.api.exception.GlobalExceptionHandler;
import com.analyzer.api.exception.auth.InvalidRefreshTokenException;
import com.analyzer.api.mapper.UserMapper;
import com.analyzer.api.repository.RefreshTokenRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.security.JwtTokenProvider;
import com.analyzer.api.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserMapper userMapper;
    @Mock EmailService emailService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;

    @Test
    void missingRefreshCookieIsAnUnauthorizedGuestState() {
        when(jwtTokenProvider.getRefreshTokenFromCookies(request)).thenReturn(null);

        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> service().refreshToken(request, response));

        assertEquals("Refresh token không tìm thấy trong cookie", exception.getMessage());
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                new GlobalExceptionHandler().handleInvalidRefreshTokenException(exception).getStatusCode());
    }

    private AuthServiceImpl service() {
        return new AuthServiceImpl(
                authenticationManager,
                jwtTokenProvider,
                userRepository,
                refreshTokenRepository,
                userMapper,
                emailService);
    }
}
