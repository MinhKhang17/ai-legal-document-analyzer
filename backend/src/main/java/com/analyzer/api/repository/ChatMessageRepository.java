package com.analyzer.api.repository;

import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(String chatSessionId);

    Page<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(String chatSessionId, Pageable pageable);

    Optional<ChatMessage> findByIdAndUserId(String id, Long userId);

    Optional<ChatMessage> findByRequestId(String requestId);

    List<ChatMessage> findByChatSessionIdAndStatusOrderByCreatedAtAsc(
            String chatSessionId,
            ChatMessageStatus status
    );

    @Query("""
            select coalesce(sum(m.totalTokens), 0)
            from ChatMessage m
            where m.user.id = :userId
              and m.role = :role
              and m.status = :status
              and m.createdAt >= :start
              and m.createdAt < :end
            """)
    long sumTotalTokensByUserAndRoleAndStatusBetween(
            Long userId,
            ChatMessageRole role,
            ChatMessageStatus status,
            LocalDateTime start,
            LocalDateTime end);
}
