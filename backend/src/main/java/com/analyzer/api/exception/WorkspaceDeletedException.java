package com.analyzer.api.exception;

import lombok.Getter;

@Getter
public class WorkspaceDeletedException extends RuntimeException {
    private final String workspaceId;
    private final String status;

    public WorkspaceDeletedException(String message, String workspaceId, String status) {
        super(message);
        this.workspaceId = workspaceId;
        this.status = status;
    }
}
