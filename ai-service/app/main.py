from fastapi import FastAPI

from app.api.technology_api import router

app = FastAPI(title="Neo4j AI Service")

app.include_router(router)


@app.get("/health")
def health():
    return {"status": "ok"}
