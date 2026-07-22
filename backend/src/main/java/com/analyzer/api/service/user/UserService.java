package com.analyzer.api.service.user;

import com.analyzer.api.dto.user.AdminCreateLawyerRequest;
import com.analyzer.api.dto.user.ChangePasswordRequest;
import com.analyzer.api.dto.user.UpdateProfileRequest;
import com.analyzer.api.dto.user.UserRequest;
import com.analyzer.api.dto.user.UserResponse;
import com.analyzer.api.dto.auth.RegistrationResponse;
import java.util.List;

public interface UserService {
    default RegistrationResponse createUser(UserRequest request) {
        return createUser(request, null, null);
    }
    RegistrationResponse createUser(UserRequest request, String remoteAddress, String userAgent);
    UserResponse getUserById(Long id);
    List<UserResponse> getAllUsers();
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
    void changePassword(Long userId, ChangePasswordRequest request);
    UserResponse createExpertUser(AdminCreateLawyerRequest request);
    List<UserResponse> getActiveExperts();

    /**
     * Resets an EXPERT account back to the default temporary password, unlocks it if it was
     * locked for missing the password-change deadline, and resends the account-info email.
     */
    void resendExpertActivation(String email);

    /**
     * Soft deletes (deactivates) a user account.
     */
    void softDeleteUser(Long userId, Long currentAdminId);

    /**
     * Restores (re-activates) a soft-deleted user account.
     */
    void restoreUser(Long userId);

    void requestPasswordReset(String email);

    void resetPassword(com.analyzer.api.dto.auth.ResetPasswordRequest request);
}
