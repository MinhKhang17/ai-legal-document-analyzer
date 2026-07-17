package com.analyzer.api.service;

import com.analyzer.api.dto.user.AdminCreateLawyerRequestDTO;
import com.analyzer.api.dto.user.ChangePasswordRequestDTO;
import com.analyzer.api.dto.user.UserRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import java.util.List;

public interface UserService {
    UserResponseDTO createUser(UserRequestDTO request);
    UserResponseDTO getUserById(Long id);
    List<UserResponseDTO> getAllUsers();
    void changePassword(Long userId, ChangePasswordRequestDTO request);
    UserResponseDTO createExpertUser(AdminCreateLawyerRequestDTO request);
    List<UserResponseDTO> getActiveExperts();
}
