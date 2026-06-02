"""Shared API dependencies: trace id propagation and internal API-key auth."""
import uuid
from typing import Optional

from fastapi import Header, Request

from app.core.config import get_settings
from app.core.errors import UnauthorizedError


def get_trace_id(
    request: Request,
    x_trace_id: Optional[str] = Header(default=None, alias="X-Trace-Id"),
) -> str:
    """Use the caller-provided trace id or generate a new one."""
    trace_id = x_trace_id or str(uuid.uuid4())
    # Store on request state so exception handlers can read it.
    request.state.trace_id = trace_id
    return trace_id


def require_internal_key(
    x_ai_service_key: Optional[str] = Header(default=None, alias="X-AI-Service-Key"),
) -> None:
    """Guard internal/dev-only endpoints.

    If AI_SERVICE_API_KEY is configured, the matching header is required.
    If it is not configured, access is allowed (single-tenant dev mode).
    """
    settings = get_settings()
    expected = settings.ai_service_api_key
    if expected:
        if not x_ai_service_key or x_ai_service_key != expected:
            raise UnauthorizedError(
                "Missing or invalid X-AI-Service-Key header",
                details={"header": "X-AI-Service-Key"},
            )
