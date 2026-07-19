package com.analyzer.api.controller.chatsession;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.chatsession.ChatSessionDocumentResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.service.chatsession.ChatSessionDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-sessions/{sessionId}/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class ChatSessionDocumentController {
    private final ChatSessionDocumentService service;

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ChatSessionDocumentResponse>>> list(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponseDTO.success(service.list(currentUserId(), sessionId)));
    }

    @PostMapping("/{documentId}")
    public ResponseEntity<ApiResponseDTO<ChatSessionDocumentResponse>> attach(@PathVariable String sessionId, @PathVariable String documentId) {
        return ResponseEntity.ok(ApiResponseDTO.success("Document attached", service.attach(currentUserId(), sessionId, documentId)));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponseDTO<Void>> detach(@PathVariable String sessionId, @PathVariable String documentId) {
        service.detach(currentUserId(), sessionId, documentId);
        return ResponseEntity.ok(ApiResponseDTO.success("Document detached"));
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl user) return user.getId();
        throw new ForbiddenException("AUTHENTICATION_REQUIRED");
    }
}
