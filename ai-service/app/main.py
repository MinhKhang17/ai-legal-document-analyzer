"""LexiGuard AI - ai-service FastAPI application.

Wires together routers (/health, /graph, /rag, /legal), CORS, global exception
handlers that emit the standard JSON envelope, and the Neo4j driver lifecycle.
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api import documents as documents_router
from app.api import graph as graph_router
from app.api import health as health_router
from app.api import legal_analysis as legal_router
from app.api import rag as rag_router
from app.core.config import get_settings
from app.core.errors import AppError, ErrorCode
from app.graph.connection import close_driver
from app.models.common import error_payload

logger = logging.getLogger("ai-service")
logging.basicConfig(level=logging.INFO)

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting %s v%s", settings.service_name, settings.service_version)
    yield
    close_driver()
    logger.info("Shutdown complete")


app = FastAPI(
    title="LexiGuard AI - AI Service",
    description="Legal-Tech AI/RAG/Graph service for contract analysis and comparison.",
    version=settings.service_version,
    lifespan=lifespan,
)

# --- CORS ---------------------------------------------------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- Helpers ------------------------------------------------------------------
def _trace_id(request: Request) -> str:
    return getattr(request.state, "trace_id", None) or request.headers.get(
        "X-Trace-Id", "n/a"
    )


# --- Exception handlers (standard envelope) -----------------------------------
@app.exception_handler(AppError)
async def app_error_handler(request: Request, exc: AppError):
    trace_id = _trace_id(request)
    logger.warning("AppError [%s]: %s", exc.code.value, exc.message)
    return JSONResponse(
        status_code=exc.status_code,
        content=error_payload(exc.code.value, exc.message, trace_id, exc.details),
    )


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, exc: RequestValidationError):
    trace_id = _trace_id(request)
    return JSONResponse(
        status_code=422,
        content=error_payload(
            ErrorCode.VALIDATION_ERROR.value,
            "Request validation failed",
            trace_id,
            {"errors": exc.errors()},
        ),
    )


@app.exception_handler(Exception)
async def unhandled_error_handler(request: Request, exc: Exception):
    trace_id = _trace_id(request)
    logger.exception("Unhandled error: %s", exc)
    return JSONResponse(
        status_code=500,
        content=error_payload(
            ErrorCode.INTERNAL_ERROR.value,
            "Internal server error",
            trace_id,
            {"cause": str(exc)},
        ),
    )


# --- Routers ------------------------------------------------------------------
# A single configurable prefix (default /api/ai) is applied to every router so
# the Spring Boot gateway can route all AI endpoints with one rule.
_prefix = settings.api_prefix.rstrip("/")
app.include_router(health_router.router, prefix=_prefix)
app.include_router(graph_router.router, prefix=_prefix)
app.include_router(rag_router.router, prefix=_prefix)
app.include_router(legal_router.router, prefix=_prefix)
app.include_router(documents_router.router, prefix=_prefix)


@app.get("/", tags=["root"])
def root():
    base = _prefix or ""
    return {
        "service": settings.service_name,
        "version": settings.service_version,
        "docs": "/docs",
        "apiPrefix": _prefix,
        "endpoints": [
            f"{base}/health",
            f"{base}/graph",
            f"{base}/rag",
            f"{base}/legal",
            f"{base}/documents",
        ],
    }
