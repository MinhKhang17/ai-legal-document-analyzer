"""Enums for legal query intent classification, contract types, and response modes.

Based on EXE201 specification: Tong_hop_case_chatbot_phap_luat_phan_tich_hop_dong.docx
"""
from __future__ import annotations

from enum import Enum


class LegalQueryIntent(str, Enum):
    """14 intent categories for legal chatbot queries."""

    # Case 1: Hỏi chung về một loại hợp đồng (chưa upload file)
    GENERAL_LEGAL_QUESTION = "GENERAL_LEGAL_QUESTION"

    # Case 1b: Phân tích theo loại hợp đồng
    CONTRACT_TYPE_ANALYSIS = "CONTRACT_TYPE_ANALYSIS"

    # Case 2: Upload hợp đồng và yêu cầu phân tích toàn bộ
    FULL_CONTRACT_REVIEW = "FULL_CONTRACT_REVIEW"

    # Case 3: Hỏi một điều khoản cụ thể
    CLAUSE_ANALYSIS = "CLAUSE_ANALYSIS"

    # Case 4: Hỏi hợp đồng thiếu gì
    MISSING_CLAUSE_CHECK = "MISSING_CLAUSE_CHECK"

    # Case 5: Hỏi hợp đồng có hợp pháp/vô hiệu không
    LEGAL_VALIDITY_CHECK = "LEGAL_VALIDITY_CHECK"

    # Case 6: Hỏi có nên ký hay không
    SIGNING_DECISION_SUPPORT = "SIGNING_DECISION_SUPPORT"

    # Case 7a: Yêu cầu soạn điều khoản
    CLAUSE_DRAFTING = "CLAUSE_DRAFTING"

    # Case 7b: Yêu cầu sửa điều khoản
    CLAUSE_REVISION = "CLAUSE_REVISION"

    # Case 10: Hỏi theo mốc thời gian/version luật
    TEMPORAL_LEGAL_ANALYSIS = "TEMPORAL_LEGAL_ANALYSIS"

    # Case 12: Phân tích thương mại liên quan hợp đồng
    COMMERCIAL_CONTRACT_ANALYSIS = "COMMERCIAL_CONTRACT_ANALYSIS"

    # Case 9: Ngoài knowledge base
    OUT_OF_KNOWLEDGE_BASE = "OUT_OF_KNOWLEDGE_BASE"

    # Case 8: Thiếu thông tin quan trọng
    NEED_MORE_INFO = "NEED_MORE_INFO"

    # Case 11: Yêu cầu không an toàn/lách luật
    UNSAFE_LEGAL_REQUEST = "UNSAFE_LEGAL_REQUEST"


class ContractType(str, Enum):
    """Types of contracts the system can analyze."""

    RENTAL = "RENTAL"                   # Hợp đồng thuê nhà/mặt bằng
    LABOR = "LABOR"                     # Hợp đồng lao động
    SERVICE = "SERVICE"                 # Hợp đồng dịch vụ
    SALE = "SALE"                       # Hợp đồng mua bán
    NDA = "NDA"                         # Thỏa thuận bảo mật
    PARTNERSHIP = "PARTNERSHIP"         # Hợp đồng hợp tác
    LOAN = "LOAN"                       # Hợp đồng vay
    AUTHORIZATION = "AUTHORIZATION"     # Hợp đồng ủy quyền
    CONSTRUCTION = "CONSTRUCTION"       # Hợp đồng xây dựng
    INSURANCE = "INSURANCE"             # Hợp đồng bảo hiểm
    TRANSFER = "TRANSFER"               # Hợp đồng chuyển nhượng
    DONATION = "DONATION"               # Hợp đồng tặng cho
    UNKNOWN = "UNKNOWN"


class ResponseMode(str, Enum):
    """How the AI should structure its response."""

    DIRECT_ANSWER = "DIRECT_ANSWER"
    GENERAL_GUIDANCE = "GENERAL_GUIDANCE"
    DOCUMENT_BASED_ANALYSIS = "DOCUMENT_BASED_ANALYSIS"
    CLAUSE_LEVEL_ANALYSIS = "CLAUSE_LEVEL_ANALYSIS"
    CHECKLIST_ANALYSIS = "CHECKLIST_ANALYSIS"
    ASK_CLARIFYING_QUESTION = "ASK_CLARIFYING_QUESTION"
    OUT_OF_SCOPE_RESPONSE = "OUT_OF_SCOPE_RESPONSE"
    SUGGEST_LAWYER = "SUGGEST_LAWYER"
    REQUIRE_LAWYER = "REQUIRE_LAWYER"


class RiskLevel(str, Enum):
    """Risk level classification."""

    NONE = "NONE"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"
    UNKNOWN = "UNKNOWN"
