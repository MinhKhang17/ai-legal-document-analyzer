"""Legal analysis service: classify, analyze and compare contracts.

Behavior contract:
  * When an LLM is configured, the service builds a grounded prompt, calls the
    LLM and parses its JSON answer (with a deterministic safety net if parsing
    fails).
  * When no LLM is configured, it returns a deterministic, honest fallback
    built from the knowledge graph + heuristic clause detection. It never
    fabricates an "AI answer"; responses are flagged with fallback=true and
    llmUsed=false.
"""
from __future__ import annotations

import json
import re
from typing import Any, Dict, List, Optional, Tuple

from app.core.errors import AppError, LLMNotConfiguredError
from app.graph import schema
from app.graph.service import GraphService
from app.models.legal_models import (
    AnalyzeContractRequest,
    ClassifyContractResponse,
    CompareContractsRequest,
    ContractType,
)
from app.services.embedding_service import get_embedding_service, normalize
from app.services.legal_prompt_builder import LegalPromptBuilder
from app.services.llm_client import LLMClient, get_llm_client


# --- Lightweight keyword signals for clause / contract detection --------------

CONTRACT_KEYWORDS: Dict[str, List[str]] = {
    "HOUSE_RENTAL": ["thuê nhà", "cho thuê", "tiền thuê", "bên thuê", "bên cho thuê"],
    "HOUSE_PURCHASE": ["mua bán nhà", "mua nhà", "bán nhà", "căn hộ", "quyền sở hữu nhà"],
    "LAND_TRANSFER": ["chuyển nhượng", "quyền sử dụng đất", "thửa đất", "sổ đỏ", "giấy chứng nhận quyền sử dụng đất"],
    "LAND_DEPOSIT": ["đặt cọc", "tiền cọc", "hợp đồng đặt cọc"],
    "SERVICE_CONTRACT": ["dịch vụ", "cung cấp dịch vụ", "nghiệm thu", "bên cung cấp", "phạm vi công việc"],
    "COMMERCIAL_CONTRACT": ["thương mại", "mua bán hàng hóa", "giao hàng", "thanh toán quốc tế", "đối tác"],
}

CLAUSE_KEYWORDS: Dict[str, List[str]] = {
    "PARTY_INFO": ["bên a", "bên b", "căn cước", "cmnd", "cccd", "địa chỉ", "đại diện", "mã số thuế", "hộ khẩu"],
    "OBJECT": ["đối tượng", "tài sản", "hàng hóa", "diện tích", "địa chỉ tài sản", "đặc điểm", "thông số kỹ thuật"],
    "PAYMENT": ["thanh toán", "thanh toan", "số tiền", "giá", "tiến độ thanh toán", "đợt thanh toán", "chuyển khoản"],
    "DEPOSIT": ["đặt cọc", "tiền cọc", "cọc", "khoản cọc"],
    "HANDOVER": ["bàn giao", "giao nhận", "biên bản bàn giao", "thời điểm giao", "nghiệm thu bàn giao"],
    "RIGHTS_OBLIGATIONS": ["quyền và nghĩa vụ", "nghĩa vụ", "quyền lợi", "trách nhiệm của các bên"],
    "PENALTY": ["phạt", "phạt vi phạm", "vi phạm hợp đồng", "mức phạt", "lãi chậm"],
    "COMPENSATION": ["bồi thường", "thiệt hại", "đền bù"],
    "TERMINATION": ["chấm dứt", "đơn phương", "hủy hợp đồng", "hủy bỏ hợp đồng", "thanh lý hợp đồng"],
    "TAX_FEE": ["thuế", "lệ phí", "phí trước bạ", "công chứng", "thuế thu nhập", "phí dịch vụ"],
    "DISPUTE": ["tranh chấp", "tòa án", "trọng tài", "giải quyết tranh chấp", "luật áp dụng", "hòa giải"],
    "FORCE_MAJEURE": ["bất khả kháng", "sự kiện khách quan", "thiên tai", "dịch bệnh"],
    "CONFIDENTIALITY": ["bảo mật", "thông tin mật", "không tiết lộ"],
    "ACCEPTANCE": ["nghiệm thu", "xác nhận hoàn thành", "biên bản nghiệm thu"],
    "WARRANTY": ["bảo hành", "thời hạn bảo hành"],
}

# Party label patterns -> human-readable display name (accent-stripped match).
PARTY_PATTERNS: Dict[str, str] = {
    r"ben\s+cho\s+thue": "bên cho thuê",
    r"ben\s+thue": "bên thuê",
    r"ben\s+ban": "bên bán",
    r"ben\s+mua": "bên mua",
    r"ben\s+chuyen\s+nhuong": "bên chuyển nhượng",
    r"ben\s+nhan\s+chuyen\s+nhuong": "bên nhận chuyển nhượng",
    r"ben\s+cung\s+cap": "bên cung cấp",
    r"ben\s+su\s+dung\s+dich\s+vu": "bên sử dụng dịch vụ",
    r"ben\s+giao": "bên giao",
    r"ben\s+nhan": "bên nhận",
    r"ben\s+a\b": "bên A",
    r"ben\s+b\b": "bên B",
}

_SEVERITY_ORDER = {"LOW": 1, "MEDIUM": 2, "HIGH": 3, "CRITICAL": 4}


def detect_clause_types(text: str) -> List[str]:
    """Return clause types whose keywords appear in the text."""
    norm = normalize(text)
    found = []
    for clause, keywords in CLAUSE_KEYWORDS.items():
        if any(normalize(k) in norm for k in keywords):
            found.append(clause)
    return found


def detect_parties(text: str) -> List[str]:
    """Return human-readable party labels detected in the text.

    Matching is done on the accent-stripped, lower-cased text so it is robust to
    Vietnamese diacritics and casing. More specific patterns (e.g. "bên cho
    thuê") are preferred over generic ones ("bên thuê") when both would match
    the same span.
    """
    norm = normalize(text)
    result: List[str] = []
    seen: set[str] = set()
    # Sort patterns by length descending so specific phrases win first.
    for pattern in sorted(PARTY_PATTERNS, key=len, reverse=True):
        if re.search(pattern, norm):
            label = PARTY_PATTERNS[pattern]
            if label not in seen:
                seen.add(label)
                result.append(label)
    return result


class LegalService:
    def __init__(
        self,
        graph_service: Optional[GraphService] = None,
        llm_client: Optional[LLMClient] = None,
        prompt_builder: Optional[LegalPromptBuilder] = None,
    ) -> None:
        self.graph = graph_service or GraphService()
        self.llm = llm_client or get_llm_client()
        self.prompts = prompt_builder or LegalPromptBuilder()
        self.embeddings = get_embedding_service()

    # ======================================================================
    # CLASSIFY
    # ======================================================================

    def classify(self, text: str) -> Dict[str, Any]:
        # If an LLM is available, try it first, then fall back on parse failure.
        if self.llm.configured:
            try:
                prompt = self.prompts.build_classify_prompt(text)
                raw = self.llm.generate(prompt, json_output=True).text
                parsed = _extract_json(raw)
                if parsed and parsed.get("contractType"):
                    return self._coerce_classify(parsed)
            except AppError:
                pass  # fall through to deterministic classification

        return self._classify_heuristic(text)

    def _classify_heuristic(self, text: str) -> Dict[str, Any]:
        norm = normalize(text)
        scores: Dict[str, int] = {}
        for ctype, keywords in CONTRACT_KEYWORDS.items():
            scores[ctype] = sum(1 for k in keywords if normalize(k) in norm)

        best_type = max(scores, key=scores.get)
        best_score = scores[best_type]
        total = sum(scores.values()) or 1

        if best_score == 0:
            contract_type = ContractType.OTHER.value
            confidence = 0.0
            reason = "Không phát hiện đủ từ khóa đặc trưng để phân loại."
        else:
            contract_type = best_type
            confidence = round(min(0.95, 0.4 + 0.5 * (best_score / total)), 2)
            reason = (
                f"Phát hiện {best_score} tín hiệu từ khóa đặc trưng cho loại "
                f"{best_type}."
            )

        return {
            "contractType": contract_type,
            "confidence": confidence,
            "reason": reason,
            "detectedParties": detect_parties(text),
            "detectedImportantTerms": detect_clause_types(text),
        }

    def _coerce_classify(self, parsed: Dict[str, Any]) -> Dict[str, Any]:
        ct = parsed.get("contractType", "OTHER")
        if ct not in {c.value for c in ContractType}:
            ct = "OTHER"
        return {
            "contractType": ct,
            "confidence": float(parsed.get("confidence", 0.0) or 0.0),
            "reason": parsed.get("reason", ""),
            "detectedParties": parsed.get("detectedParties", []) or [],
            "detectedImportantTerms": parsed.get("detectedImportantTerms", []) or [],
        }

    # ======================================================================
    # ANALYZE
    # ======================================================================

    def analyze(self, req: AnalyzeContractRequest) -> Dict[str, Any]:
        # 1) Resolve contract type.
        contract_type = req.contractType.value
        if contract_type in (ContractType.UNKNOWN.value, ContractType.OTHER.value):
            classified = self.classify(req.contractText)
            contract_type = classified["contractType"]
            if contract_type == ContractType.OTHER.value:
                contract_type = ContractType.UNKNOWN.value

        # 2) Detect present clauses.
        detected_clauses = detect_clause_types(req.contractText)

        # 3) Pull graph context.
        graph_context = self._build_graph_context(contract_type, detected_clauses) \
            if req.options.includeGraphContext else []

        # 4) Try LLM, else deterministic fallback.
        if self.llm.configured:
            try:
                prompt = self.prompts.build_analyze_prompt(
                    req.contractText,
                    contract_type,
                    req.protectedParty,
                    req.question,
                    graph_context,
                    req.options.outputMode.value,
                )
                json_mode = req.options.outputMode.value == "JSON"
                raw = self.llm.generate(prompt, json_output=json_mode).text
                parsed = _extract_json(raw)
                if parsed:
                    return self._coerce_analysis(parsed, contract_type, graph_context)
            except AppError:
                pass  # fall through

        return self._analyze_fallback(
            contract_type, detected_clauses, graph_context
        )

    def _analyze_fallback(
        self,
        contract_type: str,
        detected_clauses: List[str],
        graph_context: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        required = schema.CONTRACT_REQUIRED_CLAUSES.get(contract_type, [])
        missing = [c for c in required if c not in detected_clauses]

        risk_items: List[Dict[str, Any]] = []
        recommendations: List[str] = []

        # Risks from missing required clauses.
        for clause in missing:
            risk_code = schema.CLAUSE_RISK_MAP.get(clause, "MISSING_CLAUSE_RISK")
            rec = schema.RISK_RECOMMENDATIONS.get(risk_code, "")
            risk_items.append(
                {
                    "riskType": risk_code,
                    "severity": "HIGH",
                    "title": f"Thiếu điều khoản: {schema.CLAUSE_TYPES.get(clause, clause)}",
                    "explanation": (
                        f"Hợp đồng loại {contract_type} thường cần điều khoản "
                        f"{schema.CLAUSE_TYPES.get(clause, clause)} nhưng chưa phát hiện trong văn bản."
                    ),
                    "recommendation": rec,
                    "requiresExpertReview": True,
                }
            )
            if rec:
                recommendations.append(rec)

        # Contract-level risks from graph.
        for ctx in graph_context:
            if ctx.get("type") == "RiskType":
                recs = ctx.get("metadata", {}).get("recommendations", [])
                rec = recs[0] if recs else schema.RISK_RECOMMENDATIONS.get(ctx["id"], "")
                risk_items.append(
                    {
                        "riskType": ctx["id"],
                        "severity": "MEDIUM",
                        "title": f"Rủi ro tiềm ẩn: {ctx['id']}",
                        "explanation": ctx.get("content", ""),
                        "recommendation": rec,
                        "requiresExpertReview": False,
                    }
                )
                if rec:
                    recommendations.append(rec)

        overall = self._aggregate_risk_level(risk_items)
        missing_names = [schema.CLAUSE_TYPES.get(c, c) for c in missing]

        summary = self._fallback_summary(contract_type, detected_clauses, missing)

        return {
            "contractType": contract_type,
            "summary": summary,
            "overallRiskLevel": overall,
            "riskItems": _dedup_risk_items(risk_items),
            "missingClauses": missing_names,
            "recommendations": _dedup_list(recommendations),
            "graphContextUsed": graph_context,
            "llmUsed": False,
            "fallback": True,
        }

    def _fallback_summary(
        self, contract_type: str, detected: List[str], missing: List[str]
    ) -> str:
        ct_name = schema.CONTRACT_TYPES.get(contract_type, contract_type)
        base = (
            f"Phân tích sơ bộ (không dùng LLM) cho {ct_name}. "
            f"Phát hiện {len(detected)} nhóm điều khoản, "
            f"thiếu {len(missing)} điều khoản quan trọng."
        )
        if missing:
            base += " Cần bổ sung và rà soát các điều khoản còn thiếu."
        base += " Kết quả mang tính tham khảo, nên có chuyên gia pháp lý review."
        return base

    def _coerce_analysis(
        self,
        parsed: Dict[str, Any],
        contract_type: str,
        graph_context: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        risk_items = []
        for ri in parsed.get("riskItems", []) or []:
            risk_items.append(
                {
                    "riskType": ri.get("riskType", "UNKNOWN"),
                    "severity": _coerce_level(ri.get("severity"), "MEDIUM"),
                    "title": ri.get("title", ""),
                    "explanation": ri.get("explanation", ""),
                    "recommendation": ri.get("recommendation", ""),
                    "requiresExpertReview": bool(ri.get("requiresExpertReview", False)),
                }
            )
        return {
            "contractType": parsed.get("contractType", contract_type),
            "summary": parsed.get("summary", ""),
            "overallRiskLevel": _coerce_level(parsed.get("overallRiskLevel"), "MEDIUM"),
            "riskItems": risk_items,
            "missingClauses": parsed.get("missingClauses", []) or [],
            "recommendations": parsed.get("recommendations", []) or [],
            "graphContextUsed": graph_context,
            "llmUsed": True,
            "fallback": False,
        }

    # ======================================================================
    # COMPARE
    # ======================================================================

    def compare(self, req: CompareContractsRequest) -> Dict[str, Any]:
        contract_type = req.contractType.value
        if contract_type in (ContractType.UNKNOWN.value, ContractType.OTHER.value):
            contract_type = self.classify(req.documentAText)["contractType"]

        graph_context = self._build_graph_context(contract_type, []) \
            if req.options.includeGraphContext else []

        if self.llm.configured:
            try:
                prompt = self.prompts.build_compare_prompt(
                    req.documentAText,
                    req.documentBText,
                    contract_type,
                    req.protectedParty,
                    graph_context,
                    req.options.outputMode.value,
                )
                json_mode = req.options.outputMode.value == "JSON"
                raw = self.llm.generate(prompt, json_output=json_mode).text
                parsed = _extract_json(raw)
                if parsed:
                    return self._coerce_comparison(parsed)
            except AppError:
                pass

        return self._compare_fallback(req, contract_type)

    def _compare_fallback(
        self, req: CompareContractsRequest, contract_type: str
    ) -> Dict[str, Any]:
        clauses_a = set(detect_clause_types(req.documentAText))
        clauses_b = set(detect_clause_types(req.documentBText))
        all_clauses = sorted(clauses_a | clauses_b)

        comparisons: List[Dict[str, Any]] = []
        risk_increases: List[str] = []
        risk_reductions: List[str] = []

        for clause in all_clauses:
            topic = schema.CLAUSE_TYPES.get(clause, clause)
            in_a = clause in clauses_a
            in_b = clause in clauses_b
            if in_a and in_b:
                diff = "SAME"
                impact = "Cả hai văn bản đều có nhóm điều khoản này."
                level = "LOW"
                rec = "Đối chiếu chi tiết nội dung để xác định khác biệt cụ thể."
            elif in_a and not in_b:
                diff = "MISSING_IN_B"
                impact = f"Văn bản B thiếu điều khoản {topic}."
                level = "HIGH"
                rec = f"Bổ sung điều khoản {topic} vào văn bản B."
                risk_increases.append(f"B thiếu {topic}")
            else:
                diff = "MISSING_IN_A"
                impact = f"Văn bản A thiếu điều khoản {topic}."
                level = "HIGH"
                rec = f"Bổ sung điều khoản {topic} vào văn bản A."
                risk_increases.append(f"A thiếu {topic}")

            comparisons.append(
                {
                    "topic": topic,
                    "documentAContent": "Có đề cập" if in_a else "Không phát hiện",
                    "documentBContent": "Có đề cập" if in_b else "Không phát hiện",
                    "differenceType": diff,
                    "legalImpact": impact,
                    "riskLevel": level,
                    "recommendation": rec,
                }
            )

        # Decide favorability: more required clauses present => more favorable.
        required = set(schema.CONTRACT_REQUIRED_CLAUSES.get(contract_type, []))
        score_a = len(clauses_a & required)
        score_b = len(clauses_b & required)
        if not required:
            favorable = "INSUFFICIENT_DATA"
        elif score_a > score_b:
            favorable = "A"
        elif score_b > score_a:
            favorable = "B"
        else:
            favorable = "EQUAL"

        overall = self._aggregate_risk_level(
            [{"severity": c["riskLevel"]} for c in comparisons]
        )

        summary = (
            f"So sánh sơ bộ (không dùng LLM) cho {schema.CONTRACT_TYPES.get(contract_type, contract_type)}. "
            f"A có {score_a}/{len(required)} điều khoản bắt buộc, B có {score_b}/{len(required)}. "
            "Kết quả dựa trên phát hiện điều khoản theo từ khóa, nên có chuyên gia review."
        )

        return {
            "summary": summary,
            "moreFavorableVersion": favorable,
            "overallRiskLevel": overall,
            "clauseComparisons": comparisons,
            "riskIncreases": _dedup_list(risk_increases),
            "riskReductions": _dedup_list(risk_reductions),
            "recommendations": _dedup_list([c["recommendation"] for c in comparisons]),
            "llmUsed": False,
            "fallback": True,
        }

    def _coerce_comparison(self, parsed: Dict[str, Any]) -> Dict[str, Any]:
        comparisons = []
        for c in parsed.get("clauseComparisons", []) or []:
            comparisons.append(
                {
                    "topic": c.get("topic", ""),
                    "documentAContent": c.get("documentAContent", ""),
                    "documentBContent": c.get("documentBContent", ""),
                    "differenceType": c.get("differenceType", "SAME"),
                    "legalImpact": c.get("legalImpact", ""),
                    "riskLevel": _coerce_level(c.get("riskLevel"), "LOW"),
                    "recommendation": c.get("recommendation", ""),
                }
            )
        return {
            "summary": parsed.get("summary", ""),
            "moreFavorableVersion": parsed.get("moreFavorableVersion", "INSUFFICIENT_DATA"),
            "overallRiskLevel": _coerce_level(parsed.get("overallRiskLevel"), "MEDIUM"),
            "clauseComparisons": comparisons,
            "riskIncreases": parsed.get("riskIncreases", []) or [],
            "riskReductions": parsed.get("riskReductions", []) or [],
            "recommendations": parsed.get("recommendations", []) or [],
            "llmUsed": True,
            "fallback": False,
        }

    # ======================================================================
    # Shared helpers
    # ======================================================================

    def _build_graph_context(
        self, contract_type: str, clause_types: List[str]
    ) -> List[Dict[str, Any]]:
        context: List[Dict[str, Any]] = []
        seen: set[str] = set()

        def add(item: Dict[str, Any]) -> None:
            key = f"{item['type']}::{item['id']}"
            if key not in seen:
                seen.add(key)
                context.append(item)

        try:
            for row in self.graph.get_risks_for_contract(contract_type):
                recs = [r for r in row.get("recommendations", []) if r]
                add(
                    {
                        "id": row["code"],
                        "type": "RiskType",
                        "title": row["code"],
                        "content": row.get("description", ""),
                        "metadata": {"recommendations": recs},
                    }
                )
            if clause_types:
                for row in self.graph.get_clause_risks(clause_types):
                    recs = [r for r in row.get("recommendations", []) if r]
                    add(
                        {
                            "id": row["code"],
                            "type": "RiskType",
                            "title": row["code"],
                            "content": row.get("description", ""),
                            "metadata": {
                                "clauseType": row.get("clause"),
                                "recommendations": recs,
                            },
                        }
                    )

            # Verified legal articles for the present clauses give the LLM a
            # concrete, citable legal basis (reduces hallucinated statutes).
            if clause_types:
                for row in self.graph.get_legal_articles(
                    clause_codes=clause_types, limit=8
                ):
                    add(
                        {
                            "id": row["code"],
                            "type": "LegalArticle",
                            "title": row.get("title", row["code"]),
                            "content": row.get("summary", ""),
                            "metadata": {
                                "document": row.get("document"),
                                "reference": row.get("reference"),
                            },
                        }
                    )
        except AppError:
            # Graph context is best-effort; analysis still proceeds without it.
            return context
        return context

    def _aggregate_risk_level(self, items: List[Dict[str, Any]]) -> str:
        if not items:
            return "LOW"
        max_level = max(
            (_SEVERITY_ORDER.get(i.get("severity", "LOW"), 1) for i in items),
            default=1,
        )
        for name, val in _SEVERITY_ORDER.items():
            if val == max_level:
                return name
        return "MEDIUM"


# --- module-level helpers -----------------------------------------------------


def _coerce_level(value: Any, default: str) -> str:
    if isinstance(value, str) and value.upper() in _SEVERITY_ORDER:
        return value.upper()
    return default


def _extract_json(text: str) -> Optional[Dict[str, Any]]:
    """Best-effort extraction of a JSON object from an LLM response."""
    if not text:
        return None
    text = text.strip()
    # Strip markdown fences if present.
    if text.startswith("```"):
        text = re.sub(r"^```[a-zA-Z]*\n?", "", text)
        text = re.sub(r"\n?```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    # Fallback: grab the outermost { ... } block.
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            return None
    return None


def _dedup_list(items: List[str]) -> List[str]:
    seen = set()
    result = []
    for i in items:
        if i and i not in seen:
            seen.add(i)
            result.append(i)
    return result


def _dedup_risk_items(items: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    seen = set()
    result = []
    for i in items:
        key = (i.get("riskType"), i.get("title"))
        if key not in seen:
            seen.add(key)
            result.append(i)
    return result
