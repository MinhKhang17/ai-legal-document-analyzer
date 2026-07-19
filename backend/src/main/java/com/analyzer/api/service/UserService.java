package com.analyzer.api.service;

import com.analyzer.api.dto.user.AdminCreateLawyerRequestDTO;
import com.analyzer.api.dto.user.ChangePasswordRequestDTO;
import com.analyzer.api.dto.user.UserRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import com.analyzer.api.dto.auth.RegistrationResponseDTO;
import java.util.List;

public interface UserService {
    RegistrationResponseDTO createUser(UserRequestDTO request);
    UserResponseDTO getUserById(Long id);
    List<UserResponseDTO> getAllUsers();
    void changePassword(Long userId, ChangePasswordRequestDTO request);
    UserResponseDTO createExpertUser(AdminCreateLawyerRequestDTO request);
    List<UserResponseDTO> getActiveExperts();

    /**
     * Resets an EXPERT account back to the default temporary password, unlocks it if it was
     * locked for missing the password-change deadline, and resends the account-info email.
     */
    void resendExpertActivation(String email);
}
