package com.analyzer.api.controller.admin;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.user.AdminCreateLawyerRequestDTO;
import com.analyzer.api.dto.user.UserResponseDTO;
import com.analyzer.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "Endpoints for admin to create lawyer accounts")
public class AdminUserController {
//
//    private final UserService userService;
//
//    @PostMapping("/lawyers")
//    @PreAuthorize("hasRole('ADMIN')")
//    @Operation(summary = "Create lawyer account", description = "Creates an active EXPERT user account for a lawyer.")
//    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> createLawyer(
//            @Valid @RequestBody AdminCreateLawyerRequestDTO request) {
//        UserResponseDTO user = userService.createLawyerUser(request);
//        return new ResponseEntity<>(
//                ApiResponseDTO.created("Lawyer account created successfully", user),
//                HttpStatus.CREATED);
//    }
}
