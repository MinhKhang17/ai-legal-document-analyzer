"""Script to test AI Service API."""
import requests
import json

# API endpoint
BASE_URL = "http://localhost:8000"

def test_health():
    """Test health endpoint."""
    print("=== Testing Health Endpoint ===")
    try:
        response = requests.get(f"{BASE_URL}/health")
        print(f"Status: {response.status_code}")
        print(f"Response: {response.json()}")
        return response.status_code == 200
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

def test_query_global():
    """Test global review query."""
    print("\n=== Testing Global Review Query ===")
    
    # Test với user có dữ liệu trong Neo4j
    payload = {
        "request_id": "test_001",
        "user_id": "user_demo",  # User có trong Neo4j
        "workspace_id": "ws_001",
        "chat_session_id": "chat_test_001",
        "question": "Có vấn đề gì với văn bản này không?",
        "top_k_checklist": 10,
        "top_k_user_chunks_per_checklist": 3,
        "top_k_knowledge_chunks": 5
    }
    
    print(f"Request payload:")
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    
    try:
        response = requests.post(
            f"{BASE_URL}/internal/rag/query",
            json=payload,
            headers={"Content-Type": "application/json"}
        )
        
        print(f"\nStatus: {response.status_code}")
        print(f"\nResponse:")
        print(json.dumps(response.json(), indent=2, ensure_ascii=False))
        
        return response.status_code == 200
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

def test_query_specific():
    """Test specific question query."""
    print("\n=== Testing Specific Query ===")
    
    payload = {
        "request_id": "test_002",
        "user_id": "user_demo",
        "workspace_id": "ws_001",
        "chat_session_id": "chat_test_001",
        "question": "Hợp đồng này có điều khoản về thanh toán không?",
        "top_k_checklist": 10,
        "top_k_user_chunks_per_checklist": 3,
        "top_k_knowledge_chunks": 5
    }
    
    print(f"Request payload:")
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    
    try:
        response = requests.post(
            f"{BASE_URL}/internal/rag/query",
            json=payload,
            headers={"Content-Type": "application/json"}
        )
        
        print(f"\nStatus: {response.status_code}")
        print(f"\nResponse:")
        print(json.dumps(response.json(), indent=2, ensure_ascii=False))
        
        return response.status_code == 200
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

def main():
    """Run all tests."""
    print("=" * 60)
    print("AI Service API Test Suite")
    print("=" * 60)
    
    results = []
    
    # Test health
    results.append(("Health Check", test_health()))
    
    # Test global query
    results.append(("Global Review Query", test_query_global()))
    
    # Test specific query
    results.append(("Specific Query", test_query_specific()))
    
    # Summary
    print("\n" + "=" * 60)
    print("Test Summary")
    print("=" * 60)
    for test_name, passed in results:
        status = "✅ PASSED" if passed else "❌ FAILED"
        print(f"{test_name}: {status}")
    
    total = len(results)
    passed = sum(1 for _, p in results if p)
    print(f"\nTotal: {passed}/{total} tests passed")

if __name__ == "__main__":
    main()
