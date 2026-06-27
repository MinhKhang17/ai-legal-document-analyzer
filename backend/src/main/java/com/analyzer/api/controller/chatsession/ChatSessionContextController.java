package com.analyzer.api.controller.chatsession;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.chatsession.AppendChatContextRequest;
import com.analyzer.api.dto.chatsession.ChatSessionMemoryResponse;
import com.analyzer.api.dto.chatsession.ChatSessionSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat-sessions")
public class ChatSessionContextController {

    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponseDTO<ChatSessionSummaryResponse>> getSummary(
            @PathVariable("id") String chatSessionId) {
        return notImplemented();
    }

    @GetMapping("/{id}/memory")
    public ResponseEntity<ApiResponseDTO<ChatSessionMemoryResponse>> getMemory(
            @PathVariable("id") String chatSessionId) {
        return notImplemented();
    }

    @PostMapping("/{id}/context")
    public ResponseEntity<ApiResponseDTO<ChatSessionMemoryResponse>> appendContext(
            @PathVariable("id") String chatSessionId,
            @Valid @RequestBody AppendChatContextRequest request) {
        return notImplemented();
    }

    private <T> ResponseEntity<ApiResponseDTO<T>> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponseDTO.error(501, "Phase 2 contract only", null));
    }
}
