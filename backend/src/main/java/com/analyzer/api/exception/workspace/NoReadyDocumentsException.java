package com.analyzer.api.exception.workspace;

import lombok.Getter;

@Getter
public class NoReadyDocumentsException extends RuntimeException {
    private final String workspaceId;
    private final long readyDocumentCount;
    private final long processingDocumentCount;

    public NoReadyDocumentsException(String message, String workspaceId, long readyDocumentCount, long processingDocumentCount) {
        super(message);
        this.workspaceId = workspaceId;
        this.readyDocumentCount = readyDocumentCount;
        this.processingDocumentCount = processingDocumentCount;
    }
}
