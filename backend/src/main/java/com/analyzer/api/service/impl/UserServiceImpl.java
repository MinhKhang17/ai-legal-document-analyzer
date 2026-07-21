package com.analyzer.api.service.impl;

import com.analyzer.api.enums.RoleName;
import com.analyzer.api.dto.user.AdminCreateLawyerRequestDTO;
import com.analyzer.api.dto.user.ChangePasswordRequestDTO;
import com.analyzer.api.dto.user.UserRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import com.analyzer.api.dto.auth.RegistrationResponseDTO;
import com.analyzer.api.entity.Role;
import com.analyzer.api.entity.User;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.mapper.UserMapper;
import com.analyzer.api.repository.RoleRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int VERIFICATION_TOKEN_VALID_HOURS = 24;
    private static final String DEFAULT_EXPERT_PASSWORD = "12345678";
    private static final int EXPERT_PASSWORD_CHANGE_DEADLINE_DAYS = 7;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    @Transactional
    public RegistrationResponseDTO createUser(UserRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        if (request.getPassword() == null
                || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Password confirmation does not match");
        }

        if (!Boolean.TRUE.equals(request.getAcceptedTerms())) {
            throw new RuntimeException("You must accept the terms");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new RuntimeException("Default CUSTOMER role not found"));

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(customerRole);
        user.setActive(false);
        user.setEmailVerified(false);

        String verificationToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(sha256(verificationToken));
        user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_VALID_HOURS));
        user.setEmailVerificationRequestedAt(LocalDateTime.now());
        user.setEmailDeliveryStatus("PENDING");

        User savedUser = userRepository.save(user);
        boolean sent = emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getFirstName(), verificationToken);
        savedUser.setEmailDeliveryStatus(sent ? "SENT" : "FAILED");
        userRepository.save(savedUser);
        return RegistrationResponseDTO.builder().registrationStatus("PENDING_VERIFICATION")
                .emailDeliveryStatus(savedUser.getEmailDeliveryStatus())
                .maskedEmail(maskEmail(savedUser.getEmail())).resendAvailableInSeconds(60).build();
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

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        return userMapper.toResponseDTO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new RuntimeException("Xác nhận mật khẩu mới không khớp");
        }

        boolean hasExistingPassword = StringUtils.hasText(user.getPassword());
        if (hasExistingPassword) {
            if (!StringUtils.hasText(request.getOldPassword())
                    || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new ForbiddenException("Mật khẩu cũ không đúng");
            }
            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new RuntimeException("Mật khẩu mới không được trùng với mật khẩu cũ");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordResetDeadline(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponseDTO createExpertUser(AdminCreateLawyerRequestDTO request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email đã tồn tại trong hệ thống");
        }

        Role expertRole = roleRepository.findByName(RoleName.EXPERT)
                .orElseThrow(() -> new RuntimeException("EXPERT role not found"));

        User expert = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(DEFAULT_EXPERT_PASSWORD))
                .acceptedTerms(true)
                .role(expertRole)
                .active(request.getActive() == null || request.getActive())
                .emailVerified(true)
                .mustChangePassword(true)
                .passwordResetDeadline(LocalDateTime.now().plusDays(EXPERT_PASSWORD_CHANGE_DEADLINE_DAYS))
                .specialty(request.getSpecialty())
                .legalDomain(request.getLegalDomain())
                .description(request.getDescription())
                .build();

        User savedExpert = userRepository.save(expert);

        emailService.sendExpertAccountCreatedEmailAsync(
                savedExpert.getEmail(), savedExpert.getFirstName(), DEFAULT_EXPERT_PASSWORD, EXPERT_PASSWORD_CHANGE_DEADLINE_DAYS);

        return userMapper.toResponseDTO(savedExpert);
    }

    @Override
    @Transactional
    public void resendExpertActivation(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User expert = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Expert với email: " + normalizedEmail));

        if (expert.getRole() == null || expert.getRole().getName() != RoleName.EXPERT) {
            throw new ForbiddenException("Tài khoản không phải là Expert");
        }

        expert.setPassword(passwordEncoder.encode(DEFAULT_EXPERT_PASSWORD));
        expert.setMustChangePassword(true);
        expert.setPasswordResetDeadline(LocalDateTime.now().plusDays(EXPERT_PASSWORD_CHANGE_DEADLINE_DAYS));
        expert.setActive(true);
        userRepository.save(expert);

        emailService.sendExpertAccountCreatedEmailAsync(
                expert.getEmail(), expert.getFirstName(), DEFAULT_EXPERT_PASSWORD, EXPERT_PASSWORD_CHANGE_DEADLINE_DAYS);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getActiveExperts() {
        return userRepository.findAllByRole_NameAndActiveTrue(RoleName.EXPERT).stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void softDeleteUser(Long userId, Long currentAdminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        if (user.getId().equals(currentAdminId)) {
            throw new ForbiddenException("Bạn không thể tự vô hiệu hóa / xóa tài khoản Admin của chính mình");
        }

        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void restoreUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        user.setActive(true);
        userRepository.save(user);
    }
}
