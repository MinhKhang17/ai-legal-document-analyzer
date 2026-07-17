from __future__ import annotations

from typing import Any, Mapping


def is_published_system_kb(
    metadata: Mapping[str, Any],
    *,
    source_type: str | None = None,
    require_ingest_v2: bool = False,
) -> bool:
    """Return whether a SYSTEM_KB chunk is safe to expose to RAG.

    Lifecycle metadata was introduced after the original knowledge base had
    already been ingested.  Legacy chunks have neither ``visibility`` nor
    ``active``; their previous ACTIVE + ADMIN state is therefore preserved.
    Once either lifecycle field exists, both must explicitly mark the document
    public and active so newly ingested private documents cannot leak.
    """
    resolved_source_type = str(metadata.get("source_type") or source_type or "").upper()
    if resolved_source_type != "SYSTEM_KB":
        return False
    if str(metadata.get("effective_status") or "").upper() != "ACTIVE":
        return False
    if str(metadata.get("ingested_by_role") or "").upper() != "ADMIN":
        return False
    if require_ingest_v2 and str(metadata.get("ingest_source") or "").upper() != "INGEST_V2":
        return False

    has_visibility = "visibility" in metadata and metadata.get("visibility") is not None
    has_active = "active" in metadata and metadata.get("active") is not None
    if not has_visibility and not has_active:
        return True

    return str(metadata.get("visibility") or "").upper() == "PUBLIC" and metadata.get("active") is True
