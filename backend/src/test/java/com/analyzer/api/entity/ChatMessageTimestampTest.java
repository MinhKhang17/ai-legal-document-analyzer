package com.analyzer.api.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTimestampTest {

    @Test
    void prePersistInitializesBothTimestamps() {
        ChatMessage message = ChatMessage.builder().build();

        message.onCreate();

        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getUpdatedAt()).isNotNull();
    }

    @Test
    void preUpdateRepairsMissingCreationTimestamp() {
        ChatMessage message = ChatMessage.builder().updatedAt(LocalDateTime.now().minusMinutes(1)).build();

        message.onUpdate();

        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getUpdatedAt()).isNotNull();
    }

    @Test
    void preUpdateDoesNotReplaceExistingCreationTimestamp() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 22, 12, 0);
        ChatMessage message = ChatMessage.builder().createdAt(createdAt).updatedAt(createdAt).build();

        message.onUpdate();

        assertThat(message.getCreatedAt()).isEqualTo(createdAt);
        assertThat(message.getUpdatedAt()).isAfter(createdAt);
    }
}
