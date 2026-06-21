"""Simple test for query endpoint."""
import json

import requests

url = "http://localhost:8000/internal/rag/query"

payload = {
    "request_id": "test_001",
    "user_id": "user_demo",
    "workspace_id": "ws_001",
    "question": "Co van de gi voi van ban nay khong?",
    "top_k_checklist": 10,
    "top_k_user_chunks_per_checklist": 3,
    "top_k_knowledge_chunks": 5,
}

print("Sending request...")
print(f"URL: {url}")
print(f"Payload: {json.dumps(payload, indent=2, ensure_ascii=False)}\n")

response = requests.post(url, json=payload, timeout=120)

print(f"Status: {response.status_code}")
print("\nResponse:")
print(json.dumps(response.json(), indent=2, ensure_ascii=False))
