package com.analyzer.api.controller.policy;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.policy.PolicyAcceptanceDtos.AcceptRequest;
import com.analyzer.api.dto.policy.PolicyAcceptanceDtos.StatusResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.PolicyAcceptanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/policies") @RequiredArgsConstructor
public class PolicyAcceptanceController {
    private final PolicyAcceptanceService service;
    @GetMapping("/current")
    public ResponseEntity<ApiResponseDTO<StatusResponse>> current(@AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponseDTO.success("Current policy status", service.currentStatus(user.getId())));
    }
    @PostMapping("/accept")
    public ResponseEntity<ApiResponseDTO<StatusResponse>> accept(@AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody AcceptRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponseDTO.success("Policies accepted", service.acceptCurrent(
                user.getId(), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"))));
    }
}
