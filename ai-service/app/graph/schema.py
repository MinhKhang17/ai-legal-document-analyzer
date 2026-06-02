"""Legal knowledge-graph taxonomy and baseline seed definitions.

This module is the single source of truth for:
  * Allowed node labels and relationship types.
  * The baseline catalog (ContractType / RiskType / ClauseType) that the
    /graph/seed/legal-baseline endpoint imports idempotently.
  * The domain mappings used by the RAG and legal-analysis fallback logic
    (which clauses each contract type requires, which risks relate to which
    clauses, and recommended mitigations).

All textual content is in Vietnamese because LexiGuard AI targets Vietnamese
legal contracts.
"""
from typing import Dict, List

# --- Allowed labels / relationship types -------------------------------------

NODE_LABELS: List[str] = [
    "LegalDocument",
    "LegalArticle",
    "LegalConcept",
    "ContractType",
    "ClauseType",
    "RiskType",
    "Recommendation",
    "LegalRequirement",
]

RELATIONSHIP_TYPES: List[str] = [
    "CONTAINS",
    "REFERENCES",
    "RELATED_TO",
    "APPLIES_TO",
    "HAS_RISK",
    "MITIGATED_BY",
    "REQUIRED_FOR",
    "CONFLICTS_WITH",
]


# --- Baseline catalog ---------------------------------------------------------

CONTRACT_TYPES: Dict[str, str] = {
    "HOUSE_PURCHASE": "Hợp đồng mua bán nhà ở",
    "HOUSE_RENTAL": "Hợp đồng thuê nhà ở",
    "LAND_TRANSFER": "Hợp đồng chuyển nhượng quyền sử dụng đất",
    "LAND_DEPOSIT": "Hợp đồng đặt cọc chuyển nhượng đất",
    "SERVICE_CONTRACT": "Hợp đồng dịch vụ",
    "COMMERCIAL_CONTRACT": "Hợp đồng thương mại",
}

RISK_TYPES: Dict[str, str] = {
    "PARTY_INFORMATION_RISK": "Rủi ro về thông tin các bên không đầy đủ hoặc không chính xác",
    "OBJECT_DESCRIPTION_RISK": "Rủi ro mô tả đối tượng hợp đồng không rõ ràng",
    "DEPOSIT_RISK": "Rủi ro liên quan đến điều khoản đặt cọc",
    "PAYMENT_RISK": "Rủi ro về phương thức và tiến độ thanh toán",
    "HANDOVER_RISK": "Rủi ro về thời điểm và điều kiện bàn giao",
    "LAND_LEGAL_STATUS_RISK": "Rủi ro về tình trạng pháp lý của thửa đất",
    "OWNERSHIP_RIGHT_RISK": "Rủi ro về quyền sở hữu / quyền sử dụng",
    "PLANNING_OR_MORTGAGE_RISK": "Rủi ro quy hoạch hoặc tài sản đang thế chấp",
    "TAX_FEE_RISK": "Rủi ro về nghĩa vụ thuế, phí, lệ phí",
    "TERMINATION_RISK": "Rủi ro về điều kiện chấm dứt hợp đồng",
    "PENALTY_RISK": "Rủi ro về điều khoản phạt vi phạm",
    "COMPENSATION_RISK": "Rủi ro về bồi thường thiệt hại",
    "DISPUTE_RESOLUTION_RISK": "Rủi ro về cơ chế giải quyết tranh chấp",
    "AMBIGUOUS_LANGUAGE_RISK": "Rủi ro do ngôn ngữ hợp đồng mơ hồ, đa nghĩa",
    "MISSING_CLAUSE_RISK": "Rủi ro thiếu điều khoản quan trọng",
    "UNBALANCED_OBLIGATION_RISK": "Rủi ro nghĩa vụ giữa các bên mất cân bằng",
    "SERVICE_SCOPE_RISK": "Rủi ro phạm vi dịch vụ không rõ ràng",
    "ACCEPTANCE_RISK": "Rủi ro về nghiệm thu / xác nhận hoàn thành",
}

CLAUSE_TYPES: Dict[str, str] = {
    "PARTY_INFO": "Điều khoản thông tin các bên",
    "OBJECT": "Điều khoản về đối tượng hợp đồng",
    "PAYMENT": "Điều khoản thanh toán",
    "DEPOSIT": "Điều khoản đặt cọc",
    "HANDOVER": "Điều khoản bàn giao",
    "RIGHTS_OBLIGATIONS": "Điều khoản quyền và nghĩa vụ",
    "PENALTY": "Điều khoản phạt vi phạm",
    "COMPENSATION": "Điều khoản bồi thường thiệt hại",
    "TERMINATION": "Điều khoản chấm dứt hợp đồng",
    "TAX_FEE": "Điều khoản thuế và phí",
    "DISPUTE": "Điều khoản giải quyết tranh chấp",
    "FORCE_MAJEURE": "Điều khoản bất khả kháng",
    "CONFIDENTIALITY": "Điều khoản bảo mật",
    "ACCEPTANCE": "Điều khoản nghiệm thu",
    "WARRANTY": "Điều khoản bảo hành",
}


# --- Domain mappings (used by RAG + analysis fallback) ------------------------

# Clauses that each contract type is expected to contain.
CONTRACT_REQUIRED_CLAUSES: Dict[str, List[str]] = {
    "HOUSE_PURCHASE": [
        "PARTY_INFO", "OBJECT", "PAYMENT", "DEPOSIT", "HANDOVER",
        "RIGHTS_OBLIGATIONS", "TAX_FEE", "PENALTY", "DISPUTE",
    ],
    "HOUSE_RENTAL": [
        "PARTY_INFO", "OBJECT", "PAYMENT", "DEPOSIT", "HANDOVER",
        "RIGHTS_OBLIGATIONS", "TERMINATION", "PENALTY", "DISPUTE",
    ],
    "LAND_TRANSFER": [
        "PARTY_INFO", "OBJECT", "PAYMENT", "DEPOSIT", "HANDOVER",
        "RIGHTS_OBLIGATIONS", "TAX_FEE", "DISPUTE", "PENALTY",
    ],
    "LAND_DEPOSIT": [
        "PARTY_INFO", "OBJECT", "DEPOSIT", "PAYMENT", "PENALTY",
        "TERMINATION", "DISPUTE",
    ],
    "SERVICE_CONTRACT": [
        "PARTY_INFO", "OBJECT", "PAYMENT", "RIGHTS_OBLIGATIONS",
        "ACCEPTANCE", "TERMINATION", "PENALTY", "CONFIDENTIALITY", "DISPUTE",
    ],
    "COMMERCIAL_CONTRACT": [
        "PARTY_INFO", "OBJECT", "PAYMENT", "RIGHTS_OBLIGATIONS",
        "PENALTY", "COMPENSATION", "FORCE_MAJEURE", "DISPUTE", "CONFIDENTIALITY",
    ],
}

# Risk most directly associated with each clause type.
CLAUSE_RISK_MAP: Dict[str, str] = {
    "PARTY_INFO": "PARTY_INFORMATION_RISK",
    "OBJECT": "OBJECT_DESCRIPTION_RISK",
    "PAYMENT": "PAYMENT_RISK",
    "DEPOSIT": "DEPOSIT_RISK",
    "HANDOVER": "HANDOVER_RISK",
    "RIGHTS_OBLIGATIONS": "UNBALANCED_OBLIGATION_RISK",
    "PENALTY": "PENALTY_RISK",
    "COMPENSATION": "COMPENSATION_RISK",
    "TERMINATION": "TERMINATION_RISK",
    "TAX_FEE": "TAX_FEE_RISK",
    "DISPUTE": "DISPUTE_RESOLUTION_RISK",
    "ACCEPTANCE": "ACCEPTANCE_RISK",
}

# Contract-type level risks (beyond per-clause risks).
CONTRACT_RISK_MAP: Dict[str, List[str]] = {
    "LAND_TRANSFER": [
        "LAND_LEGAL_STATUS_RISK", "OWNERSHIP_RIGHT_RISK",
        "PLANNING_OR_MORTGAGE_RISK", "TAX_FEE_RISK",
    ],
    "LAND_DEPOSIT": ["DEPOSIT_RISK", "LAND_LEGAL_STATUS_RISK", "PENALTY_RISK"],
    "HOUSE_PURCHASE": ["OWNERSHIP_RIGHT_RISK", "HANDOVER_RISK", "TAX_FEE_RISK"],
    "HOUSE_RENTAL": ["DEPOSIT_RISK", "TERMINATION_RISK", "HANDOVER_RISK"],
    "SERVICE_CONTRACT": ["SERVICE_SCOPE_RISK", "ACCEPTANCE_RISK", "PAYMENT_RISK"],
    "COMMERCIAL_CONTRACT": ["COMPENSATION_RISK", "PENALTY_RISK", "DISPUTE_RESOLUTION_RISK"],
}

# Default mitigation recommendation per risk type.
RISK_RECOMMENDATIONS: Dict[str, str] = {
    "PARTY_INFORMATION_RISK": "Ghi rõ họ tên, CCCD/CMND, địa chỉ và tư cách pháp lý của các bên.",
    "OBJECT_DESCRIPTION_RISK": "Mô tả chi tiết đối tượng hợp đồng kèm thông tin pháp lý xác thực.",
    "DEPOSIT_RISK": "Quy định rõ số tiền cọc, thời hạn, điều kiện hoàn cọc và phạt cọc.",
    "PAYMENT_RISK": "Nêu rõ số tiền, tiến độ, phương thức và chứng từ thanh toán.",
    "HANDOVER_RISK": "Xác định thời điểm, địa điểm, hiện trạng và biên bản bàn giao.",
    "LAND_LEGAL_STATUS_RISK": "Kiểm tra sổ đỏ/sổ hồng và tình trạng pháp lý thửa đất trước khi ký.",
    "OWNERSHIP_RIGHT_RISK": "Xác minh quyền sở hữu hợp pháp và đồng sở hữu (nếu có).",
    "PLANNING_OR_MORTGAGE_RISK": "Kiểm tra quy hoạch và tình trạng thế chấp tại cơ quan có thẩm quyền.",
    "TAX_FEE_RISK": "Thỏa thuận rõ bên chịu thuế thu nhập, lệ phí trước bạ và phí công chứng.",
    "TERMINATION_RISK": "Liệt kê đầy đủ căn cứ chấm dứt và hậu quả pháp lý kèm theo.",
    "PENALTY_RISK": "Quy định mức phạt cụ thể, tuân thủ giới hạn pháp luật cho phép.",
    "COMPENSATION_RISK": "Xác định nguyên tắc và phạm vi bồi thường thiệt hại.",
    "DISPUTE_RESOLUTION_RISK": "Chọn rõ cơ quan tài phán (Tòa án/Trọng tài) và luật áp dụng.",
    "AMBIGUOUS_LANGUAGE_RISK": "Rà soát và làm rõ các thuật ngữ, tránh diễn đạt đa nghĩa.",
    "MISSING_CLAUSE_RISK": "Bổ sung các điều khoản còn thiếu theo loại hợp đồng.",
    "UNBALANCED_OBLIGATION_RISK": "Cân bằng quyền và nghĩa vụ giữa các bên.",
    "SERVICE_SCOPE_RISK": "Mô tả chi tiết phạm vi, tiêu chuẩn và sản phẩm dịch vụ.",
    "ACCEPTANCE_RISK": "Quy định tiêu chí, quy trình và biên bản nghiệm thu.",
}
