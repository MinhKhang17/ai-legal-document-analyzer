from types import SimpleNamespace

from app.models.knowledge_models import RetrievedChunk
from app.services.knowledge_service import KnowledgeServiceV2
from app.services.retrieval_service import RetrievalService


def _chunk(chunk_id: str, *, visibility: str, active: bool) -> RetrievedChunk:
    return RetrievedChunk(
        chunk_id=chunk_id,
        text=chunk_id,
        score=0.9,
        title=chunk_id,
        source_type="SYSTEM_KB",
        metadata={
            "source_type": "SYSTEM_KB",
            "ingest_source": "INGEST_V2",
            "effective_status": "ACTIVE" if active else "INACTIVE",
            "visibility": visibility,
            "active": active,
            "ingested_by_role": "ADMIN",
        },
    )


def test_new_admin_ingest_is_private_and_inactive() -> None:
    service = KnowledgeServiceV2(repository=SimpleNamespace())

    assert service.document_metadata["visibility"] == "PRIVATE"
    assert service.document_metadata["active"] is False
    assert service.document_metadata["effective_status"] == "INACTIVE"
    assert service.document_metadata["ingest_status"] == "INGESTED"


def test_system_kb_context_excludes_private_or_inactive_chunks() -> None:
    chunks = [
        _chunk("private", visibility="PRIVATE", active=True),
        _chunk("inactive", visibility="PUBLIC", active=False),
        _chunk("public-active", visibility="PUBLIC", active=True),
    ]
    repository = SimpleNamespace(search_knowledge_chunks=lambda *_, **__: chunks)
    embedding_service = SimpleNamespace(embed_text=lambda _: [0.1, 0.2])
    service = RetrievalService(repository=repository, embedding_service=embedding_service)

    hits = service.search_knowledge_chunks("query", top_k=5)

    assert [hit.rawChunkId for hit in hits] == ["public-active"]


def test_legacy_active_admin_chunks_without_lifecycle_metadata_remain_searchable() -> None:
    legacy_chunk = RetrievedChunk(
        chunk_id="legacy-public",
        text="Legacy public legal reference",
        score=0.9,
        title="Legacy",
        source_type="SYSTEM_KB",
        metadata={
            "source_type": "SYSTEM_KB",
            "ingest_source": "INGEST_V2",
            "effective_status": "ACTIVE",
            "ingested_by_role": "ADMIN",
        },
    )
    repository = SimpleNamespace(search_knowledge_chunks=lambda *_, **__: [legacy_chunk])
    embedding_service = SimpleNamespace(embed_text=lambda _: [0.1, 0.2])
    service = RetrievalService(repository=repository, embedding_service=embedding_service)

    hits = service.search_knowledge_chunks("query", top_k=5)

    assert [hit.rawChunkId for hit in hits] == ["legacy-public"]
