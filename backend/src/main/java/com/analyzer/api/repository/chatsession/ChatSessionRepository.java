package com.analyzer.api.repository.chatsession;

import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.enums.ChatSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

        Optional<ChatSession> findByIdAndUserId(String id, Long userId);

        List<ChatSession> findByWorkspaceIdAndUserIdAndStatusOrderByLastMessageAtDesc(
                        String workspaceId,
                        Long userId,
                        ChatSessionStatus status);

        Page<ChatSession> findByWorkspaceIdAndUserIdAndStatus(
                        String workspaceId,
                        Long userId,
                        ChatSessionStatus status,
                        Pageable pageable);

        Optional<ChatSession> findByWorkspaceIdAndUserIdAndIsDefaultTrueAndStatus(
                        String workspaceId,
                        Long userId,
                        ChatSessionStatus status);

        boolean existsByIdAndUserId(String id, Long userId);

        Optional<ChatSession> findByShareTokenAndIsSharedTrue(String shareToken);
}
