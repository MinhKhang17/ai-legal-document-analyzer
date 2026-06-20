from __future__ import annotations

from app.services.retrieval_service import RagChunkHit


def build_user_context(user_hits: list[RagChunkHit]) -> str:
    if not user_hits:
        return ""
    return "\n\n".join(
        f"[{hit.citationId}] {hit.chunkText}".strip()
        for hit in user_hits
        if hit.chunkText.strip()
    ).strip()


def build_legal_search_query(question: str, user_hits: list[RagChunkHit]) -> str:
    user_context = build_user_context(user_hits)
    if not user_context:
        return question.strip()
    return f"{question}\n\nRelevant user contract context:\n{user_context}".strip()


def build_knowledge_context(knowledge_hits: list[RagChunkHit]) -> str:
    if not knowledge_hits:
        return ""
    return "\n\n".join(
        f"[{hit.citationId}] {hit.chunkText}".strip()
        for hit in knowledge_hits
        if hit.chunkText.strip()
    ).strip()
