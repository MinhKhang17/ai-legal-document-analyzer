"""Comprehensive test suite for AI Service with various vague questions."""
import requests
import json
import time
from typing import List, Dict, Any

BASE_URL = "http://localhost:8000"

# Test cases with vague/general questions that AI should handle
TEST_CASES = {
    "rà soát tổng quát": [
        "Có vấn đề gì với văn bản này không?",
        "Có vấn đề gì với hợp đồng này không?",
        "Có vấn đề nào cần lưu ý không?",
        "Có điểm nào cần chú ý không?",
        "Có rủi ro gì không?",
        "Có thiếu sót gì không?",
        "Có sai sót gì không?",
    ],
    
    "yêu cầu kiểm tra": [
        "Kiểm tra văn bản này",
        "Kiểm tra hợp đồng này",
        "Rà soát văn bản này",
        "Rà soát hợp đồng này",
        "Đánh giá văn bản này",
        "Đánh giá hợp đồng này",
        "Xem xét văn bản này",
        "Phân tích văn bản này",
    ],
    
    "hợp lệ không": [
        "Văn bản này có hợp lệ không?",
        "Hợp đồng này có hợp lệ không?",
        "Văn bản này có đúng không?",
        "Hợp đồng này có đủ không?",
        "Văn bản này có ổn không?",
        "Hợp đồng này có được không?",
        "Có thể ký được không?",
    ],
    
    "câu hỏi ngắn": [
        "Có vấn đề j",
        "Có lỗi j",
        "Có gì sai",
        "Sao",
        "Review đi",
        "Check giúp",
        "Có oke ko",
    ],
    
    "câu hỏi tiếng Anh": [
        "What's wrong with this document?",
        "Any issues?",
        "Check this contract",
        "Review this",
        "Is this document valid?",
    ],
    
    "câu hỏi mơ hồ": [
        "Tài liệu này thế nào?",
        "Hợp đồng này như thế nào?",
        "Nhận xét về văn bản này",
        "Nói cho tôi biết về hợp đồng này",
        "Có gì đặc biệt không?",
        "Cần chú ý điều gì?",
    ]
}

def test_health():
    """Test health check."""
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=5)
        return response.status_code == 200, response.json()
    except Exception as e:
        return False, str(e)

def test_single_query(question: str, user_id: str = "user_demo") -> Dict[str, Any]:
    """Test a single query."""
    try:
        start_time = time.time()
        
        response = requests.get(
            f"{BASE_URL}/test/query",
            params={
                "user_id": user_id,
                "question": question
            },
            timeout=60
        )
        
        elapsed_time = (time.time() - start_time) * 1000  # ms
        
        if response.status_code == 200:
            data = response.json()
            return {
                "success": True,
                "status_code": response.status_code,
                "elapsed_time": elapsed_time,
                "ai_success": data.get("success"),
                "total_checklists": data.get("total_checklist_items"),
                "total_chunks": data.get("total_user_chunks"),
                "answer_length": len(data.get("answer", "")),
                "has_answer": bool(data.get("answer")),
                "error": data.get("error_message")
            }
        else:
            return {
                "success": False,
                "status_code": response.status_code,
                "elapsed_time": elapsed_time,
                "error": response.text
            }
            
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }

def print_test_result(question: str, result: Dict[str, Any], index: int, total: int):
    """Print formatted test result."""
    status = "✅" if result.get("success") and result.get("ai_success") else "❌"
    
    print(f"\n{status} [{index}/{total}] \"{question}\"")
    
    if result.get("success"):
        print(f"   Status: {result.get('status_code')}")
        print(f"   Time: {result.get('elapsed_time', 0):.0f}ms")
        print(f"   AI Success: {result.get('ai_success')}")
        print(f"   Checklists: {result.get('total_checklists')}")
        print(f"   Chunks: {result.get('total_chunks')}")
        print(f"   Answer Length: {result.get('answer_length')} chars")
        
        if result.get("error"):
            print(f"   ⚠️  Error: {result.get('error')}")
    else:
        print(f"   ❌ Failed: {result.get('error')}")

def run_comprehensive_test():
    """Run comprehensive test suite."""
    print("=" * 80)
    print("🧪 COMPREHENSIVE AI SERVICE TEST SUITE")
    print("Testing AI's ability to handle vague/general questions")
    print("=" * 80)
    
    # Step 1: Health check
    print("\n📋 Step 1: Health Check")
    health_ok, health_data = test_health()
    if health_ok:
        print(f"   ✅ Service is healthy: {health_data}")
    else:
        print(f"   ❌ Service is not healthy: {health_data}")
        print("\n⚠️  Please start the AI service first:")
        print("   cd ai-service && python run_dev.py")
        return
    
    # Check if Gemini is configured
    print("\n📋 Step 1.5: Check Gemini Configuration")
    print("   ℹ️  Check service logs to see if Gemini is initialized")
    print("   ✅ With Gemini: High-quality AI-generated answers")
    print("   ⚠️  Without Gemini: Fallback answer generation")
    
    # Step 2: Test all categories
    print("\n📋 Step 2: Testing Various Question Types")
    
    all_results = []
    total_questions = sum(len(questions) for questions in TEST_CASES.values())
    current_index = 0
    
    for category, questions in TEST_CASES.items():
        print(f"\n{'=' * 80}")
        print(f"📁 Category: {category.upper()}")
        print(f"{'=' * 80}")
        
        category_results = []
        
        for question in questions:
            current_index += 1
            result = test_single_query(question)
            result["question"] = question
            result["category"] = category
            category_results.append(result)
            all_results.append(result)
            
            print_test_result(question, result, current_index, total_questions)
            
            # Small delay to avoid overwhelming the service
            time.sleep(0.5)
        
        # Category summary
        success_count = sum(1 for r in category_results if r.get("success") and r.get("ai_success"))
        print(f"\n   📊 Category Summary: {success_count}/{len(questions)} passed")
    
    # Step 3: Overall summary
    print("\n" + "=" * 80)
    print("📊 OVERALL TEST SUMMARY")
    print("=" * 80)
    
    total_tests = len(all_results)
    successful_tests = sum(1 for r in all_results if r.get("success") and r.get("ai_success"))
    failed_tests = total_tests - successful_tests
    
    print(f"\n✅ Passed: {successful_tests}/{total_tests} ({successful_tests/total_tests*100:.1f}%)")
    print(f"❌ Failed: {failed_tests}/{total_tests} ({failed_tests/total_tests*100:.1f}%)")
    
    # Average metrics for successful tests
    successful_results = [r for r in all_results if r.get("success") and r.get("ai_success")]
    if successful_results:
        avg_time = sum(r.get("elapsed_time", 0) for r in successful_results) / len(successful_results)
        avg_checklists = sum(r.get("total_checklists", 0) for r in successful_results) / len(successful_results)
        avg_chunks = sum(r.get("total_chunks", 0) for r in successful_results) / len(successful_results)
        avg_answer_len = sum(r.get("answer_length", 0) for r in successful_results) / len(successful_results)
        
        print(f"\n📈 Average Metrics (Successful Tests):")
        print(f"   Response Time: {avg_time:.0f}ms")
        print(f"   Checklists Retrieved: {avg_checklists:.1f}")
        print(f"   Chunks Found: {avg_chunks:.1f}")
        print(f"   Answer Length: {avg_answer_len:.0f} chars")
    
    # Failed questions
    failed_results = [r for r in all_results if not (r.get("success") and r.get("ai_success"))]
    if failed_results:
        print(f"\n❌ Failed Questions:")
        for r in failed_results:
            print(f"   - \"{r.get('question')}\" ({r.get('category')})")
            print(f"     Reason: {r.get('error', 'Unknown')}")
    
    # Step 4: Detailed sample response
    print("\n" + "=" * 80)
    print("📝 SAMPLE DETAILED RESPONSE")
    print("=" * 80)
    
    sample_question = "Có vấn đề gì với văn bản này không?"
    print(f"\nQuestion: \"{sample_question}\"")
    
    try:
        response = requests.get(
            f"{BASE_URL}/test/query",
            params={
                "user_id": "user_demo",
                "question": sample_question
            },
            timeout=60
        )
        
        if response.status_code == 200:
            data = response.json()
            print(f"\nFull Response:")
            print(json.dumps(data, indent=2, ensure_ascii=False))
        else:
            print(f"Failed to get sample response: {response.text}")
    except Exception as e:
        print(f"Error: {e}")
    
    print("\n" + "=" * 80)
    print("✅ TEST SUITE COMPLETED")
    print("=" * 80)

if __name__ == "__main__":
    run_comprehensive_test()
