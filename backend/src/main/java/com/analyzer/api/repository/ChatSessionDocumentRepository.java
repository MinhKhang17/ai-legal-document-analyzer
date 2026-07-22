package com.analyzer.api.repository;

import com.analyzer.api.entity.ChatSessionDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionDocumentRepository extends JpaRepository<ChatSessionDocument, String> {
    @EntityGraph(attributePaths = {"document", "chatSession", "user"})
    List<ChatSessionDocument> findByChatSessionIdAndUserIdAndActiveTrueOrderByAttachedAtAsc(String sessionId, Long userId);

    @EntityGraph(attributePaths = {"document", "chatSession", "user"})
    List<ChatSessionDocument> findByChatSessionIdAndUserIdOrderByAttachedAtAsc(String sessionId, Long userId);

    @EntityGraph(attributePaths = {"document", "chatSession", "user"})
    Optional<ChatSessionDocument> findByChatSessionIdAndDocumentIdAndUserId(String sessionId, String documentId, Long userId);

    long countByChatSessionIdAndUserIdAndActiveTrue(String chatSessionId, Long userId);
}
