"""Legal Analysis API: classify, analyze and compare contracts.

Endpoints:
- POST /classify-contract: Phân loại loại hợp đồng từ văn bản
- POST /analyze-contract: Phân tích rủi ro và điều khoản hợp đồng  
- POST /compare-contracts: So sánh 2 phiên bản hợp đồng
"""
from typing import Dict, Any

from fastapi import APIRouter, Depends, status

from app.api.deps import get_trace_id
from app.models.common import success_payload
from app.models.legal_models import (
    AnalyzeContractRequest,
    ClassifyContractRequest,
    CompareContractsRequest,
)
from app.services.legal_service import LegalService

router = APIRouter(
    prefix="/legal",
    tags=["legal"],
    responses={
        500: {"description": "Internal Server Error"},
        503: {"description": "Service Unavailable - Neo4j or LLM not available"},
    },
)


def get_legal_service() -> LegalService:
    return LegalService()


@router.post(
    "/classify-contract",
    status_code=status.HTTP_200_OK,
    summary="Phân loại loại hợp đồng",
    description="""
    **Chức năng:** Phân loại văn bản hợp đồng vào một trong các loại định trước.
    
    **Các loại hợp đồng hỗ trợ:**
    - `HOUSE_RENTAL`: Hợp đồng thuê nhà
    - `HOUSE_PURCHASE`: Hợp đồng mua bán nhà
    - `LAND_TRANSFER`: Hợp đồng chuyển nhượng đất
    - `LAND_DEPOSIT`: Hợp đồng đặt cọc đất/nhà
    - `SERVICE_CONTRACT`: Hợp đồng dịch vụ
    - `COMMERCIAL_CONTRACT`: Hợp đồng thương mại
    - `OTHER`: Loại khác không thuộc các loại trên
    
    **Phương pháp:**
    - Ưu tiên sử dụng LLM (Gemini) nếu đã cấu hình
    - Fallback về keyword matching nếu LLM không khả dụng
    
    **Use cases:**
    - Tự động phân loại hợp đồng khi upload
    - Gợi ý template phù hợp
    - Lọc và tìm kiếm hợp đồng theo loại
    """,
    response_description="Kết quả phân loại với confidence score, lý do và các thông tin phát hiện được",
    responses={
        200: {
            "description": "Phân loại thành công",
            "content": {
                "application/json": {
                    "example": {
                        "success": True,
                        "data": {
                            "contractType": "HOUSE_RENTAL",
                            "confidence": 0.85,
                            "reason": "Phát hiện 5 tín hiệu từ khóa đặc trưng cho loại HOUSE_RENTAL.",
                            "detectedParties": ["bên cho thuê", "bên thuê"],
                            "detectedImportantTerms": ["PAYMENT", "DEPOSIT", "OBJECT", "PARTY_INFO"],
                        },
                        "message": "Contract classified successfully",
                        "traceId": "550e8400-e29b-41d4-a716-446655440000",
                    }
                }
            },
        },
        422: {
            "description": "Validation Error - text field is required or invalid",
        },
    },
)
def classify_contract(
    req: ClassifyContractRequest,
    trace_id: str = Depends(get_trace_id),
    svc: LegalService = Depends(get_legal_service),
) -> Dict[str, Any]:
    """
    Phân loại loại hợp đồng từ văn bản.
    
    Args:
        req: Request body chứa văn bản hợp đồng cần phân loại
        trace_id: ID để trace request qua các services
        svc: Legal service instance
        
    Returns:
        Response envelope với dữ liệu phân loại:
        - contractType: Loại hợp đồng được phân loại
        - confidence: Độ tin cậy (0.0 - 1.0)
        - reason: Lý do và cơ sở phân loại
        - detectedParties: Các bên tham gia hợp đồng được phát hiện
        - detectedImportantTerms: Các nhóm điều khoản quan trọng được phát hiện
    """
    data = svc.classify(req.text)
    return success_payload(data, "Contract classified successfully", trace_id)


@router.post(
    "/analyze-contract",
    status_code=status.HTTP_200_OK,
    summary="Phân tích rủi ro hợp đồng",
    description="""
    **Chức năng:** Phân tích chi tiết hợp đồng để xác định rủi ro pháp lý, điều khoản thiếu và đưa ra khuyến nghị.
    
    **Phân tích bao gồm:**
    - 🔍 Xác định các rủi ro pháp lý tiềm ẩn
    - 📋 Kiểm tra điều khoản còn thiếu
    - ⚖️ Đánh giá mức độ rủi ro tổng thể (LOW/MEDIUM/HIGH/CRITICAL)
    - 💡 Đưa ra khuyến nghị cải thiện cụ thể
    - 📚 Trích dẫn các điều luật liên quan (nếu có)
    
    **Phương pháp:**
    - Ưu tiên: LLM + Knowledge Graph (Neo4j) → phân tích chính xác, chi tiết
    - Fallback: Graph + Heuristic rules → phân tích cơ bản nếu LLM không khả dụng
    - Luôn trả về kết quả có cấu trúc, không bao giờ crash
    
    **Input options:**
    - `contractType`: Nếu để UNKNOWN, API sẽ tự động phân loại trước
    - `protectedParty`: Bên cần bảo vệ (ví dụ: "bên thuê", "bên mua")
    - `question`: Câu hỏi cụ thể về hợp đồng (tùy chọn)
    - `includeGraphContext`: Có sử dụng knowledge graph không (mặc định: true)
    
    **Use cases:**
    - Review hợp đồng trước khi ký
    - Xác định điểm yếu của hợp đồng
    - Tư vấn sửa đổi hợp đồng
    - So sánh với best practices pháp lý
    """,
    response_description="Báo cáo phân tích rủi ro chi tiết với recommendations",
    responses={
        200: {
            "description": "Phân tích thành công",
            "content": {
                "application/json": {
                    "example": {
                        "success": True,
                        "data": {
                            "contractType": "HOUSE_RENTAL",
                            "summary": "Hợp đồng thuê nhà có rủi ro MEDIUM với 3 điều khoản quan trọng còn thiếu. Cần bổ sung điều khoản bàn giao, chấm dứt hợp đồng và tranh chấp.",
                            "overallRiskLevel": "MEDIUM",
                            "riskItems": [
                                {
                                    "riskType": "MISSING_CLAUSE_RISK",
                                    "severity": "HIGH",
                                    "title": "Thiếu điều khoản: Bàn giao",
                                    "explanation": "Hợp đồng loại HOUSE_RENTAL thường cần điều khoản Bàn giao nhưng chưa phát hiện trong văn bản.",
                                    "recommendation": "Bổ sung điều khoản quy định rõ thời điểm, thủ tục bàn giao và trách nhiệm của các bên.",
                                    "requiresExpertReview": True,
                                }
                            ],
                            "missingClauses": ["Bàn giao", "Chấm dứt hợp đồng", "Tranh chấp"],
                            "recommendations": [
                                "Bổ sung điều khoản bàn giao chi tiết",
                                "Quy định rõ điều kiện chấm dứt hợp đồng",
                                "Thêm phương thức giải quyết tranh chấp",
                            ],
                            "llmUsed": True,
                            "fallback": False,
                        },
                        "message": "Contract analyzed successfully",
                        "traceId": "550e8400-e29b-41d4-a716-446655440000",
                    }
                }
            },
        },
        422: {"description": "Validation Error"},
        503: {"description": "Neo4j không khả dụng"},
    },
)
def analyze_contract(
    req: AnalyzeContractRequest,
    trace_id: str = Depends(get_trace_id),
    svc: LegalService = Depends(get_legal_service),
) -> Dict[str, Any]:
    """
    Phân tích rủi ro và điều khoản thiếu của hợp đồng.
    
    Args:
        req: Request body chứa thông tin hợp đồng cần phân tích
        trace_id: ID để trace request
        svc: Legal service instance
        
    Returns:
        Response envelope với báo cáo phân tích:
        - contractType: Loại hợp đồng
        - summary: Tóm tắt phân tích
        - overallRiskLevel: Mức độ rủi ro tổng thể
        - riskItems: Danh sách các rủi ro chi tiết
        - missingClauses: Các điều khoản còn thiếu
        - recommendations: Các khuyến nghị cải thiện
        - llmUsed: True nếu sử dụng LLM, False nếu dùng fallback
        
    Note:
        - Nếu contractType là UNKNOWN, API sẽ tự động phân loại trước khi phân tích
        - Kết quả fallback (không dùng LLM) vẫn đảm bảo chính xác nhưng ít chi tiết hơn
        - Field `graphContextUsed` chứa các legal articles được trích dẫn
    """
    data = svc.analyze(req)
    return success_payload(data, "Contract analyzed successfully", trace_id)


@router.post(
    "/compare-contracts",
    status_code=status.HTTP_200_OK,
    summary="So sánh 2 phiên bản hợp đồng",
    description="""
    **Chức năng:** So sánh chi tiết 2 văn bản hợp đồng để xác định sự khác biệt và phiên bản tốt hơn.
    
    **So sánh bao gồm:**
    - 📊 So sánh từng nhóm điều khoản giữa 2 văn bản
    - ⚖️ Xác định phiên bản nào có lợi hơn cho bên được bảo vệ
    - 📈 Những thay đổi làm TĂNG rủi ro
    - 📉 Những thay đổi làm GIẢM rủi ro
    - 💡 Khuyến nghị nên chọn phiên bản nào và tại sao
    
    **Phương pháp:**
    - Ưu tiên: LLM + Graph context → so sánh sâu về ngữ nghĩa và tác động pháp lý
    - Fallback: Clause detection + Graph → so sánh cơ bản theo điều khoản xuất hiện
    
    **Input:**
    - `documentAText`: Văn bản A (thường là bản gốc/hiện tại)
    - `documentBText`: Văn bản B (thường là bản sửa đổi/đề xuất)
    - `protectedParty`: Bên cần bảo vệ để đánh giá phiên bản nào có lợi hơn
    
    **Use cases:**
    - So sánh bản nháp nội bộ với bản đối tác gửi lại
    - Đánh giá các lần sửa đổi hợp đồng
    - Kiểm tra xem bản sửa đổi có tốt hơn bản gốc không
    - Review thay đổi trước khi ký phụ lục
    - Legal negotiation support
    
    **Output:**
    - `moreFavorableVersion`: A, B, EQUAL, hoặc INSUFFICIENT_DATA
    - `clauseComparisons`: So sánh chi tiết từng điều khoản
    - `riskIncreases`: Danh sách thay đổi làm tăng rủi ro
    - `riskReductions`: Danh sách thay đổi làm giảm rủi ro
    """,
    response_description="Báo cáo so sánh chi tiết giữa 2 hợp đồng",
    responses={
        200: {
            "description": "So sánh thành công",
            "content": {
                "application/json": {
                    "example": {
                        "success": True,
                        "data": {
                            "summary": "Văn bản B có lợi hơn cho bên thuê với 3 điểm khác biệt quan trọng về thanh toán và bảo hành.",
                            "moreFavorableVersion": "B",
                            "overallRiskLevel": "MEDIUM",
                            "clauseComparisons": [
                                {
                                    "topic": "Thanh toán",
                                    "documentAContent": "Thanh toán 100% trước khi nhận nhà",
                                    "documentBContent": "Thanh toán theo 3 đợt: đặt cọc 30%, nhận nhà 50%, hoàn công 20%",
                                    "differenceType": "MATERIAL_DIFFERENCE",
                                    "legalImpact": "Văn bản B giảm đáng kể rủi ro thanh toán cho bên thuê, tuân thủ thông lệ thị trường.",
                                    "riskLevel": "HIGH",
                                    "recommendation": "Nên chọn phương thức thanh toán theo đợt (văn bản B) để bảo vệ quyền lợi bên thuê.",
                                }
                            ],
                            "riskIncreases": ["Văn bản A yêu cầu thanh toán toàn bộ trước"],
                            "riskReductions": ["Văn bản B có điều khoản bảo hành rõ ràng hơn"],
                            "recommendations": [
                                "Chọn văn bản B làm cơ sở đàm phán",
                                "Giữ lại điều khoản thanh toán theo đợt của văn bản B",
                            ],
                            "llmUsed": True,
                            "fallback": False,
                        },
                        "message": "Contracts compared successfully",
                        "traceId": "550e8400-e29b-41d4-a716-446655440000",
                    }
                }
            },
        },
        422: {"description": "Validation Error - both texts are required"},
    },
)
def compare_contracts(
    req: CompareContractsRequest,
    trace_id: str = Depends(get_trace_id),
    svc: LegalService = Depends(get_legal_service),
) -> Dict[str, Any]:
    """
    So sánh 2 văn bản hợp đồng để xác định sự khác biệt và phiên bản tốt hơn.
    
    Args:
        req: Request body chứa 2 văn bản hợp đồng cần so sánh
        trace_id: ID để trace request
        svc: Legal service instance
        
    Returns:
        Response envelope với báo cáo so sánh:
        - summary: Tóm tắt kết quả so sánh
        - moreFavorableVersion: Phiên bản có lợi hơn (A/B/EQUAL/INSUFFICIENT_DATA)
        - overallRiskLevel: Mức độ rủi ro khi thay đổi từ A sang B
        - clauseComparisons: So sánh chi tiết từng nhóm điều khoản
        - riskIncreases: Các thay đổi làm tăng rủi ro
        - riskReductions: Các thay đổi làm giảm rủi ro
        - recommendations: Khuyến nghị chọn phiên bản nào và lý do
    """
    data = svc.compare(req)
    return success_payload(data, "Contracts compared successfully", trace_id)
