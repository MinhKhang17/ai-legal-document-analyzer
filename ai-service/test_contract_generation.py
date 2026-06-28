import logging
import sys
from app.schemas import RagQueryRequest
from app.services.rag_query_service import RagQueryService

logging.basicConfig(level=logging.INFO)

def test_generation():
    service = RagQueryService()
    request = RagQueryRequest(
        request_id="test_req_123",
        user_id="1",
        workspace_id="test_ws",
        question="Provide me with a residential lease agreement.",
        top_k_knowledge_chunks=3
    )
    
    print("Sending generation request to service...")
    try:
        response = service.query(request)
        print("\nSUCCESS!")
        print("Answer length:", len(response.answer))
        print("Answer preview:\n", response.answer[:500])
    except Exception as e:
        print("\nFAILED:", e)

if __name__ == "__main__":
    test_generation()
