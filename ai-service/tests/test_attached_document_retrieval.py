from app.graph.repository import GraphRepository
from app.schemas import RagQueryRequest
from app.services.rag_query_service import RagQueryService


def test_rag_request_accepts_multiple_attached_document_ids():
    request = RagQueryRequest.model_validate({
        "request_id": "req_1",
        "user_id": "1",
        "workspace_id": "ws_1",
        "question": "compare",
        "attached_document_ids": ["doc_1", "doc_2"],
    })

    assert request.attachedDocumentIds == ["doc_1", "doc_2"]


def test_rag_request_accepts_null_attached_document_ids_for_legacy_backend():
    request = RagQueryRequest.model_validate({
        "request_id": "req_legacy",
        "user_id": "1",
        "workspace_id": "ws_1",
        "question": "legacy query",
        "attached_document_ids": None,
    })

    assert request.attachedDocumentIds is None


def test_empty_attached_document_ids_retrieves_system_kb_only():
    class FakeRetrievalService:
        def __init__(self):
            self.user_search_called = False
            self.knowledge_search_called = False

        def search_user_chunks(self, *args, **kwargs):
            self.user_search_called = True
            return []

        def search_knowledge_chunks(self, *args, **kwargs):
            self.knowledge_search_called = True
            return []

    retrieval = FakeRetrievalService()
    service = RagQueryService(retrieval_service=retrieval)
    request = RagQueryRequest.model_validate({
        "request_id": "req_kb_only",
        "user_id": "1",
        "workspace_id": "ws_1",
        "question": "Quy định pháp luật là gì?",
        "attached_document_ids": [],
    })

    user_hits, _, knowledge_hits = service._retrieve(request)

    assert user_hits == []
    assert knowledge_hits == []
    assert retrieval.user_search_called is False
    assert retrieval.knowledge_search_called is True


def test_repository_restricts_search_to_all_attached_documents(monkeypatch):
    repository = GraphRepository.__new__(GraphRepository)
    captured = {}

    def fake_search(**kwargs):
        captured.update(kwargs)
        return []

    monkeypatch.setattr(repository, "_search_chunks_with_filter", fake_search)
    repository.search_user_chunks(
        [0.1],
        user_id="1",
        workspace_id="ws_1",
        document_ids=["doc_1", "doc_2"],
    )

    assert captured["metadata_filter"]["document_id"] == {"doc_1", "doc_2"}
    base_metadata = {"source_type": "USER_DOCUMENT", "user_id": "1", "workspace_id": "ws_1"}
    assert repository._matches_metadata({**base_metadata, "document_id": "doc_1"}, captured["metadata_filter"])
    assert not repository._matches_metadata({**base_metadata, "document_id": "doc_other"}, captured["metadata_filter"])
