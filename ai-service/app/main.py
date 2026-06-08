from fastapi import FastAPI

from app.api.knowledge_api import router as knowledge_router
from app.api.technology_api import router as technology_router
from app.graph.connection import close_driver

app = FastAPI(title="Neo4j AI Service")

app.include_router(technology_router)
app.include_router(knowledge_router)


@app.on_event("shutdown")
def shutdown() -> None:
    close_driver()


@app.get("/health")
def health():
    return {"status": "ok"}
