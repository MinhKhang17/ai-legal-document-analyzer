from app.models.intent_enums import LegalQueryIntent
from app.services.prompt_builder import build_system_prompt, build_user_prompt
from app.services.retrieval_service import RagChunkHit


def test_system_prompt_declares_document_scope_and_provenance_rules():
    prompt = build_system_prompt()

    assert "DOCUMENT SCOPE AND MULTI-DOCUMENT RULES" in prompt
    assert "không được mặc định sử dụng toàn bộ" in prompt
    assert "không tự trộn nội dung" in prompt
    assert "giữ provenance" in prompt


def test_summary_prompt_exposes_scopes_without_requiring_kb_citation():
    user_hit = RagChunkHit(
        citationId="USER-1",
        sourceType="USER_DOCUMENT",
        score=0.9,
        chunkText="Tiền thuê là 5 triệu đồng mỗi tháng.",
        documentId="doc_1",
        fileName="hop-dong-thue.pdf",
    )

    prompt = build_user_prompt(
        "Tóm tắt hợp đồng",
        [user_hit],
        [],
        available_user_docs=["hop-dong-thue.pdf", "phu-luc.pdf"],
        session_active_document_ids=["doc_1"],
        message_attached_document_ids=["doc_1"],
        focused_document_id="doc_1",
        contract_summary_only=True,
    )

    assert "AVAILABLE_DOCS:\nhop-dong-thue.pdf, phu-luc.pdf" in prompt
    assert "SESSION_ACTIVE_DOCS:\ndoc_1 (hop-dong-thue.pdf)" in prompt
    assert "MESSAGE_ATTACHED_DOCS:\ndoc_1 (hop-dong-thue.pdf)" in prompt
    assert "FOCUSED_DOCUMENT_ID:\ndoc_1" in prompt
    assert "không bắt buộc citation [KB-x]" in prompt
    assert "PHẢI chứa ít nhất một citation SYSTEM_KB" not in prompt


def test_contract_summary_instruction_requires_per_document_output():
    from app.services.prompt_builder import build_intent_instruction

    prompt = build_intent_instruction(LegalQueryIntent.CONTRACT_SUMMARY)

    assert "chỉ dùng USER_DOCUMENT_CONTEXT" in prompt
    assert "tóm tắt riêng từng tài liệu" in prompt
