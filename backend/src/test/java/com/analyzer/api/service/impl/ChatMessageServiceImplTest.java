package com.analyzer.api.service.impl;

import com.analyzer.api.entity.ChatSessionDocument;
import com.analyzer.api.entity.Document;
import com.analyzer.api.exception.workspace.NoReadyDocumentsException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatMessageServiceImplTest {

    @Test
    void noAttachedDocumentsKeepsSystemKnowledgeBaseChatAvailable() {
        assertDoesNotThrow(() -> ChatMessageServiceImpl.ensureAttachedDocumentsReady(List.of(), "ws-1"));
    }

    @Test
    void allAttachedDocumentsMustBeReady() {
        List<ChatSessionDocument> mappings = List.of(mapping("READY"), mapping("PROCESSING"));

        NoReadyDocumentsException exception = assertThrows(
                NoReadyDocumentsException.class,
                () -> ChatMessageServiceImpl.ensureAttachedDocumentsReady(mappings, "ws-1"));

        assertEquals(1, exception.getReadyDocumentCount());
        assertEquals(1, exception.getProcessingDocumentCount());
    }

    @Test
    void failedAttachedDocumentExplainsHowToUnblockChat() {
        NoReadyDocumentsException exception = assertThrows(
                NoReadyDocumentsException.class,
                () -> ChatMessageServiceImpl.ensureAttachedDocumentsReady(List.of(mapping("FAILED")), "ws-1"));

        assertEquals("An attached document failed processing. Remove it or upload it again before chatting",
                exception.getMessage());
    }

    private static ChatSessionDocument mapping(String status) {
        return ChatSessionDocument.builder()
                .document(Document.builder().status(status).build())
                .build();
    }
}
