package com.analyzer.api.controller.chatsession;

import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.ChatSessionMarkdownExportService;
import com.analyzer.api.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatSessionMarkdownExportControllerTest {

    @Test
    void returnsUtf8MarkdownAttachmentWithVietnameseFilename() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        ChatSessionMarkdownExportService exportService = mock(ChatSessionMarkdownExportService.class);
        UserDetailsImpl principal = mock(UserDetailsImpl.class);
        when(principal.getId()).thenReturn(7L);
        byte[] content = "# Rà soát hợp đồng".getBytes(StandardCharsets.UTF_8);
        when(exportService.export(7L, "chat_1")).thenReturn(
                new ChatSessionMarkdownExportService.MarkdownExport(content, "Hợp-đồng-thuê-nhà-2026-07-19.md"));

        ResponseEntity<byte[]> response = new ChatSessionController(chatSessionService, exportService)
                .exportMarkdown(principal, "chat_1");

        assertEquals("text/markdown;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("filename*=UTF-8''"));
        assertArrayEquals(content, response.getBody());
    }
}
