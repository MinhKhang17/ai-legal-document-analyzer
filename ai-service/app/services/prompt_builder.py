from __future__ import annotations

from app.models.intent_enums import ContractType, LegalQueryIntent, ResponseMode
from app.services.query_builder import build_knowledge_context, build_user_context
from app.services.retrieval_service import RagChunkHit


def build_system_prompt() -> str:
    base_prompt = (
        "Bạn là trợ lý pháp lý chỉ hỗ trợ phân tích hợp đồng đơn giản trong phạm vi sinh viên tại Việt Nam.\n"
        "Scope hỗ trợ: thuê trọ, làm thêm/thực tập/cộng tác viên, dịch vụ nhỏ/freelance, mua bán tài sản nhỏ, giấy vay tiền cá nhân đơn giản.\n"
        "Không xử lý sâu hợp đồng doanh nghiệp phức tạp, M&A, tín dụng ngân hàng, bất động sản giá trị lớn, hợp đồng quốc tế, tranh chấp ra tòa, hoặc yêu cầu lách luật.\n"
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
    )
    return base_prompt + grounding_rules


def build_user_prompt(
    question: str,
    user_hits: list[RagChunkHit],
    knowledge_hits: list[RagChunkHit],
    chat_history: str | None = None,
    available_user_docs: list[str] | None = None,
    available_system_docs: list[str] | None = None,
    workspace_id: str | None = None,
) -> str:
    user_context = build_user_context(user_hits) or "[none]"
    knowledge_context = build_knowledge_context(knowledge_hits) or "[none]"
    docs_summary = []
    if available_user_docs:
        docs_summary.append(f"Workspace docs: {', '.join(available_user_docs)}")
    if available_system_docs:
        docs_summary.append(f"System docs: {', '.join(available_system_docs[:10])}")
    available_kb_ids = ", ".join(hit.citationId for hit in knowledge_hits) or "[none]"
    available_user_ids = ", ".join(hit.citationId for hit in user_hits) or "[none]"

    return (
        f"QUESTION:\n{question.strip()}\n\n"
        f"CHAT_HISTORY:\n{chat_history or '[none]'}\n\n"
        f"AVAILABLE_DOCS:\n{chr(10).join(docs_summary) if docs_summary else '[none]'}\n\n"
        f"USER_DOCUMENT_CONTEXT:\n{user_context}\n\n"
        f"SYSTEM_KB_CONTEXT:\n{knowledge_context}\n\n"
        "MANDATORY_RESPONSE_GROUNDING:\n"
        f"- Available USER citation IDs: {available_user_ids}.\n"
        f"- Available SYSTEM_KB citation IDs: {available_kb_ids}.\n"
        "- Nếu đưa ra bất kỳ nhận định hoặc khuyến nghị pháp lý nào, answer cuối cùng PHẢI chứa ít nhất một citation SYSTEM_KB inline đúng dạng [KB-x].\n"
        "- Trong phần Khuyến nghị, mỗi khuyến nghị pháp lý phải có citation [KB-x] ngay trong chính phần Khuyến nghị; không được chỉ citation ở phần kết luận.\n"
        "- Khi mô tả nội dung hợp đồng, đặt citation USER inline đúng dạng [USER-x] ngay sau nội dung đó.\n"
        "- Trước khi hoàn tất, tự kiểm tra rằng mọi citation đều thuộc danh sách ID ở trên; không được bỏ citation khỏi answer.\n"
    )


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
        parts.append("- Chỉ tóm tắt các thông tin cốt lõi: loại hợp đồng, các bên, nghĩa vụ chính, thời hạn, tiền.\n")

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
