"""API routes for AI service."""
import logging
from fastapi import APIRouter, HTTPException, Query
from app.models.query import RAGQueryRequest, RAGQueryResponse
from app.services.rag_service import rag_service

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "ai-service"}


@router.post("/internal/rag/query", response_model=RAGQueryResponse)
async def rag_query(request: RAGQueryRequest) -> RAGQueryResponse:
    """
    Direct RAG query API.

    Flow:
    1. Search user document chunks for the current workspace/document first.
    2. Build a legal search query from user chunks.
    3. Search knowledge base chunks with the expanded query.
    4. Generate the final answer with Gemini.
    """
    try:
        logger.info(f"Received RAG query request: {request.request_id}")
        logger.info(f"User: {request.user_id}, Workspace: {request.workspace_id}")
        logger.info(f"Question: {request.question}")

        response = rag_service.process_query(request)

        logger.info(f"Query processed successfully: {request.request_id}")
        return response

    except Exception as e:
        logger.error(f"Error processing RAG query: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/test/query")
async def test_query(
    user_id: str = Query("user_demo", description="User ID to test with"),
    question: str = Query("Có vấn đề gì với văn bản này không?", description="Question to ask")
):
    """
    Test endpoint for quick query testing without full request body.
    """
    request = RAGQueryRequest(
        request_id="test_" + str(hash(question))[-8:],
        user_id=user_id,
        workspace_id="ws_test",
        question=question,
        top_k_checklist=10,
        top_k_user_chunks_per_checklist=3,
        top_k_knowledge_chunks=5
    )

    return await rag_query(request)
