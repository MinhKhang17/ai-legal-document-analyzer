"""Test script to call RagQueryService directly to get python traceback on failure."""
import sys
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, '.')

from app.schemas import RagQueryRequest
from app.services.rag_query_service import RagQueryService

payload = RagQueryRequest(
    request_id="test-req-123",
    user_id="user_demo",
    workspace_id="ws_demo",
    question="Tôi muốn chấm dứt hợp đồng sớm thì thế nào?",
    top_k_user_chunks=3,
    top_k_knowledge_chunks=3,
)

print("=" * 80)
print("RUNNING DIRECT PIPELINE QUERY")
print("=" * 80)

try:
    service = RagQueryService()
    response = service.query(payload)
    print("SUCCESS!")
    print(f"Intent: {response.intent}")
    print(f"Answer: {response.answer[:200]}...")
except Exception as e:
    import traceback
    print("ERROR:")
    traceback.print_exc()
