"""Gemini LLM service for answer generation."""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any, Dict, List, Sequence

from app.config import settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class LegalQueryResult:
    """Structured response for legal query answers and ticket-suggestion metadata."""

    answer: str | None
    confidence_score: float | None
    should_suggest_ticket: bool
    suggestion_type: str
    suggestion_reason: str | None
    missing_information: str | None
    risk_level: str
    legal_domain: str | None
    user_action_hint: str
    error: str | None = None


def build_legal_review_prompt(
    question: str,
    checklist_results: List[Dict[str, Any]],
    knowledge_chunks: List[Dict[str, Any]],
) -> str:
    """Build the full legal review prompt for Gemini.

    The prompt is intentionally verbose and structured so the model can:
    - stay grounded in contract evidence,
    - reason like a Vietnamese contract reviewer,
    - return a readable markdown report,
    - avoid hallucinating legal citations.
    """
    question_text = _normalize_text(question)
    checklist_context = _format_checklist_results(checklist_results)
    knowledge_context = _format_knowledge_chunks(knowledge_chunks)
    output_template = _build_output_template()

    prompt_parts = [
        "SYSTEM",
        "Bạn là chuyên gia pháp lý Việt Nam chuyên rà soát hợp đồng thuê nhà ở.",
        "Nhiệm vụ của bạn là đọc dữ liệu được cung cấp và viết một báo cáo rà soát hợp đồng bằng Markdown rõ ràng, có tính pháp lý cao, dễ đọc cho người dùng cuối.",
        "",
        "NGUYÊN TẮC BẮT BUỘC",
        "1. Chỉ sử dụng 3 nguồn dữ liệu được cung cấp trong prompt này:",
        "   - nội dung hợp đồng / trích đoạn hợp đồng trong checklist findings",
        "   - checklist findings",
        "   - knowledge chunks pháp lý",
        "2. Không suy đoán, không tự thêm sự kiện, không tự tạo điều khoản hoặc án lệ.",
        "3. Không được kết luận vi phạm chỉ vì không tìm thấy điều khoản.",
        '4. Nếu không tìm thấy thông tin trong dữ liệu được phân tích, phải ghi đúng câu: "Không tìm thấy thông tin trong tài liệu được phân tích".',
        '5. Nếu knowledge chunks chưa đủ căn cứ pháp lý, phải ghi đúng câu: "Không đủ căn cứ pháp lý để kết luận".',
        "6. Mọi nhận định pháp lý phải có căn cứ và phải nêu tối thiểu:",
        "   - tên văn bản",
        "   - số hiệu văn bản",
        "   - điều",
        "   - khoản nếu có",
        "7. Không hiển thị source id nội bộ trong phần kết luận chính, ví dụ: UNIVERSAL_DEPOSIT_ADVANCE_V1.",
        "8. Không trả về JSON, không trả về bảng, không dump dữ liệu thô. Chỉ trả về Markdown/text có cấu trúc.",
        "9. Nếu không đủ căn cứ để khẳng định, hãy nói rõ mức độ chưa đủ và nêu điều gì còn thiếu.",
        "10. Khi nói về rủi ro, phải giải thích rõ:",
        "   - rủi ro là gì",
        "   - tranh chấp nào có thể phát sinh",
        "   - vì sao điều khoản đó chưa phù hợp",
        "   - căn cứ pháp lý nào đang được dùng",
        "",
        "CÁCH SUY LUẬN CẦN THỰC HIỆN",
        "1. Đọc checklist findings để xác định vấn đề cần rà soát.",
        "2. Đối chiếu từng vấn đề với evidence từ hợp đồng.",
        "3. Chỉ dùng knowledge chunks để xác nhận hoặc bác bỏ nhận định pháp lý.",
        "4. Nếu evidence đủ, hãy kết luận có rủi ro gì, ở mức nào, và nên sửa câu chữ nào.",
        "5. Nếu evidence chưa đủ, hãy nêu rõ thiếu gì và dừng ở mức không đủ căn cứ.",
        "6. Ưu tiên viết câu ngắn, rõ, theo đúng format đầu ra.",
        "",
        "NGƯỜI DÙNG HỎI",
        question_text,
        "",
        "CHECKLIST FINDINGS",
        checklist_context,
        "",
        "KNOWLEDGE CHUNKS",
        knowledge_context,
        "",
        "OUTPUT FORMAT BẮT BUỘC",
        output_template,
        "",
        "LƯU Ý CUỐI CÙNG",
        "Hãy viết như một luật sư rà soát hợp đồng thực thụ. Không nói lan man. Không lặp lại nguyên văn dữ liệu đầu vào nếu không cần thiết. Không dùng source id nội bộ trong phần kết luận. Nếu có nhiều vấn đề, hãy đánh số và giữ đúng thứ tự ưu tiên.",
    ]
    return "\n".join(prompt_parts)


def build_legal_query_prompt(
    question: str,
    user_chunks: List[Dict[str, Any]],
    knowledge_chunks: List[Dict[str, Any]],
) -> str:
    """Build a grounded legal query prompt."""
    question_text = _normalize_text(question)
    user_context = _format_query_user_chunks(user_chunks)
    knowledge_context = _format_query_knowledge_chunks(knowledge_chunks)

    prompt_parts = [
        "# SYSTEM ROLE",
        "",
        "Bạn là chuyên gia phân tích hợp đồng và pháp luật Việt Nam.",
        "",
        "Nhiệm vụ của bạn là hỗ trợ người dùng hiểu hợp đồng, phát hiện rủi ro pháp lý, giải thích các quy định liên quan và đưa ra khuyến nghị dễ hiểu.",
        "",
        "Bạn KHÔNG phải là công cụ tra cứu luật đơn thuần.",
        "",
        "Mục tiêu là giúp người dùng hiểu:",
        "* Điều khoản đang nói gì",
        "* Có rủi ro gì không",
        "* Có dấu hiệu chưa phù hợp pháp luật không",
        "* Nên bổ sung hoặc chỉnh sửa điều gì",
        "",
        "==================================================",
        "",
        "# NGUỒN DỮ LIỆU ĐƯỢC PHÉP SỬ DỤNG",
        "",
        "Bạn chỉ được sử dụng:",
        "",
        "1. Câu hỏi của người dùng",
        "2. Các đoạn trích từ tài liệu/hợp đồng của người dùng",
        "3. Các đoạn kiến thức pháp lý được cung cấp",
        "",
        "Không được sử dụng kiến thức bên ngoài.",
        "",
        "Không được viện dẫn điều luật không xuất hiện trong phần kiến thức pháp lý được cung cấp.",
        "",
        "==================================================",
        "",
        "# LƯU Ý VỀ CHẤT LƯỢNG TÀI LIỆU",
        "",
        "Các đoạn tài liệu của người dùng có thể được tạo từ:",
        "",
        "* PDF",
        "* OCR",
        "* Scan ảnh",
        "* Chuyển đổi văn bản",
        "",
        "Do đó có thể xuất hiện:",
        "",
        "* lỗi chính tả",
        "* sai dấu tiếng Việt",
        "* sai ký tự",
        "* câu bị cắt",
        "* dòng bị ngắt",
        "* từ bị nhận diện sai",
        "",
        "Khi gặp lỗi OCR:",
        "",
        "* Hãy cố gắng hiểu theo ngữ cảnh",
        "* Có thể diễn giải lại bằng ngôn ngữ tự nhiên",
        "* Không được tự tạo ra nội dung mới",
        "* Không được tự thêm thông tin không tồn tại",
        "",
        "Nếu đoạn văn quá lỗi để hiểu:",
        "",
        'Ghi rõ: "Không đủ thông tin rõ ràng để kết luận từ đoạn tài liệu này."',
        "",
        "==================================================",
        "",
        "# NGUYÊN TẮC PHÂN TÍCH",
        "",
        "1. Ưu tiên trả lời đúng câu hỏi của người dùng.",
        "",
        "2. Không được suy đoán.",
        "",
        "3. Nếu không tìm thấy nội dung liên quan:",
        "",
        'KHÔNG được nói: "Hợp đồng không có điều khoản này"',
        "",
        "Thay vào đó ghi:",
        "",
        '"Không tìm thấy nội dung này trong các đoạn tài liệu được cung cấp."',
        "",
        "4. Nếu không đủ dữ liệu:",
        "",
        'Ghi: "Không đủ thông tin để kết luận."',
        "",
        "5. Nếu căn cứ pháp lý chưa đủ:",
        "",
        'Ghi: "Không đủ căn cứ pháp lý để kết luận chắc chắn."',
        "",
        "6. Không được khẳng định vi phạm pháp luật nếu dữ liệu chưa đủ.",
        "",
        "7. Chỉ kết luận \"có dấu hiệu chưa phù hợp\" khi có căn cứ rõ ràng.",
        "",
        "==================================================",
        "",
        "# CÁCH SỬ DỤNG KIẾN THỨC PHÁP LÝ",
        "",
        "Khi viện dẫn pháp luật:",
        "",
        "KHÔNG cần trích nguyên văn điều luật.",
        "",
        "Ưu tiên:",
        "",
        "* giải thích bằng ngôn ngữ dễ hiểu",
        "* tóm tắt nội dung liên quan",
        "* liên hệ trực tiếp tới điều khoản trong hợp đồng",
        "",
        "Chỉ trích dẫn nguyên văn khi:",
        "",
        "* câu chữ cụ thể có ý nghĩa pháp lý quan trọng",
        "* hoặc người dùng yêu cầu xem nguyên văn",
        "",
        "==================================================",
        "",
        "# ĐÁNH GIÁ MỨC ĐỘ RỦI RO",
        "",
        "Sử dụng:",
        "",
        "🟢 THẤP",
        "🟡 TRUNG BÌNH",
        "🟠 CAO",
        "🔴 RẤT CAO",
        "",
        "Đánh giá dựa trên:",
        "",
        "* mức độ ảnh hưởng",
        "* khả năng phát sinh tranh chấp",
        "* mức độ rõ ràng của điều khoản",
        "* mức độ phù hợp với căn cứ pháp lý",
        "",
        "==================================================",
        "",
        "# CẤU TRÚC TRẢ LỜI",
        "",
        "# KẾT QUẢ PHÂN TÍCH",
        "",
        "## TÓM TẮT",
        "",
        "* Trả lời ngắn gọn câu hỏi của người dùng",
        "* Nêu mức độ rủi ro tổng quan",
        "* Nêu độ tin cậy đánh giá",
        "",
        "---",
        "",
        "## PHÂN TÍCH CHI TIẾT",
        "",
        "Đối với mỗi vấn đề:",
        "",
        "### [Tên vấn đề]",
        "",
        "Mức độ rủi ro:",
        "🟢/🟡/🟠/🔴",
        "",
        "Những gì tìm thấy:",
        "",
        "* Tóm tắt ngắn gọn nội dung liên quan trong hợp đồng",
        "* Không cần trích nguyên văn dài",
        "",
        "Đánh giá:",
        "",
        "* Giải thích điều khoản đang quy định điều gì",
        "* Rủi ro nằm ở đâu",
        "* Có thể phát sinh tranh chấp gì",
        "",
        "Căn cứ pháp lý liên quan:",
        "",
        "* Tên văn bản",
        "* Số hiệu văn bản",
        "* Điều khoản liên quan",
        "",
        "Giải thích ngắn gọn ý nghĩa của quy định đó.",
        "",
        "Khuyến nghị:",
        "",
        "* Người dùng nên bổ sung gì",
        "* Người dùng nên sửa gì",
        "* Điều gì cần được làm rõ hơn",
        "",
        "---",
        "",
        "## CÁC ĐIỀU KHOẢN NÊN BỔ SUNG",
        "",
        "Liệt kê các điều khoản nên cân nhắc bổ sung.",
        "",
        "Đối với mỗi điều khoản:",
        "",
        "* Mục đích",
        "* Lợi ích",
        "* Rủi ro nếu thiếu",
        "",
        "---",
        "",
        "## KẾT LUẬN",
        "",
        "Tóm tắt:",
        "",
        "* Điểm mạnh của hợp đồng",
        "* Điểm cần lưu ý",
        "* Điểm có dấu hiệu chưa phù hợp",
        "* Điểm chưa đủ dữ liệu để đánh giá",
        "",
        "---",
        "",
        "## ĐỘ TIN CẬY",
        "",
        "CAO / TRUNG BÌNH / THẤP",
        "",
        "# Giải thích ngắn gọn lý do.",
        "",
        "# CÂU HỎI NGƯỜI DÙNG",
        "",
        question_text,
        "",
        "==================================================",
        "",
        "# TÀI LIỆU NGƯỜI DÙNG",
        "",
        user_context or "[none]",
        "",
        "==================================================",
        "",
        "# KIẾN THỨC PHÁP LÝ",
        "",
        knowledge_context or "[none]",
    ]
    return "\n".join(prompt_parts)


class GeminiService:
    """Service for generating answers using Gemini."""

    def __init__(self):
        self.api_key = settings.gemini_api_key
        self.model_name = settings.gemini_model
        self.model = None

    def initialize(self):
        if not self.api_key or self.api_key == "your_gemini_api_key_here":
            logger.warning("Gemini API key not configured. LLM features will be disabled.")
            return False

        try:
            import google.generativeai as genai

            genai.configure(api_key=self.api_key)
            self.model = genai.GenerativeModel(self.model_name)
            logger.info(f"Gemini service initialized with model: {self.model_name}")
            return True
        except Exception as e:
            logger.error(f"Failed to initialize Gemini: {e}")
            return False

    def generate_review_answer(
        self,
        question: str,
        checklist_results: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ) -> str:
        if not self.model:
            logger.warning("Gemini not initialized, using fallback answer generation")
            return self._generate_fallback_answer(question, checklist_results, knowledge_chunks)

        try:
            prompt = build_legal_review_prompt(question, checklist_results, knowledge_chunks)
            logger.info("Generating answer with Gemini...")
            response = self.model.generate_content(
                prompt,
                generation_config=self._build_generation_config(),
            )

            if response and response.text:
                logger.info("Answer generated successfully")
                return response.text.strip()

            logger.warning("Empty response from Gemini, using fallback")
            return self._generate_fallback_answer(question, checklist_results, knowledge_chunks)
        except Exception as e:
            logger.error(f"Error generating answer with Gemini: {e}")
            return self._generate_fallback_answer(question, checklist_results, knowledge_chunks)

    def generate_legal_query_result(
        self,
        question: str,
        user_chunks: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ) -> LegalQueryResult:
        """Return the legal answer plus ticket-suggestion metadata."""
        if not self.model:
            logger.warning("Gemini not initialized, using fallback query answer generation")
            return self._build_query_fallback_result(
                question,
                user_chunks,
                knowledge_chunks,
                error="LLM not initialized",
            )

        if not user_chunks and not knowledge_chunks:
            return self._build_query_fallback_result(question, user_chunks, knowledge_chunks)

        try:
            prompt = build_legal_query_prompt(question, user_chunks, knowledge_chunks)
            logger.info("Generating query answer with Gemini...")
            response = self.model.generate_content(
                prompt,
                generation_config=self._build_generation_config(),
            )

            if response and response.text:
                logger.info("Query answer generated successfully")
                return self._parse_legal_query_result(response.text.strip(), question, user_chunks, knowledge_chunks)

            logger.warning("Empty query response from Gemini, using fallback")
            return self._build_query_fallback_result(question, user_chunks, knowledge_chunks)
        except Exception as e:
            logger.error(f"Error generating query answer with Gemini: {e}")
            return self._build_query_fallback_result(question, user_chunks, knowledge_chunks, error=str(e))

    def generate_query_answer(
        self,
        question: str,
        user_chunks: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ) -> str:
        if not self.model:
            logger.warning("Gemini not initialized, using fallback query answer generation")
            return self._generate_query_fallback_answer(question, user_chunks, knowledge_chunks)

        if not user_chunks and not knowledge_chunks:
            return "Không đủ thông tin để kết luận."

        try:
            prompt = build_legal_query_prompt(question, user_chunks, knowledge_chunks)
            logger.info("Generating query answer with Gemini...")
            response = self.model.generate_content(
                prompt,
                generation_config=self._build_generation_config(),
            )

            if response and response.text:
                logger.info("Query answer generated successfully")
                return response.text.strip()

            logger.warning("Empty query response from Gemini, using fallback")
            return self._generate_query_fallback_answer(question, user_chunks, knowledge_chunks)
        except Exception as e:
            logger.error(f"Error generating query answer with Gemini: {e}")
            return self._generate_query_fallback_answer(question, user_chunks, knowledge_chunks)

    def stream_review_answer(
        self,
        question: str,
        checklist_results: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ):
        """Yield SSE-ready events for a streamed answer."""
        if not self.model:
            logger.warning("Gemini not initialized, streaming fallback answer")
            yield from self._stream_fallback_answer(question, checklist_results, knowledge_chunks)
            return

        try:
            prompt = build_legal_review_prompt(question, checklist_results, knowledge_chunks)
            logger.info("Streaming answer with Gemini...")
            stream = self.model.generate_content(
                prompt,
                generation_config=self._build_generation_config(),
                stream=True,
            )

            accumulated = []
            for chunk in stream:
                delta = self._extract_chunk_text(chunk)
                if not delta:
                    continue
                accumulated.append(delta)
                yield self._encode_stream_event(
                    "chunk",
                    {
                        "delta": delta,
                        "content": "".join(accumulated),
                    },
                )

            final_answer = "".join(accumulated).strip()
            if not final_answer:
                final_answer = self._generate_fallback_answer(question, checklist_results, knowledge_chunks)
                yield from self._stream_text_answer(final_answer)
            yield self._encode_stream_event(
                "done",
                {
                    "answer": final_answer,
                    "model": self.model_name,
                },
            )
        except Exception as e:
            logger.error(f"Error streaming answer with Gemini: {e}")
            yield from self._stream_fallback_answer(question, checklist_results, knowledge_chunks)

    def stream_query_answer(
        self,
        question: str,
        user_chunks: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ):
        """Yield SSE-ready events for a streamed query answer."""
        if not self.model:
            logger.warning("Gemini not initialized, streaming fallback query answer")
            yield from self._stream_query_fallback_answer(question, user_chunks, knowledge_chunks)
            return

        if not user_chunks and not knowledge_chunks:
            final_answer = "Không đủ thông tin để kết luận."
            yield self._encode_stream_event(
                "chunk",
                {
                    "delta": final_answer,
                    "content": final_answer,
                },
            )
            yield self._encode_stream_event(
                "done",
                {
                    "answer": final_answer,
                    "model": self.model_name,
                },
            )
            return

        try:
            prompt = build_legal_query_prompt(question, user_chunks, knowledge_chunks)
            logger.info("Streaming query answer with Gemini...")
            stream = self.model.generate_content(
                prompt,
                generation_config=self._build_generation_config(),
                stream=True,
            )

            accumulated = []
            for chunk in stream:
                delta = self._extract_chunk_text(chunk)
                if not delta:
                    continue
                accumulated.append(delta)
                yield self._encode_stream_event(
                    "chunk",
                    {
                        "delta": delta,
                        "content": "".join(accumulated),
                    },
                )

            final_answer = "".join(accumulated).strip()
            if not final_answer:
                final_answer = self._generate_query_fallback_answer(question, user_chunks, knowledge_chunks)
                yield from self._stream_text_answer(final_answer)
            yield self._encode_stream_event(
                "done",
                {
                    "answer": final_answer,
                    "model": self.model_name,
                },
            )
        except Exception as e:
            logger.error(f"Error streaming query answer with Gemini: {e}")
            yield from self._stream_query_fallback_answer(question, user_chunks, knowledge_chunks)

    def _build_generation_config(self) -> Dict[str, Any]:
        return {
            "temperature": 0.2,
            "top_p": 0.9,
            "top_k": 40,
            "max_output_tokens": 4096,
        }

    def _encode_stream_event(self, event_type: str, payload: Dict[str, Any]) -> str:
        return f"event: {event_type}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"

    def _extract_chunk_text(self, chunk: Any) -> str:
        text = getattr(chunk, "text", "") or ""
        if not text:
            candidates = getattr(chunk, "candidates", None) or []
            for candidate in candidates:
                content = getattr(candidate, "content", None)
                parts = getattr(content, "parts", None) or []
                for part in parts:
                    part_text = getattr(part, "text", "") or ""
                    if part_text:
                        text += part_text
        return text

    def _stream_text_answer(self, answer_text: str):
        if not answer_text:
            return
        yield self._encode_stream_event(
            "chunk",
            {
                "delta": answer_text,
                "content": answer_text,
            },
        )

    def _generate_fallback_answer(
        self,
        question: str,
        checklist_results: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ) -> str:
        findings_with_evidence, findings_without_evidence = self._split_findings(checklist_results)
        issue_count = len(findings_with_evidence) + len(findings_without_evidence)
        confidence = self._estimate_confidence(findings_with_evidence, knowledge_chunks)
        overall_risk = self._estimate_overall_risk(findings_with_evidence, findings_without_evidence, knowledge_chunks)

        answer_parts = [
            "# KẾT QUẢ RÀ SOÁT HỢP ĐỒNG",
            "",
            "## TÓM TẮT",
            f"- **Mức độ rủi ro:** {overall_risk}",
            f"- **Số vấn đề phát hiện:** {issue_count}",
            f"- **Độ tin cậy đánh giá:** {confidence}",
            f"- **Câu hỏi được rà soát:** {self._normalize_text(question) or 'Không tìm thấy thông tin trong tài liệu được phân tích'}",
            "",
        ]

        if findings_with_evidence:
            for index, finding in enumerate(findings_with_evidence, 1):
                answer_parts.extend(self._render_fallback_issue(index, finding))
        else:
            answer_parts.extend(
                [
                    "## VẤN ĐỀ 1",
                    "",
                    "### Mức độ rủi ro",
                    "- Không xác định",
                    "",
                    "### Bằng chứng từ hợp đồng",
                    "- Không tìm thấy thông tin trong tài liệu được phân tích",
                    "",
                    "### Căn cứ pháp lý",
                    "- Không đủ căn cứ pháp lý để kết luận",
                    "",
                    "### Phân tích",
                    "- Không đủ dữ liệu để kết luận chắc chắn về rủi ro pháp lý.",
                    "",
                    "### Khuyến nghị",
                    "- Bổ sung hoặc làm rõ các điều khoản còn thiếu trước khi ký kết.",
                    "",
                ]
            )

        if findings_without_evidence:
            answer_parts.append("## CÁC ĐIỀU KHOẢN NÊN BỔ SUNG")
            answer_parts.append("")
            for finding in findings_without_evidence[:10]:
                answer_parts.extend(self._render_missing_clause_item(finding))

        answer_parts.extend(
            [
                "## ĐÁNH GIÁ TỔNG QUAN",
                "",
                f"- Hợp đồng có {issue_count} nội dung cần rà soát.",
                "- Các nhận định bên trên chỉ dựa trên dữ liệu được cung cấp.",
                "- Nếu cần kết luận cuối cùng để ký, nên đối chiếu thêm toàn bộ hợp đồng và văn bản pháp luật hiện hành.",
            ]
        )
        return "\n".join(answer_parts)

    def _parse_legal_query_result(
        self,
        raw_text: str,
        question: str,
        user_chunks: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ) -> LegalQueryResult:
        payload = self._extract_json_payload(raw_text)
        if not payload:
            return self._build_query_fallback_result(question, user_chunks, knowledge_chunks)

        answer = self._normalize_text(payload.get("answer") or payload.get("response") or raw_text)
        confidence_score = self._to_float(payload.get("confidenceScore") or payload.get("confidence_score"))
        should_suggest_ticket = bool(payload.get("shouldSuggestTicket", payload.get("should_suggest_ticket", False)))
        suggestion_type = self._normalize_query_enum(
            payload.get("suggestionType") or payload.get("suggestion_type"),
            default="NONE",
            allowed={"NONE", "ASK_MORE_INFO", "SUGGEST_LAWYER", "REQUIRE_LAWYER"},
        )
        suggestion_reason = self._normalize_text(payload.get("suggestionReason") or payload.get("suggestion_reason")) or None
        missing_information = self._normalize_text(payload.get("missingInformation") or payload.get("missing_information")) or None
        risk_level = self._normalize_query_enum(
            payload.get("riskLevel") or payload.get("risk_level"),
            default="MEDIUM" if should_suggest_ticket else "LOW",
            allowed={"LOW", "MEDIUM", "HIGH"},
        )
        legal_domain = self._normalize_text(payload.get("legalDomain") or payload.get("legal_domain")) or None
        user_action_hint = self._normalize_query_enum(
            payload.get("userActionHint") or payload.get("user_action_hint"),
            default="CREATE_TICKET" if should_suggest_ticket else "CONTINUE_CHAT",
            allowed={"CONTINUE_CHAT", "PROVIDE_MORE_INFO", "CREATE_TICKET"},
        )

        if should_suggest_ticket and suggestion_type == "NONE":
            suggestion_type = "REQUIRE_LAWYER" if risk_level == "HIGH" else "SUGGEST_LAWYER"
        if should_suggest_ticket and user_action_hint == "CONTINUE_CHAT":
            user_action_hint = "CREATE_TICKET"
        if confidence_score is None:
            confidence_score = self._estimate_query_confidence(user_chunks, knowledge_chunks, risk_level)

        return LegalQueryResult(
            answer=answer or None,
            confidence_score=confidence_score,
            should_suggest_ticket=should_suggest_ticket,
            suggestion_type=suggestion_type,
            suggestion_reason=suggestion_reason,
            missing_information=missing_information,
            risk_level=risk_level,
            legal_domain=legal_domain,
            user_action_hint=user_action_hint,
            error=None,
        )

    def _build_query_fallback_result(
        self,
        question: str,
        user_chunks: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
        *,
        error: str | None = None,
    ) -> LegalQueryResult:
        answer = self._generate_query_fallback_answer(question, user_chunks, knowledge_chunks)
        has_context = bool(user_chunks or knowledge_chunks)
        risk_level = "HIGH" if not has_context else "MEDIUM"
        should_suggest_ticket = not has_context or len(knowledge_chunks) == 0
        suggestion_type = "REQUIRE_LAWYER" if not has_context else "SUGGEST_LAWYER"
        user_action_hint = "CREATE_TICKET" if should_suggest_ticket else "PROVIDE_MORE_INFO"
        missing_information = "Thiếu dữ liệu đầu vào để kết luận chắc chắn." if should_suggest_ticket else "Cần thêm thông tin và căn cứ pháp lý."
        suggestion_reason = error or "Câu hỏi chưa đủ dữ liệu hoặc cần chuyên gia pháp lý rà soát thêm."
        return LegalQueryResult(
            answer=answer,
            confidence_score=self._estimate_query_confidence(user_chunks, knowledge_chunks, risk_level),
            should_suggest_ticket=should_suggest_ticket,
            suggestion_type=suggestion_type,
            suggestion_reason=suggestion_reason,
            missing_information=missing_information,
            risk_level=risk_level,
            legal_domain=self._detect_legal_domain(user_chunks, knowledge_chunks),
            user_action_hint=user_action_hint,
            error=error,
        )

    def _extract_json_payload(self, text: str) -> Dict[str, Any] | None:
        compact = text.strip()
        if compact.startswith("```"):
            compact = compact.removeprefix("```").strip()
            if compact.lower().startswith("json"):
                compact = compact[4:].lstrip()
            if compact.endswith("```"):
                compact = compact[:-3].strip()
        try:
            parsed = json.loads(compact)
        except json.JSONDecodeError:
            return None
        return parsed if isinstance(parsed, dict) else None

    def _normalize_query_enum(self, value: Any, *, default: str, allowed: set[str]) -> str:
        normalized = self._normalize_text(value).upper().replace(" ", "_")
        if normalized in allowed:
            return normalized
        return default

    def _estimate_query_confidence(
        self,
        user_chunks: Sequence[Dict[str, Any]],
        knowledge_chunks: Sequence[Dict[str, Any]],
        risk_level: str,
    ) -> float:
        score = 0.4
        score += min(len(user_chunks), 3) * 0.12
        score += min(len(knowledge_chunks), 3) * 0.1
        if risk_level == "HIGH":
            score -= 0.15
        elif risk_level == "LOW":
            score += 0.1
        return max(0.05, min(0.98, round(score, 2)))

    def _detect_legal_domain(
        self,
        user_chunks: Sequence[Dict[str, Any]],
        knowledge_chunks: Sequence[Dict[str, Any]],
    ) -> str | None:
        candidates = list(user_chunks) + list(knowledge_chunks)
        for chunk in candidates:
            domain = self._normalize_text(
                chunk.get("legalDomain")
                or chunk.get("legal_domain")
                or chunk.get("category")
                or chunk.get("title")
            )
            if domain:
                return domain[:120]
        return None

    def _to_float(self, value: Any) -> float | None:
        try:
            if value is None:
                return None
            return float(value)
        except Exception:
            return None

    def _generate_query_fallback_answer(
        self,
        question: str,
        user_chunks: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ) -> str:
        if not user_chunks and not knowledge_chunks:
            return "Không đủ thông tin để kết luận."

        answer_parts = [
            "# KẾT QUẢ TRA CỨU HỢP ĐỒNG",
            "",
            "## TỔNG QUAN",
            f"- Câu hỏi: {self._normalize_text(question) or 'Không tìm thấy thông tin trong tài liệu được phân tích'}",
            f"- Số đoạn từ tài liệu người dùng: {len(user_chunks)}",
            f"- Số đoạn từ knowledge base: {len(knowledge_chunks)}",
            "",
            "## TRÍCH DẪN TỪ TÀI LIỆU NGƯỜI DÙNG",
        ]
        answer_parts.extend(self._render_query_chunk_lines(user_chunks, source_label="Tài liệu người dùng"))
        answer_parts.extend(["", "## TRÍCH DẪN TỪ KNOWLEDGE BASE"])
        answer_parts.extend(self._render_query_chunk_lines(knowledge_chunks, source_label="Knowledge base"))
        answer_parts.extend(
            [
                "",
                "## KẾT LUẬN",
                "- Nội dung trên chỉ là tóm tắt từ dữ liệu hiện có.",
                "- Nếu cần kết luận pháp lý chắc chắn hơn, cần bổ sung thêm căn cứ phù hợp.",
            ]
        )
        return "\n".join(answer_parts)

    def _stream_query_fallback_answer(
        self,
        question: str,
        user_chunks: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ):
        answer = self._generate_query_fallback_answer(question, user_chunks, knowledge_chunks)
        yield from self._stream_text_answer(answer)
        yield self._encode_stream_event(
            "done",
            {
                "answer": answer,
                "model": self.model_name,
            },
        )

    def _render_query_chunk_lines(self, chunks: Sequence[Dict[str, Any]], *, source_label: str) -> List[str]:
        if not chunks:
            return ["- Không tìm thấy thông tin trong tài liệu được phân tích"]

        lines: list[str] = []
        for index, chunk in enumerate(chunks[:5], 1):
            title = self._normalize_text(
                chunk.get("title")
                or chunk.get("law_name")
                or chunk.get("lawName")
                or chunk.get("file_name")
                or chunk.get("fileName")
            )
            excerpt = self._compact_text(
                chunk.get("text") or chunk.get("chunkText") or chunk.get("chunk_text"),
                max_chars=360,
            )
            score_text = self._format_score(chunk.get("score"))
            lines.append(f"- {source_label} {index}")
            if title:
                lines.append(f"  - Tiêu đề: {title}")
            if excerpt:
                lines.append(f"  - Trích đoạn: {excerpt}")
            if score_text:
                lines.append(f"  - Độ liên quan: {score_text}")
        return lines or ["- Không tìm thấy thông tin trong tài liệu được phân tích"]

    def _stream_fallback_answer(
        self,
        question: str,
        checklist_results: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]],
    ):
        answer = self._generate_fallback_answer(question, checklist_results, knowledge_chunks)
        yield from self._stream_text_answer(answer)
        yield self._encode_stream_event(
            "done",
            {
                "answer": answer,
                "model": self.model_name,
            },
        )

    def _render_fallback_issue(self, index: int, finding: Dict[str, Any]) -> List[str]:
        issue_title = self._normalize_text(finding.get("title")) or f"Vấn đề {index}"
        risk_question = self._normalize_text(finding.get("risk_question"))
        priority = finding.get("priority")
        evidence_lines = self._format_evidence_lines(finding.get("user_chunks_found", []))
        legal_basis_lines = self._format_legal_basis_lines(finding.get("legal_basis", []))
        has_evidence = any(line.strip() != "- Không tìm thấy thông tin trong tài liệu được phân tích" for line in evidence_lines)
        has_legal_basis = any(line.strip() != "- Không đủ căn cứ pháp lý để kết luận" for line in legal_basis_lines)

        risk_level = self._risk_level_from_priority(priority, has_evidence, has_legal_basis)

        analysis_lines = [
            "### Phân tích",
            f"- Vấn đề này liên quan đến: {risk_question or issue_title}.",
        ]
        if has_evidence and has_legal_basis:
            analysis_lines.append(
                "- Dữ liệu cho thấy điều khoản này có thể tạo ra tranh chấp nếu nội dung trong hợp đồng không khớp với nghĩa vụ hoặc quyền đã được nêu trong văn bản pháp luật được trích dẫn."
            )
        elif has_evidence:
            analysis_lines.append(
                "- Có dấu hiệu cần rà soát thêm vì hợp đồng đã thể hiện nội dung liên quan, nhưng knowledge chunks chưa đủ để khẳng định chắc chắn."
            )
        else:
            analysis_lines.append("- Không tìm thấy thông tin trong tài liệu được phân tích.")

        recommendation_lines = [
            "### Khuyến nghị",
            "- Làm rõ hoặc bổ sung điều khoản để giảm nguy cơ tranh chấp khi thực hiện hợp đồng.",
        ]
        if not has_legal_basis:
            recommendation_lines.append("- Cần thêm căn cứ pháp lý phù hợp trước khi kết luận cuối cùng.")

        return [
            f"## VẤN ĐỀ {index}: {issue_title}",
            "",
            "### Mức độ rủi ro",
            f"- {risk_level}",
            "",
            "### Bằng chứng từ hợp đồng",
            *evidence_lines,
            "",
            "### Căn cứ pháp lý",
            *legal_basis_lines,
            "",
            *analysis_lines,
            "",
            *recommendation_lines,
            "",
        ]

    def _render_missing_clause_item(self, finding: Dict[str, Any]) -> List[str]:
        title = self._normalize_text(finding.get("title")) or "Điều khoản cần bổ sung"
        risk_question = self._normalize_text(finding.get("risk_question"))
        evidence_lines = self._format_evidence_lines(finding.get("user_chunks_found", []))
        legal_basis_lines = self._format_legal_basis_lines(finding.get("legal_basis", []))
        return [
            f"### {title}",
            f"- Lý do cần bổ sung: {risk_question or 'Không tìm thấy thông tin trong tài liệu được phân tích'}",
            "#### Bằng chứng từ hợp đồng",
            *evidence_lines,
            "#### Căn cứ pháp lý",
            *legal_basis_lines,
            "",
        ]

    def _split_findings(
        self, checklist_results: Sequence[Dict[str, Any]]
    ) -> tuple[list[Dict[str, Any]], list[Dict[str, Any]]]:
        findings_with_evidence = []
        findings_without_evidence = []
        for result in checklist_results:
            if result.get("user_chunks_found"):
                findings_with_evidence.append(result)
            else:
                findings_without_evidence.append(result)
        return findings_with_evidence, findings_without_evidence

    def _estimate_confidence(
        self,
        findings_with_evidence: Sequence[Dict[str, Any]],
        knowledge_chunks: Sequence[Dict[str, Any]],
    ) -> str:
        if not findings_with_evidence:
            return "Thấp"
        if knowledge_chunks and len(findings_with_evidence) >= 3:
            return "Cao"
        return "Trung bình"

    def _estimate_overall_risk(
        self,
        findings_with_evidence: Sequence[Dict[str, Any]],
        findings_without_evidence: Sequence[Dict[str, Any]],
        knowledge_chunks: Sequence[Dict[str, Any]],
    ) -> str:
        score = 0
        score += min(len(findings_with_evidence), 5) * 2
        score += min(len(findings_without_evidence), 5)
        if knowledge_chunks:
            score += 1
        if score >= 10:
            return "Cao"
        if score >= 5:
            return "Trung bình"
        return "Thấp"

    def _risk_level_from_priority(self, priority: Any, has_evidence: bool, has_legal_basis: bool) -> str:
        try:
            priority_value = int(priority)
        except Exception:
            priority_value = None

        if not has_evidence or not has_legal_basis:
            return "Trung bình"
        if priority_value is not None and priority_value <= 2:
            return "Cao"
        if priority_value is not None and priority_value <= 4:
            return "Trung bình"
        return "Thấp"

    def _format_evidence_lines(self, user_chunks: Sequence[Dict[str, Any]]) -> List[str]:
        if not user_chunks:
            return ["- Không tìm thấy thông tin trong tài liệu được phân tích"]

        lines: list[str] = []
        for index, chunk in enumerate(user_chunks[:3], 1):
            file_name = self._normalize_text(chunk.get("file_name")) or "Không rõ tên tài liệu"
            title = self._normalize_text(chunk.get("title"))
            excerpt = self._compact_text(chunk.get("text"), max_chars=360)
            score = chunk.get("score")
            score_text = self._format_score(score)
            lines.append(f"- Nguồn {index}: {file_name}")
            if title:
                lines.append(f"  - Tiêu đề đoạn: {title}")
            if excerpt:
                lines.append(f"  - Trích đoạn: {excerpt}")
            if score_text:
                lines.append(f"  - Độ liên quan: {score_text}")
        return lines or ["- Không tìm thấy thông tin trong tài liệu được phân tích"]

    def _format_legal_basis_lines(self, knowledge_chunks: Sequence[Dict[str, Any]]) -> List[str]:
        if not knowledge_chunks:
            return ["- Không đủ căn cứ pháp lý để kết luận"]

        lines: list[str] = []
        for index, chunk in enumerate(knowledge_chunks[:4], 1):
            title = self._normalize_text(chunk.get("title"))
            excerpt = self._compact_text(chunk.get("text"), max_chars=360)
            if not title and not excerpt:
                continue
            lines.append(f"- Căn cứ {index}")
            if title:
                lines.append(f"  - Tên văn bản: {title}")
            else:
                lines.append("  - Tên văn bản: Không đủ căn cứ pháp lý để kết luận")
            lines.append("  - Số hiệu: Không đủ căn cứ pháp lý để kết luận")
            lines.append("  - Điều: Không đủ căn cứ pháp lý để kết luận")
            lines.append("  - Khoản: Không đủ căn cứ pháp lý để kết luận")
            if excerpt:
                lines.append(f"  - Trích dẫn hỗ trợ: {excerpt}")

        return lines or ["- Không đủ căn cứ pháp lý để kết luận"]

    def _format_score(self, score: Any) -> str | None:
        try:
            if score is None:
                return None
            score_value = float(score)
            if score_value <= 1:
                return f"{score_value:.2%}"
            return f"{score_value:.4f}"
        except Exception:
            return None

    def _compact_text(self, value: Any, max_chars: int = 260) -> str:
        text = self._normalize_text(value)
        if not text:
            return ""
        if len(text) <= max_chars:
            return text
        return text[: max_chars - 3].rstrip() + "..."

    def _normalize_text(self, value: Any) -> str:
        if value is None:
            return ""
        text = str(value).strip()
        text = " ".join(text.split())
        return text


def _format_checklist_results(checklist_results: List[Dict[str, Any]]) -> str:
    if not checklist_results:
        return "- Không tìm thấy checklist findings phù hợp."

    lines: list[str] = []
    for index, result in enumerate(checklist_results, 1):
        title = _normalize_text(result.get("title")) or f"Vấn đề {index}"
        risk_question = _normalize_text(result.get("risk_question"))
        priority = result.get("priority")
        lines.append(f"{index}. {title}")
        lines.append(f"   - Mức ưu tiên: {priority if priority is not None else 'Không rõ'}")
        lines.append(f"   - Câu hỏi rà soát: {risk_question or 'Không tìm thấy thông tin trong tài liệu được phân tích'}")
        evidence_lines = _format_user_chunks(result.get("user_chunks_found", []))
        lines.append("   - Bằng chứng từ hợp đồng:")
        lines.extend([f"     {line}" for line in evidence_lines])
    return "\n".join(lines)


def _format_knowledge_chunks(knowledge_chunks: List[Dict[str, Any]]) -> str:
    if not knowledge_chunks:
        return "- Không tìm thấy knowledge chunks phù hợp."

    lines: list[str] = []
    for index, chunk in enumerate(knowledge_chunks, 1):
        title = _normalize_text(chunk.get("title")) or f"Căn cứ {index}"
        excerpt = _compact_text(chunk.get("text"), max_chars=420)
        lines.append(f"{index}. {title}")
        lines.append("   - Ghi chú: Chỉ dùng để xác nhận căn cứ pháp lý, không được sao chép source id nội bộ.")
        if excerpt:
            lines.append(f"   - Trích đoạn: {excerpt}")
    return "\n".join(lines)


def _format_user_chunks(user_chunks: Sequence[Dict[str, Any]]) -> List[str]:
    if not user_chunks:
        return ["- Không tìm thấy thông tin trong tài liệu được phân tích"]

    lines: list[str] = []
    for index, chunk in enumerate(user_chunks[:3], 1):
        file_name = _normalize_text(chunk.get("file_name")) or "Không rõ tên tài liệu"
        title = _normalize_text(chunk.get("title"))
        excerpt = _compact_text(chunk.get("text"), max_chars=260)
        lines.append(f"- Nguồn {index}: {file_name}")
        if title:
            lines.append(f"  - Tiêu đề đoạn: {title}")
        if excerpt:
            lines.append(f"  - Trích đoạn: {excerpt}")
    return lines


def _format_query_user_chunks(user_chunks: List[Dict[str, Any]]) -> str:
    if not user_chunks:
        return "- Không tìm thấy thông tin trong tài liệu được phân tích."

    lines: list[str] = []
    for index, chunk in enumerate(user_chunks, 1):
        title = _normalize_text(chunk.get("title") or chunk.get("file_name") or chunk.get("fileName")) or f"Chunk {index}"
        excerpt = _compact_text(chunk.get("text") or chunk.get("chunkText") or chunk.get("chunk_text"), max_chars=320)
        score = chunk.get("score")
        lines.append(f"{index}. {title}")
        if excerpt:
            lines.append(f"   - Trích đoạn: {excerpt}")
        if score is not None:
            try:
                score_value = float(score)
                score_text = f"{score_value:.2%}" if score_value <= 1 else f"{score_value:.4f}"
            except Exception:
                score_text = str(score)
            lines.append(f"   - Độ liên quan: {score_text}")
    return "\n".join(lines)


def _format_query_knowledge_chunks(knowledge_chunks: List[Dict[str, Any]]) -> str:
    if not knowledge_chunks:
        return "- Không tìm thấy thông tin trong tài liệu được phân tích."

    lines: list[str] = []
    for index, chunk in enumerate(knowledge_chunks, 1):
        title = _normalize_text(
            chunk.get("title")
            or chunk.get("law_name")
            or chunk.get("lawName")
            or chunk.get("section_title")
            or chunk.get("sectionTitle")
        ) or f"Knowledge {index}"
        law_code = _normalize_text(chunk.get("law_code") or chunk.get("lawCode"))
        article = _normalize_text(chunk.get("article_number") or chunk.get("articleNumber"))
        clause = _normalize_text(chunk.get("clause_number") or chunk.get("clauseNumber"))
        excerpt = _compact_text(chunk.get("text") or chunk.get("chunkText") or chunk.get("chunk_text"), max_chars=320)
        lines.append(f"{index}. {title}")
        if law_code:
            lines.append(f"   - Số hiệu: {law_code}")
        if article:
            lines.append(f"   - Điều: {article}")
        if clause:
            lines.append(f"   - Khoản: {clause}")
        if excerpt:
            lines.append(f"   - Trích đoạn: {excerpt}")
    return "\n".join(lines)


def _build_output_template() -> str:
    return "\n".join(
        [
            "# KẾT QUẢ RÀ SOÁT HỢP ĐỒNG",
            "",
            "## TÓM TẮT",
            "- Mức độ rủi ro: [Cao/Trung bình/Thấp]",
            "- Số vấn đề phát hiện: [số]",
            "- Độ tin cậy đánh giá: [Cao/Trung bình/Thấp]",
            "",
            "## VẤN ĐỀ 1: [Tên vấn đề]",
            "",
            "### Mức độ rủi ro",
            "- [Cao/Trung bình/Thấp]",
            "",
            "### Bằng chứng từ hợp đồng",
            "- [Trích đoạn hoặc câu mô tả ngắn]",
            "",
            "### Căn cứ pháp lý",
            "- Tên văn bản: [Tên văn bản]",
            "- Số hiệu: [Số hiệu văn bản]",
            "- Điều: [Điều]",
            "- Khoản: [Khoản nếu có; nếu không đủ thì ghi: Không đủ căn cứ pháp lý để kết luận]",
            "",
            "### Phân tích",
            "- Rủi ro là gì",
            "- Tranh chấp nào có thể phát sinh",
            "- Vì sao điều khoản chưa phù hợp",
            "- Căn cứ điều luật nào",
            "",
            "### Khuyến nghị",
            "- Cách sửa hoặc bổ sung điều khoản",
            "",
            "## VẤN ĐỀ 2: [Tên vấn đề]",
            "",
            "## CÁC ĐIỀU KHOẢN NÊN BỔ SUNG",
            "- [Điều khoản cần thêm]",
            "",
            "## ĐÁNH GIÁ TỔNG QUAN",
            "- Kết luận tổng thể",
            "- Điều gì còn thiếu",
            "- Khi nào cần chuyên gia pháp lý rà soát thêm",
        ]
    )


def _normalize_text(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    text = " ".join(text.split())
    return text


def _compact_text(value: Any, max_chars: int = 260) -> str:
    text = _normalize_text(value)
    if not text:
        return ""
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 3].rstrip() + "..."


gemini_service = GeminiService()
