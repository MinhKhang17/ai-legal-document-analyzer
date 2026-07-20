package com.analyzer.api.service;

public interface ChatSessionMarkdownExportService {
    MarkdownExport export(Long userId, String sessionId);

    record MarkdownExport(byte[] content, String fileName) {}
}
