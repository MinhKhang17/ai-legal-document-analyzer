"""Quick test with a few vague questions."""
import sys
import io
import requests
import json
import time

# Fix encoding
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8000"

TEST_QUESTIONS = [
    "Co van de gi voi van ban nay khong?",
    "Ra soat hop dong nay",
    "Co rui ro gi khong?",
    "Hop dong nay co hop le khong?",
    "Danh gia van ban nay",
]

print("=" * 80)
print("QUICK TEST - Vague Question Handling")
print("=" * 80)

for i, question in enumerate(TEST_QUESTIONS, 1):
    print(f"\n[{i}/{len(TEST_QUESTIONS)}] Testing: \"{question}\"")
    
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
            checklists = data.get("total_checklist_items", 0)
            chunks = data.get("total_user_chunks", 0)
            answer = data.get("answer") or ""
            answer_len = len(answer)
            error = data.get("error_message")
            
            if error:
                print(f"   FAILED: {error[:100]}")
            else:
                status = "SUCCESS" if success and answer_len > 100 else "PARTIAL"
                print(f"   Status: {status}")
                print(f"   Time: {elapsed:.0f}ms")
                print(f"   Checklists: {checklists}, Chunks: {chunks}")
                print(f"   Answer: {answer_len} chars")
                
                if answer_len > 0:
                    preview = answer[:150].replace("\n", " ")
                    print(f"   Preview: {preview}...")
        else:
            print(f"   FAILED: Status {response.status_code}")
    
    except Exception as e:
        print(f"   ERROR: {e}")
    
    time.sleep(1)  # Small delay

print("\n" + "=" * 80)
print("TEST COMPLETED")
print("=" * 80)
