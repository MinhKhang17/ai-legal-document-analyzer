from __future__ import annotations

from fastapi import APIRouter, File, Form, UploadFile
from pydantic import BaseModel, Field

from app.models.knowledge_models import IngestionResult, RetrievedChunk
from app.services.knowledge_service import KnowledgeService


class SearchChunksRequest(BaseModel):
    query: str = Field(..., description="Search query used to retrieve relevant chunks")
    top_k: int = Field(default=5, ge=1, le=20)


router = APIRouter(prefix="/knowledge", tags=["knowledge"])
service = KnowledgeService()


@router.get("/supported-formats")
def supported_formats() -> list[str]:
    return service.supported_formats()


@router.post("/ingest")
async def ingest_document(
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
) -> IngestionResult:
    return await service.ingest_upload(file=file, title=title)


@router.post("/search")
def search_chunks(payload: SearchChunksRequest) -> list[RetrievedChunk]:
    return service.search(payload.query, top_k=payload.top_k)
