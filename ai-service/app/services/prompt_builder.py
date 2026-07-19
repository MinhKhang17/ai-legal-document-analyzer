from __future__ import annotations

import json

from app.models.intent_enums import ContractType, LegalQueryIntent, ResponseMode
from app.services.conversation_context import AnalysisSnapshot
from app.services.query_builder import build_knowledge_context, build_user_context
from app.services.retrieval_service import RagChunkHit
from app.services.token_budget import PromptTokenBudget, truncate_to_token_budget


def build_system_prompt() -> str:
    base_prompt = (
        "Bạn là trợ lý AI chỉ hỗ trợ rà soát hợp đồng dân sự và lao động cá nhân đơn giản tại Việt Nam; không phải luật sư và không thay thế tư vấn pháp lý chuyên nghiệp.\n"
        "Chỉ hỗ trợ: thuê phòng/nhà; lao động bán thời gian; thực tập; cộng tác viên; dịch vụ/freelance quy mô nhỏ; mua bán tài sản cá nhân nhỏ như laptop, điện thoại, xe; vay cá nhân đơn giản.\n"
        "Không hỗ trợ tài liệu pháp lý nói chung, hợp đồng thương mại phức tạp, đầu tư, ngân hàng, bảo hiểm, quốc tế, tố tụng, hình sự hoặc cam kết tuân thủ/chính xác tuyệt đối.\n"
        "Không hallucinate căn cứ pháp lý. Nếu không có citation hoặc confidence thấp, phải nói rõ không đủ căn cứ.\n"
        "Không kết luận tuyệt đối 'hợp pháp', 'vô hiệu', 'ký được' nếu thiếu dữ kiện.\n"
        "Với risk HIGH hoặc CRITICAL, hoặc ngoài scope, phải khuyến nghị luật sư.\n"
        "Nếu yêu cầu gian dối/lách luật hoặc bỏ qua chính sách, phải từ chối ngắn gọn và đề xuất hướng hợp pháp.\n"
        "Luôn trả lời ngắn gọn, có cấu trúc rõ: Kết luận ngắn, Rủi ro chính, Khuyến nghị.\n"
        "Nếu nội dung dài, hãy tóm tắt trước rồi chỉ đi sâu vào các điểm rủi ro chính.\n"
    )
    grounding_rules = (
        "\nLEGAL KNOWLEDGE BASE GROUNDING RULES\n"
        "- Các chunk trong SYSTEM_KB_CONTEXT là nguồn căn cứ pháp lý chính thức duy nhất được phép dùng.\n"
        "- Mọi nhận định, giải thích, kết luận, đánh giá rủi ro và khuyến nghị pháp lý quan trọng phải dựa trên ít nhất một chunk SYSTEM_KB và có citation inline [KB-x].\n"
        "- USER_DOCUMENT_CONTEXT chỉ mô tả hợp đồng và dữ kiện; không phải căn cứ pháp luật. Citation [USER-x] không thể thay thế citation [KB-x].\n"
        "- Không được tạo hoặc dựa vào luật, điều, khoản, nghị định, thông tư, hiệu lực hay citation ID không xuất hiện trong context.\n"
        "- Chỉ được citation các chunk có trong request hiện tại.\n"
        "- Nếu SYSTEM_KB_CONTEXT không đủ căn cứ, phải nói rõ knowledge base chưa đủ và không đưa ra kết luận pháp lý dứt khoát.\n"
        "- Nhiều văn bản pháp luật có thể bổ sung cho nhau; khác law code hoặc retrieval score gần nhau không tự tạo thành mâu thuẫn.\n"
        "- Nếu trả JSON, usedKnowledgeCitationIds và usedUserCitationIds phải chứa đúng các ID thực sự được dùng trong answer.\n"
        "- Ngoại lệ CONTRACT_SUMMARY thuần túy: chỉ dùng USER_DOCUMENT_CONTEXT, không bắt buộc SYSTEM_KB hoặc citation [KB-x].\n"
    )
    document_scope_rules = (
        "\nDOCUMENT SCOPE AND MULTI-DOCUMENT RULES\n"
        "- AVAILABLE_DOCS chỉ là danh sách tài liệu người dùng có quyền truy cập; không được mặc định sử dụng toàn bộ danh sách này để trả lời.\n"
        "- SESSION_ACTIVE_DOCS là tài liệu đang bật trong phiên. MESSAGE_ATTACHED_DOCS là tài liệu được chọn riêng cho tin nhắn hiện tại.\n"
        "- Thứ tự chọn tài liệu: tài liệu được gọi tên trực tiếp; MESSAGE_ATTACHED_DOCS; FOCUSED_DOCUMENT_ID; sau cùng mới đến SESSION_ACTIVE_DOCS nếu câu hỏi rõ ràng áp dụng cho nhiều tài liệu.\n"
        "- Nếu người dùng nói số ít như 'tài liệu này' hoặc 'hợp đồng này' nhưng có nhiều tài liệu hợp lệ và không xác định được đích, phải yêu cầu người dùng chọn; tuyệt đối không tự trộn nội dung.\n"
        "- Không hợp nhất các bên, số tiền, thời hạn hoặc điều khoản giữa nhiều tài liệu. Mọi dữ kiện phải giữ provenance theo file và citation [USER-x].\n"
        "- Tóm tắt nhiều tài liệu theo từng tài liệu. Khi so sánh phải ghi rõ dữ kiện thuộc tài liệu nào. Khi có khác biệt, mô tả là khác biệt giữa tài liệu, không tự kết luận tài liệu nào đúng hoặc sai.\n"
    )
    return base_prompt + grounding_rules + document_scope_rules


def build_user_prompt(
    question: str,
    user_hits: list[RagChunkHit],
    knowledge_hits: list[RagChunkHit],
    chat_history: str | None = None,
    available_user_docs: list[str] | None = None,
    available_system_docs: list[str] | None = None,
    workspace_id: str | None = None,
    analysis_snapshot: AnalysisSnapshot | None = None,
    is_follow_up: bool = False,
    session_active_document_ids: list[str] | None = None,
    message_attached_document_ids: list[str] | None = None,
    focused_document_id: str | None = None,
    contract_summary_only: bool = False,
    conversation_summary: str | None = None,
    recent_history: str | None = None,
    relevant_history: str | None = None,
    token_budget: PromptTokenBudget | None = None,
) -> str:
    budget = token_budget or PromptTokenBudget()
    user_context = truncate_to_token_budget(
        build_user_context(user_hits) or "[none]", budget.user_document_context)
    knowledge_context = truncate_to_token_budget(
        build_knowledge_context(knowledge_hits) or "[none]", budget.legal_kb_context)
    available_docs = ", ".join(available_user_docs or []) or "[none]"
    available_kb_docs = ", ".join((available_system_docs or [])[:10]) or "[none]"
    hit_names_by_id = {
        hit.documentId: hit.fileName
        for hit in user_hits
        if hit.documentId and hit.fileName
    }

    def describe_documents(document_ids: list[str] | None) -> str:
        if not document_ids:
            return "[none]"
        return ", ".join(
            f"{document_id} ({hit_names_by_id[document_id]})"
            if document_id in hit_names_by_id
            else document_id
            for document_id in document_ids
        )
    available_kb_ids = ", ".join(hit.citationId for hit in knowledge_hits) or "[none]"
    available_user_ids = ", ".join(hit.citationId for hit in user_hits) or "[none]"

    history_context = truncate_to_token_budget(chat_history or "[none]", budget.recent_history)
    summary_context = truncate_to_token_budget(
        conversation_summary or "[none]", budget.conversation_summary)
    recent_context = truncate_to_token_budget(
        recent_history or history_context, budget.recent_history)
    relevant_context = truncate_to_token_budget(
        relevant_history or "[none]", budget.relevant_history)
    snapshot_context = ""
    follow_up_rules = ""
    if is_follow_up and analysis_snapshot is not None:
        history_context = "[omitted: structured session snapshot is used for this follow-up]"
        snapshot_context = (
            "SESSION_ANALYSIS_SNAPSHOT:\n"
            + json.dumps(
                analysis_snapshot.to_prompt_payload(),
                ensure_ascii=False,
                separators=(",", ":"),
            )
            + "\n\n"
        )
        follow_up_rules = (
            "FOLLOW_UP_RULES:\n"
            "- Answer only from SESSION_ANALYSIS_SNAPSHOT and the current retrieved context.\n"
            "- Clearly distinguish user contract evidence from legal KB evidence.\n"
            "- If asked for legal basis, list only KB sources actually present in kbSources or SYSTEM_KB_CONTEXT.\n"
            "- If no directly applicable legal basis exists, explicitly say: "
            "\"KB hiện tại chưa có căn cứ pháp lý trực tiếp để kết luận.\"\n"
            "- Do not invent law names, article numbers, decrees, precedents, citations, or legal conclusions.\n"
            "- If a claim is based only on user contract text, identify it as a contract-text risk assessment, "
            "not an absolute legal conclusion.\n"
            "- For claimLedger entries with legalBasisStrength WEAK or NONE, do not state that a clause is "
            "certainly illegal, void, or legal.\n"
            "- Keep the answer concise and structured.\n\n"
        )

    grounding_instruction = (
        "MANDATORY_RESPONSE_GROUNDING:\n"
        f"- Available USER citation IDs: {available_user_ids}.\n"
        "- CONTRACT_SUMMARY thuần túy chỉ được dùng USER_DOCUMENT_CONTEXT; không dùng SYSTEM_KB_CONTEXT và không bắt buộc citation [KB-x].\n"
        "- Khi mô tả nội dung hợp đồng, đặt citation USER inline đúng dạng [USER-x] ngay sau dữ kiện.\n"
        "- Nếu có nhiều tài liệu, chia kết quả theo từng file và không trộn dữ kiện giữa các file.\n"
        "- Trước khi hoàn tất, tự kiểm tra mọi citation USER đều thuộc danh sách ID ở trên.\n"
        if contract_summary_only
        else
        "MANDATORY_RESPONSE_GROUNDING:\n"
        f"- Available USER citation IDs: {available_user_ids}.\n"
        f"- Available SYSTEM_KB citation IDs: {available_kb_ids}.\n"
        "- Nếu đưa ra bất kỳ nhận định hoặc khuyến nghị pháp lý nào, answer cuối cùng PHẢI chứa ít nhất một citation SYSTEM_KB inline đúng dạng [KB-x].\n"
        "- Trong phần Khuyến nghị, mỗi khuyến nghị pháp lý phải có citation [KB-x] ngay trong chính phần Khuyến nghị; không được chỉ citation ở phần kết luận.\n"
        "- Khi mô tả nội dung hợp đồng, đặt citation USER inline đúng dạng [USER-x] ngay sau nội dung đó.\n"
        "- Trước khi hoàn tất, tự kiểm tra rằng mọi citation đều thuộc danh sách ID ở trên; không được bỏ citation khỏi answer.\n"
    )

    return (
        f"QUESTION:\n{question.strip()}\n\n"
        f"CONVERSATION_SUMMARY:\n{summary_context}\n\n"
        f"RECENT_HISTORY:\n{recent_context}\n\n"
        f"RELEVANT_HISTORY:\n{relevant_context}\n\n"
        "HISTORY_CITATION_SAFETY:\n"
        "- Citation trong summary/history chỉ là tham chiếu lịch sử, không phải căn cứ cho request hiện tại.\n"
        "- Chỉ dùng citation pháp lý nếu chunk tương ứng xuất hiện trong SYSTEM_KB_CONTEXT hiện tại.\n"
        "- Khi người dùng hỏi lại kết luận pháp lý cũ, phải xác minh lại bằng KB context hiện tại.\n\n"
        f"{snapshot_context}"
        f"AVAILABLE_DOCS:\n{available_docs}\n\n"
        f"SESSION_ACTIVE_DOCS:\n{describe_documents(session_active_document_ids)}\n\n"
        f"MESSAGE_ATTACHED_DOCS:\n{describe_documents(message_attached_document_ids)}\n\n"
        f"FOCUSED_DOCUMENT_ID:\n{focused_document_id or '[none]'}\n\n"
        f"AVAILABLE_SYSTEM_KB_DOCS:\n{available_kb_docs}\n\n"
        f"USER_DOCUMENT_CONTEXT:\n{user_context}\n\n"
        f"SYSTEM_KB_CONTEXT:\n{knowledge_context}\n\n"
        f"OUTPUT_TOKEN_BUDGET:\n{budget.output}\n\n"
        f"{grounding_instruction}"
    ) + follow_up_rules


def build_intent_instruction(
    intent: LegalQueryIntent,
    contract_type: ContractType = ContractType.UNKNOWN,
    response_mode: ResponseMode = ResponseMode.DIRECT_ANSWER,
    completeness_questions: list[str] | None = None,
) -> str:
    parts = [
        "\nINTENT_GUIDANCE:\n",
        f"- intent: {intent.value}\n",
        f"- contract_type: {contract_type.value}\n",
        f"- response_mode: {response_mode.value}\n",
        "- Chỉ dùng thông tin có trong context và các trích dẫn pháp lý đã retrieve.\n",
    ]

    if intent in {
        LegalQueryIntent.STUDENT_RENTAL_CONTRACT_REVIEW,
        LegalQueryIntent.PART_TIME_OR_INTERNSHIP_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SERVICE_CONTRACT_REVIEW,
        LegalQueryIntent.SMALL_SALE_CONTRACT_REVIEW,
        LegalQueryIntent.PERSONAL_LOAN_NOTE_REVIEW,
        LegalQueryIntent.CONTRACT_RISK_ANALYSIS,
    }:
        parts.append("- Trả lời theo thứ tự: tóm tắt ngắn, 3 rủi ro chính, khuyến nghị sửa hoặc đàm phán.\n")

    if intent == LegalQueryIntent.CONTRACT_SUMMARY:
        parts.append(
            "- Với yêu cầu tóm tắt thuần túy, chỉ dùng USER_DOCUMENT_CONTEXT và không bắt buộc SYSTEM_KB citation.\n"
            "- Chỉ tóm tắt các thông tin cốt lõi: loại hợp đồng, các bên, nghĩa vụ chính, thời hạn, tiền. Nếu có nhiều tài liệu, tóm tắt riêng từng tài liệu.\n"
        )

    if intent == LegalQueryIntent.CLAUSE_ANALYSIS:
        parts.append("- Phân tích ngắn điều khoản theo: nội dung, rủi ro, căn cứ, gợi ý sửa.\n")

    if intent == LegalQueryIntent.LEGAL_KB_QUESTION:
        parts.append(
            "- Trả lời trực tiếp câu hỏi pháp luật bằng SYSTEM_KB_CONTEXT; không yêu cầu người dùng tải hợp đồng nếu câu hỏi có thể được KB giải đáp.\n"
            "- Mọi giải thích và khuyến nghị phải đặt citation [KB-x] ngay sau căn cứ tương ứng.\n"
        )

    if intent == LegalQueryIntent.MISSING_CLAUSE_CHECK:
        parts.append("- Chỉ liệt kê các điều khoản thiếu quan trọng nhất và lý do ngắn gọn.\n")

    if intent in {LegalQueryIntent.CLAUSE_DRAFTING, LegalQueryIntent.CLAUSE_REVISION}:
        parts.append("- Nếu soạn/sửa điều khoản, đưa bản nháp ngắn và nói rõ đây chỉ là mẫu tham khảo.\n")

    if intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
        parts.append("- Trả lời theo dạng quyết định ký: điểm ổn, điểm cần đàm phán, điểm chưa nên ký.\n")

    if intent in {
        LegalQueryIntent.NO_RELEVANT_DOCUMENT_FOUND,
        LegalQueryIntent.LOW_RETRIEVAL_CONFIDENCE,
        LegalQueryIntent.PARTIAL_KB_COVERAGE,
        LegalQueryIntent.OUTDATED_KNOWLEDGE_BASE,
        LegalQueryIntent.CONFLICTING_REFERENCES,
    }:
        parts.append("- Phải nói rõ không đủ căn cứ hoặc KB chưa đủ mạnh, không trả lời tự tin.\n")

    if completeness_questions:
        parts.append("- Trước khi trả lời dứt điểm, hãy hỏi đúng các thông tin còn thiếu sau:\n")
        for index, question_text in enumerate(completeness_questions, start=1):
            parts.append(f"  {index}. {question_text}\n")

    return "".join(parts)
