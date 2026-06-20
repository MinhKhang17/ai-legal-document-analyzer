from __future__ import annotations

from app.services.query_builder import build_knowledge_context, build_user_context
from app.services.retrieval_service import RagChunkHit


def build_system_prompt() -> str:
    return (
        "You are a Vietnamese legal contract analysis assistant.\n"
        "Rules:\n"
        "1. Answer only from the provided context.\n"
        "2. Distinguish clearly between user contract content and system legal knowledge base content.\n"
        "3. Do not invent legal articles, law names, or case law.\n"
        "4. If legal context is insufficient, say that the information is insufficient and recommend expert review.\n"
        "5. Use citations exactly in the format [U1], [U2], [K1], [K2].\n"
        "6. Return a risk level: LOW, MEDIUM, HIGH, NEED_EXPERT, or UNKNOWN.\n"
        "7. Prefer grounded, concise answers."
    )


def build_user_prompt(question: str, user_hits: list[RagChunkHit], knowledge_hits: list[RagChunkHit]) -> str:
    user_context = build_user_context(user_hits)
    knowledge_context = build_knowledge_context(knowledge_hits)
    return (
        f"User question:\n{question.strip()}\n\n"
        f"User document context:\n{user_context or '[none]'}\n\n"
        f"System Knowledge Base context:\n{knowledge_context or '[none]'}\n\n"
        "Required answer structure:\n"
        "1. Kết luận ngắn gọn\n"
        "2. Phân tích nội dung trong hợp đồng\n"
        "3. Đối chiếu với Knowledge Base pháp luật\n"
        "4. Mức độ rủi ro\n"
        "5. Nguồn trích dẫn"
    )
