package com.analyzer.api.service.impl;

import com.analyzer.api.dto.auth.ResetPasswordRequestDTO;
import com.analyzer.api.entity.User;
import com.analyzer.api.mapper.UserMapper;
import com.analyzer.api.repository.RoleRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordResetTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    @Test
    void resetTokenIsHashedInDatabaseAndClearedAfterUse() {
        User user = User.builder().id(1L).email("user@example.com").firstName("User")
                .password("old-hash").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        UserServiceImpl service = service();
        service.requestPasswordReset(" USER@EXAMPLE.COM ");

        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmailAsync(eq("user@example.com"), eq("User"), rawToken.capture(), eq(30));
        assertNotEquals(rawToken.getValue(), user.getForgotPasswordToken());
        assertNotNull(user.getForgotPasswordTokenExpiry());

        when(userRepository.findByForgotPasswordToken(user.getForgotPasswordToken())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NewPass@123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-hash");
        ResetPasswordRequestDTO request = new ResetPasswordRequestDTO();
        request.setToken(rawToken.getValue());
        request.setNewPassword("NewPass@123");
        request.setConfirmNewPassword("NewPass@123");
        service.resetPassword(request);

        assertEquals("new-hash", user.getPassword());
        assertNull(user.getForgotPasswordToken());
        assertNull(user.getForgotPasswordTokenExpiry());
    }

    @Test
    void expiredTokenCannotResetPassword() {
        User user = User.builder().password("old-hash").forgotPasswordToken("hash")
                .forgotPasswordTokenExpiry(LocalDateTime.now().minusMinutes(1)).build();
        when(userRepository.findByForgotPasswordToken(anyString())).thenReturn(Optional.of(user));
        ResetPasswordRequestDTO request = new ResetPasswordRequestDTO();
        request.setToken("expired");
        request.setNewPassword("NewPass@123");
        request.setConfirmNewPassword("NewPass@123");

        assertThrows(com.analyzer.api.exception.auth.ExpiredVerificationTokenException.class,
                () -> service().resetPassword(request));
        verify(passwordEncoder, never()).encode(anyString());
    }

    private UserServiceImpl service() {
        return new UserServiceImpl(userRepository, roleRepository, userMapper, passwordEncoder, emailService,
                mock(com.analyzer.api.service.PolicyAcceptanceService.class));
    }
}
