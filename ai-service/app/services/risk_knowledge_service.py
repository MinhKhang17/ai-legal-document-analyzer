from __future__ import annotations

from app.core.config import settings
from app.graph.repository import GraphRepository
from app.services.knowledge_service import KnowledgeService, KnowledgeServiceV2


class RiskKnowledgeService(KnowledgeService):
    def __init__(self, repository: GraphRepository | None = None) -> None:
        super().__init__(
            repository=repository or GraphRepository(vector_index_name=settings.legal_vector_index_name),
            document_metadata={
                "knowledge_scope": "risk_training",
                "vector_index_name": settings.legal_vector_index_name,
            },
        )


class RiskKnowledgeServiceV2(KnowledgeServiceV2):
    def __init__(self, repository: GraphRepository | None = None) -> None:
        super().__init__(
            repository=repository or GraphRepository(vector_index_name=settings.legal_vector_index_name),
            document_metadata={
                "knowledge_scope": "risk_training",
                "vector_index_name": settings.legal_vector_index_name,
            },
        )
