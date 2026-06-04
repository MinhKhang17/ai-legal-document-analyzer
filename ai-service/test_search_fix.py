"""Test search với query không dấu."""
import httpx

def test_search_no_accent():
    """Search với query không dấu."""
    url = "http://localhost:8000/api/ai/documents/search"
    
    data = {
        'user_id': 'test_user_123',
        'query': 'dat coc',  # Không dấu
        'top_k': 5
    }
    
    print("🔍 Searching with: 'dat coc'")
    response = httpx.post(url, data=data, timeout=10.0)
    
    print(f"📊 Status Code: {response.status_code}")
    result = response.json()
    print(f"📄 Response:\n{result}")
    
    if result.get('data', {}).get('results'):
        print("\n✅ Found documents!")
        for doc in result['data']['results']:
            print(f"  - {doc['filename']} (score: {doc['score']})")
            print(f"    Snippet: {doc['snippet']}")
    else:
        print("\n❌ No documents found")


if __name__ == "__main__":
    test_search_no_accent()
