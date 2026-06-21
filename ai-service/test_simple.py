"""Simple test script for quick testing."""
import requests
import json

BASE_URL = "http://localhost:8000"

def test_simple():
    """Quick test using the simple endpoint."""
    print("=" * 60)
    print("Testing AI Service - Simple Test")
    print("=" * 60)
    
    # Test 1: Health
    print("\n1. Health Check...")
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=5)
        print(f"   ✅ Status: {response.status_code}")
        print(f"   Response: {response.json()}")
    except Exception as e:
        print(f"   ❌ Error: {e}")
        return
    
    # Test 2: Test query endpoint
    print("\n2. Testing Global Query...")
    try:
        response = requests.get(
            f"{BASE_URL}/test/query",
            params={
                "user_id": "user_demo",
                "question": "Có vấn đề gì với văn bản này không?"
            },
            timeout=60
        )
        
        print(f"   Status: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"\n   ✅ Success: {data.get('success')}")
            print(f"   Checklists: {data.get('total_checklist_items')}")
            print(f"   User Chunks: {data.get('total_user_chunks')}")
            print(f"   Knowledge Chunks: {data.get('total_knowledge_chunks')}")
            print(f"   Processing Time: {data.get('processing_time_ms'):.2f}ms")
            
            print(f"\n   📝 Answer Preview:")
            answer = data.get('answer', '')
            print(f"   {answer[:500]}...")
            
        else:
            print(f"   ❌ Error: {response.text}")
            
    except Exception as e:
        print(f"   ❌ Error: {e}")
    
    print("\n" + "=" * 60)

if __name__ == "__main__":
    test_simple()
