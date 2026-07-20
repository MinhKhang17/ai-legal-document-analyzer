package com.analyzer.api.controller.chatsession;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.chatsession.SharedChatSessionResponse;
import com.analyzer.api.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shared/chat")
@RequiredArgsConstructor
@Tag(name = "Shared Chat Session", description = "Read-only access to a customer's shared chat session, restricted to Admin/Expert")
public class SharedChatSessionController {

    private final ChatSessionService chatSessionService;

    @GetMapping("/{shareToken}")
    @Operation(summary = "View shared chat session", description = "Retrieves the read-only history of a chat session shared via token. Sending new messages is not supported through this endpoint.")
    public ResponseEntity<ApiResponseDTO<SharedChatSessionResponse>> getSharedChatSession(
            @PathVariable String shareToken,
            Authentication authentication) {
        boolean adminOrExpert = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_EXPERT".equals(authority.getAuthority()));
        SharedChatSessionResponse response = chatSessionService.getSharedChatSession(shareToken, adminOrExpert);
        return ResponseEntity.ok(ApiResponseDTO.success("Shared chat session retrieved successfully", response));
    }
}
