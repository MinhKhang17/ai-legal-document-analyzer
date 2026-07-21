"""Test script to invoke the FastAPI router handler function directly to see the exception traceback."""
import sys
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, '.')

from app.schemas import RagQueryRequest
from app.api.rag_api import query_rag

payload = RagQueryRequest(
    request_id="test-req-123",
    user_id="user_demo",
    workspace_id="ws_demo",
    question="Tôi muốn chấm dứt hợp đồng sớm thì thế nào?",
    top_k_user_chunks=3,
    top_k_knowledge_chunks=3,
)

print("=" * 80)
print("RUNNING FASTAPI HANDLER DIRECTLY")
print("=" * 80)

try:
    response = query_rag(payload)
    print("SUCCESS!")
    print(response.model_dump_json(indent=2))
except Exception as e:
    import traceback
    print("ERROR:")
    traceback.print_exc()
