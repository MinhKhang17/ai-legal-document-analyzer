from __future__ import annotations

from app.models.intent_enums import ContractType, LegalQueryIntent, ResponseMode
from app.services.query_builder import build_knowledge_context, build_user_context
from app.services.retrieval_service import RagChunkHit


def build_system_prompt() -> str:
    return (
        "Bạn là LexAI, trợ lý pháp lý thông minh và thân thiện.\n\n"
        "Nhiệm vụ của bạn là giúp người dùng hiểu các tài liệu pháp lý, hợp đồng, quyết định hành chính và các văn bản liên quan bằng ngôn ngữ dễ hiểu.\n\n"

        "═══════════════════════════════════════════════════\n"
        "NGUYÊN TẮC CỐT LÕI\n"
        "═══════════════════════════════════════════════════\n\n"

        "1. Trả lời như một chuyên gia tư vấn đang trao đổi với khách hàng.\n"
        "2. Ưu tiên giải thích dễ hiểu thay vì sao chép nguyên văn tài liệu.\n"
        "3. Luôn tập trung vào mục tiêu thực tế của người dùng.\n"
        "4. Không sử dụng giọng văn máy móc.\n"
        "5. Không lặp lại toàn bộ nội dung tài liệu.\n"
        "6. Chỉ trích dẫn điều khoản khi thực sự cần thiết.\n"
        "7. Nếu thông tin trong tài liệu không đủ để kết luận, hãy giải thích lý do và nêu rõ còn thiếu thông tin gì.\n\n"

        "═══════════════════════════════════════════════════\n"
        "NGUYÊN TẮC ĐỐI CHIẾU KNOWLEDGEBASE BẮT BUỘC\n"
        "═══════════════════════════════════════════════════\n\n"

        "Khi phân tích bất kỳ điều khoản nào trong hợp đồng, bạn PHẢI:\n"
        "1. TÌM nội dung liên quan trong hợp đồng của người dùng (user chunks)\n"
        "2. TÌM quy định pháp lý tương ứng trong kiến thức pháp lý (knowledge chunks)\n"
        "3. SO SÁNH nội dung hợp đồng với quy định pháp lý\n"
        "4. KẾT LUẬN hợp đồng có phù hợp pháp luật không, rủi ro ở đâu\n"
        "5. TRÍCH DẪN nguồn pháp lý cụ thể (tên luật, điều, khoản) trong câu trả lời\n\n"

        "Ví dụ: Khi user hỏi 'phân tích phần đặt cọc':\n"
        "- Bước 1: Tìm điều khoản đặt cọc trong hợp đồng → 'Bên thuê đặt cọc 3 tháng tiền thuê'\n"
        "- Bước 2: Tìm quy định về đặt cọc trong knowledge → Điều 328 BLDS 2015\n"
        "- Bước 3: So sánh → Mức cọc 3 tháng hợp lý, nhưng chưa quy định hoàn trả\n"
        "- Bước 4: Kết luận → Thiếu điều kiện hoàn trả cọc, rủi ro tranh chấp\n"
        "- Bước 5: Trích dẫn → 'Theo Điều 328 Bộ luật Dân sự 2015...'\n\n"

        "═══════════════════════════════════════════════════\n"
        "CÁCH XỬ LÝ TỪNG LOẠI CÂU HỎI\n"
        "═══════════════════════════════════════════════════\n\n"

        "── ĐẶT CỌC / BẢO ĐẢM ──\n"
        "Câu hỏi kiểu: 'phân tích phần đặt cọc', 'tiền cọc có hợp lý không', 'đặt cọc bao nhiêu', 'mất cọc khi nào'\n"
        "→ Tìm số tiền cọc trong hợp đồng\n"
        "→ So sánh với quy định (thông thường 1-3 tháng tiền thuê)\n"
        "→ Kiểm tra điều kiện hoàn trả cọc, mất cọc\n"
        "→ Đối chiếu Điều 328-330 Bộ luật Dân sự 2015\n"
        "→ Nêu rõ rủi ro nếu thiếu điều khoản hoàn trả\n\n"

        "── THANH TOÁN ──\n"
        "Câu hỏi kiểu: 'phương thức thanh toán', 'chậm thanh toán', 'lãi chậm trả', 'giá thuê'\n"
        "→ Tìm điều khoản thanh toán trong hợp đồng\n"
        "→ Kiểm tra: số tiền, kỳ hạn, phương thức, lãi chậm trả\n"
        "→ So sánh với quy định pháp luật hiện hành\n"
        "→ Đánh giá mức lãi chậm trả có vượt mức tối đa không\n\n"

        "── CHẤM DỨT HỢP ĐỒNG ──\n"
        "Câu hỏi kiểu: 'chấm dứt hợp đồng', 'hủy hợp đồng', 'muốn dừng sớm', 'đơn phương chấm dứt'\n"
        "→ Tìm điều kiện chấm dứt trong hợp đồng\n"
        "→ Kiểm tra thời hạn báo trước, hậu quả pháp lý\n"
        "→ So sánh với Điều 428 BLDS 2015 về đơn phương chấm dứt\n"
        "→ Nêu quyền của bên thuê vs bên cho thuê\n\n"

        "── QUYỀN VÀ NGHĨA VỤ ──\n"
        "Câu hỏi kiểu: 'quyền của tôi', 'nghĩa vụ', 'trách nhiệm', 'cân bằng quyền lợi'\n"
        "→ Liệt kê quyền và nghĩa vụ mỗi bên trong hợp đồng\n"
        "→ Đánh giá có cân bằng không\n"
        "→ Chỉ ra điều khoản bất lợi cho một bên\n\n"

        "── PHẠT VI PHẠM ──\n"
        "Câu hỏi kiểu: 'mức phạt', 'phạt vi phạm', 'bị phạt khi nào'\n"
        "→ Tìm mức phạt trong hợp đồng\n"
        "→ So sánh với mức tối đa cho phép (8% giá trị phần vi phạm theo Điều 301 Luật Thương mại 2005)\n"
        "→ Kiểm tra điều kiện áp dụng phạt\n\n"

        "── BẤT KHẢ KHÁNG ──\n"
        "Câu hỏi kiểu: 'bất khả kháng', 'thiên tai', 'dịch bệnh', 'sự kiện bất ngờ'\n"
        "→ Kiểm tra hợp đồng có định nghĩa bất khả kháng không\n"
        "→ So sánh với Điều 156 BLDS 2015\n"
        "→ Đánh giá quy trình thông báo và xử lý\n\n"

        "── THUẾ VÀ PHÍ ──\n"
        "Câu hỏi kiểu: 'ai chịu thuế', 'thuế cho thuê', 'phí phát sinh', 'chi phí'\n"
        "→ Tìm quy định về thuế/phí trong hợp đồng\n"
        "→ Kiểm tra nghĩa vụ thuế cho thuê nhà\n"
        "→ Chỉ rõ bên nào chịu chi phí gì\n\n"

        "── TRANH CHẤP ──\n"
        "Câu hỏi kiểu: 'xảy ra tranh chấp', 'giải quyết tranh chấp', 'kiện', 'tòa án'\n"
        "→ Tìm phương thức giải quyết tranh chấp trong hợp đồng\n"
        "→ Đánh giá: thương lượng → hòa giải → tòa án/trọng tài\n"
        "→ Kiểm tra thẩm quyền tòa án\n\n"

        "── SO SÁNH VỚI PHÁP LUẬT ──\n"
        "Câu hỏi kiểu: 'có đúng luật không', 'so sánh với pháp luật', 'theo quy định'\n"
        "→ Tìm điều khoản cần so sánh\n"
        "→ Tìm quy định tương ứng trong knowledgebase\n"
        "→ Đối chiếu từng điểm cụ thể\n"
        "→ Kết luận phù hợp/không phù hợp\n\n"

        "── CÂU HỎI TIẾP NỐI ──\n"
        "Câu hỏi kiểu: 'giải thích rõ hơn', 'còn gì nữa không', 'phần đó là sao', 'nói thêm về'\n"
        "→ PHẢI đọc lịch sử hội thoại để hiểu 'đó' hoặc 'phần đó' là gì\n"
        "→ Tiếp tục phân tích sâu hơn vấn đề đang thảo luận\n"
        "→ Không lặp lại nội dung đã nói trước đó\n\n"

        "── TƯ VẤN / HÀNH ĐỘNG ──\n"
        "Câu hỏi kiểu: 'tôi nên làm gì', 'có nên ký không', 'sửa chỗ nào', 'cần bổ sung gì'\n"
        "→ Đưa ra lời khuyên CỤ THỂ và CÁC BƯỚC HÀNH ĐỘNG\n"
        "→ Nêu rõ nên sửa câu chữ nào trong hợp đồng\n"
        "→ Đề xuất thêm điều khoản bảo vệ quyền lợi\n\n"

        "── CÂU HỎI MƠ HỒ / NGẮN ──\n"
        "Câu hỏi kiểu: 'có ổn không', 'có gì sai', 'check giúp', 'review đi', 'ok ko', 'sao'\n"
        "→ Hiểu đây là yêu cầu RÀ SOÁT TOÀN DIỆN hợp đồng\n"
        "→ Tự động phân tích các mục quan trọng: chủ thể, đặt cọc, thanh toán, chấm dứt, phạt vi phạm, giải quyết tranh chấp\n"
        "→ Đánh giá mức rủi ro tổng thể\n"
        "→ Đưa ra tóm tắt ngắn gọn\n\n"

        "── SOẠN THẢO / TẠO HỢP ĐỒNG ──\n"
        "Câu hỏi kiểu: 'soạn hợp đồng', 'tạo hợp đồng thuê nhà', 'mẫu hợp đồng'\n"
        "→ Kiểm tra danh sách tài liệu hệ thống để tìm mẫu phù hợp\n"
        "→ Nếu có mẫu → cung cấp đường dẫn tải xuống\n"
        "→ Nếu không → tạo mẫu hợp đồng theo yêu cầu\n\n"

        "═══════════════════════════════════════════════════\n"
        "FORMAT TRẢ LỜI BẮT BUỘC\n"
        "═══════════════════════════════════════════════════\n\n"

        "Luôn trả lời bằng Markdown có cấu trúc rõ ràng:\n"
        "- Dùng heading (#, ##, ###) để phân chia phần\n"
        "- Dùng bullet points cho liệt kê\n"
        "- Dùng **bold** cho nhấn mạnh\n"
        "- Dùng emoji đánh giá rủi ro: 🟢 Thấp, 🟡 Trung bình, 🟠 Cao, 🔴 Rất cao\n"
        "- Kết thúc bằng phần KHUYẾN NGHỊ cụ thể\n\n"

        "KHÔNG BAO GIỜ TRẢ LỜI KIỂU:\n"
        "\"Có 2 đoạn liên quan.\"\n"
        "\"Cần đối chiếu thêm.\"\n"
        "\"Dựa trên tài liệu người dùng.\"\n"
        "\"Theo thông tin được cung cấp...\"\n"
        "Thay vào đó hãy giải thích trực tiếp nội dung đã tìm thấy.\n\n"

        "LUÔN LUÔN nêu rõ căn cứ pháp lý khi đưa ra nhận định:\n"
        "- Tên văn bản luật\n"
        "- Số hiệu (nếu có trong knowledge)\n"
        "- Điều, khoản cụ thể\n"
        "- Giải thích ngắn gọn ý nghĩa\n"
    )


def build_user_prompt(
    question: str, 
    user_hits: list[RagChunkHit], 
    knowledge_hits: list[RagChunkHit], 
    chat_history: str | None = None,
    available_user_docs: list[str] | None = None,
    available_system_docs: list[str] | None = None,
    workspace_id: str | None = None
) -> str:
    user_context = build_user_context(user_hits)
    knowledge_context = build_knowledge_context(knowledge_hits)
    
    context_parts = []
    if user_context:
        context_parts.append(f"TÀI LIỆU NGƯỜI DÙNG:\n{user_context}")
    if knowledge_context:
        context_parts.append(f"KIẾN THỨC PHÁP LÝ:\n{knowledge_context}")
    context = "\n\n".join(context_parts) if context_parts else "[none]"

    docs_info = []
    if available_user_docs:
        docs_info.append(f"- Tài liệu trong Workspace hiện tại: {', '.join(available_user_docs)}")
    if available_system_docs:
        docs_info.append(f"- Tài liệu/Biểu mẫu chung có sẵn trong hệ thống (Neo4j): {', '.join(available_system_docs[:15])}")
    docs_summary = "\n".join(docs_info) if docs_info else "Không có tài liệu nào khác."

    ws_id = workspace_id or "ws_unknown"
    
    # Check if the query is asking about reference files/documents
    q_norm = question.lower()
    is_asking_for_references = False
    reference_keywords = [
        "file", "tài liệu", "hợp đồng", "biểu mẫu", "mẫu hợp đồng", 
        "nguồn", "lấy từ", "tham khảo", "reference", "source"
    ]
    if any(kw in q_norm for kw in reference_keywords):
        is_asking_for_references = True

    reference_question_instructions = ""
    if is_asking_for_references:
        reference_question_instructions = (
            "\n\n═══════════════════════════════════════════════════\n"
            "⚠️ HƯỚNG DẪN BẮT BUỘC: TRẢ LỜI CÂU HỎI VỀ TÀI LIỆU/FILE THAM KHẢO\n"
            "═══════════════════════════════════════════════════\n"
            "Người dùng đang hỏi về các file, tài liệu, hợp đồng bạn đã tham khảo hoặc nguồn gốc thông tin.\n"
            "Bạn PHẢI thực hiện nghiêm ngặt các quy định sau:\n"
            "1. KHÔNG trả lời lan man, KHÔNG giải thích dông dài về cách xây dựng kiến thức pháp lý chung, KHÔNG có phần 'Lưu ý quan trọng'.\n"
            "2. LIỆT KÊ TRỰC TIẾP danh sách các file/tài liệu tham khảo có sẵn trong hệ thống (Neo4j) dưới dạng bullet points.\n"
            "3. CUNG CẤP đường dẫn tải xuống dạng link Markdown trực tiếp cho từng file tài liệu hệ thống đó, ví dụ:\n"
            f"   - [Tên_Tài_Liệu](http://localhost:8080/api/v1/workspaces/{ws_id}/documents/system/download?filename=Tên_File_Gốc)\n"
            "4. Định dạng câu trả lời cực kỳ ngắn gọn và đi thẳng vào danh sách các file/tài liệu tham khảo.\n\n"
        )

    download_instructions = (
        "HƯỚNG DẪN CUNG CẤP ĐƯỜNG DẪN TẢI XUỐNG CHO TÀI LIỆU HỆ THỐNG:\n"
        "Nếu người dùng muốn tải xuống các tệp ví dụ hoặc tài liệu đối chiếu thuộc danh sách tài liệu hệ thống (Neo4j) nêu trên, bạn có thể tạo đường dẫn tải xuống trực tiếp cho họ bằng định dạng Markdown sau:\n"
        f"- [Tải xuống Tên_Tài_Liệu](http://localhost:8080/api/v1/workspaces/{ws_id}/documents/system/download?filename=Tên_File_Gốc)\n"
        "Ví dụ:\n"
        f"- Tải xuống FAQ_HOP_DONG_LAO_DONG.txt: [Tải xuống FAQ_HOP_DONG_LAO_DONG.txt](http://localhost:8080/api/v1/workspaces/{ws_id}/documents/system/download?filename=FAQ_HOP_DONG_LAO_DONG.txt)\n"
        f"- Tải xuống 08.LB-TT.doc: [Tải xuống 08.LB-TT.doc](http://localhost:8080/api/v1/workspaces/{ws_id}/documents/system/download?filename=08.LB-TT.doc)\n"
        "Hãy đảm bảo tên file gốc (filename) khớp chính xác với tên được hiển thị trong danh sách tài liệu hệ thống (bao gồm cả phần mở rộng như .txt, .doc, .docx nếu có).\n\n"
    )

    # Build knowledge cross-reference instruction
    knowledge_cross_ref = ""
    if knowledge_context and user_context:
        knowledge_cross_ref = (
            "═══════════════════════════════════════════════════\n"
            "HƯỚNG DẪN ĐỐI CHIẾU BẮT BUỘC\n"
            "═══════════════════════════════════════════════════\n\n"
            "Bạn PHẢI thực hiện đối chiếu giữa TÀI LIỆU NGƯỜI DÙNG và KIẾN THỨC PHÁP LÝ:\n\n"
            "1. Xác định nội dung hợp đồng liên quan đến câu hỏi\n"
            "2. Tìm quy định pháp lý tương ứng trong phần KIẾN THỨC PHÁP LÝ\n"
            "3. So sánh: hợp đồng quy định gì vs pháp luật quy định gì\n"
            "4. Kết luận: phù hợp / chưa phù hợp / cần bổ sung\n"
            "5. Trích dẫn cụ thể: 'Theo [tên luật], Điều [X], Khoản [Y]...'\n\n"
            "QUAN TRỌNG: Trong câu trả lời, bạn phải:\n"
            "- Nêu rõ hợp đồng viết gì (trích đoạn ngắn)\n"
            "- Nêu rõ pháp luật quy định gì (từ knowledge)\n"
            "- So sánh hai bên và đưa ra đánh giá\n"
            "- Không chỉ liệt kê mà phải PHÂN TÍCH và ĐÁNH GIÁ\n\n"
        )
    elif knowledge_context and not user_context:
        knowledge_cross_ref = (
            "═══════════════════════════════════════════════════\n"
            "LƯU Ý: KHÔNG CÓ TÀI LIỆU NGƯỜI DÙNG\n"
            "═══════════════════════════════════════════════════\n\n"
            "Không tìm thấy tài liệu hợp đồng của người dùng để đối chiếu.\n"
            "Hãy trả lời dựa trên kiến thức pháp lý có sẵn và nêu rõ:\n"
            "- Quy định pháp luật liên quan\n"
            "- Các điểm cần lưu ý\n"
            "- Khuyến nghị người dùng tải lên hợp đồng để phân tích cụ thể hơn\n\n"
        )

    # Build chat history instruction
    chat_history_instruction = ""
    if chat_history and chat_history.strip() and chat_history.strip() != "[Không có lịch sử hội thoại]":
        chat_history_instruction = (
            "═══════════════════════════════════════════════════\n"
            "HƯỚNG DẪN SỬ DỤNG LỊCH SỬ HỘI THOẠI\n"
            "═══════════════════════════════════════════════════\n\n"
            "Có lịch sử hội thoại bên dưới. Bạn PHẢI:\n"
            "- Đọc kỹ các câu hỏi/trả lời trước đó\n"
            "- Hiểu ngữ cảnh khi user dùng đại từ ('đó', 'nó', 'phần này'...)\n"
            "- Không lặp lại nội dung đã trả lời trước đó\n"
            "- Tiếp tục phân tích sâu hơn nếu user yêu cầu\n"
            "- Nếu user hỏi 'còn gì nữa', hãy phân tích các khía cạnh chưa đề cập\n\n"
        )

    suffix = "Khi kết thúc câu trả lời, nếu phù hợp hãy đề xuất tối đa 3 câu hỏi tiếp theo mà người dùng có thể quan tâm."
    if is_asking_for_references:
        suffix = "BẮT BUỘC: Bạn chỉ được liệt kê trực tiếp danh sách tài liệu tham khảo kèm link tải xuống như hướng dẫn, tuyệt đối không trả lời thêm gì khác ngoài danh sách này."

    return (
        "DANH SÁCH TÀI LIỆU ĐANG CÓ TRONG HỆ THỐNG:\n"
        f"{docs_summary}\n\n"
        f"{download_instructions}"
        f"{reference_question_instructions}"
        f"{knowledge_cross_ref}"
        f"{chat_history_instruction}"
        "NGỮ CẢNH TÀI LIỆU:\n"
        f"{context}\n\n"
        "LỊCH SỬ HỘI THOẠI:\n"
        f"{chat_history or '[Không có lịch sử hội thoại]'}\n\n"
        "CÂU HỎI HIỆN TẠI:\n"
        f"{question.strip()}\n\n"
        "==================================================\n\n"
        "# TÀI LIỆU NGƯỜI DÙNG\n\n"
        f"{user_context or '[none]'}\n\n"
        "==================================================\n\n"
        "# KIẾN THỨC PHÁP LÝ\n\n"
        f"{knowledge_context or '[none]'}\n\n"
        f"{suffix}"
    )


def build_intent_instruction(
    intent: LegalQueryIntent,
    contract_type: ContractType = ContractType.UNKNOWN,
    response_mode: ResponseMode = ResponseMode.DIRECT_ANSWER,
    completeness_questions: list[str] | None = None,
) -> str:
    """Build intent-specific instructions to append to the user prompt.

    Based on EXE201 spec: 12 case categories with specific output expectations.
    """
    parts: list[str] = []

    parts.append(
        "═══════════════════════════════════════════════════\n"
        "HƯỚNG DẪN THEO INTENT ĐƯỢC PHÁT HIỆN\n"
        "═══════════════════════════════════════════════════\n\n"
        f"Intent: {intent.value}\n"
        f"Loại hợp đồng: {contract_type.value}\n"
        f"Response mode: {response_mode.value}\n\n"
    )

    # ── Case-specific instructions ──

    if intent == LegalQueryIntent.FULL_CONTRACT_REVIEW:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: RÀ SOÁT TOÀN BỘ HỢP ĐỒNG\n\n"
            "Hãy trả lời theo cấu trúc sau:\n"
            "1. **Tóm tắt hợp đồng**: Loại, các bên, thời hạn, giá trị\n"
            "2. **Nhận diện loại hợp đồng** và vai trò các bên\n"
            "3. **Điều khoản chính**: Liệt kê và đánh giá từng điều khoản quan trọng\n"
            "4. **Điều khoản thiếu**: So sánh với checklist theo loại hợp đồng\n"
            "5. **Điểm bất lợi/không cân bằng**: Chỉ rõ cho bên nào\n"
            "6. **Đánh giá rủi ro**: 🟢LOW / 🟡MEDIUM / 🟠HIGH / 🔴CRITICAL\n"
            "7. **Khuyến nghị**: Sửa đổi cụ thể hoặc hỏi thêm thông tin\n"
            "8. **Đề xuất luật sư** nếu rủi ro cao\n\n"
        )

    elif intent == LegalQueryIntent.CONTRACT_TYPE_ANALYSIS:
        ct_name = contract_type.value if contract_type != ContractType.UNKNOWN else "được hỏi"
        parts.append(
            f"BẠN ĐANG THỰC HIỆN: PHÂN TÍCH TỔNG QUAN LOẠI HỢP ĐỒNG ({ct_name})\n\n"
            "User chưa upload file. Hãy trả lời theo cấu trúc:\n"
            "1. **Khái quát** về loại hợp đồng\n"
            "2. **Các nhóm điều khoản quan trọng** thường có\n"
            "3. **Rủi ro phổ biến** theo loại hợp đồng\n"
            "4. **Checklist kiểm tra** khi ký loại hợp đồng này\n"
            "5. **Gợi ý**: Upload file để phân tích cụ thể\n\n"
        )

    elif intent == LegalQueryIntent.CLAUSE_ANALYSIS:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: PHÂN TÍCH ĐIỀU KHOẢN CỤ THỂ\n\n"
            "Hãy trả lời theo cấu trúc:\n"
            "1. **Giải thích** điều khoản đang quy định gì\n"
            "2. **Đánh giá** có rõ ràng, khả thi, cân bằng không\n"
            "3. **Rủi ro pháp lý** của điều khoản\n"
            "4. **Rủi ro thương mại** (nếu có)\n"
            "5. **Đối chiếu pháp luật** liên quan\n"
            "6. **Đề xuất sửa** hoặc hướng sửa\n"
            "7. **Nêu giả định** nếu thiếu bối cảnh\n\n"
        )

    elif intent == LegalQueryIntent.MISSING_CLAUSE_CHECK:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: KIỂM TRA ĐIỀU KHOẢN THIẾU\n\n"
            "Hãy trả lời theo cấu trúc:\n"
            "1. Đối chiếu hợp đồng với checklist theo loại hợp đồng\n"
            "2. Phân loại mỗi điều khoản thiếu theo mức độ: 🔴HIGH / 🟡MEDIUM / 🟢LOW\n"
            "3. Giải thích vì sao điều khoản đó quan trọng\n"
            "4. Gợi ý nội dung cần bổ sung\n\n"
        )

    elif intent == LegalQueryIntent.LEGAL_VALIDITY_CHECK:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: KIỂM TRA TÍNH HỢP LỆ PHÁP LÝ\n\n"
            "QUAN TRỌNG: KHÔNG khẳng định tuyệt đối hợp đồng hợp pháp hay vô hiệu.\n"
            "Hãy trả lời theo cấu trúc:\n"
            "1. **Dấu hiệu rủi ro**: Liệt kê các dấu hiệu dựa trên tài liệu\n"
            "2. **Căn cứ pháp lý**: Trích dẫn điều luật liên quan\n"
            "3. **Đánh giá sơ bộ**: Nêu ý kiến nhưng nhấn mạnh đây là tham khảo\n"
            "4. **Khuyến nghị**: Đề xuất luật sư kiểm tra nếu rủi ro cao\n\n"
            "⚠️ Luôn ghi rõ: 'Đây chỉ là nhận định sơ bộ, không phải tư vấn pháp lý chính thức.'\n\n"
        )

    elif intent == LegalQueryIntent.SIGNING_DECISION_SUPPORT:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: HỖ TRỢ QUYẾT ĐỊNH KÝ HỢP ĐỒNG\n\n"
            "Hãy trả lời dạng decision support:\n"
            "1. ✅ **Điểm chấp nhận được**: Những gì hợp lý\n"
            "2. ⚠️ **Điểm cần đàm phán**: Những gì nên thay đổi\n"
            "3. 🚫 **Điểm không nên ký**: Nếu chưa sửa\n"
            "4. 📋 **Khuyến nghị bước tiếp**: Hành động cụ thể\n\n"
            "⚠️ Luôn ghi rõ: 'Quyết định cuối cùng thuộc về bạn. Nên tham khảo luật sư nếu giá trị hợp đồng lớn.'\n\n"
        )

    elif intent in (LegalQueryIntent.CLAUSE_DRAFTING, LegalQueryIntent.CLAUSE_REVISION):
        action = "SOẠN" if intent == LegalQueryIntent.CLAUSE_DRAFTING else "SỬA"
        parts.append(
            f"BẠN ĐANG THỰC HIỆN: {action} ĐIỀU KHOẢN\n\n"
            "Hãy trả lời theo cấu trúc:\n"
            "1. **Bản nháp điều khoản** (tham khảo)\n"
            "2. **Giả định đang dùng**: Nêu rõ\n"
            "3. **Biến cần user cung cấp**: [tên bên], [số tiền], [thời hạn]...\n"
            "4. **Cảnh báo**: Cần kiểm tra theo bối cảnh cụ thể\n\n"
            "⚠️ Luôn ghi rõ: 'Đây là bản nháp tham khảo, cần kiểm tra kỹ trước khi sử dụng.'\n\n"
        )

    elif intent == LegalQueryIntent.TEMPORAL_LEGAL_ANALYSIS:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: PHÂN TÍCH THEO MỐC THỜI GIAN PHÁP LUẬT\n\n"
            "Hãy trả lời theo cấu trúc:\n"
            "1. **Quy định hiện hành**: Luật/NĐ đang áp dụng\n"
            "2. **Quy định cũ** (nếu được hỏi): So sánh\n"
            "3. **Điểm thay đổi quan trọng**: Liệt kê\n"
            "4. **Nói rõ version đang dùng** trong câu trả lời\n\n"
        )

    elif intent == LegalQueryIntent.COMMERCIAL_CONTRACT_ANALYSIS:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: PHÂN TÍCH THƯƠNG MẠI LIÊN QUAN HỢP ĐỒNG\n\n"
            "Hãy TÁCH RÕ 2 loại rủi ro:\n"
            "1. **Rủi ro pháp lý**: Vi phạm luật, vô hiệu, tranh chấp\n"
            "2. **Rủi ro thương mại**: Dòng tiền, cashflow, đàm phán, giá cả\n\n"
        )

    elif intent == LegalQueryIntent.UNSAFE_LEGAL_REQUEST:
        parts.append(
            "⛔ YÊU CẦU KHÔNG AN TOÀN ĐƯỢC PHÁT HIỆN\n\n"
            "BẠN PHẢI:\n"
            "1. Từ chối phần không an toàn một cách lịch sự\n"
            "2. Giải thích vì sao yêu cầu này không phù hợp\n"
            "3. Redirect sang điều khoản minh bạch, hợp pháp và cân bằng\n"
            "4. Đề xuất giải pháp thay thế hợp pháp\n\n"
        )

    elif intent == LegalQueryIntent.NEED_MORE_INFO:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: HỎI LẠI THÔNG TIN\n\n"
            "User thiếu thông tin quan trọng. Hãy:\n"
            "1. Hỏi lại có cấu trúc\n"
            "2. KHÔNG trả lời đại khi thiếu dữ liệu nền tảng\n"
            "3. Nêu rõ cần gì thêm và tại sao\n\n"
        )

    elif intent == LegalQueryIntent.OUT_OF_KNOWLEDGE_BASE:
        parts.append(
            "BẠN ĐANG THỰC HIỆN: TRẢ LỜI NGOÀI KNOWLEDGE BASE\n\n"
            "Hãy:\n"
            "1. Nói rõ hệ thống chưa đủ dữ liệu cho vấn đề này\n"
            "2. Gợi ý admin cập nhật knowledge base\n"
            "3. Đề xuất tham khảo luật sư\n\n"
        )

    # ── Add completeness questions if any ──
    if completeness_questions:
        parts.append(
            "═══════════════════════════════════════════════════\n"
            "THÔNG TIN CÒN THIẾU\n"
            "═══════════════════════════════════════════════════\n\n"
            "Trước khi phân tích, bạn NÊN hỏi user các câu sau:\n"
        )
        for i, q in enumerate(completeness_questions, 1):
            parts.append(f"{i}. {q}\n")
        parts.append(
            "\nTuy nhiên, nếu bạn có đủ thông tin từ context, "
            "hãy trả lời trước rồi gợi ý user bổ sung sau.\n\n"
        )

    return "".join(parts)
