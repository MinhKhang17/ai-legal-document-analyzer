"""Test với tiếng Việt có dấu."""
import sys
import io
import requests
import json
import time

# Fix encoding
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8000"

# Test với tiếng Việt CÓ DẤU
TEST_QUESTIONS = {
    "Câu hỏi về vấn đề": [
        "Có vấn đề gì với văn bản này không?",
        "Có vấn đề nào không?",
        "Có điểm nào cần lưu ý không?",
        "Có điểm gì đáng chú ý không?",
    ],
    
    "Câu hỏi về rủi ro": [
        "Có rủi ro gì không?",
        "Có rủi ro nào không?",
        "Có nguy cơ gì không?",
        "Có thiếu sót gì không?",
        "Có lỗi gì không?",
        "Có sai sót gì không?",
    ],
    
    "Yêu cầu rà soát": [
        "Rà soát văn bản này",
        "Rà soát hợp đồng này",
        "Kiểm tra văn bản này",
        "Kiểm tra hợp đồng này",
        "Đánh giá văn bản này",
        "Phân tích hợp đồng này",
    ],
    
    "Câu hỏi về tính hợp lệ": [
        "Văn bản này có hợp lệ không?",
        "Hợp đồng này có hợp lệ không?",
        "Có đúng không?",
        "Có ổn không?",
        "Có được không?",
        "Có thể ký được không?",
    ],
    
    "Câu hỏi ngắn gọn": [
        "Có gì sai",
        "Như thế nào",
        "Review đi",
        "Check giúp",
    ],
    
    "Câu hỏi mơ hồ": [
        "Tài liệu này thế nào?",
        "Hợp đồng này như thế nào?",
        "Nhận xét về văn bản này",
        "Có gì đặc biệt không?",
        "Cần chú ý điều gì?",
    ],
    
    "Mix tiếng Việt có dấu và không dấu": [
        "Co van de gi voi van ban nay khong?",  # Không dấu
        "Có vấn đề gì với văn bản này không?",  # Có dấu
        "Ra soat hop dong nay",  # Không dấu  
        "Rà soát hợp đồng này",  # Có dấu
    ],
}

print("=" * 80)
print("TEST TIẾNG VIỆT CÓ DẤU - Vague Question Handling")
print("=" * 80)

# Health check
print("\n[1/3] Health Check...")
try:
    r = requests.get(f"{BASE_URL}/health", timeout=5)
    if r.status_code == 200:
        print("   ✅ Service is healthy")
    else:
        print(f"   ❌ FAILED - Status {r.status_code}")
        sys.exit(1)
except Exception as e:
    print(f"   ❌ FAILED - {e}")
    sys.exit(1)

# Test questions
print("\n[2/3] Testing Questions...")

all_results = []
total_questions = sum(len(qs) for qs in TEST_QUESTIONS.values())
current = 0

for category, questions in TEST_QUESTIONS.items():
    print(f"\n  📁 {category}")
    
    category_success = 0
    
    for question in questions:
        current += 1
        # Truncate long questions for display
        display_q = question if len(question) <= 45 else question[:42] + "..."
        print(f"    [{current}/{total_questions}] {display_q} ", end="", flush=True)
        
        try:
            start = time.time()
            response = requests.get(
                f"{BASE_URL}/test/query",
                params={"user_id": "user_demo", "question": question},
                timeout=120
            )
            elapsed = (time.time() - start) * 1000
            
            if response.status_code == 200:
                data = response.json()
                success = data.get("success")
                answer = data.get("answer") or ""
                error = data.get("error_message")
                checklists = data.get("total_checklist_items", 0)
                
                if success and not error and len(answer) > 100:
                    print(f"✅ ({elapsed/1000:.1f}s, {len(answer)} chars)")
                    category_success += 1
                    all_results.append({
                        "question": question,
                        "status": "success",
                        "time": elapsed,
                        "answer_len": len(answer),
                        "checklists": checklists
                    })
                elif success and not error:
                    print(f"⚠️  Short answer ({len(answer)} chars)")
                    all_results.append({
                        "question": question,
                        "status": "partial",
                        "time": elapsed,
                        "answer_len": len(answer),
                        "reason": "Short answer"
                    })
                else:
                    print(f"❌ Error: {error[:30] if error else 'Unknown'}")
                    all_results.append({
                        "question": question,
                        "status": "failed",
                        "error": error
                    })
            else:
                print(f"❌ HTTP {response.status_code}")
                all_results.append({
                    "question": question,
                    "status": "failed",
                    "error": f"HTTP {response.status_code}"
                })
        
        except Exception as e:
            error_msg = str(e)[:40]
            print(f"❌ {error_msg}")
            all_results.append({
                "question": question,
                "status": "error",
                "error": str(e)
            })
        
        time.sleep(0.5)  # Small delay
    
    print(f"  📊 Category: {category_success}/{len(questions)} passed")

# Summary
print("\n[3/3] Test Summary")
print("=" * 80)

success_count = sum(1 for r in all_results if r.get("status") == "success")
partial_count = sum(1 for r in all_results if r.get("status") == "partial")
failed_count = len(all_results) - success_count - partial_count

print(f"\n📊 Overall Results:")
print(f"  ✅ SUCCESS: {success_count}/{len(all_results)} ({success_count/len(all_results)*100:.1f}%)")
print(f"  ⚠️  PARTIAL: {partial_count}/{len(all_results)} ({partial_count/len(all_results)*100:.1f}%)")
print(f"  ❌ FAILED:  {failed_count}/{len(all_results)} ({failed_count/len(all_results)*100:.1f}%)")

# Calculate averages for successful tests
successful = [r for r in all_results if r.get("status") == "success"]
if successful:
    avg_time = sum(r.get("time", 0) for r in successful) / len(successful)
    avg_len = sum(r.get("answer_len", 0) for r in successful) / len(successful)
    avg_checklists = sum(r.get("checklists", 0) for r in successful) / len(successful)
    
    print(f"\n📈 Average Metrics (Successful Tests):")
    print(f"  ⏱️  Response Time: {avg_time/1000:.1f}s")
    print(f"  📝 Answer Length: {avg_len:.0f} chars")
    print(f"  📋 Checklists: {avg_checklists:.1f}")

# Show failed/partial tests
problematic = [r for r in all_results if r.get("status") in ["failed", "error", "partial"]]
if problematic:
    print(f"\n⚠️  Problematic Tests ({len(problematic)}):")
    for r in problematic:
        q = r['question']
        q_display = q if len(q) <= 50 else q[:47] + "..."
        status = r.get("status").upper()
        reason = r.get("error") or r.get("reason", "Unknown")
        reason_display = reason if len(reason) <= 60 else reason[:57] + "..."
        print(f"  [{status}] {q_display}")
        print(f"           {reason_display}")

# Show a detailed example
print("\n" + "=" * 80)
print("📝 Sample Detailed Response (Tiếng Việt có dấu)")
print("=" * 80)

sample_q = "Có vấn đề gì với văn bản này không?"
print(f"\nCâu hỏi: \"{sample_q}\"")

try:
    response = requests.get(
        f"{BASE_URL}/test/query",
        params={"user_id": "user_demo", "question": sample_q},
        timeout=120
    )
    
    if response.status_code == 200:
        data = response.json()
        answer = data.get("answer") or ""
        
        print(f"\nThông tin:")
        print(f"  Success: {data.get('success')}")
        print(f"  Checklists: {data.get('total_checklist_items')}")
        print(f"  User Chunks: {data.get('total_user_chunks')}")
        print(f"  Knowledge Chunks: {data.get('total_knowledge_chunks')}")
        print(f"  Processing Time: {data.get('processing_time_ms', 0)/1000:.1f}s")
        print(f"  Answer Length: {len(answer)} chars")
        
        print(f"\nTrích đoạn câu trả lời (800 ký tự đầu):")
        print("-" * 80)
        preview = answer[:800] if answer else "Không có câu trả lời"
        # Clean up for display
        lines = preview.split('\n')
        for line in lines[:25]:  # Show first 25 lines
            print(line)
        if len(answer) > 800:
            print("...")
        print("-" * 80)
    else:
        print(f"Failed: HTTP {response.status_code}")
except Exception as e:
    print(f"Error: {e}")

print("\n" + "=" * 80)
print("✅ TEST HOÀN TẤT")
print("=" * 80)
