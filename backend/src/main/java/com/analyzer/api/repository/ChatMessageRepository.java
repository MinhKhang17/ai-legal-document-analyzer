package com.analyzer.api.repository;

import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.enums.ChatMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(String chatSessionId);

    Page<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(String chatSessionId, Pageable pageable);

    Optional<ChatMessage> findByIdAndUserId(String id, Long userId);

    List<ChatMessage> findByChatSessionIdAndStatusOrderByCreatedAtAsc(
            String chatSessionId,
            ChatMessageStatus status
    );
}
