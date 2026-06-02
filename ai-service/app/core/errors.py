"""Domain error codes and application exceptions.

A single AppError type carries a stable error code, HTTP status, a human
message and optional structured details. The global exception handlers in
main.py translate these into the standard JSON error envelope.
"""
from enum import Enum
from typing import Any, Dict, Optional


class ErrorCode(str, Enum):
    NEO4J_CONNECTION_FAILED = "NEO4J_CONNECTION_FAILED"
    GRAPH_QUERY_FAILED = "GRAPH_QUERY_FAILED"
    VALIDATION_ERROR = "VALIDATION_ERROR"
    NOT_FOUND = "NOT_FOUND"
    UNAUTHORIZED = "UNAUTHORIZED"
    LLM_NOT_CONFIGURED = "LLM_NOT_CONFIGURED"
    LLM_GENERATION_FAILED = "LLM_GENERATION_FAILED"
    RAG_RETRIEVAL_FAILED = "RAG_RETRIEVAL_FAILED"
    INTERNAL_ERROR = "INTERNAL_ERROR"


# Default HTTP status per error code.
_STATUS_MAP: Dict[ErrorCode, int] = {
    ErrorCode.NEO4J_CONNECTION_FAILED: 503,
    ErrorCode.GRAPH_QUERY_FAILED: 400,
    ErrorCode.VALIDATION_ERROR: 422,
    ErrorCode.NOT_FOUND: 404,
    ErrorCode.UNAUTHORIZED: 401,
    ErrorCode.LLM_NOT_CONFIGURED: 503,
    ErrorCode.LLM_GENERATION_FAILED: 502,
    ErrorCode.RAG_RETRIEVAL_FAILED: 500,
    ErrorCode.INTERNAL_ERROR: 500,
}


class AppError(Exception):
    """Base application exception mapped to the standard error envelope."""

    def __init__(
        self,
        code: ErrorCode,
        message: str,
        details: Optional[Dict[str, Any]] = None,
        status_code: Optional[int] = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.details = details or {}
        self.status_code = status_code or _STATUS_MAP.get(code, 500)


# --- Convenience subclasses ---------------------------------------------------


class Neo4jConnectionError(AppError):
    def __init__(self, message: str = "Cannot connect to Neo4j", details=None):
        super().__init__(ErrorCode.NEO4J_CONNECTION_FAILED, message, details)


class GraphQueryError(AppError):
    def __init__(self, message: str = "Graph query failed", details=None):
        super().__init__(ErrorCode.GRAPH_QUERY_FAILED, message, details)


class ValidationError(AppError):
    def __init__(self, message: str = "Validation error", details=None):
        super().__init__(ErrorCode.VALIDATION_ERROR, message, details)


class NotFoundError(AppError):
    def __init__(self, message: str = "Resource not found", details=None):
        super().__init__(ErrorCode.NOT_FOUND, message, details)


class UnauthorizedError(AppError):
    def __init__(self, message: str = "Unauthorized", details=None):
        super().__init__(ErrorCode.UNAUTHORIZED, message, details)


class LLMNotConfiguredError(AppError):
    def __init__(self, message: str = "LLM provider is not configured", details=None):
        super().__init__(ErrorCode.LLM_NOT_CONFIGURED, message, details)


class LLMGenerationError(AppError):
    def __init__(self, message: str = "LLM generation failed", details=None):
        super().__init__(ErrorCode.LLM_GENERATION_FAILED, message, details)


class RagRetrievalError(AppError):
    def __init__(self, message: str = "RAG retrieval failed", details=None):
        super().__init__(ErrorCode.RAG_RETRIEVAL_FAILED, message, details)
