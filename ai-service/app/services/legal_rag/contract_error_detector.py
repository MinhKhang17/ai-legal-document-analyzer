"""Contract Error Detection Service.

Phát hiện tất cả lỗi sai trong hợp đồng thuê nhà / thuê đất từ file PDF:
1. Thiếu điều khoản bắt buộc (Missing Required Clauses)
2. Lỗi hình thức / định dạng (Format Errors)
3. Lỗi nội dung / logic (Logical Errors) — sử dụng Gemini LLM
4. Rủi ro pháp lý (Legal Risks) — tái sử dụng pipeline hiện tại
"""
from __future__ import annotations

import json
import logging
import re
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Sequence

from fastapi import UploadFile

from app.core.config import settings
from app.models.knowledge_models import ExtractedDocument
from app.services.gemini_client import GeminiClient, GeminiResponse
from app.services.legal_rag.knowledge_base import (
    KnowledgeBaseParser,
    RiskKbSearchIndex,
    RiskKnowledgeBase,
    normalize_text,
    load_knowledge_base,
)
from app.services.legal_rag.pipeline import (
    ContractAnalysisService,
    ContractClause,
    ContractClauseExtractor,
    ClauseFinding,
)
from app.services.document_embedding_service import HashingEmbeddingProvider
from app.services.loader.document_loaders import build_default_loader_registry

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Data Models
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class ContractError:
    """Một lỗi sai được phát hiện trong hợp đồng."""

    error_id: str
    category: str  # "missing_clause" | "format_error" | "logical_error" | "legal_risk"
    severity: str  # "HIGH" | "MEDIUM" | "LOW"
    title: str
    description: str
    suggestion: str = ""
    clause_reference: str = ""
    legal_basis: str = ""
    confidence: float = 1.0


@dataclass(frozen=True)
class ErrorDetectionReport:
    """Báo cáo tổng hợp tất cả lỗi sai tìm được."""

    document_id: str
    filename: str
    title: str
    file_type: str
    total_clauses: int
    errors: list[ContractError]
    summary: ErrorSummary
    full_text_preview: str = ""


@dataclass(frozen=True)
class ErrorSummary:
    """Thống kê tổng hợp lỗi."""

    total_errors: int
    missing_clause_count: int
    format_error_count: int
    logical_error_count: int
    legal_risk_count: int
    high_count: int
    medium_count: int
    low_count: int


# ---------------------------------------------------------------------------
# 1. Missing Clause Detector — Kiểm tra thiếu điều khoản bắt buộc
# ---------------------------------------------------------------------------

# Các điều khoản bắt buộc theo BLDS 2015 và Luật Nhà ở 2023
REQUIRED_CLAUSES = [
    {
        "id": "info_ben_cho_thue",
        "name": "Thông tin bên cho thuê",
        "severity": "HIGH",
        "patterns": [
            r"bên\s*(cho\s*thuê|a)",
            r"ben\s*(cho\s*thue|a)",
            r"chủ\s*(?:sở\s*hữu|nhà|đất)",
            r"chu\s*(?:so\s*huu|nha|dat)",
        ],
        "legal_basis": "Điều 472 BLDS 2015",
        "suggestion": "Bổ sung đầy đủ thông tin bên cho thuê: họ tên, số CCCD/CMND, địa chỉ thường trú, số điện thoại.",
    },
    {
        "id": "info_ben_thue",
        "name": "Thông tin bên thuê",
        "severity": "HIGH",
        "patterns": [
            r"bên\s*(thuê|b)",
            r"ben\s*(thue|b)",
            r"người\s*thuê",
            r"nguoi\s*thue",
        ],
        "legal_basis": "Điều 472 BLDS 2015",
        "suggestion": "Bổ sung đầy đủ thông tin bên thuê: họ tên, số CCCD/CMND, địa chỉ thường trú, số điện thoại.",
    },
    {
        "id": "mo_ta_tai_san",
        "name": "Mô tả tài sản cho thuê",
        "severity": "HIGH",
        "patterns": [
            r"(?:địa\s*chỉ|dia\s*chi).*(?:nhà|nha|căn\s*hộ|can\s*ho|đất|dat|phòng|phong)",
            r"diện\s*tích|dien\s*tich",
            r"tài\s*sản\s*(?:cho\s*)?thuê|tai\s*san\s*(?:cho\s*)?thue",
            r"(?:nhà|nha|căn\s*hộ|can\s*ho).*(?:số|so|tại|tai|ở|o)",
        ],
        "legal_basis": "Điều 472 BLDS 2015",
        "suggestion": "Bổ sung mô tả chi tiết tài sản cho thuê: địa chỉ, diện tích, tình trạng hiện tại, trang thiết bị đi kèm.",
    },
    {
        "id": "thoi_han_thue",
        "name": "Thời hạn thuê",
        "severity": "HIGH",
        "patterns": [
            r"thời\s*hạn|thoi\s*han",
            r"(?:từ\s*ngày|tu\s*ngay).*(?:đến\s*ngày|den\s*ngay)",
            r"thời\s*gian\s*thuê|thoi\s*gian\s*thue",
            r"(?:bắt\s*đầu|bat\s*dau).*(?:kết\s*thúc|ket\s*thuc)",
        ],
        "legal_basis": "Điều 472 BLDS 2015",
        "suggestion": "Bổ sung thời hạn thuê rõ ràng: ngày bắt đầu, ngày kết thúc, hoặc thời gian thuê cụ thể.",
    },
    {
        "id": "gia_thue",
        "name": "Giá thuê và phương thức thanh toán",
        "severity": "HIGH",
        "patterns": [
            r"giá\s*thuê|gia\s*thue",
            r"tiền\s*thuê|tien\s*thue",
            r"(?:thanh\s*toán|thanh\s*toan).*(?:tháng|thang|quý|quy|năm|nam)",
            r"(?:\d+[\.,]?\d*)\s*(?:đồng|dong|vnđ|vnd|triệu|trieu)",
        ],
        "legal_basis": "Điều 472 BLDS 2015",
        "suggestion": "Bổ sung giá thuê cụ thể (bằng số và chữ), kỳ thanh toán, phương thức thanh toán (tiền mặt/chuyển khoản).",
    },
    {
        "id": "tien_dat_coc",
        "name": "Tiền đặt cọc",
        "severity": "MEDIUM",
        "patterns": [
            r"đặt\s*cọc|dat\s*coc",
            r"tiền\s*cọc|tien\s*coc",
            r"ký\s*quỹ|ky\s*quy",
        ],
        "legal_basis": "Điều 328 BLDS 2015",
        "suggestion": "Bổ sung điều khoản đặt cọc: số tiền cọc, điều kiện hoàn trả, thời hạn hoàn trả.",
    },
    {
        "id": "quyen_nghia_vu",
        "name": "Quyền và nghĩa vụ các bên",
        "severity": "HIGH",
        "patterns": [
            r"quyền\s*và\s*nghĩa\s*vụ|quyen\s*va\s*nghia\s*vu",
            r"quyền\s*(?:của\s*)?(?:bên|ben)",
            r"nghĩa\s*vụ\s*(?:của\s*)?(?:bên|ben)",
            r"trách\s*nhiệm|trach\s*nhiem",
        ],
        "legal_basis": "Điều 474-476 BLDS 2015",
        "suggestion": "Bổ sung điều khoản quyền và nghĩa vụ rõ ràng cho cả hai bên.",
    },
    {
        "id": "cham_dut_hop_dong",
        "name": "Điều kiện chấm dứt hợp đồng",
        "severity": "HIGH",
        "patterns": [
            r"chấm\s*dứt|cham\s*dut",
            r"(?:đơn\s*phương|don\s*phuong).*(?:chấm\s*dứt|hủy|cham\s*dut|huy)",
            r"thanh\s*lý\s*hợp\s*đồng|thanh\s*ly\s*hop\s*dong",
            r"kết\s*thúc\s*hợp\s*đồng|ket\s*thuc\s*hop\s*dong",
        ],
        "legal_basis": "Điều 428-429 BLDS 2015",
        "suggestion": "Bổ sung điều khoản chấm dứt hợp đồng: các trường hợp được đơn phương chấm dứt, thời gian thông báo trước, nghĩa vụ khi chấm dứt.",
    },
    {
        "id": "giai_quyet_tranh_chap",
        "name": "Giải quyết tranh chấp",
        "severity": "MEDIUM",
        "patterns": [
            r"tranh\s*chấp|tranh\s*chap",
            r"giải\s*quyết|giai\s*quyet",
            r"(?:tòa\s*án|toa\s*an|trọng\s*tài|trong\s*tai)",
            r"hòa\s*giải|hoa\s*giai",
        ],
        "legal_basis": "Điều 14 BLDS 2015",
        "suggestion": "Bổ sung phương thức giải quyết tranh chấp: thương lượng → hòa giải → tòa án/trọng tài.",
    },
]


class MissingClauseDetector:
    """Phát hiện các điều khoản bắt buộc bị thiếu."""

    def detect(self, full_text: str, clauses: list[ContractClause]) -> list[ContractError]:
        errors: list[ContractError] = []
        normalized_full = normalize_text(full_text)

        for idx, required in enumerate(REQUIRED_CLAUSES, start=1):
            found = False
            for pattern_str in required["patterns"]:
                pattern = re.compile(pattern_str, re.IGNORECASE)
                if pattern.search(full_text) or pattern.search(normalized_full):
                    found = True
                    break

            if not found:
                errors.append(
                    ContractError(
                        error_id=f"MISSING_{idx:03d}",
                        category="missing_clause",
                        severity=required["severity"],
                        title=f"Thiếu: {required['name']}",
                        description=f"Hợp đồng thiếu điều khoản bắt buộc về '{required['name']}'. "
                        f"Đây là nội dung bắt buộc theo quy định pháp luật.",
                        suggestion=required["suggestion"],
                        legal_basis=required["legal_basis"],
                        confidence=0.95,
                    )
                )

        return errors


# ---------------------------------------------------------------------------
# 2. Format Error Detector — Kiểm tra lỗi hình thức
# ---------------------------------------------------------------------------

DATE_PATTERNS = [
    re.compile(r"\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{4}"),
    re.compile(r"ngày\s+\d{1,2}\s+tháng\s+\d{1,2}\s+năm\s+\d{4}", re.IGNORECASE),
    re.compile(r"ngay\s+\d{1,2}\s+thang\s+\d{1,2}\s+nam\s+\d{4}", re.IGNORECASE),
]

CCCD_PATTERN = re.compile(r"\b\d{9,12}\b")

SIGNATURE_PATTERNS = [
    re.compile(r"(?:ký\s*tên|ky\s*ten)", re.IGNORECASE),
    re.compile(r"(?:chữ\s*ký|chu\s*ky)", re.IGNORECASE),
    re.compile(r"(?:đại\s*diện|dai\s*dien)", re.IGNORECASE),
    re.compile(r"(?:ký\s*và\s*ghi\s*rõ|ky\s*va\s*ghi\s*ro)", re.IGNORECASE),
]

LOCATION_PATTERNS = [
    re.compile(r"(?:tại|tai)\s+(?:thành\s*phố|thanh\s*pho|tỉnh|tinh|huyện|huyen|quận|quan)", re.IGNORECASE),
    re.compile(r"(?:lập\s*tại|lap\s*tai)", re.IGNORECASE),
]


class FormatErrorDetector:
    """Phát hiện lỗi hình thức trong hợp đồng."""

    def detect(self, full_text: str, clauses: list[ContractClause]) -> list[ContractError]:
        errors: list[ContractError] = []
        error_idx = 1

        # Kiểm tra thiếu ngày ký
        has_date = any(pattern.search(full_text) for pattern in DATE_PATTERNS)
        if not has_date:
            errors.append(
                ContractError(
                    error_id=f"FORMAT_{error_idx:03d}",
                    category="format_error",
                    severity="HIGH",
                    title="Thiếu ngày ký hợp đồng",
                    description="Hợp đồng không ghi rõ ngày tháng năm ký kết. "
                    "Điều này có thể gây khó khăn trong việc xác định hiệu lực và thời hạn hợp đồng.",
                    suggestion="Bổ sung ngày ký hợp đồng theo định dạng: 'Ngày ... tháng ... năm ...'",
                    legal_basis="Điều 401 BLDS 2015 - Hình thức hợp đồng",
                    confidence=0.9,
                )
            )
            error_idx += 1

        # Kiểm tra thiếu nơi ký
        has_location = any(pattern.search(full_text) for pattern in LOCATION_PATTERNS)
        if not has_location:
            errors.append(
                ContractError(
                    error_id=f"FORMAT_{error_idx:03d}",
                    category="format_error",
                    severity="MEDIUM",
                    title="Thiếu địa điểm ký hợp đồng",
                    description="Hợp đồng không ghi rõ nơi ký kết. "
                    "Thông tin này giúp xác định thẩm quyền giải quyết tranh chấp.",
                    suggestion="Bổ sung địa điểm ký hợp đồng, ví dụ: 'Lập tại Thành phố Hồ Chí Minh'",
                    confidence=0.8,
                )
            )
            error_idx += 1

        # Kiểm tra thiếu số CCCD/CMND
        has_id_numbers = CCCD_PATTERN.search(full_text)
        if not has_id_numbers:
            errors.append(
                ContractError(
                    error_id=f"FORMAT_{error_idx:03d}",
                    category="format_error",
                    severity="HIGH",
                    title="Thiếu số CCCD/CMND",
                    description="Hợp đồng không có số căn cước công dân hoặc chứng minh nhân dân "
                    "của các bên. Đây là thông tin bắt buộc để xác định danh tính.",
                    suggestion="Bổ sung số CCCD/CMND (12 số) của tất cả các bên tham gia hợp đồng.",
                    legal_basis="Điều 472 BLDS 2015",
                    confidence=0.85,
                )
            )
            error_idx += 1

        # Kiểm tra thiếu chữ ký
        has_signature = any(pattern.search(full_text) for pattern in SIGNATURE_PATTERNS)
        if not has_signature:
            errors.append(
                ContractError(
                    error_id=f"FORMAT_{error_idx:03d}",
                    category="format_error",
                    severity="HIGH",
                    title="Thiếu phần ký tên",
                    description="Hợp đồng không có phần dành cho chữ ký của các bên. "
                    "Hợp đồng cần có chữ ký hoặc điểm chỉ để có hiệu lực pháp lý.",
                    suggestion="Bổ sung phần ký tên cuối hợp đồng cho tất cả các bên (ký và ghi rõ họ tên).",
                    legal_basis="Điều 119 BLDS 2015 - Hình thức giao dịch dân sự",
                    confidence=0.85,
                )
            )
            error_idx += 1

        # Kiểm tra số lượng điều khoản quá ít
        if len(clauses) < 3:
            errors.append(
                ContractError(
                    error_id=f"FORMAT_{error_idx:03d}",
                    category="format_error",
                    severity="MEDIUM",
                    title="Hợp đồng quá ngắn / thiếu cấu trúc",
                    description=f"Hợp đồng chỉ có {len(clauses)} điều khoản. "
                    "Một hợp đồng thuê nhà/đất đầy đủ thường có tối thiểu 8-10 điều.",
                    suggestion="Xem xét bổ sung thêm các điều khoản cần thiết cho hợp đồng thuê.",
                    confidence=0.75,
                )
            )

        return errors


# ---------------------------------------------------------------------------
# 3. Logical Error Detector — Phát hiện lỗi logic / mâu thuẫn bằng LLM
# ---------------------------------------------------------------------------

LOGICAL_ANALYSIS_SYSTEM_PROMPT = """Bạn là chuyên gia pháp lý Việt Nam chuyên phân tích hợp đồng thuê nhà/thuê đất.

Nhiệm vụ: Tìm TẤT CẢ lỗi sai, bất hợp lý, mâu thuẫn trong hợp đồng.

Phân tích các khía cạnh:
1. Mâu thuẫn nội dung: Các điều khoản mâu thuẫn nhau
2. Bất hợp lý pháp lý: Vi phạm BLDS 2015, Luật Nhà ở 2023, Luật Đất đai 2024
3. Điều khoản bất lợi: Bất đối xứng quyền lợi giữa hai bên
4. Sai sót số liệu: Thời hạn > 20 năm, mức phạt > 8% giá trị hợp đồng
5. Thiếu rõ ràng: Điều khoản mơ hồ, không xác định được quyền nghĩa vụ cụ thể

Trả về JSON array, mỗi phần tử có cấu trúc:
{
  "severity": "HIGH|MEDIUM|LOW",
  "title": "Tiêu đề lỗi ngắn gọn",
  "description": "Mô tả chi tiết lỗi sai",
  "suggestion": "Gợi ý cách sửa",
  "clause_reference": "Điều khoản liên quan (nếu có)",
  "legal_basis": "Căn cứ pháp lý (nếu có)"
}

Chỉ trả về JSON array, không kèm markdown hay text khác.
Nếu không tìm thấy lỗi nào, trả về: []"""


class LogicalErrorDetector:
    """Phát hiện lỗi logic/mâu thuẫn bằng Gemini LLM.

    Hỗ trợ Gemini free tier:
    - Retry với exponential backoff khi bị rate limit (429)
    - Giới hạn text gửi đi để tránh vượt token limit
    - Timeout dài hơn cho model xử lý
    """

    MAX_RETRIES = 3
    RETRY_DELAYS = [5, 15, 30]  # seconds
    MAX_INPUT_CHARS = 6000  # Giới hạn ký tự cho free tier

    def __init__(self) -> None:
        self._client: GeminiClient | None = None
        if settings.gemini_api_key and settings.gemini_model:
            self._client = GeminiClient(
                api_key=settings.gemini_api_key,
                model=settings.gemini_model,
                base_url=settings.gemini_base_url,
                timeout_seconds=max(settings.gemini_timeout_seconds, 60.0),
                max_output_tokens=max(settings.gemini_max_output_tokens, 8192),
            )

    @property
    def is_available(self) -> bool:
        return self._client is not None

    def detect(self, full_text: str, clauses: list[ContractClause]) -> list[ContractError]:
        if not self.is_available:
            logger.info("Gemini LLM not configured, skipping logical error detection")
            return []

        # Chuẩn bị nội dung hợp đồng cho LLM
        contract_content = self._prepare_contract_text(full_text, clauses)
        if not contract_content.strip():
            return []

        user_prompt = f"Phân tích hợp đồng thuê nhà/thuê đất sau và tìm tất cả lỗi sai:\n\n{contract_content}"

        # Gọi Gemini với retry logic cho free tier
        result = self._call_with_retry(user_prompt)
        if result is None or result.error or not result.text:
            if result and result.error:
                logger.warning("Gemini LLM error after retries: %s", result.error)
            return []

        return self._parse_llm_response(result.text)

    def _call_with_retry(self, user_prompt: str) -> GeminiResponse | None:
        """Gọi Gemini API với retry khi bị rate limit (429)."""
        import time

        last_result: GeminiResponse | None = None

        for attempt in range(self.MAX_RETRIES + 1):
            result = self._client.generate_text(
                system_prompt=LOGICAL_ANALYSIS_SYSTEM_PROMPT,
                user_prompt=user_prompt,
            )
            last_result = result

            if result.text and not result.error:
                return result

            # Kiểm tra có phải lỗi rate limit không
            is_rate_limit = result.error and ("429" in result.error or "RESOURCE_EXHAUSTED" in result.error)

            if is_rate_limit and attempt < self.MAX_RETRIES:
                delay = self.RETRY_DELAYS[attempt]
                logger.warning(
                    "Gemini rate limited (attempt %d/%d), retrying in %ds...",
                    attempt + 1, self.MAX_RETRIES, delay,
                )
                time.sleep(delay)
                continue

            # Không phải rate limit hoặc hết retry → dừng
            break

        return last_result

    def _prepare_contract_text(self, full_text: str, clauses: list[ContractClause]) -> str:
        max_chars = self.MAX_INPUT_CHARS

        if clauses:
            parts = []
            current_len = 0
            for clause in clauses:
                chunk = f"[Điều khoản {clause.clause_id}: {clause.title}]\n{clause.text}"
                if current_len + len(chunk) > max_chars:
                    parts.append("...(các điều khoản còn lại bị lược bỏ do giới hạn)")
                    break
                parts.append(chunk)
                current_len += len(chunk) + 2
            return "\n\n".join(parts)

        # Fallback: sử dụng full text, giới hạn ký tự
        if len(full_text) <= max_chars:
            return full_text
        return full_text[:max_chars] + "\n...(nội dung bị cắt ngắn do giới hạn free tier)"

    def _parse_llm_response(self, response_text: str) -> list[ContractError]:
        errors: list[ContractError] = []

        # Log raw response for debugging
        logger.info(f"LLM raw response (first 500 chars): {response_text[:500]}")

        # Loại bỏ markdown code blocks
        clean_text = response_text.strip()
        if clean_text.startswith("```"):
            lines = clean_text.splitlines()
            if lines and lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].startswith("```"):
                lines = lines[:-1]
            clean_text = "\n".join(lines).strip()

        logger.info(f"Cleaned text (first 300 chars): {clean_text[:300]}")

        try:
            parsed = json.loads(clean_text)
        except json.JSONDecodeError as e:
            logger.warning(f"JSON decode error: {e}")
            # Thử extract JSON array từ text
            match = re.search(r"\[.*\]", clean_text, re.DOTALL)
            if match:
                try:
                    parsed = json.loads(match.group())
                    logger.info(f"Successfully extracted JSON array from text")
                except json.JSONDecodeError:
                    logger.warning(f"Failed to parse extracted JSON: {match.group()[:200]}")
                    return []
            else:
                logger.warning(f"No JSON array found in LLM response")
                return []

        if not isinstance(parsed, list):
            return []

        for idx, item in enumerate(parsed, start=1):
            if not isinstance(item, dict):
                continue

            severity = str(item.get("severity", "MEDIUM")).upper()
            if severity not in {"HIGH", "MEDIUM", "LOW"}:
                severity = "MEDIUM"

            title = str(item.get("title", "")).strip()
            description = str(item.get("description", "")).strip()
            if not title and not description:
                continue

            errors.append(
                ContractError(
                    error_id=f"LOGIC_{idx:03d}",
                    category="logical_error",
                    severity=severity,
                    title=title or "Lỗi logic được phát hiện",
                    description=description or title,
                    suggestion=str(item.get("suggestion", "")).strip(),
                    clause_reference=str(item.get("clause_reference", "")).strip(),
                    legal_basis=str(item.get("legal_basis", "")).strip(),
                    confidence=0.8,
                )
            )

        return errors


# ---------------------------------------------------------------------------
# 4. Legal Risk Detector — Tái sử dụng pipeline hiện tại + KB thuê nhà/đất
# ---------------------------------------------------------------------------

class LegalRiskDetector:
    """Phát hiện rủi ro pháp lý dựa trên knowledge base thuê nhà/đất."""

    def __init__(self) -> None:
        self._kb: RiskKnowledgeBase | None = None
        self._search_index: RiskKbSearchIndex | None = None
        self._load_rental_kb()

    def _load_rental_kb(self) -> None:
        """Load knowledge base đặc thù cho hợp đồng thuê nhà/đất."""
        rental_kb_path = settings.docs_flow_dir / "RISK_KB_RENTAL_VI.md"
        if not rental_kb_path.exists():
            logger.warning("Rental knowledge base not found: %s", rental_kb_path)
            # Fallback to default KB
            self._kb = load_knowledge_base()
        else:
            parser = KnowledgeBaseParser()
            self._kb = parser.parse(rental_kb_path)

        if self._kb and self._kb.concepts:
            embedding_provider = HashingEmbeddingProvider()
            self._search_index = RiskKbSearchIndex(self._kb, embedding_provider)

    def detect(self, full_text: str, clauses: list[ContractClause]) -> list[ContractError]:
        if not self._search_index or not self._kb:
            return []

        errors: list[ContractError] = []
        error_idx = 1

        for clause in clauses:
            candidates = self._search_index.search(clause.text, top_k=3)
            if not candidates:
                continue

            best = candidates[0]
            if best.combined_score < 0.35:
                continue

            taxonomy = self._search_index.map_taxonomy(clause.text, best.concept)

            errors.append(
                ContractError(
                    error_id=f"RISK_{error_idx:03d}",
                    category="legal_risk",
                    severity=best.concept.severity,
                    title=f"Rủi ro: {best.concept.display_name}",
                    description=self._build_description(clause, best),
                    suggestion=self._build_suggestion(best),
                    clause_reference=f"Điều khoản {clause.clause_id}: {clause.title}",
                    confidence=round(best.combined_score, 4),
                )
            )
            error_idx += 1

        return errors

    def _build_description(self, clause: ContractClause, candidate: Any) -> str:
        parts = [
            f"Phát hiện rủi ro '{candidate.concept.display_name}' "
            f"(mức độ: {candidate.concept.severity}) trong điều khoản '{clause.title}'.",
        ]
        if candidate.matched_aliases:
            parts.append(f"Từ khóa phát hiện: {', '.join(candidate.matched_aliases)}.")
        return " ".join(parts)

    def _build_suggestion(self, candidate: Any) -> str:
        concept = candidate.concept
        severity = concept.severity
        if severity == "HIGH":
            return (
                f"Cần xem xét lại điều khoản liên quan đến '{concept.display_name}'. "
                "Nên tham khảo ý kiến luật sư trước khi ký."
            )
        if severity == "MEDIUM":
            return (
                f"Nên thương lượng điều chỉnh điều khoản liên quan đến '{concept.display_name}' "
                "để đảm bảo quyền lợi cân bằng."
            )
        return f"Lưu ý về '{concept.display_name}' — xem xét có phù hợp không."


# ---------------------------------------------------------------------------
# Orchestrator — Tổng hợp tất cả detector
# ---------------------------------------------------------------------------

class ContractErrorDetectionService:
    """Service tổng hợp tất cả detector để tìm lỗi sai trong hợp đồng."""

    def __init__(self) -> None:
        self.loaders = build_default_loader_registry()
        self.clause_extractor = ContractClauseExtractor()
        self.missing_clause_detector = MissingClauseDetector()
        self.format_error_detector = FormatErrorDetector()
        self.logical_error_detector = LogicalErrorDetector()
        self.legal_risk_detector = LegalRiskDetector()

    def supported_formats(self) -> list[str]:
        return self.loaders.supported_extensions()

    def analyze_file(
        self, source_path: str, title: str | None = None, filename: str | None = None
    ) -> ErrorDetectionReport:
        path = Path(source_path)
        if not path.exists():
            raise FileNotFoundError(f"File not found: {source_path}")

        loader = self.loaders.resolve(path)
        extracted = loader.load(path)
        if title and title.strip():
            extracted = ExtractedDocument(
                source_path=extracted.source_path,
                title=title.strip(),
                file_type=extracted.file_type,
                blocks=extracted.blocks,
                metadata=extracted.metadata,
            )

        return self._analyze_document(extracted, filename=filename or path.name)

    async def analyze_upload(
        self, file: UploadFile, title: str | None = None
    ) -> ErrorDetectionReport:
        suffix = Path(file.filename or "").suffix.lower()
        if suffix not in self.supported_formats():
            raise ValueError(f"Unsupported file type: {suffix or '[unknown]'}")

        temp_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
                temp_path = Path(temp_file.name)
                while True:
                    chunk = await file.read(1024 * 1024)
                    if not chunk:
                        break
                    temp_file.write(chunk)

            return self.analyze_file(
                str(temp_path),
                title=title or Path(file.filename or "").stem,
                filename=file.filename,
            )
        finally:
            await file.close()
            if temp_path and temp_path.exists():
                try:
                    temp_path.unlink()
                except OSError:
                    logger.warning("Failed to remove temp file: %s", temp_path)

    def _analyze_document(
        self, extracted: ExtractedDocument, filename: str
    ) -> ErrorDetectionReport:
        # Extract clauses
        clauses = self.clause_extractor.extract(extracted)

        # Build full text
        full_text = "\n\n".join(
            block.text.strip() for block in extracted.blocks if block.text.strip()
        )

        # Run all detectors
        all_errors: list[ContractError] = []

        logger.info("Running missing clause detection...")
        all_errors.extend(self.missing_clause_detector.detect(full_text, clauses))

        logger.info("Running format error detection...")
        all_errors.extend(self.format_error_detector.detect(full_text, clauses))

        logger.info("Running logical error detection (LLM)...")
        all_errors.extend(self.logical_error_detector.detect(full_text, clauses))

        logger.info("Running legal risk detection...")
        all_errors.extend(self.legal_risk_detector.detect(full_text, clauses))

        # Build summary
        severity_counts = {"HIGH": 0, "MEDIUM": 0, "LOW": 0}
        category_counts = {
            "missing_clause": 0,
            "format_error": 0,
            "logical_error": 0,
            "legal_risk": 0,
        }
        for error in all_errors:
            sev = error.severity.upper()
            if sev in severity_counts:
                severity_counts[sev] += 1
            cat = error.category
            if cat in category_counts:
                category_counts[cat] += 1

        summary = ErrorSummary(
            total_errors=len(all_errors),
            missing_clause_count=category_counts["missing_clause"],
            format_error_count=category_counts["format_error"],
            logical_error_count=category_counts["logical_error"],
            legal_risk_count=category_counts["legal_risk"],
            high_count=severity_counts["HIGH"],
            medium_count=severity_counts["MEDIUM"],
            low_count=severity_counts["LOW"],
        )

        # Text preview (first 500 chars)
        text_preview = full_text[:500] + ("..." if len(full_text) > 500 else "")

        logger.info(
            "Error detection complete: filename=%s clauses=%d errors=%d (high=%d, medium=%d, low=%d)",
            filename,
            len(clauses),
            len(all_errors),
            severity_counts["HIGH"],
            severity_counts["MEDIUM"],
            severity_counts["LOW"],
        )

        return ErrorDetectionReport(
            document_id=extracted.metadata.get("document_id", extracted.title),
            filename=filename,
            title=extracted.title,
            file_type=extracted.file_type,
            total_clauses=len(clauses),
            errors=all_errors,
            summary=summary,
            full_text_preview=text_preview,
        )
