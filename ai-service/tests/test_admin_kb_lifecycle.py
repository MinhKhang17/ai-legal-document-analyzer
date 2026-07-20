import json
from types import SimpleNamespace

from app.api import knowledge_api
from app.graph.repository import GraphRepository
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


def test_async_ingest_callback_contains_actual_ai_document_id(monkeypatch, tmp_path) -> None:
    callback_payloads = []
    source_file = tmp_path / "job.pdf"
    source_file.write_bytes(b"pdf")

    class FakeCallback:
        def post_json(self, _url, payload):
            callback_payloads.append(payload)
            return True

    class FakeService:
        def __init__(self, **_kwargs):
            pass

        def ingest_file(self, *_args, **_kwargs):
            return SimpleNamespace(total_chunks=3, document_id="actual-ai-document-id")

    monkeypatch.setattr(knowledge_api, "CallbackClient", FakeCallback)
    monkeypatch.setattr(knowledge_api, "KnowledgeServiceV2", FakeService)

    knowledge_api._run_admin_ingest_job(
        file_path=str(source_file),
        filename="law.pdf",
        title="Law",
        job_id="job-1",
        callback_url="http://backend/callback",
        knowledge_base_id="kb-1",
        ingested_by_user_id="1",
    )

    assert callback_payloads[-1]["status"] == "INGESTED"
    assert callback_payloads[-1]["neo4jDocumentId"] == "actual-ai-document-id"


def test_lifecycle_lookup_uses_actual_document_metadata_not_knowledge_base_id() -> None:
    calls = []

    class FakeResult:
        def consume(self):
            return None

    class FakeSession:
        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def run(self, query, **params):
            calls.append((query, params))
            return FakeResult()

    class FakeDriver:
        def session(self):
            return FakeSession()

    repository = object.__new__(GraphRepository)
    repository.driver = FakeDriver()
    repository.list_chunks = lambda: [{
        "chunk_id": "chunk-1",
        "metadata_json": json.dumps({
            "knowledge_document_id": "actual-ai-document-id",
            "knowledge_base_id": "kb-1",
        }),
    }]

    assert repository.update_document_lifecycle(
        "actual-ai-document-id", knowledge_base_id="kb-1",
        visibility="PUBLIC", active=True, published_at="now"
    ) == 1
    assert repository.update_document_lifecycle(
        "kb-1", knowledge_base_id="kb-1",
        visibility="PUBLIC", active=True, published_at="now"
    ) == 0
    assert len(calls) == 1
