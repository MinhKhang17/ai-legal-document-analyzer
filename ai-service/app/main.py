"""Main FastAPI application."""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.database.neo4j_client import neo4j_client
from app.services.embedding_service import embedding_service
from app.services.gemini_service import gemini_service
from app.services.checklist_seed_service import seed_review_checklists
from app.api.routes import router

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager."""
    # Startup
    logger.info("=== Starting AI Service ===")
    
    try:
        # Connect to Neo4j
        logger.info("Connecting to Neo4j...")
        neo4j_client.connect()
        
        # Load embedding model
        logger.info("Loading embedding model...")
        embedding_service.load_model()
        
        # Initialize Gemini
        logger.info("Initializing Gemini...")
        gemini_initialized = gemini_service.initialize()
        if gemini_initialized:
            logger.info("✅ Gemini LLM initialized successfully")
        else:
            logger.warning("⚠️  Gemini LLM not configured - using fallback answer generation")
        
        # Seed review checklists
        logger.info("Seeding review checklists...")
        seed_review_checklists()
        
        logger.info("=== AI Service Started Successfully ===")
        
    except Exception as e:
        logger.error(f"Failed to start AI Service: {e}")
        raise
    
    yield
    
    # Shutdown
    logger.info("=== Shutting Down AI Service ===")
    neo4j_client.close()
    logger.info("=== AI Service Stopped ===")


# Create FastAPI app
app = FastAPI(
    title="Legal RAG Platform - AI Service",
    description="AI service for legal document analysis with RAG capabilities",
    version="2.0.0",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routes
app.include_router(router, tags=["AI Service"])


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "Legal RAG Platform - AI Service",
        "version": "2.0.0",
        "status": "running"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.api_host,
        port=settings.api_port,
        reload=True
    )
