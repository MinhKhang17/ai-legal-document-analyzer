import os

from fastapi import FastAPI


app = FastAPI(title="AI Service", version="1.0.0")


@app.get("/health")
def health_check() -> dict[str, str]:
    return {
        "status": "ok",
        "neo4j_uri": os.getenv("NEO4J_URI", ""),
    }