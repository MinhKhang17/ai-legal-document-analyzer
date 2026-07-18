from types import SimpleNamespace

from app.graph.repository import GraphRepository
from app.schemas import RagQueryRequest
from app.services.rag_query_service import RagQueryService
from app.services.retrieval_service import RagChunkHit
from app.services.conversation_context import UserSource, merge_snapshot_with_current_hits


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


def test_pure_contract_summary_does_not_query_legal_kb():
    class FakeRetrievalService:
        def __init__(self):
            self.knowledge_search_called = False

        def search_user_chunks(self, *args, **kwargs):
            return [RagChunkHit(
                citationId="USER-1",
                sourceType="USER_DOCUMENT",
                score=0.9,
                chunkText="Bên thuê thanh toán vào ngày 05 hàng tháng.",
                documentId="doc_1",
                fileName="hop-dong-thue.pdf",
            )]

        def search_knowledge_chunks(self, *args, **kwargs):
            self.knowledge_search_called = True
            return []

    retrieval = FakeRetrievalService()
    service = RagQueryService(retrieval_service=retrieval)
    request = RagQueryRequest.model_validate({
        "request_id": "req_summary",
        "user_id": "1",
        "workspace_id": "ws_1",
        "question": "Tóm tắt hợp đồng thuê nhà",
        "attached_document_ids": ["doc_1"],
    })

    _, _, knowledge_hits = service._retrieve(request)

    assert knowledge_hits == []
    assert retrieval.knowledge_search_called is False


def test_summary_with_legal_risk_request_still_queries_legal_kb():
    class FakeRetrievalService:
        def __init__(self):
            self.knowledge_search_called = False

        def search_user_chunks(self, *args, **kwargs):
            return [RagChunkHit(
                citationId="USER-1",
                sourceType="USER_DOCUMENT",
                score=0.9,
                chunkText="Điều khoản phạt chậm thanh toán.",
                documentId="doc_1",
                fileName="hop-dong-thue.pdf",
            )]

        def search_knowledge_chunks(self, *args, **kwargs):
            self.knowledge_search_called = True
            return []

    retrieval = FakeRetrievalService()
    service = RagQueryService(retrieval_service=retrieval)
    request = RagQueryRequest.model_validate({
        "request_id": "req_legal_summary",
        "user_id": "1",
        "workspace_id": "ws_1",
        "question": "Tóm tắt và đánh giá rủi ro pháp lý của hợp đồng",
        "attached_document_ids": ["doc_1"],
    })

    service._retrieve(request)

    assert retrieval.knowledge_search_called is True


def test_singular_reference_with_multiple_active_documents_requires_selection():
    service = RagQueryService(retrieval_service=object())
    request = RagQueryRequest.model_validate({
        "request_id": "req_ambiguous",
        "user_id": "1",
        "workspace_id": "ws_1",
        "question": "Tóm tắt hợp đồng này",
        "attached_document_ids": ["doc_1", "doc_2"],
    })

    assert service._needs_document_selection(request, []) is True

    focused_request = request.model_copy(update={"documentId": "doc_2"})
    assert service._needs_document_selection(focused_request, []) is False


def test_follow_up_snapshot_drops_documents_no_longer_active():
    snapshot = SimpleNamespace(
        userSources=(
            UserSource(
                id="USER-1",
                documentId="doc_removed",
                type="uploaded_document",
                title="old-contract.pdf",
                clauseName=None,
                content="Old contract content",
                page=1,
                section=None,
            ),
        ),
        kbSources=(),
    )
    current_hit = RagChunkHit(
        citationId="USER-1",
        sourceType="USER_DOCUMENT",
        score=0.9,
        chunkText="Current contract content",
        documentId="doc_active",
        fileName="current-contract.pdf",
    )

    user_hits, _ = merge_snapshot_with_current_hits(
        snapshot,
        [current_hit],
        [],
        allowed_document_ids={"doc_active"},
    )

    assert [hit.documentId for hit in user_hits] == ["doc_active"]
    assert all(hit.fileName != "old-contract.pdf" for hit in user_hits)
