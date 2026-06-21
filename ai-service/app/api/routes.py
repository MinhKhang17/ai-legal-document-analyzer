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
    RAG Query API v2 - Global Review Query.
    
    This endpoint processes user questions using a checklist-based retrieval approach.
    For global questions like "Có vấn đề gì với văn bản này không?", it:
    
    1. Retrieves universal review checklists from Neo4j
    2. For each checklist item, searches relevant user document chunks
    3. Retrieves knowledge base chunks for additional context
    4. Builds structured context and generates answer
    
    **Important**: userId and workspaceId must be validated by Spring Boot backend
    before calling this endpoint.
    """
    try:
        logger.info(f"Received RAG query request: {request.request_id}")
        logger.info(f"User: {request.user_id}, Workspace: {request.workspace_id}")
        logger.info(f"Question: {request.question}")
        
        # Determine query type
        is_global = rag_service.is_global_review_query(request.question)
        
        if is_global:
            logger.info("Detected global review query - using checklist-based retrieval")
            response = rag_service.process_global_review_query(request)
        else:
            # For specific questions, could use different strategy
            logger.info("Detected specific query - using direct retrieval")
            # TODO: Implement specific query handling
            response = RAGQueryResponse(
                request_id=request.request_id,
                success=False,
                error_message="Specific query handling not yet implemented"
            )
        
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
    
    Example:
    - /test/query?user_id=user_demo&question=Có vấn đề gì với văn bản này không?
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
