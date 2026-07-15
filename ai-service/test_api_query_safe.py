"""API Query test script with safe UTF-8 output formatting."""
import requests
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://127.0.0.1:8000"

payload = {
    "request_id": "test-req-123",
    "user_id": "user_demo",
    "workspace_id": "ws_demo",
    "question": "Phân tích hợp đồng lao động",
    "top_k_user_chunks": 3,
    "top_k_knowledge_chunks": 3,
}

print("=" * 80)
print("SENDING RAG QUERY REQUEST")
print(f"Question: \"{payload['question']}\"")
print("=" * 80)

try:
    response = requests.post(f"{BASE_URL}/internal/rag/query", json=payload, timeout=60)
    print(f"Status Code: {response.status_code}")
    if response.status_code == 200:
        data = response.json()
        print("\nSTRUCTURED RESPONSE FIELDS:")
        print(f"  RequestId: {data.get('requestId')}")
        print(f"  Intent: {data.get('intent')}")
        print(f"  ContractType: {data.get('contractType')}")
        print(f"  ResponseMode: {data.get('responseMode')}")
        print(f"  InputComplete: {data.get('inputComplete')}")
        print(f"  MissingInputs: {data.get('missingInputs')}")
        print(f"  RiskLevel: {data.get('riskLevel')}")
        print(f"  SuggestionType: {data.get('suggestionType')}")
        print(f"  UserActionHint: {data.get('userActionHint')}")
        print(f"  ConfidenceScore: {data.get('confidenceScore')}")
        
        analysis = data.get("analysis")
        if analysis:
            print("\nANALYSIS FIELD:")
            print(json.dumps(analysis, indent=2, ensure_ascii=False))
        else:
            print("\nAnalysis field is null/none.")
            
        print("\nANSWER PREVIEW:")
        print("-" * 80)
        print(data.get("answer")[:500] + "...")
        print("-" * 80)
    else:
        print(f"Error Response: {response.text}")
except Exception as e:
    print(f"Connection failed: {e}")
