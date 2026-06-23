from __future__ import annotations

import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.schemas import RagQueryRequest, RagQueryResponse
from app.services.llm_client import LlmResponse
from app.services.rag_query_service import RagQueryService
from app.services.retrieval_service import RagChunkHit


class _FakeRetrievalService:
    def search_user_chunks(self, question: str, user_id: str, workspace_id: str, top_k: int):
        return [
            RagChunkHit(
                citationId="U1",
                sourceType="USER_DOCUMENT",
                score=0.91,
                chunkText="Điều 4: Bên thuê phải thanh toán đúng hạn.",
                documentId="doc-1",
                workspaceId=workspace_id,
                userId=user_id,
                fileName="hop-dong.pdf",
            )
        ]

    def search_knowledge_chunks(self, legal_search_query: str, top_k: int):
        return [
            RagChunkHit(
                citationId="K1",
                sourceType="SYSTEM_KB",
                score=0.88,
                chunkText="Bộ luật Dân sự quy định nghĩa vụ thực hiện đúng cam kết.",
                knowledgeDocumentId="law-1",
                lawName="Bộ luật Dân sự",
                legalDomain="contract_law",
            )
        ]


class _FakeLlmClient:
    def generate(self, *, system_prompt: str, user_prompt: str):
        return LlmResponse(answer="Kết luận: cần kiểm tra điều khoản thanh toán [U1] [K1].", risk_level="MEDIUM")


class RagQueryTests(unittest.TestCase):
    def test_rag_query_service_builds_response(self) -> None:
        service = RagQueryService(
            retrieval_service=_FakeRetrievalService(),
            llm_client=_FakeLlmClient(),
        )
        response = service.query(
            RagQueryRequest(
                requestId="req-1",
                userId="user-1",
                workspaceId="ws-1",
                chatSessionId="chat-1",
                question="Hợp đồng có rủi ro gì?",
            )
        )

        self.assertEqual(response.requestId, "req-1")
        self.assertEqual(response.chatSessionId, "chat-1")
        self.assertEqual(response.riskLevel, "MEDIUM")
        self.assertEqual(response.retrievedUserChunks, 1)
        self.assertEqual(response.retrievedKnowledgeChunks, 1)
        self.assertEqual([item.citationId for item in response.citations], ["U1", "K1"])

    def test_rag_query_endpoint_returns_response(self) -> None:
        fake_service = SimpleNamespace(
            query=lambda payload: RagQueryResponse(
                requestId=payload.requestId,
                chatSessionId=payload.chatSessionId,
                answer="OK",
                riskLevel="LOW",
                citations=[],
                retrievedUserChunks=0,
                retrievedKnowledgeChunks=0,
            )
        )
        with patch("app.api.rag_api.get_rag_query_service", return_value=fake_service):
            client = TestClient(app)
            response = client.post(
                "/internal/rag/query",
                json={
                    "requestId": "req-1",
                    "userId": "user-1",
                    "workspaceId": "ws-1",
                    "chatSessionId": "chat-1",
                    "question": "Hợp đồng có rủi ro gì?",
                    "topKUserChunks": 5,
                    "topKKnowledgeChunks": 5,
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["answer"], "OK")
        self.assertEqual(response.json()["riskLevel"], "LOW")

    def test_rag_preview_endpoint_returns_chunks(self) -> None:
        fake_service = SimpleNamespace(
            preview=lambda payload: SimpleNamespace(
                requestId=payload.requestId,
                chatSessionId=payload.chatSessionId,
                question=payload.question,
                legalSearchQuery=f"{payload.question}\n\nRelevant user contract context:\n[U1] demo",
                userChunks=[],
                knowledgeChunks=[],
                retrievedUserChunks=0,
                retrievedKnowledgeChunks=0,
            )
        )
        with patch("app.api.rag_api.get_rag_query_service", return_value=fake_service):
            client = TestClient(app)
            response = client.post(
                "/internal/rag/preview",
                json={
                    "requestId": "req-1",
                    "userId": "user-1",
                    "workspaceId": "ws-1",
                    "chatSessionId": "chat-1",
                    "question": "Hợp đồng có rủi ro gì?",
                    "topKUserChunks": 5,
                    "topKKnowledgeChunks": 5,
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertIn("legalSearchQuery", response.json())


if __name__ == "__main__":
    unittest.main()
