from __future__ import annotations

import re

from app.services.retrieval_service import RagChunkHit


_LEGAL_QUERY_EXPANSIONS = {
    "đặt cọc": "đặt cọc bảo đảm giao kết thực hiện nghĩa vụ hợp đồng phạt cọc hoàn trả tiền cọc",
    "tiền cọc": "đặt cọc bảo đảm giao kết thực hiện nghĩa vụ hợp đồng phạt cọc hoàn trả tiền cọc",
    "thanh toán": "nghĩa vụ thanh toán thời hạn chậm trả lãi chậm trả phương thức thanh toán",
    "trả góp": "trả chậm trả dần trả góp kỳ hạn thanh toán lãi chậm trả",
    "chấm dứt": "đơn phương chấm dứt hợp đồng vi phạm nghĩa vụ báo trước hậu quả bồi thường",
    "tăng giá": "Điều 473 Bộ luật Dân sự giá thuê tài sản do các bên thỏa thuận điều chỉnh giá đơn phương thay đổi hợp đồng",
    "quyền lợi": "quyền nghĩa vụ các bên thực hiện hợp đồng vi phạm bồi thường",
    "bồi thường": "bồi thường thiệt hại vi phạm nghĩa vụ trách nhiệm dân sự",
    "phạt": "phạt vi phạm hợp đồng mức phạt bồi thường thiệt hại",
}


def build_user_context(user_hits: list[RagChunkHit]) -> str:
    if not user_hits:
        return ""
    parts = []
    for hit in user_hits:
        if hit.chunkText.strip():
            metadata_lines = [
                f"File: {hit.fileName or 'User Document'}",
                f"Page: {hit.pageNumber}" if hit.pageNumber is not None else "",
                f"Section: {hit.sectionTitle}" if hit.sectionTitle else "",
            ]
            metadata_text = "\n".join(line for line in metadata_lines if line)
            parts.append(f"[{hit.citationId}]\n{metadata_text}\nContent: {hit.chunkText}".strip())
    return "\n\n".join(parts).strip()


def extract_recent_user_history(
    chat_history: str | None,
    *,
    max_messages: int = 4,
    max_chars: int = 1600,
) -> str:
    if not chat_history:
        return ""
    matches = re.findall(
        r"(?:^|\n\n)USER:\s*(.*?)(?=\n\n(?:USER|ASSISTANT|SYSTEM):|\Z)",
        chat_history,
        flags=re.DOTALL | re.IGNORECASE,
    )
    messages = [re.sub(r"\s+", " ", message).strip() for message in matches if message.strip()]
    recent = messages[-max_messages:]
    context = "\n".join(recent)
    return context[-max_chars:]


def build_legal_search_query(
    question: str,
    user_hits: list[RagChunkHit],
    *,
    chat_history: str | None = None,
) -> str:
    parts = [f"Current question: {question.strip()}"]
    recent_history = extract_recent_user_history(chat_history)
    if recent_history:
        parts.append(f"Recent user conversation:\n{recent_history}")

    excerpts = []
    for hit in user_hits[:3]:
        text = re.sub(r"\s+", " ", hit.chunkText).strip()
        if not text:
            continue
        location = " | ".join(
            item
            for item in [
                hit.fileName,
                f"page {hit.pageNumber}" if hit.pageNumber is not None else None,
                hit.sectionTitle,
            ]
            if item
        )
        excerpts.append(f"[{hit.citationId}] {location}\n{text[:1200]}".strip())
    if excerpts:
        parts.append("Relevant user document facts:\n" + "\n\n".join(excerpts))
    return "\n\n".join(parts).strip()


def build_legal_text_query(question: str, *, chat_history: str | None = None) -> str:
    normalized_question = re.sub(r"\s+", " ", question.lower()).strip()
    parts = [question.strip()]
    for topic, expansion in _LEGAL_QUERY_EXPANSIONS.items():
        if topic in normalized_question:
            parts.append(expansion)
    recent_history = extract_recent_user_history(chat_history, max_messages=2, max_chars=500)
    if recent_history:
        parts.append(recent_history)
    return "\n".join(dict.fromkeys(part for part in parts if part)).strip()


def build_knowledge_context(knowledge_hits: list[RagChunkHit]) -> str:
    if not knowledge_hits:
        return ""
    parts = []
    for hit in knowledge_hits:
        if hit.chunkText.strip():
            metadata_lines = [
                "Source type: SYSTEM_KB",
                f"Law name: {hit.lawName}" if hit.lawName else "",
                f"Law code: {hit.lawCode}" if hit.lawCode else "",
                f"Article: {hit.articleNumber}" if hit.articleNumber else "",
                f"Clause: {hit.clauseNumber}" if hit.clauseNumber else "",
                f"Page: {hit.pageNumber}" if hit.pageNumber is not None else "",
                f"Section: {hit.sectionTitle}" if hit.sectionTitle else "",
            ]
            metadata_text = "\n".join(line for line in metadata_lines if line)
            parts.append(f"[{hit.citationId}]\n{metadata_text}\nContent: {hit.chunkText}".strip())
    return "\n\n".join(parts).strip()
