"""RAG API: context retrieval and contract-context endpoints.

Endpoints:
- POST /retrieve: Truy xuất context từ knowledge graph
- POST /contract-context: Build context cho một loại hợp đồng cụ thể
"""
from typing import Dict, Any

from fastapi import APIRouter, Depends, status

from app.api.deps import get_trace_id
from app.models.common import success_payload
from app.models.rag_models import ContractContextRequest, RagRetrieveRequest
from app.services.rag_service import RagService

router = APIRouter(
    prefix="/rag",
    tags=["rag"],
    responses={
        500: {"description": "Internal Server Error"},
        503: {"description": "Neo4j not available"},
    },
)


def get_rag_service() -> RagService:
    return RagService()


@router.post(
    "/retrieve",
    status_code=status.HTTP_200_OK,
    summary="Truy xuất context pháp lý từ knowledge graph",
    description="""
    **Chức năng:** Semantic search trên Neo4j knowledge graph để truy xuất context pháp lý liên quan.
    
    **Truy xuất bao gồm:**
    - 📚 Các điều luật liên quan (LegalArticle)
    - ⚠️ Các loại rủi ro (RiskType)
    - 📋 Các loại điều khoản (ClauseType)
    - 💡 Khuyến nghị xử lý rủi ro (Recommendation)
    
    **Phương pháp ranking:**
    1. Graph traversal: Contract type → Risks, Clauses → Risks
    2. Keyword search across knowledge nodes
    3. Semantic similarity scoring (embeddings)
    4. Sort by relevance score
    
    **Filters:**
    - `contractType`: Lọc theo loại hợp đồng
    - `riskTypes`: Lọc theo loại rủi ro cụ thể
    - `clauseTypes`: Lọc theo loại điều khoản cụ thể
    - `topK`: Số lượng items trả về (1-50)
    
    **Use cases:**
    - Build prompt cho LLM với grounded context
    - Semantic search pháp luật
    - Tìm điều luật liên quan đến rủi ro cụ thể
    """,
    response_description="Danh sách context items với relevance score",
    responses={
        200: {
            "description": "Truy xuất thành công",
            "content": {
                "application/json": {
                    "example": {
                        "success": True,
                        "data": {
                            "query": "rủi ro đặt cọc",
                            "items": [
                                {
                                    "id": "DEPOSIT_RISK",
                                    "type": "RiskType",
                                    "title": "DEPOSIT_RISK",
                                    "content": "Rủi ro liên quan đến tiền đặt cọc...",
                                    "score": 0.92,
                                    "metadata": {
                                        "recommendations": ["Quy định rõ điều kiện trả lại tiền cọc"]
                                    },
                                }
                            ],
                        },
                        "message": "Context retrieved successfully",
                        "traceId": "550e8400-e29b-41d4-a716-446655440000",
                    }
                }
            },
        },
        422: {"description": "Validation Error"},
    },
)
def retrieve(
    req: RagRetrieveRequest,
    trace_id: str = Depends(get_trace_id),
    svc: RagService = Depends(get_rag_service),
) -> Dict[str, Any]:
    """
    Truy xuất legal context từ knowledge graph theo query.
    
    Args:
        req: Request body chứa query và filters
        trace_id: ID để trace request
        svc: RAG service instance
        
    Returns:
        Response envelope với danh sách context items được rank theo relevance
    """
    data = svc.retrieve(
        query=req.query,
        contract_type=req.contractType,
        top_k=req.topK,
        risk_filters=req.filters.riskTypes,
        clause_filters=req.filters.clauseTypes,
    )
    return success_payload(data, "Context retrieved successfully", trace_id)


@router.post(
    "/contract-context",
    status_code=status.HTTP_200_OK,
    summary="Build context cho loại hợp đồng cụ thể",
    description="""
    **Chức năng:** Build structured context về một loại hợp đồng để hỗ trợ analysis/review.
    
    **Context bao gồm:**
    - ✅ Required clauses check: Các điều khoản bắt buộc và trạng thái có/thiếu
    - ⚠️ Related risks: Các rủi ro thường gặp với loại hợp đồng này
    - 💡 Recommendations: Khuyến nghị xử lý từng rủi ro
    
    **Input:**
    - `contractType`: Loại hợp đồng (HOUSE_RENTAL, LAND_TRANSFER, etc.)
    - `detectedClauseTypes`: Các điều khoản đã phát hiện trong hợp đồng
    - `question`: Câu hỏi cụ thể (tùy chọn)
    
    **Use cases:**
    - Pre-analysis: Hiểu yêu cầu của loại hợp đồng trước khi review
    - Checklist: Kiểm tra đủ điều khoản bắt buộc chưa
    - Risk awareness: Biết trước các rủi ro thường gặp
    """,
    response_description="Structured context về loại hợp đồng với required checks và risks",
    responses={
        200: {
            "description": "Build context thành công",
            "content": {
                "application/json": {
                    "example": {
                        "success": True,
                        "data": {
                            "contractType": "HOUSE_RENTAL",
                            "requiredChecks": [
                                {
                                    "clauseType": "PAYMENT",
                                    "title": "Thanh toán",
                                    "present": True,
                                    "description": "Quy định về giá thuê và phương thức thanh toán",
                                },
                                {
                                    "clauseType": "HANDOVER",
                                    "title": "Bàn giao",
                                    "present": False,
                                    "description": "Thời điểm và thủ tục bàn giao nhà",
                                },
                            ],
                            "relatedRisks": [
                                {
                                    "id": "DEPOSIT_RISK",
                                    "type": "RiskType",
                                    "title": "DEPOSIT_RISK",
                                    "content": "Rủi ro về tiền đặt cọc...",
                                }
                            ],
                            "recommendations": [
                                {
                                    "id": "REC::0",
                                    "type": "Recommendation",
                                    "title": "Khuyến nghị",
                                    "content": "Quy định rõ điều kiện trả lại tiền cọc",
                                }
                            ],
                        },
                        "message": "Contract context built successfully",
                        "traceId": "550e8400-e29b-41d4-a716-446655440000",
                    }
                }
            },
        },
    },
)
def contract_context(
    req: ContractContextRequest,
    trace_id: str = Depends(get_trace_id),
    svc: RagService = Depends(get_rag_service),
) -> Dict[str, Any]:
    """
    Build structured context về một loại hợp đồng cụ thể.
    
    Args:
        req: Request body chứa contract type và detected clauses
        trace_id: ID để trace request
        svc: RAG service instance
        
    Returns:
        Response envelope với:
        - requiredChecks: Danh sách điều khoản bắt buộc và trạng thái
        - relatedRisks: Các rủi ro thường gặp
        - recommendations: Khuyến nghị xử lý rủi ro
    """
    data = svc.contract_context(
        contract_type=req.contractType,
        question=req.question,
        detected_clause_types=req.detectedClauseTypes,
    )
    return success_payload(data, "Contract context built successfully", trace_id)
