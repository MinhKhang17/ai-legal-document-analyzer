"""Test script to verify how the RAG pipeline responds to questions about sources and references."""
import requests
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://127.0.0.1:8000"

test_queries = [
    "Văn bản này lấy từ đâu?",
    "Thông tin nguồn ở đâu?",
    "Có tài liệu tham khảo nào không?"
]

print("=" * 80)
print("TESTING QUERIES ABOUT DOCUMENT SOURCES AND CITATIONS")
print("=" * 80)

for idx, q in enumerate(test_queries, 1):
    payload = {
        "request_id": f"test-citation-req-{idx}",
        "user_id": "user_demo",
        "workspace_id": "ws_demo",
        "question": q,
        "top_k_user_chunks": 3,
        "top_k_knowledge_chunks": 3,
    }
    
    print(f"\n[{idx}] Question: \"{q}\"")
    try:
        response = requests.post(f"{BASE_URL}/internal/rag/query", json=payload, timeout=60)
        print(f"Status Code: {response.status_code}")
        if response.status_code == 200:
            data = response.json()
            print(f"  Detected Intent: {data.get('intent')}")
            print(f"  Response Mode: {data.get('responseMode')}")
            print(f"  Total Citations Returned: {len(data.get('citations', []))}")
            for cit_idx, citation in enumerate(data.get('citations', []), 1):
                print(f"    - Citation #{cit_idx}: doc_id={citation.get('document_id')}, title={citation.get('document_title')}")
            
            print("\n  Answer Preview:")
            print("-" * 60)
            print(data.get("answer")[:300] + "...")
            print("-" * 60)
        else:
            print(f"  Error Response: {response.text}")
    except Exception as e:
        print(f"  Connection failed: {e}")
