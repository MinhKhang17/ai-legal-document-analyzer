"""Builds grounded legal prompts for the LLM.

Design principles enforced in every prompt:
  * Do NOT invent statutes or article numbers.
  * Reason ONLY from the provided contract text and graph context.
  * If data is insufficient, say so explicitly.
  * When outputMode=JSON, instruct the model to return strict JSON matching the
    response schema (no markdown, no commentary).
"""
from __future__ import annotations

import json
from typing import Any, Dict, List, Optional

_GROUNDING_RULES = (
    "QUY TẮC BẮT BUỘC:\n"
    "1. Không được bịa đặt điều luật, số hiệu văn bản hay điều khoản pháp luật.\n"
    "2. Chỉ phân tích dựa trên nội dung hợp đồng và bối cảnh tri thức được cung cấp.\n"
    "3. Khi viện dẫn pháp lý, CHỈ dùng các điều luật xuất hiện trong phần 'BỐI CẢNH "
    "TRI THỨC' (mục LegalArticle); nếu không có thì nói 'cần kiểm tra văn bản luật liên quan'.\n"
    "4. Nếu thiếu dữ liệu để kết luận, phải nói rõ 'chưa đủ dữ liệu'.\n"
    "5. Trả lời bằng tiếng Việt, văn phong pháp lý, khách quan.\n"
)

# A compact worked example (few-shot) that anchors the expected JSON shape and
# the grounded reasoning style. It is illustrative, not legal advice.
_ANALYZE_FEWSHOT = """VÍ DỤ THAM KHẢO (định dạng và văn phong mong muốn):
[Đầu vào rút gọn] Hợp đồng thuê nhà, không quy định điều kiện hoàn trả tiền đặt cọc.
[Bối cảnh] LegalArticle "Điều 328 - Đặt cọc" (Bộ luật Dân sự 2015).
[Đầu ra JSON mẫu]
{
  "contractType": "HOUSE_RENTAL",
  "summary": "Hợp đồng thiếu quy định về điều kiện hoàn trả/khấu trừ tiền đặt cọc, gây bất lợi cho bên thuê.",
  "overallRiskLevel": "HIGH",
  "riskItems": [
    {
      "riskType": "DEPOSIT_RISK",
      "severity": "HIGH",
      "title": "Thiếu điều kiện hoàn trả tiền đặt cọc",
      "explanation": "Hợp đồng không nêu khi nào bên thuê được hoàn cọc hay bị khấu trừ. Theo Điều 328 Bộ luật Dân sự 2015, đặt cọc bảo đảm giao kết/thực hiện hợp đồng; thiếu thỏa thuận rõ dễ phát sinh tranh chấp.",
      "recommendation": "Bổ sung điều kiện, thời hạn và trường hợp hoàn/khấu trừ tiền cọc.",
      "requiresExpertReview": true
    }
  ],
  "missingClauses": ["Điều kiện hoàn trả đặt cọc"],
  "recommendations": ["Quy định rõ cơ chế xử lý tiền đặt cọc khi chấm dứt hợp đồng."]
}
"""


def _format_graph_context(context: List[Dict[str, Any]]) -> str:
    if not context:
        return "(Không có bối cảnh tri thức từ graph)"
    lines = []
    for item in context:
        title = item.get("title") or item.get("code") or item.get("type", "")
        content = item.get("content") or item.get("description", "")
        lines.append(f"- [{item.get('type', 'CONTEXT')}] {title}: {content}")
    return "\n".join(lines)


class LegalPromptBuilder:
    def build_analyze_prompt(
        self,
        contract_text: str,
        contract_type: str,
        protected_party: Optional[str],
        question: Optional[str],
        graph_context: List[Dict[str, Any]],
        output_mode: str = "JSON",
    ) -> str:
        schema_hint = json.dumps(
            {
                "contractType": contract_type,
                "summary": "string",
                "overallRiskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                "riskItems": [
                    {
                        "riskType": "string",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "title": "string",
                        "explanation": "string",
                        "recommendation": "string",
                        "requiresExpertReview": True,
                    }
                ],
                "missingClauses": ["string"],
                "recommendations": ["string"],
            },
            ensure_ascii=False,
            indent=2,
        )

        parts = [
            "Bạn là chuyên gia rà soát hợp đồng pháp lý cho nền tảng LexiGuard AI.",
            _GROUNDING_RULES,
            _ANALYZE_FEWSHOT,
            f"LOẠI HỢP ĐỒNG: {contract_type}",
            f"BÊN CẦN BẢO VỆ: {protected_party or 'Không chỉ định'}",
            f"CÂU HỎI: {question or 'Phân tích rủi ro tổng quát của hợp đồng.'}",
            "\nBỐI CẢNH TRI THỨC (từ knowledge graph):",
            _format_graph_context(graph_context),
            "\nNỘI DUNG HỢP ĐỒNG:\n" + contract_text,
        ]
        if output_mode == "JSON":
            parts.append(
                "\nYÊU CẦU ĐẦU RA: Trả về DUY NHẤT một đối tượng JSON hợp lệ theo "
                "schema sau (không kèm markdown, không giải thích ngoài JSON):\n"
                + schema_hint
            )
        return "\n".join(parts)

    def build_compare_prompt(
        self,
        document_a: str,
        document_b: str,
        contract_type: str,
        protected_party: Optional[str],
        graph_context: List[Dict[str, Any]],
        output_mode: str = "JSON",
    ) -> str:
        schema_hint = json.dumps(
            {
                "summary": "string",
                "moreFavorableVersion": "A|B|EQUAL|INSUFFICIENT_DATA",
                "overallRiskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                "clauseComparisons": [
                    {
                        "topic": "string",
                        "documentAContent": "string",
                        "documentBContent": "string",
                        "differenceType": "SAME|MINOR_DIFFERENCE|MATERIAL_DIFFERENCE|CONFLICT|MISSING_IN_A|MISSING_IN_B",
                        "legalImpact": "string",
                        "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                        "recommendation": "string",
                    }
                ],
                "riskIncreases": ["string"],
                "riskReductions": ["string"],
                "recommendations": ["string"],
            },
            ensure_ascii=False,
            indent=2,
        )
        parts = [
            "Bạn là chuyên gia so sánh hợp đồng pháp lý cho nền tảng LexiGuard AI.",
            _GROUNDING_RULES,
            f"LOẠI HỢP ĐỒNG: {contract_type}",
            f"BÊN CẦN BẢO VỆ: {protected_party or 'Không chỉ định'}",
            "\nBỐI CẢNH TRI THỨC (từ knowledge graph):",
            _format_graph_context(graph_context),
            "\nVĂN BẢN A:\n" + document_a,
            "\nVĂN BẢN B:\n" + document_b,
            "\nNhiệm vụ: so sánh từng nhóm điều khoản, chỉ ra khác biệt, đánh giá "
            "mức độ có lợi cho bên cần bảo vệ.",
        ]
        if output_mode == "JSON":
            parts.append(
                "\nYÊU CẦU ĐẦU RA: Trả về DUY NHẤT một đối tượng JSON hợp lệ theo "
                "schema sau (không kèm markdown):\n" + schema_hint
            )
        return "\n".join(parts)

    def build_classify_prompt(self, text: str) -> str:
        schema_hint = json.dumps(
            {
                "contractType": "HOUSE_PURCHASE|HOUSE_RENTAL|LAND_TRANSFER|LAND_DEPOSIT|SERVICE_CONTRACT|COMMERCIAL_CONTRACT|OTHER",
                "confidence": 0.0,
                "reason": "string",
                "detectedParties": ["string"],
                "detectedImportantTerms": ["string"],
            },
            ensure_ascii=False,
            indent=2,
        )
        return "\n".join(
            [
                "Bạn là bộ phân loại hợp đồng pháp lý của LexiGuard AI.",
                _GROUNDING_RULES,
                "Phân loại đoạn văn bản hợp đồng sau vào đúng một loại.",
                "\nVĂN BẢN:\n" + text,
                "\nTrả về DUY NHẤT JSON hợp lệ theo schema:\n" + schema_hint,
            ]
        )
