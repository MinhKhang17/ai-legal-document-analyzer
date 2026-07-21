package com.analyzer.api.repository;

import com.analyzer.api.entity.ChatMessageFeedback;
import com.analyzer.api.enums.FeedbackRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatMessageFeedbackRepository extends JpaRepository<ChatMessageFeedback, String>, JpaSpecificationExecutor<ChatMessageFeedback> {

    Optional<ChatMessageFeedback> findByChatMessageId(String chatMessageId);

    Optional<ChatMessageFeedback> findByChatMessageIdAndUserId(String chatMessageId, Long userId);

    Page<ChatMessageFeedback> findByRating(FeedbackRating rating, Pageable pageable);

    long countByFeedbackType(com.analyzer.api.enums.AiFeedbackType feedbackType);
}
