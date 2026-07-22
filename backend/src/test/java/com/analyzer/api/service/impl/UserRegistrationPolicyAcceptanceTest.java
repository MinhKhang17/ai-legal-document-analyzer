package com.analyzer.api.service.impl;
import com.analyzer.api.service.user.impl.UserServiceImpl;

import com.analyzer.api.dto.auth.RegistrationResponse;
import com.analyzer.api.dto.user.UserRequest;
import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.mapper.UserMapper;
import com.analyzer.api.repository.user.RoleRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.policy.PolicyAcceptanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationPolicyAcceptanceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock PolicyAcceptanceService policyAcceptanceService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, roleRepository, userMapper, passwordEncoder,
                emailService, policyAcceptanceService);
    }

    @Test
    void registrationRequiresPrivacyPolicyAcceptance() {
        UserRequest request = validRequest();
        request.setAcceptedPrivacyPolicy(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.createUser(request, "127.0.0.1", "test-agent"));

        assertTrue(exception.getMessage().contains("privacy"));
        verify(userRepository, never()).save(any());
        verify(policyAcceptanceService, never()).acceptCurrent(any(), any(), any());
    }

    @Test
    void registrationRecordsCurrentPolicyAcceptanceWithRequestMetadata() {
        UserRequest request = validRequest();
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .acceptedTerms(true)
                .build();

        when(roleRepository.findByName(RoleName.CUSTOMER))
                .thenReturn(Optional.of(Role.builder().id(1L).name(RoleName.CUSTOMER).build()));
        when(userMapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        when(emailService.sendVerificationEmail(any(), any(), any())).thenReturn(true);

        RegistrationResponse response = service.createUser(request, "203.0.113.10", "test-agent");

        assertEquals("PENDING_VERIFICATION", response.getRegistrationStatus());
        verify(policyAcceptanceService).acceptCurrent(42L, "203.0.113.10", "test-agent");
    }

    private UserRequest validRequest() {
        UserRequest request = new UserRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail("test@example.com");
        request.setPassword("Password@123");
        request.setConfirmPassword("Password@123");
        request.setAcceptedTerms(true);
        request.setAcceptedPrivacyPolicy(true);
        return request;
    }
}
