from __future__ import annotations
import logging
import os
import uuid
import re
import datetime
import httpx
from fastapi import HTTPException
from app.schemas import RagCitation, RagQueryRequest, RagQueryResponse, RagUsage
from app.services.llm_client import build_default_llm_client
from app.services.retrieval_service import RetrievalService

logger = logging.getLogger(__name__)


def is_contract_generation_intent(question: str) -> bool:
    question_lower = question.lower().strip()
    
    # Informational or QA keywords that should bypass contract generation
    qa_keywords = [
        "what is", "why ", "how to", "how do", "how can", "is there", "are there",
        "là gì", "như thế nào", "làm sao", "làm thế nào", "có được", "có nên",
        "tại sao", "giải thích", "khái niệm", "quy định", "luật nào", "thủ tục",
        "cần lưu ý gì", "rủi ro gì", "bắt buộc", "yêu cầu", "điều kiện",
        "liệt kê", "danh sách", "kể tên", "cho biết các", "những hợp đồng nào",
        "gồm những", "có bao nhiêu", "ở đâu", "từ đâu", "phân tích", "so sánh",
        "kiểm tra", "đánh giá", "nhận xét", "góp ý", "sửa lỗi", "rà soát",
        "nguồn nào", "file nào", "tệp nào", "thông tin lấy từ", "lấy từ đâu",
        "tham khảo", "đã tham khảo", "tham chiếu", "đã dùng", "đã sử dụng", "nguồn gốc"
    ]
    if any(kw in question_lower for kw in qa_keywords):
        return False
        
    creation_keywords = [
        "generate", "create", "draft", "provide", "give", "make", "write",
        "tạo", "soạn", "soạn thảo", "viết", "cung cấp", "bản thảo", "lập",
        "in", "xuất", "tải", "lấy", "mẫu", "bản", "cho tôi"
    ]
    contract_keywords = [
        "contract", "agreement", "lease", "tenancy", "rental",
        "hợp đồng", "thoả thuận", "thuê nhà", "thuê trọ", "thuê văn phòng", "thuê mặt bằng",
        "lao động", "mua bán", "dịch vụ", "chuyển nhượng", "tặng cho", "vay tiền", "ủy quyền"
    ]
    
    has_creation = any(kw in question_lower for kw in creation_keywords)
    has_contract = any(kw in question_lower for kw in contract_keywords)
    
    direct_patterns = [
        "lease agreement", "tenancy agreement", "rental agreement", 
        "rental contract", "lease contract", "tenancy contract",
        "mẫu hợp đồng", "bản hợp đồng", "hợp đồng lao động", "hợp đồng thuê",
        "hợp đồng mua bán", "hợp đồng dịch vụ"
    ]
    has_direct = any(pat in question_lower for pat in direct_patterns)
    
    return (has_creation and has_contract) or has_direct


class ContractGenerationService:
    def __init__(
        self,
        *,
        retrieval_service: RetrievalService | None = None,
        llm_client: None = None,
        llm_enabled: bool = True,
    ) -> None:
        self.retrieval_service = retrieval_service or RetrievalService()
        self.llm_client = llm_client or build_default_llm_client()
        self.llm_enabled = llm_enabled

    def generate_contract(self, request: RagQueryRequest) -> RagQueryResponse:
        # Search Neo4j for the Top K relevant legal contract templates
        retrieved_chunks = self.retrieval_service.search_knowledge_chunks(
            request.question,
            top_k=request.topKKnowledgeChunks or 3,
            query_text=request.question
        )

        documents_text = ""
        if retrieved_chunks:
            # 1. Identify the best matched template document ID
            best_chunk = retrieved_chunks[0]
            best_doc_id = best_chunk.knowledgeDocumentId or best_chunk.documentId
            
            reconstructed_full_text = ""
            if best_doc_id:
                try:
                    from app.database.neo4j_client import neo4j_client
                    if not neo4j_client.driver:
                        neo4j_client.connect()
                    
                    query = """
                    MATCH (d:Document {node_id: $doc_id})
                    MATCH (c:Chunk {source_path: d.source_path})
                    RETURN c.text as text, c.order as order
                    ORDER BY c.order ASC, c.node_id ASC
                    """
                    records = neo4j_client.execute_query(query, {"doc_id": best_doc_id})
                    if records:
                        reconstructed_full_text = "\n\n".join(rec["text"] for rec in records if rec["text"])
                        logger.info(f"Reconstructed full template document for ID {best_doc_id}, length {len(reconstructed_full_text)} chars")
                except Exception as e:
                    logger.error(f"Failed to reconstruct full template document: {e}")
            
            if reconstructed_full_text:
                template_name = best_chunk.fileName or best_chunk.title or "Matched Template"
                documents_text += f"Primary Template (Full Document: {template_name}):\n{reconstructed_full_text}\n\n"
                
                # Append other chunks as auxiliary references (skipping the primary document's chunks to avoid duplicates)
                aux_idx = 1
                for chunk in retrieved_chunks[1:]:
                    chunk_doc_id = chunk.knowledgeDocumentId or chunk.documentId
                    if chunk_doc_id != best_doc_id:
                        documents_text += f"Auxiliary Reference {aux_idx}:\n{chunk.chunkText}\n\n"
                        aux_idx += 1
            else:
                # Fallback to standard chunk list
                for idx, chunk in enumerate(retrieved_chunks, start=1):
                    documents_text += f"Reference Document {idx}:\n{chunk.chunkText}\n\n"
        else:
            documents_text = "[No reference documents found in knowledge base]"

        # If a document is active in the workspace, fetch its context to guide "similar contract" queries
        user_documents_text = ""
        user_hits = []
        if request.documentId:
            user_hits = self.retrieval_service.search_user_chunks(
                question=request.question,
                user_id=request.userId,
                workspace_id=request.workspaceId,
                top_k=5,
                document_id=request.documentId
            )
            if user_hits:
                user_documents_text = "\n\n-----------------------------------------------------\n\n# USER DOCUMENTS / ORIGINAL CONTRACT CONTEXT\n"
                for idx, hit in enumerate(user_hits, start=1):
                    user_documents_text += f"User Document Fragment {idx}:\n{hit.chunkText}\n\n"

        user_prompt = f"""# ROLE
You are an experienced legal professional specializing in Vietnamese administrative and legal documents.
Your responsibility is to generate a document based on legal reference documents retrieved from a knowledge base.

-----------------------------------------------------

# CONTEXT
The reference agreements below were retrieved from a legal knowledge base using semantic search.
These documents are examples only.
You must use them as legal guidance.
Never copy personal information from them.

-----------------------------------------------------

# REFERENCE DOCUMENTS
{documents_text}
{user_documents_text}

-----------------------------------------------------

# YOUR TASK
Carefully analyze the provided "Primary Template (Full Document)" and "Auxiliary References".
You MUST strictly follow the exact structure, sections, articles, layout, and sequence of the "Primary Template (Full Document)".
Keep all sections, headers, and clauses in the same order as in the Primary Template.
Do not omit any sections or articles. Adapt the contents, terms, pricing, names, and specifics to match the user's request while maintaining the exact same structure and formatting style.
Generate the document in the same language as the primary template and the user's request (typically Vietnamese).
If some user information is missing, insert placeholders enclosed in square brackets.

-----------------------------------------------------

# EXPECTED PLACEHOLDERS
If information is missing, use placeholders such as:
[Tên bên cho thuê / Landlord Name]
[Tên bên thuê / Tenant Name]
[Địa chỉ tài sản / Property Address]
[Ngày bắt đầu thuê / Lease Start Date]
[Ngày kết thúc thuê / Lease End Date]

-----------------------------------------------------

# AVOID RECITATION / COPYRIGHT BLOCKS
To prevent Google Gemini API recitation/copyright filters from cutting off the response mid-sentence, you MUST NOT copy the reference or user documents verbatim. Instead, professionally rephrase, paraphrase, and restructure the text into a clean legal draft while maintaining all original meanings, clauses, numbers, and facts. Ensure the response is complete and does not cut off. The document inside <noi_dung> MUST be a full-length, complete, detailed draft with all clauses, terms, rights, and responsibilities fully generated. Do NOT summarize, shorten, or simplify the content inside <noi_dung>.

# OUTPUT RULES
You MUST structure your entire response using the following XML-like tags:
<tom_tat>
[Write a brief summary of the generated document here in Vietnamese, about 2-3 sentences]
</tom_tat>
<noi_dung>
[Write the complete, detailed document ready for export as DOCX or PDF here. Follow the format guidelines below]
</noi_dung>

Format guidelines for the content inside <noi_dung>:
- Return only the document content.
- Do not explain anything.
- Do not mention the reference documents.
- Do not use Markdown inside <noi_dung>.
- Generate a document that is ready for export as DOCX or PDF.
- All Vietnamese administrative document rules (margins, font size, layout structure) will be applied during document rendering."""

        system_prompt = (
            "Bạn là chuyên gia định dạng văn bản hành chính và văn bản quy phạm pháp luật Việt Nam.\n"
            "Nhiệm vụ của bạn là định dạng nội dung thành đúng mẫu văn bản Quyết định của Chính phủ Việt Nam theo Nghị định 30/2020/NĐ-CP.\n\n"
            "YÊU CẦU QUAN TRỌNG: Để tránh bộ lọc sao chép (recitation/copyright filter) của API tự động cắt cụt văn bản giữa chừng, bạn TUYỆT ĐỐI KHÔNG sao chép nguyên văn các đoạn dài của tài liệu nguồn. Hãy diễn đạt lại (rephrase) các điều khoản bằng văn phong pháp lý chuyên nghiệp của bạn. Lưu ý: Đối với phần nội dung trong thẻ <noi_dung>, bạn phải giữ lại toàn bộ các điều khoản chi tiết, quyền lợi, nghĩa vụ, điều kiện và các mục pháp lý quan trọng của tài liệu gốc, không được phép lược bỏ hay tóm tắt nội dung chi tiết này.\n\n"
            "YÊU CẦU ĐẶC BIỆT VỀ CẤU TRÚC PHẢN HỒI (RẤT QUAN TRỌNG):\n"
            "Bạn phải trả về phản hồi chính xác dưới cấu trúc sau:\n"
            "1. Bọc phần tóm tắt ngắn gọn của tài liệu (2-3 câu) trong thẻ <tom_tat> và </tom_tat>.\n"
            "2. Bọc toàn bộ nội dung chi tiết của văn bản hành chính/hợp đồng đầy đủ (không được tóm tắt hay cắt bớt phần chi tiết này) trong thẻ <noi_dung> và </noi_dung>.\n\n"
            "Ví dụ minh họa cách định dạng và rephrase một văn bản dài thành Quyết định gọn gàng (Lưu ý ví dụ dưới đây rút ngắn để minh họa cấu trúc, còn khi thực hiện thực tế bạn phải sinh đầy đủ tất cả các điều khoản chi tiết):\n"
            "--- BẮT ĐẦU VÍ DỤ ---\n"
            "Đầu vào:\n"
            "\"Thông tư hướng dẫn về việc thu và quản lý tiền cho thuê nhà ở thuộc sở hữu nhà nước. Cán bộ công nhân viên chức và nhân dân thuê nhà đều phải nộp tiền... Đối tượng miễn giảm gồm thương binh, gia đình cách mạng...\"\n\n"
            "Đầu ra Quyết định:\n"
            "<tom_tat>\n"
            "Quyết định hướng dẫn bổ sung các khoản thu ngân sách nhà nước đối với hoạt động cho thuê nhà, quy định các đối tượng có nghĩa vụ nộp tiền thuê nhà và các trường hợp được miễn giảm theo quy định hiện hành.\n"
            "</tom_tat>\n"
            "<noi_dung>\n"
            "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM\n"
            "Độc lập - Tự do - Hạnh phúc\n"
            "---------------\n\n"
            "CHÍNH PHỦ\n\n"
            "Số: 08/QĐ-CP\n"
            "Hà Nội, ngày 10 tháng 2 năm 1993\n\n"
            "QUYẾT ĐỊNH\n"
            "Về việc ban hành Quy chế quản lý quỹ nhà ở thuộc sở hữu nhà nước\n\n"
            "THỦ TƯỚNG CHÍNH PHỦ\n\n"
            "Căn cứ Luật Tổ chức Chính phủ ngày 25 tháng 12 năm 2001;\n"
            "Căn cứ Quyết định số 118/TTg ngày 27 tháng 11 năm 1992 của Thủ tướng Chính phủ;\n"
            "Xét đề nghị của Bộ trưởng Bộ Xây dựng và Bộ trưởng Bộ Tài chính,\n\n"
            "QUYẾT ĐỊNH:\n\n"
            "Điều 1. Ban hành kèm theo Quyết định này \"Quy chế thu và quản lý tiền thuê nhà ở thuộc sở hữu nhà nước\".\n"
            "Điều 2. Quy định các đối tượng cán bộ, công chức, viên chức và người dân đang thuê nhà thuộc sở hữu nhà nước có nghĩa vụ nộp tiền thuê đầy đủ theo biểu giá do Ủy ban nhân dân cấp tỉnh ban hành.\n"
            "Điều 3. Thực hiện miễn, giảm tiền thuê nhà ở cho các đối tượng chính sách, thương bệnh binh và người có công theo quy định hiện hành.\n"
            "Điều 4. Quyết định này có hiệu lực kể từ ngày ký. Các Bộ trưởng, Thủ trưởng cơ quan ngang Bộ và Chủ tịch Ủy ban nhân dân các tỉnh chịu trách nhiệm thi hành Quyết định này.\n\n"
            "THỦ TƯỚNG CHÍNH PHỦ\n"
            "(Đã ký)\n"
            "</noi_dung>\n"
            "--- KẾT THÚC VÍ DỤ ---"
        )

        if not self.llm_enabled:
            logger.info("LLM_QUERY_ENABLED=false; returning contract prompt preview for request %s", request.requestId)
            return RagQueryResponse(
                requestId=request.requestId,
                chatSessionId=request.chatSessionId,
                answer=(
                    "[LLM PROMPT PREVIEW - PROMPT CHUA DUOC GUI TOI LLM]\n\n"
                    "===== SYSTEM PROMPT =====\n"
                    f"{system_prompt}\n\n"
                    "===== USER PROMPT =====\n"
                    f"{user_prompt}"
                ),
                confidenceScore=None,
                shouldSuggestTicket=False,
                suggestionType="NONE",
                suggestionReason="LLM preview mode is enabled.",
                missingInformation=None,
                riskLevel="NONE",
                legalDomain="Contract Law",
                userActionHint="CONTINUE_CHAT",
                citations=[],
                retrievedUserChunks=len(user_hits),
                retrievedKnowledgeChunks=len(retrieved_chunks),
                model="prompt-preview",
                usage=RagUsage(promptTokens=0, completionTokens=0, totalTokens=0),
                llmExecuted=False,
                systemPromptPreview=system_prompt,
                userPromptPreview=user_prompt,
            )

        # Call LLM to generate contract
        llm_result = self.llm_client.generate(system_prompt=system_prompt, user_prompt=user_prompt)

        logger.info("=== CONTRACT GENERATION SERVICE LOGGING ===")
        logger.info(f"System Prompt:\n{system_prompt}")
        logger.info(f"User Prompt:\n{user_prompt}")
        logger.info(f"Raw Gemini Response:\n{llm_result.raw_response}")
        logger.info("===========================================")

        if llm_result.error:
            logger.error(f"Gemini API generation failed: {llm_result.error}")
            raise HTTPException(status_code=503, detail=f"Gemini API Error: {llm_result.error}")

        raw_answer = llm_result.answer or "Failed to generate contract."
        from app.services.llm_client import sanitize_response
        answer_sanitized = sanitize_response(raw_answer)

        # Parse tags to separate summary and detailed content
        summary_text = ""
        content_text = ""

        summary_match = re.search(r'<tom_tat>(.*?)</tom_tat>', answer_sanitized, re.DOTALL | re.IGNORECASE)
        content_match = re.search(r'<noi_dung>(.*?)</noi_dung>', answer_sanitized, re.DOTALL | re.IGNORECASE)

        if summary_match:
            summary_text = summary_match.group(1).strip()
        if content_match:
            content_text = content_match.group(1).strip()

        # Fallback logic if tags are missing or empty
        if not content_text:
            content_text = re.sub(r'</?(?:tom_tat|noi_dung)>', '', answer_sanitized).strip()
            summary_text = "Hợp đồng đã được tạo lập thành công. Vui lòng tải về file DOCX/PDF bên dưới để xem toàn bộ nội dung chi tiết."

        if not summary_text:
            summary_text = "Hợp đồng đã được tạo lập thành công. Vui lòng tải về file DOCX/PDF bên dưới để xem toàn bộ nội dung chi tiết."

        # The final answer returned in chat will show the summary
        answer = summary_text

        # Parse the detailed content (instead of answer) to extract official header components dynamically
        lines = content_text.split("\n")
        national_name = ""
        national_motto = ""
        doc_date = ""
        issuing_authority = ""
        doc_number = ""
        contract_title = ""
        
        # Identify the indices of header metadata lines to separate them from the document body text
        header_indices = set()
        for idx, line in enumerate(lines):
            line_stripped = line.strip()
            if not line_stripped:
                continue

            # Check for combined national name and motto in one line
            if "CỘNG HÒA" in line_stripped.upper() and ("ĐỘC LẬP" in line_stripped.upper() or "TỰ DO" in line_stripped.upper() or "HẠNH PHÚC" in line_stripped.upper()):
                parts = re.split(r'(?i)(?=độc lập|tự do|hạnh phúc|độc\s+lập)', line_stripped)
                national_name = parts[0].strip().replace("*", "").replace("#", "")
                if len(parts) > 1:
                    national_motto = "".join(parts[1:]).strip().replace("*", "").replace("#", "")
                header_indices.add(idx)
                continue

            # National name
            if ("CỘNG HÒA" in line_stripped.upper() or "CONG HOA" in line_stripped.upper()) and not national_name:
                national_name = line_stripped.replace("*", "").replace("#", "").strip()
                header_indices.add(idx)
                continue

            # National motto
            if ("ĐỘC LẬP" in line_stripped.upper() or "DOC LAP" in line_stripped.upper() or "TỰ DO" in line_stripped.upper() or "HẠNH PHÚC" in line_stripped.upper() or "HANH PHUC" in line_stripped.upper()) and not national_motto:
                national_motto = line_stripped.replace("*", "").replace("#", "").strip()
                header_indices.add(idx)
                continue

            # Date
            if ("ngày" in line_stripped.lower() and "tháng" in line_stripped.lower() and "năm" in line_stripped.lower() and len(line_stripped) < 60) and not doc_date:
                doc_date = line_stripped.replace("*", "").replace("#", "").strip()
                header_indices.add(idx)
                continue

            # Doc Number
            if (line_stripped.upper().startswith("SỐ:") or line_stripped.upper().startswith("SO:")) and not doc_number:
                doc_number = line_stripped.replace("*", "").replace("#", "").strip()
                header_indices.add(idx)
                continue

            # Issuing Authority (before title)
            if ("THỦ TƯỚNG" in line_stripped.upper() or "ỦY BAN" in line_stripped.upper() or "UBND" in line_stripped.upper() or "BỘ" in line_stripped.upper() or "HỘI ĐỒNG" in line_stripped.upper()) and not contract_title and not issuing_authority:
                issuing_authority = line_stripped.replace("*", "").replace("#", "").strip()
                header_indices.add(idx)
                continue

            # Detect Title
            if not contract_title and ("QUYẾT ĐỊNH" in line_stripped.upper() or "QUYET DINH" in line_stripped.upper() or "HỢP ĐỒNG" in line_stripped.upper() or "HOP DONG" in line_stripped.upper() or "QUY CHẾ" in line_stripped.upper() or "QUY CHE" in line_stripped.upper() or "THÔNG TƯ" in line_stripped.upper() or "THONG TU" in line_stripped.upper() or "HƯỚNG DẪN" in line_stripped.upper() or "HUONG DAN" in line_stripped.upper()):
                contract_title = line_stripped.replace("*", "").replace("#", "").strip()
                header_indices.add(idx)
                continue

        # Filter out the header indices to get the remaining text
        remaining_lines = [lines[i] for i in range(len(lines)) if i not in header_indices]

        # Apply defaults if anything was missing
        if not national_name:
            national_name = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM"
        if not national_motto:
            national_motto = "Độc lập - Tự do - Hạnh phúc"
        if not doc_date:
            now = datetime.datetime.now()
            doc_date = f"Hà Nội, ngày {now.strftime('%d')} tháng {now.strftime('%m')} năm {now.strftime('%Y')}"
        if not issuing_authority:
            issuing_authority = "THỦ TƯỚNG CHÍNH PHỦ"
        if not doc_number:
            doc_number = "Số: ....../QĐ-TTg"
        if not contract_title:
            contract_title = "QUYẾT ĐỊNH"

        # Separate Decision Title and the Description (Decision name/subject)
        decision_name = ""
        preamble_lines = []
        body_lines = []

        is_decision = "QUYẾT ĐỊNH" in contract_title.upper() or "QUYET DINH" in contract_title.upper()
        
        # Check if the remaining lines have a "QUYẾT ĐỊNH:" transition
        transition_index = -1
        if is_decision:
            for idx, line in enumerate(remaining_lines):
                line_stripped = line.strip()
                if line_stripped.upper() in ["QUYẾT ĐỊNH:", "QUYET DINH:", "QUYẾT ĐỊNH", "QUYET DINH"]:
                    transition_index = idx
                    break

        if is_decision and transition_index != -1:
            preamble_lines_raw = remaining_lines[:transition_index]
            body_lines_raw = remaining_lines[transition_index+1:]
            
            # Extract decision name if it starts with "Về việc" in preamble lines
            for l in preamble_lines_raw:
                l_stripped = l.strip()
                if not l_stripped:
                    continue
                if not decision_name and (l_stripped.lower().startswith("về việc") or l_stripped.lower().startswith("ve viec")):
                    decision_name = l_stripped
                else:
                    preamble_lines.append(l_stripped)
                    
            for l in body_lines_raw:
                if l.strip():
                    body_lines.append(l.strip())
        else:
            # For Circulars, Contracts, or other documents: do not split. Print everything as body lines.
            for l in remaining_lines:
                if l.strip():
                    body_lines.append(l.strip())

        # Extract signature and recipients from body_lines to format them separately
        signature_lines = []
        recipients_lines = []
        
        # 1. Scan from the end of body_lines to find the "Nơi nhận" (recipients) block first
        recipients_start_idx = -1
        scan_start = max(0, len(body_lines) - 10)
        for i in range(len(body_lines) - 1, scan_start - 1, -1):
            line_stripped = body_lines[i].strip()
            if line_stripped.lower().startswith("nơi nhận:") or line_stripped.lower().startswith("noi nhan:"):
                recipients_start_idx = i
                break
                
        if recipients_start_idx != -1:
            recipients_lines = [l.strip() for l in body_lines[recipients_start_idx:]]
            body_lines = body_lines[:recipients_start_idx]

        # 2. Scan from the end of remaining body_lines to find the signature block
        signature_start_idx = -1
        scan_start = max(0, len(body_lines) - 12)
        for i in range(len(body_lines) - 1, scan_start - 1, -1):
            line_stripped = body_lines[i].strip()
            line_upper = line_stripped.upper()
            if len(line_stripped) < 60 and (
                line_upper.startswith("TM.") or 
                line_upper.startswith("KT.") or 
                line_upper.startswith("Q.") or 
                line_upper.startswith("THỦ TƯỚNG") or 
                "CHỦ TỊCH" in line_upper or 
                "GIÁM ĐỐC" in line_upper or 
                "BỘ TRƯỞNG" in line_upper or
                "HIỆU TRƯỞNG" in line_upper or
                "ĐẠI DIỆN" in line_upper or
                line_upper == "ĐÃ KÝ" or
                line_upper == "(ĐÃ KÝ)" or
                line_upper == "(DA KY)"
            ):
                signature_start_idx = i
                
        if signature_start_idx != -1:
            signature_lines = [l.strip() for l in body_lines[signature_start_idx:]]
            body_lines = body_lines[:signature_start_idx]

        backend_base_url = os.getenv("BACKEND_BASE_URL", "http://backend:8080")
        download_links = []

        # Helper to format paragraph text (bold Điều x.)
        def format_paragraph_text(text: str) -> str:
            match = re.match(r'^(Điều\s+\d+\.)(.*)', text, re.IGNORECASE)
            if match:
                return f"<b>{match.group(1)}</b>{match.group(2)}"
            return text

        # 1. Generate PDF File
        try:
            pdf_id = "doc_" + uuid.uuid4().hex
            pdf_filename = f"{pdf_id}_Generated_Contract.pdf"
            pdf_path = f"/app/uploads/{pdf_filename}"

            from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
            from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
            from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY
            from reportlab.pdfbase import pdfmetrics
            from reportlab.pdfbase.ttfonts import TTFont

            # Strict A4 sizing & margins (Top: 2cm, Bottom: 2cm, Left: 3.5cm, Right: 2cm)
            # 2 cm = 56.69 points, 3.5 cm = 99.21 points
            # A4 = 595.27 x 841.89 points
            a4_width, a4_height = 595.27, 841.89
            left_margin = 99.21
            right_margin = 56.69
            top_margin = 56.69
            bottom_margin = 56.69

            try:
                # Try to register LiberationSerif (Times New Roman fallback)
                pdfmetrics.registerFont(TTFont('TimesNewRoman', '/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf'))
                pdfmetrics.registerFont(TTFont('TimesNewRoman-Bold', '/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf'))
                pdfmetrics.registerFont(TTFont('TimesNewRoman-Italic', '/usr/share/fonts/truetype/liberation/LiberationSerif-Italic.ttf'))
                font_name = 'TimesNewRoman'
            except Exception:
                try:
                    pdfmetrics.registerFont(TTFont('TimesNewRoman', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'))
                    pdfmetrics.registerFont(TTFont('TimesNewRoman-Bold', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'))
                    font_name = 'TimesNewRoman'
                except Exception:
                    font_name = 'Helvetica'

            doc = SimpleDocTemplate(
                pdf_path, 
                pagesize=(a4_width, a4_height), 
                rightMargin=right_margin, 
                leftMargin=left_margin, 
                topMargin=top_margin, 
                bottomMargin=bottom_margin
            )
            styles = getSampleStyleSheet()

            title_style = ParagraphStyle(
                'DocTitle',
                fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                fontSize=16,
                leading=22,
                alignment=TA_CENTER,
                spaceBefore=15,
                spaceAfter=10
            )
            decision_name_style = ParagraphStyle(
                'DecisionName',
                fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                fontSize=15,
                leading=20,
                alignment=TA_CENTER,
                spaceAfter=15
            )
            preamble_head_style = ParagraphStyle(
                'PreambleHead',
                fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                fontSize=14,
                leading=18,
                alignment=TA_CENTER,
                spaceAfter=10
            )
            body_style = ParagraphStyle(
                'DocBody',
                fontName=font_name,
                fontSize=14,
                leading=21, # 1.5 line spacing (14 * 1.5 = 21)
                alignment=TA_JUSTIFY,
                spaceBefore=0,
                spaceAfter=6
            )
            quyet_dinh_head_style = ParagraphStyle(
                'QuyetDinhHead',
                fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                fontSize=15,
                leading=20,
                alignment=TA_CENTER,
                spaceBefore=10,
                spaceAfter=15
            )

            story = []

            # 2-column Table Header (Printable width = 439.37)
            cell_left_style = ParagraphStyle(
                'CellLeft',
                fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                fontSize=13,
                leading=16,
                alignment=TA_CENTER
            )
            cell_right_style = ParagraphStyle(
                'CellRight',
                fontName=font_name,
                fontSize=13,
                leading=16,
                alignment=TA_CENTER
            )
            
            left_text = f"<b>{issuing_authority.upper()}</b><br/>______<br/>{doc_number}"
            right_text = f"<b>{national_name.upper()}</b><br/><b>{national_motto}</b><br/>___________<br/><i>{doc_date}</i>"
            
            header_table_data = [
                [Paragraph(left_text, cell_left_style), Paragraph(right_text, cell_right_style)]
            ]
            
            header_table = Table(header_table_data, colWidths=[170, 269])
            header_table.setStyle(TableStyle([
                ('VALIGN', (0,0), (-1,-1), 'TOP'),
                ('BOTTOMPADDING', (0,0), (-1,-1), 15),
            ]))
            story.append(header_table)
            story.append(Spacer(1, 10))

            # Document Title
            story.append(Paragraph(contract_title.upper(), title_style))
            
            # Decision Name
            if decision_name:
                story.append(Paragraph(decision_name, decision_name_style))
                story.append(Paragraph("______", cell_right_style))
                story.append(Spacer(1, 15))

            # Preamble (if any exists)
            if preamble_lines:
                story.append(Paragraph(issuing_authority.upper(), preamble_head_style))
                story.append(Spacer(1, 5))
                for line in preamble_lines:
                    if line.strip():
                        story.append(Paragraph(line, body_style))
                
                # QUYẾT ĐỊNH:
                story.append(Spacer(1, 10))
                story.append(Paragraph("QUYẾT ĐỊNH:", quyet_dinh_head_style))
                story.append(Spacer(1, 10))

            # Articles / Body lines
            for line in body_lines:
                if line.strip():
                    story.append(Paragraph(format_paragraph_text(line), body_style))

            # Create a 2-column table for recipients and signature at the end
            if signature_lines or recipients_lines:
                recipients_paragraphs = []
                if recipients_lines:
                    rec_title_style = ParagraphStyle(
                        'RecTitle',
                        fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                        fontSize=11,
                        leading=14,
                        alignment=0 # Left
                    )
                    rec_item_style = ParagraphStyle(
                        'RecItem',
                        fontName=font_name,
                        fontSize=10,
                        leading=13,
                        alignment=0 # Left
                    )
                    for idx, line in enumerate(recipients_lines):
                        if idx == 0:
                            recipients_paragraphs.append(Paragraph(line, rec_title_style))
                        else:
                            recipients_paragraphs.append(Paragraph(line, rec_item_style))
                else:
                    recipients_paragraphs.append(Paragraph("", body_style))
                    
                sig_paragraphs = []
                if signature_lines:
                    sig_title_style = ParagraphStyle(
                        'SigTitle',
                        fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                        fontSize=13,
                        leading=16,
                        alignment=TA_CENTER
                    )
                    sig_italic_style = ParagraphStyle(
                        'SigItalic',
                        fontName=font_name,
                        fontSize=12,
                        leading=15,
                        alignment=TA_CENTER
                    )
                    sig_name_style = ParagraphStyle(
                        'SigName',
                        fontName=font_name + '-Bold' if font_name == 'TimesNewRoman' else font_name,
                        fontSize=13,
                        leading=16,
                        alignment=TA_CENTER
                    )
                    
                    for idx, line in enumerate(signature_lines):
                        # Check if last line is a name (and not a signature action)
                        if idx == len(signature_lines) - 1 and not ("ĐÃ KÝ" in line.upper() or "DA KY" in line.upper() or line.startswith("(")):
                            sig_paragraphs.append(Spacer(1, 45))
                            sig_paragraphs.append(Paragraph(line, sig_name_style))
                        elif "ĐÃ KÝ" in line.upper() or "DA KY" in line.upper():
                            if idx == len(signature_lines) - 1:
                                sig_paragraphs.append(Spacer(1, 30))
                            sig_paragraphs.append(Paragraph(f"<i>{line}</i>", sig_italic_style))
                        else:
                            sig_paragraphs.append(Paragraph(line.upper(), sig_title_style))
                else:
                    sig_paragraphs.append(Paragraph("", body_style))
                    
                sig_table_data = [[recipients_paragraphs, sig_paragraphs]]
                sig_table = Table(sig_table_data, colWidths=[200, 239])
                sig_table.setStyle(TableStyle([
                    ('VALIGN', (0,0), (-1,-1), 'TOP'),
                    ('LEFTPADDING', (0,0), (-1,-1), 0),
                    ('RIGHTPADDING', (0,0), (-1,-1), 0),
                    ('BOTTOMPADDING', (0,0), (-1,-1), 0),
                    ('TOPPADDING', (0,0), (-1,-1), 15),
                ]))
                story.append(sig_table)

            doc.build(story)
            pdf_size = os.path.getsize(pdf_path)

            # Register PDF document in backend
            pdf_payload = {
                "workspaceId": request.workspaceId,
                "userId": request.userId,
                "originalFileName": "Generated_Contract.pdf",
                "storedFileName": pdf_filename,
                "filePath": pdf_path,
                "fileSize": pdf_size
            }

            with httpx.Client(timeout=10.0) as client:
                resp = client.post(f"{backend_base_url}/api/internal/documents/register-generated", json=pdf_payload)
                if resp.status_code == 200:
                    reg_pdf_id = resp.json().get("data", {}).get("documentId") or pdf_id
                    download_links.append(f"📥 [Tải về hợp đồng (PDF)](http://localhost:8080/api/v1/workspaces/{request.workspaceId}/documents/{reg_pdf_id}/download)")
                else:
                    logger.error(f"Failed to register PDF: {resp.text}")
        except Exception as pe:
            logger.error(f"Failed to generate and register PDF file: {pe}")

        # 2. Generate DOCX File
        try:
            docx_id = "doc_" + uuid.uuid4().hex
            docx_filename = f"{docx_id}_Generated_Contract.docx"
            docx_path = f"/app/uploads/{docx_filename}"

            import docx
            from docx.shared import Pt, Cm
            from docx.enum.text import WD_ALIGN_PARAGRAPH

            doc = docx.Document()
            
            # Setup A4 & Margins
            section = doc.sections[0]
            section.page_width = Cm(21.0)
            section.page_height = Cm(29.7)
            section.top_margin = Cm(2.0)
            section.bottom_margin = Cm(2.0)
            section.left_margin = Cm(3.5)
            section.right_margin = Cm(2.0)

            # Define default normal Times New Roman style
            normal_style = doc.styles['Normal']
            normal_style.font.name = 'Times New Roman'
            normal_style.font.size = Pt(14)
            normal_style.paragraph_format.line_spacing = 1.5
            normal_style.paragraph_format.space_after = Pt(6)
            normal_style.paragraph_format.space_before = Pt(0)
            normal_style.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

            # Setup Header utilizing a 2-column borderless table
            # Left column: issuing_authority & doc_number
            # Right column: national_name, national_motto & doc_date
            header_table = doc.add_table(rows=1, cols=2)
            header_table.style = 'Normal Table'
            header_table.autofit = False
            
            # Set widths (Printable width = 15.5 cm)
            for row in header_table.rows:
                row.cells[0].width = Cm(6.5)
                row.cells[1].width = Cm(9.0)
                
            cell_left = header_table.cell(0, 0)
            cell_right = header_table.cell(0, 1)
            
            # Left Cell: Issuing Authority & Doc Number
            p_left = cell_left.paragraphs[0]
            p_left.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_left.paragraph_format.space_before = Pt(0)
            p_left.paragraph_format.space_after = Pt(2)
            p_left.paragraph_format.line_spacing = 1.15
            
            # Split issuing authority if it starts with "ỦY BAN NHÂN DÂN" and is long
            auth_lines = []
            if issuing_authority.upper().startswith("ỦY BAN NHÂN DÂN") and len(issuing_authority) > 20:
                auth_lines.append("ỦY BAN NHÂN DÂN")
                auth_lines.append(issuing_authority[15:].strip())
            else:
                auth_lines.append(issuing_authority)
                
            for i, auth_line in enumerate(auth_lines):
                if i > 0:
                    p_left = cell_left.add_paragraph()
                    p_left.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    p_left.paragraph_format.space_before = Pt(0)
                    p_left.paragraph_format.space_after = Pt(2)
                    p_left.paragraph_format.line_spacing = 1.15
                run_auth = p_left.add_run(auth_line.upper())
                run_auth.font.name = 'Times New Roman'
                run_auth.bold = True
                run_auth.font.size = Pt(12)
                
            # Line separator
            p_sep_left = cell_left.add_paragraph()
            p_sep_left.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_sep_left.paragraph_format.space_before = Pt(0)
            p_sep_left.paragraph_format.space_after = Pt(2)
            p_sep_left.paragraph_format.line_spacing = 1.15
            run_sep_left = p_sep_left.add_run("______")
            run_sep_left.font.name = 'Times New Roman'
            run_sep_left.font.size = Pt(12)
            
            # Doc Number
            p_num = cell_left.add_paragraph()
            p_num.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_num.paragraph_format.space_before = Pt(0)
            p_num.paragraph_format.space_after = Pt(2)
            p_num.paragraph_format.line_spacing = 1.15
            run_num = p_num.add_run(doc_number)
            run_num.font.name = 'Times New Roman'
            run_num.font.size = Pt(12)
            
            # Right Cell: National Name, Motto & Date
            p_right = cell_right.paragraphs[0]
            p_right.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_right.paragraph_format.space_before = Pt(0)
            p_right.paragraph_format.space_after = Pt(2)
            p_right.paragraph_format.line_spacing = 1.15
            
            run_nat = p_right.add_run(national_name.upper())
            run_nat.font.name = 'Times New Roman'
            run_nat.bold = True
            run_nat.font.size = Pt(12)
            
            # Motto
            p_motto = cell_right.add_paragraph()
            p_motto.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_motto.paragraph_format.space_before = Pt(0)
            p_motto.paragraph_format.space_after = Pt(2)
            p_motto.paragraph_format.line_spacing = 1.15
            run_motto = p_motto.add_run(national_motto)
            run_motto.font.name = 'Times New Roman'
            run_motto.bold = True
            run_motto.font.size = Pt(13)
            
            # Line separator
            p_sep_right = cell_right.add_paragraph()
            p_sep_right.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_sep_right.paragraph_format.space_before = Pt(0)
            p_sep_right.paragraph_format.space_after = Pt(2)
            p_sep_right.paragraph_format.line_spacing = 1.15
            run_sep_right = p_sep_right.add_run("___________")
            run_sep_right.font.name = 'Times New Roman'
            run_sep_right.font.size = Pt(13)
            
            # Doc Date
            p_date = cell_right.add_paragraph()
            p_date.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_date.paragraph_format.space_before = Pt(0)
            p_date.paragraph_format.space_after = Pt(2)
            p_date.paragraph_format.line_spacing = 1.15
            run_date = p_date.add_run(doc_date)
            run_date.font.name = 'Times New Roman'
            run_date.italic = True
            run_date.font.size = Pt(13)

            # Add spacer below header
            p_space = doc.add_paragraph()
            p_space.paragraph_format.space_before = Pt(15)
            p_space.paragraph_format.space_after = Pt(0)

            # QUYẾT ĐỊNH Title (cỡ 16, Bold)
            p_title = doc.add_paragraph()
            p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_title.paragraph_format.space_before = Pt(15)
            r_title = p_title.add_run(contract_title.upper())
            r_title.bold = True
            r_title.font.size = Pt(16)

            # Decision name (cỡ 15, Bold)
            if decision_name:
                p_dec = doc.add_paragraph()
                p_dec.alignment = WD_ALIGN_PARAGRAPH.CENTER
                r_dec = p_dec.add_run(decision_name)
                r_dec.bold = True
                r_dec.font.size = Pt(15)
                
                # Underline under decision name
                p_line = doc.add_paragraph()
                p_line.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p_line.add_run("______")

            # Preamble (only if exists)
            if preamble_lines:
                p_pre_head = doc.add_paragraph()
                p_pre_head.alignment = WD_ALIGN_PARAGRAPH.CENTER
                r_pre_head = p_pre_head.add_run(issuing_authority.upper())
                r_pre_head.bold = True
                r_pre_head.font.size = Pt(14)

                for line in preamble_lines:
                    if line.strip():
                        p_pre = doc.add_paragraph()
                        p_pre.paragraph_format.first_line_indent = Cm(1.0)
                        p_pre.add_run(line)

                p_qdh = doc.add_paragraph()
                p_qdh.alignment = WD_ALIGN_PARAGRAPH.CENTER
                r_qdh = p_qdh.add_run("QUYẾT ĐỊNH:")
                r_qdh.bold = True
                r_qdh.font.size = Pt(15)

            # Articles (with 1cm indentation, bold "Điều x.")
            for line in body_lines:
                if line.strip():
                    p_art = doc.add_paragraph()
                    p_art.paragraph_format.first_line_indent = Cm(1.0)
                    
                    match = re.match(r'^(Điều\s+\d+\.)(.*)', line, re.IGNORECASE)
                    if match:
                        r_art_num = p_art.add_run(match.group(1))
                        r_art_num.bold = True
                        p_art.add_run(match.group(2))
                    else:
                        p_art.add_run(line)

            # Add recipients and signature table
            if signature_lines or recipients_lines:
                # Add some spacing before the signature block
                p_space = doc.add_paragraph()
                p_space.paragraph_format.space_before = Pt(12)
                p_space.paragraph_format.space_after = Pt(0)
                
                sig_table = doc.add_table(rows=1, cols=2)
                sig_table.style = 'Normal Table'
                sig_table.autofit = False
                
                for row in sig_table.rows:
                    row.cells[0].width = Cm(6.5)
                    row.cells[1].width = Cm(9.0)
                    
                cell_left = sig_table.cell(0, 0)
                cell_right = sig_table.cell(0, 1)
                
                # 1. Left cell: Recipients (Nơi nhận)
                p_left = cell_left.paragraphs[0]
                p_left.alignment = WD_ALIGN_PARAGRAPH.LEFT
                p_left.paragraph_format.space_before = Pt(0)
                p_left.paragraph_format.space_after = Pt(2)
                p_left.paragraph_format.line_spacing = 1.15
                
                if recipients_lines:
                    for idx, line in enumerate(recipients_lines):
                        if idx > 0:
                            p_left = cell_left.add_paragraph()
                            p_left.alignment = WD_ALIGN_PARAGRAPH.LEFT
                            p_left.paragraph_format.space_before = Pt(0)
                            p_left.paragraph_format.space_after = Pt(2)
                            p_left.paragraph_format.line_spacing = 1.15
                        
                        run = p_left.add_run(line)
                        run.font.name = 'Times New Roman'
                        if idx == 0:
                            run.bold = True
                            run.italic = True
                            run.font.size = Pt(11)
                        else:
                            run.font.size = Pt(10)
                else:
                    p_left.add_run("")
                    
                # 2. Right cell: Signature
                p_right = cell_right.paragraphs[0]
                p_right.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p_right.paragraph_format.space_before = Pt(0)
                p_right.paragraph_format.space_after = Pt(2)
                p_right.paragraph_format.line_spacing = 1.15
                
                if signature_lines:
                    for idx, line in enumerate(signature_lines):
                        if idx > 0:
                            p_right = cell_right.add_paragraph()
                            p_right.alignment = WD_ALIGN_PARAGRAPH.CENTER
                            p_right.paragraph_format.space_before = Pt(0)
                            p_right.paragraph_format.space_after = Pt(2)
                            p_right.paragraph_format.line_spacing = 1.15
                        
                        # Last line is name of signer (if not signature action)
                        if idx == len(signature_lines) - 1 and not ("ĐÃ KÝ" in line.upper() or "DA KY" in line.upper() or line.startswith("(")):
                            p_right.paragraph_format.space_before = Pt(45) # gap for signature
                            run = p_right.add_run(line)
                            run.font.name = 'Times New Roman'
                            run.bold = True
                            run.font.size = Pt(13)
                        elif "ĐÃ KÝ" in line.upper() or "DA KY" in line.upper():
                            if idx == len(signature_lines) - 1:
                                p_right.paragraph_format.space_before = Pt(30)
                            run = p_right.add_run(line)
                            run.font.name = 'Times New Roman'
                            run.italic = True
                            run.font.size = Pt(12)
                        else:
                            run = p_right.add_run(line.upper())
                            run.font.name = 'Times New Roman'
                            run.bold = True
                            run.font.size = Pt(13)
                else:
                    p_right.add_run("")

            doc.save(docx_path)
            docx_size = os.path.getsize(docx_path)

            # Register DOCX document in backend
            docx_payload = {
                "workspaceId": request.workspaceId,
                "userId": request.userId,
                "originalFileName": "Generated_Contract.docx",
                "storedFileName": docx_filename,
                "filePath": docx_path,
                "fileSize": docx_size
            }

            with httpx.Client(timeout=10.0) as client:
                resp = client.post(f"{backend_base_url}/api/internal/documents/register-generated", json=docx_payload)
                if resp.status_code == 200:
                    reg_docx_id = resp.json().get("data", {}).get("documentId") or docx_id
                    download_links.append(f"📝 [Tải về hợp đồng (DOCX)](http://localhost:8080/api/v1/workspaces/{request.workspaceId}/documents/{reg_docx_id}/download)")
                else:
                    logger.error(f"Failed to register DOCX: {resp.text}")
        except Exception as de:
            logger.error(f"Failed to generate and register DOCX file: {de}")

        # 3. Append download links to response
        if download_links:
            answer += "\n\n---\n\n🎉 **Hợp đồng đã được xuất thành công dưới các dạng tệp tải về!**\n" + "\n".join(download_links)

        # Convert hits to citations
        citations = []
        combined_hits = list(user_hits) + list(retrieved_chunks)
        for index, hit in enumerate(combined_hits, start=1):
            citations.append(
                RagCitation(
                    citationId=hit.citationId,
                    sourceType=hit.sourceType,
                    score=hit.score,
                    documentId=hit.documentId,
                    workspaceId=hit.workspaceId,
                    userId=hit.userId,
                    fileName=hit.fileName,
                    knowledgeDocumentId=hit.knowledgeDocumentId,
                    lawName=hit.lawName,
                    lawCode=hit.lawCode,
                    legalDomain=hit.legalDomain,
                    pageNumber=hit.pageNumber,
                    articleNumber=hit.articleNumber,
                    clauseNumber=hit.clauseNumber,
                    sectionTitle=hit.sectionTitle,
                )
            )

        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=llm_result.confidence_score or 1.0,
            shouldSuggestTicket=False,
            suggestionType="NONE",
            suggestionReason=None,
            missingInformation=None,
            riskLevel="LOW",
            legalDomain="Contract Law",
            userActionHint="CONTINUE_CHAT",
            citations=citations,
            retrievedUserChunks=len(user_hits),
            retrievedKnowledgeChunks=len(retrieved_chunks),
            llmExecuted=True,
        )
