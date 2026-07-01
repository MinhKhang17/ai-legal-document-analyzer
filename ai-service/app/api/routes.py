from fastapi import APIRouter, Query
from app.schemas import RagQueryRequest
from app.services.rag_query_service import RagQueryService
import time
import logging

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "ai-service"}


@router.get("/test/query")
def test_query(
    user_id: str = Query("user_demo", description="User ID to test with"),
    question: str = Query("Có vấn đề gì với văn bản này không?", description="Question to ask")
):
    """
    Test endpoint for quick query testing without full request body.
    Maps GET params to RagQueryRequest and calls RagQueryService.
    """
    start_time = time.perf_counter()
    request_id = "test_" + str(hash(question))[-8:]
    
    payload = RagQueryRequest(
        request_id=request_id,
        user_id=user_id,
        workspace_id="ws_test",
        question=question,
        top_k_user_chunks=3,
        top_k_knowledge_chunks=3
    )
    
    try:
        service = RagQueryService()
        response = service.query(payload)
        elapsed = (time.perf_counter() - start_time) * 1000
        
        # Format response matching expected fields in test scripts
        return {
            "success": True,
            "answer": response.answer,
            "total_checklist_items": 20,
            "total_user_chunks": response.retrievedUserChunks,
            "total_knowledge_chunks": response.retrievedKnowledgeChunks,
            "processing_time_ms": elapsed,
            "error_message": None
        }
    except Exception as e:
        logger.exception("Error during test query processing")
        elapsed = (time.perf_counter() - start_time) * 1000
        return {
            "success": False,
            "answer": None,
            "total_checklist_items": 0,
            "total_user_chunks": 0,
            "total_knowledge_chunks": 0,
            "processing_time_ms": elapsed,
            "error_message": str(e)
        }

