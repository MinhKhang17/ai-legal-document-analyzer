package com.analyzer.api.controller.chatsession;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.chatsession.SharedChatSessionResponse;
import com.analyzer.api.service.chatsession.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shared/chat")
@RequiredArgsConstructor
@Tag(name = "Shared Chat Session", description = "Public read-only access to a shared chat session for anyone with its token")
public class SharedChatSessionController {

    private final ChatSessionService chatSessionService;

    @GetMapping("/{shareToken}")
    @Operation(summary = "View shared chat session", description = "Retrieves the read-only history of a chat session shared via token. Sending new messages is not supported through this endpoint.")
    public ResponseEntity<ApiResponseDTO<SharedChatSessionResponse>> getSharedChatSession(
            @PathVariable String shareToken) {
        SharedChatSessionResponse response = chatSessionService.getSharedChatSession(shareToken);
        return ResponseEntity.ok(ApiResponseDTO.success("Shared chat session retrieved successfully", response));
    }
}
