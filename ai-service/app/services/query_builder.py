from __future__ import annotations

from app.services.retrieval_service import RagChunkHit


def build_user_context(user_hits: list[RagChunkHit]) -> str:
    if not user_hits:
        return ""
    parts = []
    for hit in user_hits:
        if hit.chunkText.strip():
            source = f"File: {hit.fileName}" if hit.fileName else "User Document"
            parts.append(f"[{hit.citationId}] (Source: {source})\n{hit.chunkText}".strip())
    return "\n\n".join(parts).strip()


def build_legal_search_query(question: str, user_hits: list[RagChunkHit]) -> str:
    user_context = build_user_context(user_hits)
    if not user_context:
        return question.strip()
    return f"{question}\n\nRelevant user contract context:\n{user_context}".strip()


def build_knowledge_context(knowledge_hits: list[RagChunkHit]) -> str:
    if not knowledge_hits:
        return ""
    parts = []
    for hit in knowledge_hits:
        if hit.chunkText.strip():
            source_parts = []
            if hit.lawName:
                source_parts.append(hit.lawName)
            if hit.lawCode:
                source_parts.append(hit.lawCode)
            if hit.fileName:
                source_parts.append(hit.fileName)
            
            source = ", ".join(source_parts) if source_parts else "System Knowledge Base"
            parts.append(f"[{hit.citationId}] (Source: {source})\n{hit.chunkText}".strip())
    return "\n\n".join(parts).strip()
