"""Comprehensive test for vague question handling - simplified version."""
import sys
import io
import requests
import json
import time

# Fix encoding
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8000"

# Carefully selected test questions covering different patterns
TEST_QUESTIONS = {
    "Co dau voi van ban": [
        "Co van de gi voi van ban nay khong?",
        "Co van de nao khong?",
        "Co diem nao can luu y khong?",
    ],
    "Co dau voi rui ro": [
        "Co rui ro gi khong?",
        "Co thieu sot gi khong?",
        "Co loi gi khong?",
    ],
    "Yeu cau ra soat": [
        "Ra soat van ban nay",
        "Kiem tra hop dong nay",
        "Danh gia van ban nay",
    ],
    "Cau hoi hop le": [
        "Van ban nay co hop le khong?",
        "Hop dong nay co on khong?",
        "Co the ky duoc khong?",
    ],
    "Cau hoi ngan": [
        "Co gi sai",
        "Review di",
        "Check giup",
    ],
    "Tieng Anh": [
        "What's wrong with this document?",
        "Any issues?",
        "Check this contract",
    ],
}

print("=" * 80)
print("COMPREHENSIVE TEST - Vague Question Handling")
print("Testing AI's ability to handle vague/general questions")
print("=" * 80)

# Health check
print("\n[1/4] Health Check...")
try:
    r = requests.get(f"{BASE_URL}/health", timeout=5)
    if r.status_code == 200:
        print("   SUCCESS - Service is healthy")
    else:
        print(f"   FAILED - Status {r.status_code}")
        sys.exit(1)
except Exception as e:
    print(f"   FAILED - {e}")
    sys.exit(1)

# Test questions
print("\n[2/4] Testing Questions...")

all_results = []
total_questions = sum(len(qs) for qs in TEST_QUESTIONS.values())
current = 0

for category, questions in TEST_QUESTIONS.items():
    print(f"\n  Category: {category}")
    
    for question in questions:
        current += 1
        print(f"    [{current}/{total_questions}] {question[:40]}... ", end="", flush=True)
        
        try:
            start = time.time()
            response = requests.get(
                f"{BASE_URL}/test/query",
                params={"user_id": "user_demo", "question": question},
                timeout=90
            )
            elapsed = (time.time() - start) * 1000
            
            if response.status_code == 200:
                data = response.json()
                success = data.get("success")
                answer = data.get("answer") or ""
                error = data.get("error_message")
                checklists = data.get("total_checklist_items", 0)
                
                if success and not error and len(answer) > 100:
                    print(f"SUCCESS ({elapsed:.0f}ms, {len(answer)} chars)")
                    all_results.append({
                        "question": question,
                        "status": "success",
                        "time": elapsed,
                        "answer_len": len(answer),
                        "checklists": checklists
                    })
                else:
                    print(f"PARTIAL (answer too short or error)")
                    all_results.append({
                        "question": question,
                        "status": "partial",
                        "error": error
                    })
            else:
                print(f"FAILED (HTTP {response.status_code})")
                all_results.append({
                    "question": question,
                    "status": "failed",
                    "error": f"HTTP {response.status_code}"
                })
        
        except Exception as e:
            print(f"ERROR ({str(e)[:30]})")
            all_results.append({
                "question": question,
                "status": "error",
                "error": str(e)
            })
        
        time.sleep(0.5)  # Small delay

# Summary
print("\n[3/4] Test Summary...")

success_count = sum(1 for r in all_results if r.get("status") == "success")
partial_count = sum(1 for r in all_results if r.get("status") == "partial")
failed_count = len(all_results) - success_count - partial_count

print(f"\n  Total Tests: {len(all_results)}")
print(f"  SUCCESS: {success_count} ({success_count/len(all_results)*100:.1f}%)")
print(f"  PARTIAL: {partial_count} ({partial_count/len(all_results)*100:.1f}%)")
print(f"  FAILED:  {failed_count} ({failed_count/len(all_results)*100:.1f}%)")

# Calculate averages for successful tests
successful = [r for r in all_results if r.get("status") == "success"]
if successful:
    avg_time = sum(r.get("time", 0) for r in successful) / len(successful)
    avg_len = sum(r.get("answer_len", 0) for r in successful) / len(successful)
    avg_checklists = sum(r.get("checklists", 0) for r in successful) / len(successful)
    
    print(f"\n  Average Metrics (Successful Tests):")
    print(f"    Response Time: {avg_time:.0f}ms")
    print(f"    Answer Length: {avg_len:.0f} chars")
    print(f"    Checklists: {avg_checklists:.1f}")

# Show failed tests
failed_tests = [r for r in all_results if r.get("status") in ["failed", "error"]]
if failed_tests:
    print(f"\n  Failed Tests ({len(failed_tests)}):")
    for r in failed_tests:
        print(f"    - {r['question'][:50]}")
        print(f"      Error: {r.get('error', 'Unknown')}")

# Detailed example
print("\n[4/4] Sample Detailed Response...")
sample_q = "Co van de gi voi van ban nay khong?"
print(f"\nQuestion: \"{sample_q}\"")

try:
    response = requests.get(
        f"{BASE_URL}/test/query",
        params={"user_id": "user_demo", "question": sample_q},
        timeout=90
    )
    
    if response.status_code == 200:
        data = response.json()
        answer = data.get("answer") or ""
        
        print(f"\nMetadata:")
        print(f"  Success: {data.get('success')}")
        print(f"  Checklists: {data.get('total_checklist_items')}")
        print(f"  Chunks: {data.get('total_user_chunks')}")
        print(f"  Processing Time: {data.get('processing_time_ms', 0):.0f}ms")
        print(f"  Answer Length: {len(answer)} chars")
        
        print(f"\nAnswer Preview (first 500 chars):")
        print("-" * 80)
        print(answer[:500].replace("\n", "\n  "))
        print("-" * 80)
    else:
        print(f"Failed: HTTP {response.status_code}")
except Exception as e:
    print(f"Error: {e}")

print("\n" + "=" * 80)
print("TEST COMPLETED")
print("=" * 80)
