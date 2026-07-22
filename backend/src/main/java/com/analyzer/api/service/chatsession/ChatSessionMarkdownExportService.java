package com.analyzer.api.service.chatsession;

public interface ChatSessionMarkdownExportService {
    MarkdownExport export(Long userId, String sessionId);

    record MarkdownExport(byte[] content, String fileName) {}
}
