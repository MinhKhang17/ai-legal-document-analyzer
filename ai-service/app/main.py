"""Main FastAPI application."""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import router as rag_router
from app.api.rag_api import router as rag_api_router
from app.api.admin_knowledge_base_api import router as admin_knowledge_base_router
from app.api.technology_api import router as technology_router
from app.api.knowledge_api import router as knowledge_router
from app.api.internal_documents_api import router as internal_documents_router
from app.api.risk_knowledge_api import router as risk_knowledge_router
from app.api.v2.contracts_api import router as contracts_v2_router
from app.config import settings
from app.database.neo4j_client import neo4j_client
from app.services.checklist_seed_service import seed_review_checklists
from app.services.embedding_service import embedding_service
from app.services.gemini_service import gemini_service

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager."""
    logger.info("=== Starting AI Service ===")

    try:
        logger.info("Connecting to Neo4j...")
        neo4j_client.connect()

        logger.info("Loading embedding model...")
        embedding_service.load_model()

        logger.info("Initializing Gemini...")
        gemini_initialized = gemini_service.initialize()
        if gemini_initialized:
            logger.info("Gemini LLM initialized successfully")
        else:
            logger.warning("Gemini LLM not configured - using fallback answer generation")

        logger.info("Seeding review checklists...")
        seed_review_checklists()

        logger.info("=== AI Service Started Successfully ===")
    except Exception as e:
        logger.error(f"Failed to start AI Service: {e}")
        raise

    yield

    logger.info("=== Shutting Down AI Service ===")
    neo4j_client.close()
    logger.info("=== AI Service Stopped ===")


app = FastAPI(
    title="Legal RAG Platform - AI Service",
    description="AI service for legal document analysis with RAG capabilities",
    version="2.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(rag_router, tags=["AI Service"])
app.include_router(rag_api_router, tags=["RAG Query"])
app.include_router(admin_knowledge_base_router, tags=["Admin Knowledge Base"])
app.include_router(technology_router, tags=["technologies"])
app.include_router(knowledge_router, tags=["knowledge"])
app.include_router(internal_documents_router, tags=["internal-documents"])
app.include_router(risk_knowledge_router, tags=["risk-knowledge"])
app.include_router(contracts_v2_router, tags=["contracts-v2"])


@app.get("/")
async def root():
    return {
        "service": "Legal RAG Platform - AI Service",
        "version": "2.0.0",
        "status": "running",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=settings.api_host,
        port=settings.api_port,
        reload=True,
    )
