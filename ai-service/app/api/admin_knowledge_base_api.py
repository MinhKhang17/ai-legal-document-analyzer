from __future__ import annotations

import json
import logging
from collections import defaultdict
from pathlib import Path

from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field

from app.graph.repository import GraphRepository


class IngestedDocumentVersionResponse(BaseModel):
    versionId: str | None = None
    versionLabel: str | None = None
    effectiveFrom: str | None = None
    effectiveTo: str | None = None
    visibility: str | None = None
    active: bool | None = None
    ingestStatus: str | None = None
    chunkCount: int = 0
    embeddedCount: int = 0
    sourceFileId: str | None = None
    contentHash: str | None = None
    ingestedAt: str | None = None
    publishedAt: str | None = None
    ingestedBy: str | None = None
    errorMessage: str | None = None


class LifecycleUpdateRequest(BaseModel):
    public: bool


class IngestedDocumentResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    legalDocumentId: str
    title: str | None = None
    documentCode: str | None = None
    versions: list[IngestedDocumentVersionResponse] = Field(default_factory=list)


class PageResponse(BaseModel):
    items: list[IngestedDocumentResponse] = Field(default_factory=list)
    page: int
    size: int
    totalItems: int
    totalPages: int


router = APIRouter(prefix="/ai/admin/knowledge-bases", tags=["admin-knowledge-base"])
logger = logging.getLogger(__name__)


def _parse_metadata(metadata_json: str | None) -> dict[str, object]:
    if not metadata_json:
        return {}
    try:
        parsed = json.loads(metadata_json)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        return {}


def _safe_str(value: object | None) -> str | None:
    text = str(value).strip() if value is not None else ""
    return text or None


def _compute_status(chunk_count: int, metadata: dict[str, object]) -> str:
    stored_status = _safe_str(metadata.get("ingest_status"))
    if stored_status:
        return stored_status.upper()
    if chunk_count <= 0:
        return "UNKNOWN"
    if metadata.get("embedding_text") is None:
        return "PROCESSING"
    return "INGESTED"


def _matches(value: str | None, keyword: str | None) -> bool:
    if not keyword:
        return True
    if not value:
        return False
    return keyword.lower() in value.lower()


@router.get("/{kb_id}/ingested-documents", response_model=PageResponse)
def get_ingested_documents(
    kb_id: str,
    keyword: str | None = Query(default=None),
    ingest_status: str | None = Query(default=None, alias="ingestStatus"),
    visibility: str | None = Query(default=None),
    page: int = Query(default=0, ge=0),
    size: int = Query(default=10, ge=1, le=100),
) -> PageResponse:
    repo = GraphRepository()
    try:
        chunk_rows = repo.list_chunks()
    except Exception as exc:
        logger.warning("Failed to load ingested documents for kb_id=%s: %s", kb_id, exc)
        return PageResponse(items=[], page=page, size=size, totalItems=0, totalPages=0)

    grouped: dict[str, dict[str, object]] = defaultdict(lambda: {
        "legalDocumentId": "",
        "title": None,
        "documentCode": None,
        "versions": [],
        "chunkCount": 0,
        "embeddedCount": 0,
        "contentHash": None,
        "visibility": None,
        "sourceFileId": None,
    })

    for row in chunk_rows:
        metadata = _parse_metadata(row.get("metadata_json"))
        # TODO: once KB ingestion stores kbId in vector metadata, filter here by kb_id directly.
        document_id = _safe_str(metadata.get("document_id")) or _safe_str(row.get("source_path")) or _safe_str(row.get("chunk_id"))
        if not document_id:
            continue

        title = _safe_str(metadata.get("document_title")) or _safe_str(metadata.get("file_name")) or _safe_str(row.get("title"))
        document_code = _safe_str(metadata.get("document_code")) or (
            Path(str(metadata.get("file_name") or "")).stem if metadata.get("file_name") else None
        )
        source_file_id = _safe_str(metadata.get("document_id")) or document_id
        content_hash = _safe_str(metadata.get("file_hash"))
        visibility_value = _safe_str(metadata.get("visibility_scope") or metadata.get("visibility"))
        active_value = metadata.get("active") if isinstance(metadata.get("active"), bool) else None
        chunk_text = _safe_str(row.get("text")) or ""

        entry = grouped[document_id]
        entry["legalDocumentId"] = document_id
        entry["title"] = title or entry["title"]
        entry["documentCode"] = document_code or entry["documentCode"]
        entry["chunkCount"] = int(entry["chunkCount"]) + 1
        entry["embeddedCount"] = int(entry["embeddedCount"]) + 1
        entry["contentHash"] = content_hash or entry["contentHash"]
        entry["visibility"] = visibility_value or entry["visibility"]
        entry["sourceFileId"] = source_file_id

        version = IngestedDocumentVersionResponse(
            versionId=document_id,
            versionLabel=title or document_code or document_id,
            effectiveFrom=None,
            effectiveTo=None,
            visibility=visibility_value,
            active=active_value,
            ingestStatus=_compute_status(int(entry["chunkCount"]), metadata),
            chunkCount=int(entry["chunkCount"]),
            embeddedCount=int(entry["embeddedCount"]),
            sourceFileId=source_file_id,
            contentHash=content_hash,
            ingestedAt=_safe_str(metadata.get("ingested_at")),
            publishedAt=_safe_str(metadata.get("published_at")),
            ingestedBy=_safe_str(metadata.get("ingested_by_user_id")),
            errorMessage=_safe_str(metadata.get("error_message")),
        )
        entry["versions"] = [version]
        entry["_text"] = f"{title or ''} {document_code or ''} {document_id or ''} {content_hash or ''} {visibility_value or ''} {chunk_text}"

    documents = []
    for entry in grouped.values():
        if not entry["legalDocumentId"]:
            continue
        version = entry["versions"][0] if entry["versions"] else None
        if ingest_status and version and (version.ingestStatus or "").lower() != ingest_status.lower():
            continue
        if visibility and (version.visibility or "").lower() != visibility.lower():
            continue
        if keyword and keyword.lower() not in str(entry.get("_text") or "").lower():
            continue
        documents.append(
            IngestedDocumentResponse(
                legalDocumentId=str(entry["legalDocumentId"]),
                title=entry["title"],
                documentCode=entry["documentCode"],
                versions=[version] if version else [],
            )
        )

    documents.sort(key=lambda item: (item.title or item.documentCode or item.legalDocumentId).lower())
    total_items = len(documents)
    total_pages = (total_items + size - 1) // size if total_items else 0
    start = min(page * size, total_items)
    end = min(start + size, total_items)
    items = documents[start:end]

    return PageResponse(
        items=items,
        page=page,
        size=size,
        totalItems=total_items,
        totalPages=total_pages,
    )


@router.put("/{kb_id}/ingested-documents/{document_id}/lifecycle", response_model=IngestedDocumentVersionResponse)
def update_document_lifecycle(
    kb_id: str,
    document_id: str,
    request: LifecycleUpdateRequest,
) -> IngestedDocumentVersionResponse:
    visibility = "PUBLIC" if request.public else "PRIVATE"
    published_at = datetime.now(timezone.utc).isoformat() if request.public else None
    repo = GraphRepository()
    try:
        updated = repo.update_document_lifecycle(
            document_id,
            knowledge_base_id=kb_id,
            visibility=visibility,
            active=request.public,
            published_at=published_at,
        )
    except Exception as exc:
        logger.warning("Failed to update KB lifecycle kb_id=%s document_id=%s: %s", kb_id, document_id, exc)
        raise HTTPException(status_code=503, detail="Knowledge store is unavailable") from exc
    if updated == 0:
        raise HTTPException(status_code=404, detail="Ingested document not found")
    return IngestedDocumentVersionResponse(
        versionId=document_id,
        sourceFileId=document_id,
        visibility=visibility,
        active=request.public,
        ingestStatus="INGESTED",
        publishedAt=published_at,
    )
