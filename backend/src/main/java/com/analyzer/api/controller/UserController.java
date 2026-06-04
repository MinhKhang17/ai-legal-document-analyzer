package com.analyzer.api.controller;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.UserRequestDTO;
import com.analyzer.api.dto.UserResponseDTO;
import com.analyzer.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for managing users")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a new user", description = "Creates a new user account in the system")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> createUser(@RequestBody UserRequestDTO request) {
        UserResponseDTO user = userService.createUser(request);
        return new ResponseEntity<>(ApiResponseDTO.created("Tạo tài khoản thành công", user), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user's details based on their ID")
    public ResponseEntity<ApiResponseDTO<UserResponseDTO>> getUserById(
            @Parameter(description = "ID of the user to be retrieved") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDTO.success(userService.getUserById(id)));
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves a list of all registered users")
    public ResponseEntity<ApiResponseDTO<List<UserResponseDTO>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponseDTO.success(userService.getAllUsers()));
    }
}
