from __future__ import annotations

from pathlib import Path
from functools import lru_cache

from fastapi import APIRouter, BackgroundTasks, File, UploadFile

from app.core.config import settings
from app.schemas import DocumentImportResponse, DocumentProcessAcceptedResponse, DocumentProcessRequest
from app.services.document_processor import DocumentProcessor


router = APIRouter(prefix="/internal/documents", tags=["internal-documents"])


@lru_cache(maxsize=1)
def get_processor() -> DocumentProcessor:
    return DocumentProcessor()


@router.post("/import", response_model=DocumentImportResponse)
async def import_document(file: UploadFile = File(...)) -> DocumentImportResponse:
    import_dir = settings.document_import_dir
    import_dir.mkdir(parents=True, exist_ok=True)

    filename = Path(file.filename or "uploaded_file").name
    target_path = import_dir / filename

    size_bytes = 0
    try:
        with target_path.open("wb") as handle:
            while True:
                chunk = await file.read(1024 * 1024)
                if not chunk:
                    break
                size_bytes += len(chunk)
                handle.write(chunk)
    finally:
        await file.close()

    return DocumentImportResponse(
        fileName=filename,
        fileType=target_path.suffix.lower().lstrip("."),
        filePath=str(target_path),
        sizeBytes=size_bytes,
    )


@router.post("/process", response_model=DocumentProcessAcceptedResponse, status_code=202)
def process_document(
    payload: DocumentProcessRequest,
    background_tasks: BackgroundTasks,
) -> DocumentProcessAcceptedResponse:
    background_tasks.add_task(get_processor().process, payload)
    return DocumentProcessAcceptedResponse(jobId=payload.jobId, documentId=payload.documentId)
