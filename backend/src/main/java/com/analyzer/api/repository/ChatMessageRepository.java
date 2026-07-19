package com.analyzer.api.repository;

import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(String chatSessionId);

    Page<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(String chatSessionId, Pageable pageable);

    Optional<ChatMessage> findByIdAndUserId(String id, Long userId);

    Optional<ChatMessage> findTopByRequestIdAndUserIdAndRoleOrderByCreatedAtDesc(
            String requestId,
            Long userId,
            ChatMessageRole role);

    List<ChatMessage> findByChatSessionIdAndStatusOrderByCreatedAtAsc(
            String chatSessionId,
            ChatMessageStatus status
    );

    List<ChatMessage> findByChatSessionIdAndStatusOrderByCreatedAtDesc(
            String chatSessionId,
            ChatMessageStatus status,
            Pageable pageable
    );

    long countByChatSessionIdAndStatus(String chatSessionId, ChatMessageStatus status);

    @Query("""
            select m from ChatMessage m
            where m.chatSession.id = :sessionId
              and m.status = :status
              and m.createdAt < :before
            order by m.createdAt asc
            """)
    List<ChatMessage> findMessagesToSummarizeBefore(
            @Param("sessionId") String sessionId,
            @Param("status") ChatMessageStatus status,
            @Param("before") LocalDateTime before,
            Pageable pageable);

    @Query("""
            select m from ChatMessage m
            where m.chatSession.id = :sessionId
              and m.status = :status
              and m.createdAt > :after
              and m.createdAt < :before
            order by m.createdAt asc
            """)
    List<ChatMessage> findMessagesToSummarizeBetween(
            @Param("sessionId") String sessionId,
            @Param("status") ChatMessageStatus status,
            @Param("after") LocalDateTime after,
            @Param("before") LocalDateTime before,
            Pageable pageable);

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
