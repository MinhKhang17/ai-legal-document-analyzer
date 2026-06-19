"""Test Knowledge Base sau khi import văn bản pháp luật.

Kiểm tra:
- Số lượng chunks trong Neo4j
- Search quality
- Chatbot trích dẫn đúng luật
"""
import requests
import json

API_BASE = "http://localhost:8000"


def test_neo4j_chunks():
    """Test số lượng chunks trong Neo4j."""
    print("=" * 70)
    print("1. Testing Neo4j Chunks Count")
    print("=" * 70)
    
    # Note: Cần thêm endpoint này vào API
    # Tạm thời dùng search để test
    
    response = requests.post(
        f"{API_BASE}/admin/risk-knowledge/query-v2",
        json={"query": "hợp đồng", "top_k": 1}
    )
    
    if response.status_code == 200:
        result = response.json()
        print("✅ Neo4j connection OK")
        print(f"   Found {len(result.get('chunks', []))} chunks for 'hợp đồng'")
    else:
        print(f"❌ Failed: {response.status_code}")
    
    print()


def test_legal_queries():
    """Test các câu hỏi pháp lý."""
    print("=" * 70)
    print("2. Testing Legal Queries")
    print("=" * 70)
    
    test_queries = [
        {
            "query": "Điều 472 BLDS 2015 quy định gì về hợp đồng thuê?",
            "expected_keywords": ["điều 472", "hợp đồng", "thuê", "blds"]
        },
        {
            "query": "Quyền và nghĩa vụ của bên cho thuê nhà",
            "expected_keywords": ["quyền", "nghĩa vụ", "cho thuê"]
        },
        {
            "query": "Điều kiện chấm dứt hợp đồng thuê nhà theo luật",
            "expected_keywords": ["chấm dứt", "hợp đồng", "thuê"]
        },
        {
            "query": "Mức phạt vi phạm hợp đồng tối đa bao nhiêu?",
            "expected_keywords": ["phạt", "vi phạm"]
        },
    ]
    
    for idx, test in enumerate(test_queries, 1):
        print(f"\n--- Query {idx} ---")
        print(f"Q: {test['query']}")
        
        response = requests.post(
            f"{API_BASE}/admin/risk-knowledge/query-v2",
            json={"query": test["query"], "top_k": 3}
        )
        
        if response.status_code == 200:
            result = response.json()
            chunks = result.get("chunks", [])
            
            if chunks:
                print(f"✅ Found {len(chunks)} relevant chunks")
                
                # Check if answer contains expected keywords
                answer = result.get("answer_preview", "").lower()
                matched = sum(1 for kw in test["expected_keywords"] if kw in answer)
                
                print(f"   Keyword match: {matched}/{len(test['expected_keywords'])}")
                
                # Show top result
                top_chunk = chunks[0]
                print(f"   Top result: {top_chunk['title'][:60]}...")
                print(f"   Score: {top_chunk['score']:.3f}")
            else:
                print("⚠️  No chunks found!")
        else:
            print(f"❌ Query failed: {response.status_code}")
    
    print()


def test_chatbot_citation():
    """Test chatbot trích dẫn luật."""
    print("=" * 70)
    print("3. Testing Chatbot Citation")
    print("=" * 70)
    
    # Tạo test contract
    test_contract = """HỢP ĐỒNG THUÊ NHÀ

Bên A: Nguyễn Văn A
Bên B: Trần Thị B

Nhà cho thuê tại 123 Đường ABC
Giá thuê: 5 triệu đồng/tháng
"""
    
    # Save to temp file
    import tempfile
    from pathlib import Path
    
    with tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False, encoding='utf-8') as f:
        f.write(test_contract)
        temp_file = Path(f.name)
    
    try:
        # Upload and ask về luật
        with open(temp_file, 'rb') as f:
            response = requests.post(
                f"{API_BASE}/v2/chatbot/upload",
                files={"file": ("test.txt", f, "text/plain")},
                data={
                    "session_id": "kb-test-001",
                    "initial_question": "Hợp đồng này có đủ các điều khoản bắt buộc theo Điều 472 BLDS 2015 không?",
                    "title": "Test Contract"
                }
            )
        
        if response.status_code == 200:
            result = response.json()
            message = result.get("message", "")
            
            print("✅ Chatbot response received")
            print("\nBot answer (first 500 chars):")
            print("-" * 70)
            print(message[:500])
            if len(message) > 500:
                print("... (truncated)")
            print("-" * 70)
            
            # Check if cites BLDS 2015
            if "blds" in message.lower() or "điều 472" in message.lower():
                print("\n✅ Bot trích dẫn BLDS 2015!")
            else:
                print("\n⚠️  Bot không trích dẫn BLDS 2015")
            
            # Check sources
            sources = result.get("sources", [])
            if sources:
                print(f"\n📌 Sources provided: {len(sources)}")
                for source in sources[:3]:
                    print(f"   - {source.get('title', 'N/A')}")
        else:
            print(f"❌ Upload failed: {response.status_code}")
            print(f"   Error: {response.text[:200]}")
    
    finally:
        # Cleanup
        if temp_file.exists():
            temp_file.unlink()
        
        # Clear session
        requests.post(f"{API_BASE}/v2/chatbot/clear-session?session_id=kb-test-001")
    
    print()


def test_search_coverage():
    """Test coverage của knowledge base."""
    print("=" * 70)
    print("4. Testing Knowledge Base Coverage")
    print("=" * 70)
    
    # Các chủ đề quan trọng
    topics = [
        "hợp đồng thuê nhà",
        "quyền sở hữu tài sản",
        "nghĩa vụ thanh toán",
        "chấm dứt hợp đồng",
        "bồi thường thiệt hại",
        "tranh chấp hợp đồng",
        "điều kiện vô hiệu",
        "trách nhiệm vi phạm",
    ]
    
    coverage_results = []
    
    for topic in topics:
        response = requests.post(
            f"{API_BASE}/admin/risk-knowledge/query-v2",
            json={"query": topic, "top_k": 1}
        )
        
        if response.status_code == 200:
            result = response.json()
            chunks = result.get("chunks", [])
            
            if chunks:
                score = chunks[0]["score"]
                status = "✅" if score > 0.5 else "⚠️" if score > 0.3 else "❌"
                coverage_results.append({
                    "topic": topic,
                    "status": status,
                    "score": score,
                })
            else:
                coverage_results.append({
                    "topic": topic,
                    "status": "❌",
                    "score": 0.0,
                })
    
    # Print results
    print("\nCoverage Report:")
    print("-" * 70)
    for result in coverage_results:
        print(f"{result['status']} {result['topic']:30s} (score: {result['score']:.3f})")
    print("-" * 70)
    
    # Summary
    good = sum(1 for r in coverage_results if r["status"] == "✅")
    fair = sum(1 for r in coverage_results if r["status"] == "⚠️")
    poor = sum(1 for r in coverage_results if r["status"] == "❌")
    
    print(f"\nSummary:")
    print(f"  Good (>0.5):  {good}/{len(topics)}")
    print(f"  Fair (0.3-0.5): {fair}/{len(topics)}")
    print(f"  Poor (<0.3):  {poor}/{len(topics)}")
    
    coverage_pct = (good + fair * 0.5) / len(topics) * 100
    print(f"\n  Overall Coverage: {coverage_pct:.1f}%")
    
    if coverage_pct >= 80:
        print("  ✅ Knowledge Base is good!")
    elif coverage_pct >= 60:
        print("  ⚠️  Knowledge Base needs improvement")
    else:
        print("  ❌ Knowledge Base is insufficient - import more docs!")
    
    print()


def main():
    print("\n" + "=" * 70)
    print("🧪 KNOWLEDGE BASE TEST SUITE")
    print("=" * 70)
    print(f"API: {API_BASE}")
    print("=" * 70 + "\n")
    
    try:
        # Check health first
        response = requests.get(f"{API_BASE}/health", timeout=5)
        if response.status_code != 200:
            print("❌ AI Service is not running!")
            print("   Run: cd ai-service && docker-compose up -d")
            return
        
        # Run tests
        test_neo4j_chunks()
        test_legal_queries()
        test_chatbot_citation()
        test_search_coverage()
        
        print("=" * 70)
        print("✅ ALL TESTS COMPLETED")
        print("=" * 70)
        print("\nNext steps:")
        print("1. If coverage is low, import more PDFs")
        print("2. Check IMPORT_LEGAL_DOCS.md for import guide")
        print("3. Test chatbot with real contracts")
        
    except requests.exceptions.ConnectionError:
        print("\n❌ ERROR: Cannot connect to AI service")
        print("   Make sure the service is running:")
        print("   cd ai-service && docker-compose up -d")
    except Exception as e:
        print(f"\n❌ ERROR: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
