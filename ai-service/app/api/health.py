"""Health, Neo4j connectivity and readiness endpoints."""
from fastapi import APIRouter, Depends

from app.api.deps import get_trace_id
from app.core.config import get_settings
from app.graph import connection
from app.models.common import success_payload
from app.services.llm_client import get_llm_client

router = APIRouter(prefix="/health", tags=["health"])


@router.get("")
@router.get("/")
def health(trace_id: str = Depends(get_trace_id)):
    settings = get_settings()
    data = {
        "status": "UP",
        "service": settings.service_name,
        "version": settings.service_version,
    }
    return success_payload(data, "Service is up", trace_id)


@router.get("/neo4j")
def health_neo4j(trace_id: str = Depends(get_trace_id)):
    settings = get_settings()
    connected = connection.ping()
    data = {"connected": connected, "uri": settings.neo4j_uri}
    return success_payload(data, "Neo4j connection OK", trace_id)


@router.get("/readiness")
def readiness(trace_id: str = Depends(get_trace_id)):
    settings = get_settings()

    # Neo4j status
    try:
        neo4j_ok = connection.ping()
        neo4j_status = "UP" if neo4j_ok else "DOWN"
    except Exception:  # noqa: BLE001 - readiness must not raise
        neo4j_status = "DOWN"

    # LLM status
    llm = get_llm_client()
    if not settings.llm_configured or not llm.configured:
        llm_status = "NOT_CONFIGURED"
    else:
        llm_status = "UP"

    ready = neo4j_status == "UP"
    data = {"ready": ready, "neo4j": neo4j_status, "llm": llm_status}
    return success_payload(data, "Readiness check", trace_id)
