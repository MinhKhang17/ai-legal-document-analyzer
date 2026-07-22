package com.analyzer.api.controller.chatsession;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.chatsession.AppendChatContextRequest;
import com.analyzer.api.dto.chatsession.ChatSessionMemoryResponse;
import com.analyzer.api.dto.chatsession.ChatSessionSummaryResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.chatsession.ChatMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat-sessions")
@RequiredArgsConstructor
@Tag(name = "Chat Session Memory & Context", description = "Endpoints for retrieving and updating chat memory and context snapshots")
public class ChatSessionContextController {

    private final ChatMemoryService chatMemoryService;

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get summary of chat session")
    public ResponseEntity<ApiResponseDTO<ChatSessionSummaryResponse>> getSummary(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String chatSessionId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved chat summary successfully",
                chatMemoryService.getSummary(chatSessionId, currentUser.getId())));
    }

    @GetMapping("/{id}/memory")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Get memory and context json of chat session")
    public ResponseEntity<ApiResponseDTO<ChatSessionMemoryResponse>> getMemory(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String chatSessionId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Retrieved chat memory successfully",
                chatMemoryService.getMemory(chatSessionId, currentUser.getId())));
    }

    @PostMapping("/{id}/context")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EXPERT')")
    @Operation(summary = "Append context json to chat session")
    public ResponseEntity<ApiResponseDTO<ChatSessionMemoryResponse>> appendContext(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable("id") String chatSessionId,
            @Valid @RequestBody AppendChatContextRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Appended context successfully",
                chatMemoryService.appendContext(chatSessionId, currentUser.getId(), request)));
    }
}
