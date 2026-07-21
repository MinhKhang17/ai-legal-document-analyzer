"""Test suite for vague/general question handling."""
import sys
import io
import requests
import json
import time
from typing import List, Dict, Any

# Fix encoding for Windows console
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8000"

# Comprehensive list of vague/general questions
VAGUE_QUESTIONS = {
    "Câu hỏi về vấn đề": [
        "Có vấn đề gì với văn bản này không?",
        "Có vấn đề gì với hợp đồng này không?",
        "Có vấn đề gì không?",
        "Có vấn đề nào không?",
        "Có vấn đề j",
        "Có điểm nào cần lưu ý không?",
        "Có điểm gì đáng chú ý không?",
    ],
    
    "Câu hỏi về rủi ro": [
        "Có rủi ro gì không?",
        "Có rủi ro nào không?",
        "Rủi ro là gì?",
        "Có nguy cơ gì không?",
        "Có mối lo gì không?",
    ],
    
    "Câu hỏi về thiếu sót": [
        "Có thiếu sót gì không?",
        "Có thiếu sót nào không?",
        "Có sai sót gì không?",
        "Có lỗi gì không?",
        "Có lỗi nào không?",
        "Có lỗi j",
    ],
    
    "Yêu cầu rà soát": [
        "Rà soát văn bản này",
        "Rà soát hợp đồng này",
        "Rà soát giúp tôi",
        "Rà soát đi",
        "Kiểm tra văn bản này",
        "Kiểm tra hợp đồng này",
        "Kiểm tra giúp tôi",
    ],
    
    "Yêu cầu đánh giá": [
        "Đánh giá văn bản này",
        "Đánh giá hợp đồng này",
        "Xem xét văn bản này",
        "Xem xét hợp đồng này",
        "Phân tích văn bản này",
        "Phân tích hợp đồng này",
        "Review văn bản này",
        "Review hợp đồng này",
    ],
    
    "Câu hỏi về tính hợp lệ": [
        "Văn bản này có hợp lệ không?",
        "Hợp đồng này có hợp lệ không?",
        "Có hợp lệ ko?",
        "Có đúng không?",
        "Có đúng ko?",
        "Có ổn không?",
        "Có ổn ko?",
        "Có được không?",
        "Có được ko?",
        "Có thể ký được không?",
    ],
    
    "Câu hỏi rất ngắn": [
        "Có gì sai",
        "Sao",
        "Như nào",
        "Thế nào",
        "Review đi",
        "Check giúp",
        "Có oke ko",
        "Ok ko",
    ],
    
    "Câu hỏi mơ hồ": [
        "Tài liệu này thế nào?",
        "Hợp đồng này như thế nào?",
        "Nhận xét về văn bản này",
        "Nói cho tôi biết về hợp đồng này",
        "Có gì đặc biệt không?",
        "Cần chú ý điều gì?",
        "Cần lưu ý gì?",
    ],
    
    "Tiếng Anh": [
        "What's wrong with this document?",
        "Any issues?",
        "Check this contract",
        "Review this",
        "Is this document valid?",
        "Any problems?",
        "Check it",
    ],

    # ═══════════════════════════════════════════════════
    # CÂU HỎI MỚI BỔ SUNG ĐỂ TRAIN AI
    # ═══════════════════════════════════════════════════

    "Đặt cọc & Bảo đảm": [
        "Phân tích phần đặt cọc",
        "Tiền cọc có hợp lý không?",
        "Đặt cọc bao nhiêu?",
        "Mức đặt cọc có đúng quy định không?",
        "Điều kiện hoàn trả cọc thế nào?",
        "Khi nào bị mất cọc?",
        "Tiền cọc có được trả lại không?",
        "Cọc 3 tháng có nhiều quá không?",
        "Có quy định về phạt cọc không?",
        "Thiếu điều khoản hoàn trả cọc",
        "So sánh mức cọc với Điều 328 BLDS",
        "Tiền cọc có tính vào tháng cuối không?",
    ],

    "Thanh toán": [
        "Phương thức thanh toán có rủi ro gì?",
        "Lãi chậm thanh toán bao nhiêu?",
        "Thời hạn thanh toán thế nào?",
        "Thanh toán trước hay sau?",
        "Lãi suất chậm trả có hợp lý không?",
        "Giá thuê có được tăng không?",
        "Cách tính tiền thuê trong hợp đồng",
        "Chậm thanh toán thì bị phạt bao nhiêu?",
        "Giá thuê đã bao gồm VAT chưa?",
    ],

    "Chấm dứt hợp đồng": [
        "Nếu tôi muốn chấm dứt sớm thì sao?",
        "Điều kiện chấm dứt hợp đồng?",
        "Muốn hủy hợp đồng phải làm gì?",
        "Báo trước bao lâu để chấm dứt?",
        "Đơn phương chấm dứt có được không?",
        "Hậu quả khi chấm dứt sớm?",
        "Chủ nhà có quyền đuổi không?",
        "Muốn dừng thuê sớm mà không mất cọc?",
        "Hết hạn hợp đồng thì sao?",
        "Gia hạn hợp đồng thế nào?",
    ],

    "Quyền và nghĩa vụ": [
        "Quyền của bên thuê có đầy đủ không?",
        "Nghĩa vụ của tôi là gì?",
        "Quyền và nghĩa vụ có cân bằng không?",
        "Bên cho thuê có quyền gì?",
        "Tôi có quyền sửa chữa nhà không?",
        "Ai chịu trách nhiệm sửa chữa?",
        "Chủ nhà có được vào nhà không?",
        "Có được cho thuê lại không?",
        "Trách nhiệm của mỗi bên",
        "Có điều khoản bất lợi cho tôi không?",
    ],

    "Phạt vi phạm": [
        "Mức phạt này có hợp lý không?",
        "Phạt vi phạm bao nhiêu phần trăm?",
        "Mức phạt tối đa là bao nhiêu?",
        "Phạt vi phạm có kèm bồi thường không?",
        "Có bị phạt khi chấm dứt sớm không?",
        "Điều khoản phạt có đúng luật không?",
        "Phạt 8% có đúng quy định không?",
    ],

    "Bất khả kháng": [
        "Có điều khoản bất khả kháng không?",
        "Dịch bệnh có phải bất khả kháng không?",
        "Thiên tai thì xử lý sao?",
        "Nếu xảy ra sự kiện bất ngờ thì sao?",
        "Thiếu điều khoản bất khả kháng",
        "Force majeure trong hợp đồng",
    ],

    "Thuế và phí": [
        "Ai chịu thuế cho thuê?",
        "Thuế cho thuê nhà bao nhiêu?",
        "Có phí phát sinh nào không?",
        "Chi phí điện nước tính sao?",
        "Phí quản lý ai trả?",
        "Thuế VAT tính thế nào?",
        "Chi phí ngoài hợp đồng?",
    ],

    "Tranh chấp": [
        "Nếu xảy ra tranh chấp thì xử lý sao?",
        "Giải quyết tranh chấp thế nào?",
        "Phương thức giải quyết tranh chấp?",
        "Tòa án nào có thẩm quyền?",
        "Có được đưa ra trọng tài không?",
        "Cần chứng cứ gì khi tranh chấp?",
        "Thời hiệu khởi kiện bao lâu?",
        "Hòa giải trước khi kiện?",
    ],

    "So sánh với pháp luật": [
        "Hợp đồng có đúng luật không?",
        "So sánh với Bộ luật Dân sự 2015",
        "Có phù hợp quy định pháp luật không?",
        "Điều khoản nào trái luật?",
        "Có vi phạm Luật Nhà ở không?",
        "Theo pháp luật thì thế nào?",
        "Căn cứ pháp lý nào áp dụng?",
    ],

    "Câu hỏi tiếp nối": [
        "Giải thích rõ hơn phần đó",
        "Còn gì nữa không?",
        "Phần đó là sao?",
        "Nói thêm về điểm này",
        "Chi tiết hơn được không?",
        "Tại sao lại rủi ro?",
        "Ý bạn là gì?",
        "Liên quan đến điều gì?",
        "Phân tích sâu hơn được không?",
        "Có ví dụ cụ thể không?",
    ],

    "Tư vấn và hành động": [
        "Tôi nên làm gì?",
        "Có nên ký không?",
        "Tôi nên sửa điều gì?",
        "Cần bổ sung gì?",
        "Nên thương lượng lại điều gì?",
        "Làm sao để bảo vệ quyền lợi?",
        "Tôi cần chuẩn bị gì trước khi ký?",
        "Điều khoản nào nên thêm vào?",
        "Có nên nhờ luật sư không?",
        "Sửa hợp đồng chỗ nào?",
        "Đề xuất sửa đổi?",
    ],
}

def test_vague_question(question: str, user_id: str = "user_demo") -> Dict[str, Any]:
    """Test a single vague question."""
    try:
        start_time = time.time()
        
        response = requests.get(
            f"{BASE_URL}/test/query",
            params={
                "user_id": user_id,
                "question": question
            },
            timeout=120
        )
        
        elapsed_time = (time.time() - start_time) * 1000
        
        if response.status_code == 200:
            data = response.json()
            
            # Handle None answer
            answer = data.get("answer") or ""
            error_msg = data.get("error_message")
            
            # Analyze response quality
            has_answer = bool(answer)
            answer_length = len(answer)
            is_meaningful = answer_length > 100  # At least 100 chars
            
            checklist_count = data.get("total_checklist_items", 0)
            chunk_count = data.get("total_user_chunks", 0)
            
            # Check if it's a proper review response
            answer_lower = answer.lower()
            has_structure = any(keyword in answer_lower for keyword in [
                "kết quả", "điểm cần", "lưu ý", "rủi ro", "khuyến nghị", 
                "tổng kết", "phân tích", "review", "checklist"
            ])
            
            return {
                "success": True,
                "ai_success": data.get("success") and not error_msg,
                "status_code": response.status_code,
                "elapsed_time": elapsed_time,
                "has_answer": has_answer,
                "answer_length": answer_length,
                "is_meaningful": is_meaningful,
                "has_structure": has_structure,
                "checklist_count": checklist_count,
                "chunk_count": chunk_count,
                "answer_preview": answer[:200] if has_answer else "",
                "error": error_msg
            }
        else:
            return {
                "success": False,
                "status_code": response.status_code,
                "error": response.text
            }
    
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }

def print_result(question: str, result: Dict[str, Any], index: int, total: int):
    """Print test result with detailed analysis."""
    
    # Determine status
    if result.get("success") and result.get("ai_success"):
        if result.get("is_meaningful") and result.get("has_structure"):
            status = "✅"  # Perfect
        elif result.get("has_answer"):
            status = "⚠️ "  # Has answer but quality issues
        else:
            status = "❌"  # No answer
    else:
        status = "❌"  # Failed
    
    print(f"\n{status} [{index}/{total}] \"{question}\"")
    
    if result.get("success"):
        print(f"   ⏱️  Time: {result.get('elapsed_time', 0):.0f}ms")
        print(f"   📊 Checklists: {result.get('checklist_count', 0)}, Chunks: {result.get('chunk_count', 0)}")
        print(f"   📝 Answer: {result.get('answer_length', 0)} chars")
        
        if result.get('is_meaningful'):
            print(f"   ✅ Meaningful answer (>100 chars)")
        else:
            print(f"   ⚠️  Short answer")
        
        if result.get('has_structure'):
            print(f"   ✅ Structured response")
        else:
            print(f"   ⚠️  No clear structure")
        
        if result.get('answer_preview'):
            print(f"   💬 Preview: {result.get('answer_preview')}...")
        
        if result.get('error'):
            print(f"   ⚠️  Error: {result.get('error')}")
    else:
        print(f"   ❌ Failed: {result.get('error')}")

def check_detection_logic():
    """Test if detection logic correctly identifies vague questions."""
    print("=" * 80)
    print("🧪 TESTING VAGUE QUESTION DETECTION LOGIC")
    print("=" * 80)
    
    # Test a few questions to see if they're detected as global review
    test_questions = [
        ("Có vấn đề gì với văn bản này không?", True),
        ("Rà soát hợp đồng này", True),
        ("Có ổn không?", True),
        ("Điều 5 nói gì về trách nhiệm?", False),  # Specific question
        ("Thời hạn hợp đồng là bao lâu?", False),  # Specific question
    ]
    
    print("\nChecking detection logic manually...\n")
    for question, should_be_global in test_questions:
        # Manually check keywords
        question_lower = question.lower().strip()
        global_keywords = [
            "có vấn đề gì", "có vấn đề nào", "có điểm nào",
            "rà soát", "kiểm tra", "đánh giá", "phân tích", "xem xét",
            "có hợp lệ không", "có đúng không", "có thiếu sót gì",
            "có rủi ro gì", "có lỗi gì", "có sai sót gì",
            "review", "check", "có ổn không", "có được không"
        ]
        
        has_global_keyword = any(kw in question_lower for kw in global_keywords)
        is_short_general = len(question_lower) < 50 and ("văn bản" in question_lower or "hợp đồng" in question_lower)
        is_detected_global = has_global_keyword or is_short_general
        
        status = "✅" if is_detected_global == should_be_global else "❌"
        print(f"{status} \"{question}\"")
        print(f"   Expected: {'Global' if should_be_global else 'Specific'}")
        print(f"   Detected: {'Global' if is_detected_global else 'Specific'}")
        print(f"   Has keyword: {has_global_keyword}, Is short general: {is_short_general}\n")

def run_comprehensive_test():
    """Run comprehensive test."""
    print("\n" + "=" * 80)
    print("🚀 COMPREHENSIVE VAGUE QUESTION TEST")
    print("=" * 80)
    
    # Step 1: Detection logic test
    check_detection_logic()
    
    # Step 2: Health check
    print("\n" + "=" * 80)
    print("📋 HEALTH CHECK")
    print("=" * 80)
    
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=5)
        if response.status_code == 200:
            print(f"✅ Service is healthy")
        else:
            print(f"❌ Service returned status {response.status_code}")
            return
    except Exception as e:
        print(f"❌ Service is not running: {e}")
        print("\n⚠️  Please start the service first:")
        print("   cd ai-service")
        print("   python run_dev.py")
        return
    
    # Step 3: Test all vague questions
    print("\n" + "=" * 80)
    print("🧪 TESTING VAGUE QUESTIONS")
    print("=" * 80)
    
    all_results = []
    total_questions = sum(len(qs) for qs in VAGUE_QUESTIONS.values())
    current_index = 0
    
    for category, questions in VAGUE_QUESTIONS.items():
        print(f"\n{'─' * 80}")
        print(f"📁 {category}")
        print(f"{'─' * 80}")
        
        category_results = []
        
        for question in questions:
            current_index += 1
            result = test_vague_question(question)
            result["question"] = question
            result["category"] = category
            category_results.append(result)
            all_results.append(result)
            
            print_result(question, result, current_index, total_questions)
            
            time.sleep(0.3)  # Small delay
        
        # Category summary
        perfect = sum(1 for r in category_results 
                     if r.get("success") and r.get("ai_success") 
                     and r.get("is_meaningful") and r.get("has_structure"))
        partial = sum(1 for r in category_results 
                     if r.get("success") and r.get("ai_success") 
                     and r.get("has_answer"))
        
        print(f"\n   📊 Category: ✅ {perfect} perfect, ⚠️  {partial-perfect} partial, ❌ {len(questions)-partial} failed")
    
    # Step 4: Overall summary
    print("\n" + "=" * 80)
    print("📊 OVERALL SUMMARY")
    print("=" * 80)
    
    perfect = sum(1 for r in all_results 
                 if r.get("success") and r.get("ai_success") 
                 and r.get("is_meaningful") and r.get("has_structure"))
    partial = sum(1 for r in all_results 
                 if r.get("success") and r.get("ai_success") 
                 and r.get("has_answer"))
    failed = total_questions - partial
    
    print(f"\n✅ Perfect: {perfect}/{total_questions} ({perfect/total_questions*100:.1f}%)")
    print(f"⚠️  Partial: {partial-perfect}/{total_questions} ({(partial-perfect)/total_questions*100:.1f}%)")
    print(f"❌ Failed: {failed}/{total_questions} ({failed/total_questions*100:.1f}%)")
    
    # Average metrics
    successful = [r for r in all_results if r.get("success") and r.get("ai_success")]
    if successful:
        avg_time = sum(r.get("elapsed_time", 0) for r in successful) / len(successful)
        avg_checklists = sum(r.get("checklist_count", 0) for r in successful) / len(successful)
        avg_chunks = sum(r.get("chunk_count", 0) for r in successful) / len(successful)
        avg_length = sum(r.get("answer_length", 0) for r in successful) / len(successful)
        
        print(f"\n📈 Average Metrics:")
        print(f"   Response Time: {avg_time:.0f}ms")
        print(f"   Checklists: {avg_checklists:.1f}")
        print(f"   Chunks: {avg_chunks:.1f}")
        print(f"   Answer Length: {avg_length:.0f} chars")
    
    # Failed questions
    failed_results = [r for r in all_results if not (r.get("success") and r.get("ai_success"))]
    if failed_results:
        print(f"\n❌ Failed Questions ({len(failed_results)}):")
        for r in failed_results[:10]:  # Show first 10
            print(f"   - \"{r.get('question')}\"")
            if r.get('error'):
                print(f"     Error: {r.get('error')}")
    
    # Step 5: Show a detailed sample
    print("\n" + "=" * 80)
    print("📝 DETAILED SAMPLE RESPONSE")
    print("=" * 80)
    
    sample_q = "Có vấn đề gì với văn bản này không?"
    print(f"\nQuestion: \"{sample_q}\"\n")
    
    try:
        response = requests.get(
            f"{BASE_URL}/test/query",
            params={"user_id": "user_demo", "question": sample_q},
            timeout=120
        )
        
        if response.status_code == 200:
            data = response.json()
            print(f"Success: {data.get('success')}")
            print(f"Checklists: {data.get('total_checklist_items')}")
            print(f"User Chunks: {data.get('total_user_chunks')}")
            print(f"Knowledge Chunks: {data.get('total_knowledge_chunks')}")
            print(f"Processing Time: {data.get('processing_time_ms', 0):.0f}ms")
            print(f"\n{'─' * 80}")
            print("ANSWER:")
            print(f"{'─' * 80}")
            print(data.get('answer', 'No answer'))
            print(f"{'─' * 80}")
        else:
            print(f"Failed: {response.text}")
    except Exception as e:
        print(f"Error: {e}")
    
    print("\n" + "=" * 80)
    print("✅ TEST COMPLETED")
    print("=" * 80)

if __name__ == "__main__":
    run_comprehensive_test()
