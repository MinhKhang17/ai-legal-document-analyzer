"""Common response envelope models shared by every endpoint.

The Spring Boot backend always receives the same top-level shape:

Success: {"success": true, "data": {...}, "message": "...", "traceId": "..."}
Error:   {"success": false, "error": {...}, "traceId": "..."}
"""
from typing import Any, Dict, Generic, Optional, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class ErrorDetail(BaseModel):
    code: str = Field(..., description="Stable machine-readable error code")
    message: str = Field(..., description="Human-readable error message")
    details: Dict[str, Any] = Field(default_factory=dict)


class ApiResponse(BaseModel, Generic[T]):
    """Standard success envelope."""

    success: bool = True
    data: Optional[T] = None
    message: str = "OK"
    traceId: str = Field(..., description="Correlation id for this request")


class ErrorResponse(BaseModel):
    """Standard error envelope."""

    success: bool = False
    error: ErrorDetail
    traceId: str


def success_payload(data: Any, message: str, trace_id: str) -> Dict[str, Any]:
    """Build a raw success envelope dict (used by routes/handlers)."""
    return {
        "success": True,
        "data": data,
        "message": message,
        "traceId": trace_id,
    }


def error_payload(
    code: str, message: str, trace_id: str, details: Optional[Dict[str, Any]] = None
) -> Dict[str, Any]:
    """Build a raw error envelope dict (used by exception handlers)."""
    return {
        "success": False,
        "error": {
            "code": code,
            "message": message,
            "details": details or {},
        },
        "traceId": trace_id,
    }
