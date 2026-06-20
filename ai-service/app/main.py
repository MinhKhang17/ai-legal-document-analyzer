from dotenv import load_dotenv
from fastapi import FastAPI
import logging
import os

load_dotenv()

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)

from app.api.knowledge_api import router as knowledge_router
from app.api.internal_documents_api import router as internal_documents_router
from app.api.rag_api import router as rag_router
from app.api.risk_knowledge_api import router as risk_knowledge_router
from app.api.technology_api import router as technology_router
from app.api.v2.contracts_api import router as contracts_v2_router
from app.graph.connection import close_driver

app = FastAPI(title="Neo4j AI Service")

app.include_router(technology_router)
app.include_router(knowledge_router)
app.include_router(internal_documents_router)
app.include_router(rag_router)
app.include_router(risk_knowledge_router)
app.include_router(contracts_v2_router)


@app.on_event("shutdown")
def shutdown() -> None:
    close_driver()


@app.get("/health")
def health():
    return {
        "status": "ok",
        "neo4j_uri": os.getenv("NEO4J_URI", ""),
    }
