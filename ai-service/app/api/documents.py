"""Documents API: upload và manage user documents.

Endpoints:
- POST /upload: Upload PDF/DOCX document
- GET /list: List user's documents  
- POST /search: Semantic search trong user's documents
"""
from typing import Dict, Any

from fastapi import APIRouter, Depends, File, Form, UploadFile, status, Query

from app.api.deps import get_trace_id
from app.models.common import success_payload
from app.services.document_service import DocumentService

router = APIRouter(
    prefix="/documents",
    tags=["documents"],
    responses={
        500: {"description": "Internal Server Error"},
        503: {"description": "Neo4j not available"},
    },
)


def get_document_service() -> DocumentService:
    return DocumentService()


@router.post(
    "/upload",
    status_code=status.HTTP_200_OK,
    summary="Upload PDF/DOCX document",
    description="""
    **Chức năng:** Upload tài liệu (contract, legal document) vào hệ thống.
    
    **Xử lý:**
    1. Extract text từ PDF/DOCX
    2. Generate embeddings
    3. Store trong Neo4j Graph
    4. Link với System Knowledge (legal corpus)
    5. Enable semantic search
    
    **Supported formats:**
    - PDF (.pdf)
    - Microsoft Word (.docx)
    
    **Use cases:**
    - Upload contracts để analyze
    - Build user-specific knowledge base
    - Enable RAG retrieval từ uploaded documents
    
    **Note:**
    - Max file size: 10MB (có thể config)
    - Text extraction có thể mất vài giây
    - Embeddings sẽ được generate bất đồng bộ
    """,
    response_description="Document metadata và ID",
    responses={
        200: {
            "description": "Upload thành công",
            "content": {
                "application/json": {
                    "example": {
                        "success": True,
                        "data": {
                            "documentId": "DOC_user123_a1b2c3d4",
                            "filename": "contract.pdf",
                            "textLength": 5430,
                            "contractType": "HOUSE_RENTAL",
                            "uploadedAt": "2024-06-04T12:00:00Z",
                        },
                        "message": "Document uploaded successfully",
                        "traceId": "550e8400-e29b-41d4-a716-446655440000",
                    }
                }
            },
        },
        400: {"description": "Invalid file type or corrupted file"},
        413: {"description": "File too large"},
    },
)
async def upload_document(
    file: UploadFile = File(..., description="PDF or DOCX file to upload"),
    user_id: str = Form(..., description="ID của user upload document"),
    contract_type: str = Form(
        default="UNKNOWN",
        description="Loại hợp đồng (HOUSE_RENTAL, LAND_TRANSFER, etc.)",
    ),
    trace_id: str = Depends(get_trace_id),
    svc: DocumentService = Depends(get_document_service),
) -> Dict[str, Any]:
    """
    Upload và xử lý document.
    
    Args:
        file: File upload (PDF hoặc DOCX)
        user_id: ID của user
        contract_type: Loại hợp đồng
        trace_id: Trace ID
        svc: Document service instance
        
    Returns:
        Response envelope với document metadata
        
    Raises:
        400: File type không supported hoặc corrupted
        413: File quá lớn
    """
    # Read file content
    content = await file.read()
    
    # Validate file size (10MB)
    max_size = 10 * 1024 * 1024  # 10MB
    if len(content) > max_size:
        from app.core.errors import ValidationError
        raise ValidationError(
            f"File too large. Max size: {max_size / (1024*1024)}MB",
            details={"fileSize": len(content), "maxSize": max_size},
        )
    
    # Validate file type
    allowed_types = ['.pdf', '.docx']
    if not any(file.filename.lower().endswith(ext) for ext in allowed_types):
        from app.core.errors import ValidationError
        raise ValidationError(
            f"Unsupported file type. Allowed: {allowed_types}",
            details={"filename": file.filename},
        )
    
    # Process upload
    data = svc.process_upload(
        file_content=content,
        filename=file.filename,
        user_id=user_id,
        contract_type=contract_type if contract_type != "UNKNOWN" else None,
    )
    
    return success_payload(data, "Document uploaded successfully", trace_id)


@router.get(
    "/list",
    status_code=status.HTTP_200_OK,
    summary="List user's documents",
    description="""
    **Chức năng:** Lấy danh sách tất cả documents đã upload của user.
    
    **Returns:**
    - Document metadata (filename, type, upload date)
    - Không include full text (tiết kiệm bandwidth)
    
    **Use cases:**
    - Hiển thị document library
    - History của uploaded contracts
    """,
)
def list_documents(
    user_id: str = Query(..., description="ID của user"),
    limit: int = Query(default=50, ge=1, le=100, description="Max số lượng documents"),
    trace_id: str = Depends(get_trace_id),
    svc: DocumentService = Depends(get_document_service),
) -> Dict[str, Any]:
    """List all documents của user."""
    documents = svc.get_user_documents(user_id, limit)
    
    # Format response
    items = []
    for doc in documents:
        props = doc.get("properties", {})
        items.append({
            "documentId": props.get("id"),
            "filename": props.get("filename"),
            "contractType": props.get("contractType"),
            "uploadedAt": props.get("uploadedAt"),
            "textLength": len(props.get("text", "")),
        })
    
    return success_payload(
        {"items": items, "total": len(items)},
        "Documents retrieved successfully",
        trace_id,
    )


@router.post(
    "/search",
    status_code=status.HTTP_200_OK,
    summary="Semantic search trong user's documents",
    description="""
    **Chức năng:** Tìm kiếm documents của user theo semantic meaning.
    
    **Phương pháp:**
    - Query được convert thành embedding
    - So sánh với embeddings của documents
    - Return top-k most similar documents
    
    **Use cases:**
    - "Tìm hợp đồng có điều khoản đặt cọc"
    - "Contracts liên quan đến bất động sản"
    - Semantic search thay vì keyword search
    """,
)
def search_documents(
    user_id: str = Form(...),
    query: str = Form(..., min_length=2, max_length=200),
    top_k: int = Form(default=5, ge=1, le=20),
    trace_id: str = Depends(get_trace_id),
    svc: DocumentService = Depends(get_document_service),
) -> Dict[str, Any]:
    """Semantic search trong documents của user."""
    results = svc.search_user_documents(user_id, query, top_k)
    
    return success_payload(
        {"query": query, "results": results},
        "Search completed successfully",
        trace_id,
    )
