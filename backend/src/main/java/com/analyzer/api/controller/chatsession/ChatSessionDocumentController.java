package com.analyzer.api.controller.chatsession;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.chatsession.ChatSessionDocumentResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.chatsession.ChatSessionDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-sessions/{sessionId}/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class ChatSessionDocumentController {
    private final ChatSessionDocumentService service;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ChatSessionDocumentResponse>>> list(
            @AuthenticationPrincipal UserDetailsImpl currentUser, @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponseDTO.success(service.list(currentUser.getId(), sessionId)));
    }

    @PostMapping("/{documentId}")
    public ResponseEntity<ApiResponseDTO<ChatSessionDocumentResponse>> attach(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String sessionId, @PathVariable String documentId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Document attached", service.attach(currentUser.getId(), sessionId, documentId)));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponseDTO<Void>> detach(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable String sessionId, @PathVariable String documentId) {
        service.detach(currentUser.getId(), sessionId, documentId);
        return ResponseEntity.ok(ApiResponseDTO.success("Document detached"));
    }
}
