"""Intent detection and completeness checker evaluation script.

Loads the comprehensive dataset from data/intent_training_data.json and evaluates
the accuracy of the keyword-based classifier.
"""
import sys
import json
import os

sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, '.')

from app.services.intent_detector import detect_intent
from app.services.completeness_checker import check_completeness
from app.models.intent_enums import LegalQueryIntent

def run_evaluation():
    print("=" * 80)
    print("🚀 EVALUATING INTENT DETECTOR")
    print("=" * 80)

    dataset_path = os.path.join("data", "intent_training_data.json")
    if not os.path.exists(dataset_path):
        print(f"❌ Dataset not found at: {dataset_path}")
        return

    with open(dataset_path, "r", encoding="utf-8") as f:
        examples = json.load(f)

    print(f"Loaded {len(examples)} evaluation examples.")
    print("-" * 80)

    passed = 0
    failed_cases = []

    for idx, ex in enumerate(examples, start=1):
        question = ex["question"]
        expected = ex["expected_intent"]
        has_chunks = ex.get("has_user_chunks", False)
        
        result = detect_intent(question, has_user_chunks=has_chunks, has_knowledge_chunks=True)
        predicted = result.intent.value

        if predicted == expected:
            passed += 1
            status = "✅"
        else:
            status = "❌"
            failed_cases.append({
                "idx": idx,
                "question": question,
                "expected": expected,
                "predicted": predicted,
                "has_chunks": has_chunks,
                "notes": ex.get("notes", "")
            })

        # print status of first few and failed ones
        if idx <= 15 or predicted != expected:
            print(f"  {status} [{idx:3d}] '{question[:45]:45s}' -> {predicted:30s} (expected: {expected})")

    total = len(examples)
    accuracy = (passed / total) * 100
    print("-" * 80)
    print(f"📊 SUMMARY: Passed {passed}/{total} cases ({accuracy:.2f}% accuracy)")
    print("-" * 80)

    if failed_cases:
        print(f"\n❌ FAILED CASES ({len(failed_cases)}):")
        for fc in failed_cases:
            print(f"  - Case #{fc['idx']}: \"{fc['question']}\"")
            print(f"    Expected : {fc['expected']}")
            print(f"    Predicted: {fc['predicted']}")
            print(f"    Chunks   : {fc['has_chunks']}")
            print(f"    Notes    : {fc['notes']}\n")
    else:
        print("\n🎉 ALL CASES PASSED PERFECTLY!")

    # ─── completeness checker test suite ─────────────────────────────────────
    print("\n" + "=" * 80)
    print("📋 COMPLETENESS CHECK TEST")
    print("=" * 80)

    comp_tests = [
        (LegalQueryIntent.FULL_CONTRACT_REVIEW, True, "Kiểm tra hợp đồng", True),
        (LegalQueryIntent.FULL_CONTRACT_REVIEW, False, "Kiểm tra hợp đồng", False),
        (LegalQueryIntent.SIGNING_DECISION_SUPPORT, True, "Tôi là bên B, có nên ký?", True),
        (LegalQueryIntent.SIGNING_DECISION_SUPPORT, True, "Có nên ký không?", False),
        (LegalQueryIntent.CLAUSE_ANALYSIS, True, "Phân tích phần đặt cọc", True),
        (LegalQueryIntent.CLAUSE_ANALYSIS, True, "Phân tích điều khoản", False),
    ]

    comp_passed = 0
    for intent, has_chunks, question, expected_complete in comp_tests:
        result = check_completeness(intent, has_user_chunks=has_chunks, question=question)
        status = "✅" if result.is_complete == expected_complete else "❌"
        if result.is_complete == expected_complete:
            comp_passed += 1
        missing_str = ", ".join(result.missing_items) if result.missing_items else "none"
        print(f"  {status} {intent.value:30s} chunks={has_chunks} -> complete={result.is_complete} (expected: {expected_complete}) missing=[{missing_str}]")

    print(f"\nCompleteness results: {comp_passed}/{len(comp_tests)} passed")

if __name__ == "__main__":
    run_evaluation()
