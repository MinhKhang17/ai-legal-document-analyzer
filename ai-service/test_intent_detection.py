"""Intent detection and completeness checker evaluation script.

Loads the comprehensive dataset from data/intent_training_data.json and evaluates
the accuracy and F1-score of the keyword-based classifier.
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
    
    # Khởi tạo list để lưu nhãn thực tế và nhãn dự đoán để tính toán F1
    y_true = []
    y_pred = []

    for idx, ex in enumerate(examples, start=1):
        question = ex["question"]
        expected = ex["expected_intent"]
        has_chunks = ex.get("has_user_chunks", False)
        
        result = detect_intent(question, has_user_chunks=has_chunks, has_knowledge_chunks=True)
        predicted = result.intent.value
        
        y_true.append(expected)
        y_pred.append(predicted)

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

        # In ra 15 trường hợp đầu tiên hoặc các trường hợp đoán sai để phân tích nhanh
        if idx <= 15 or predicted != expected:
            print(f"  {status} [{idx:3d}] '{question[:45]:45s}' -> {predicted:30s} (expected: {expected})")

    total = len(examples)
    accuracy = (passed / total) * 100
    
    # ─── PHẦN TÍNH TOÁN F1-SCORE THỦ CÔNG KHÔNG DÙNG THƯ VIỆN NGOÀI ─────────
    all_classes = sorted(list(set(y_true + y_pred)))
    class_metrics = {}

    for c in all_classes:
        # Tính True Positive, False Positive, False Negative cho từng class (intent)
        tp = sum(1 for t, p in zip(y_true, y_pred) if t == c and p == c)
        fp = sum(1 for t, p in zip(y_true, y_pred) if t != c and p == c)
        fn = sum(1 for t, p in zip(y_true, y_pred) if t == c and p != c)
        
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
        
        class_metrics[c] = {
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "support": sum(1 for t in y_true if t == c)
        }

    # Tính toán chỉ số trung bình (Macro average)
    macro_precision = sum(m["precision"] for m in class_metrics.values()) / len(all_classes) if all_classes else 0.0
    macro_recall = sum(m["recall"] for m in class_metrics.values()) / len(all_classes) if all_classes else 0.0
    macro_f1 = sum(m["f1"] for m in class_metrics.values()) / len(all_classes) if all_classes else 0.0

    print("-" * 80)
    print("📊 BÁO CÁO KẾT QUẢ ĐÁNH GIÁ CHẤT LƯỢNG:")
    print(f"  - Accuracy (Độ chính xác tổng quan): {accuracy:.2f}%")
    print(f"  - Macro Precision (Độ chuẩn xác trung bình): {macro_precision * 100:.2f}%")
    print(f"  - Macro Recall (Độ bao phủ trung bình): {macro_recall * 100:.2f}%")
    print(f"  - Macro F1-Score (F1 trung bình): {macro_f1 * 100:.2f}%  👈 (Độ đo chính)")
    print("-" * 80)
    
    # In bảng thống kê chi tiết cho từng loại ý định (Intent Class)
    print("📝 CHI TIẾT ĐỘ ĐO CHO TỪNG LOẠI Ý ĐỊNH:")
    print(f"{'Intent Class':<35} | {'Precision':<10} | {'Recall':<10} | {'F1-Score':<10} | {'Support':<8}")
    print("-" * 79)
    for cls, m in class_metrics.items():
        print(f"{cls[:35]:<35} | {m['precision']*100:8.2f}% | {m['recall']*100:8.2f}% | {m['f1']*100:8.2f}% | {m['support']:<8}")
    print("-" * 80)

    if failed_cases:
        print(f"\n❌ CÁC TRƯỜNG HỢP PHÂN LOẠI SAI ({len(failed_cases)}):")
        for fc in failed_cases:
            print(f"  - Case #{fc['idx']}: \"{fc['question']}\"")
            print(f"    Expected : {fc['expected']}")
            print(f"    Predicted: {fc['predicted']}")
            print(f"    Chunks   : {fc['has_chunks']}")
            print(f"    Notes    : {fc['notes']}\n")
    else:
        print("\n🎉 TẤT CẢ CÁC CASE ĐỀU PHÂN LOẠI CHÍNH XÁC!")

    # ─── completeness checker test suite ─────────────────────────────────────
    print("\n" + "=" * 80)
    print("📋 COMPLETENESS CHECK TEST (KIỂM TRA ĐỘ ĐẦY ĐỦ THÔNG TIN)")
    print("=" * 80)

    comp_tests = [
        (LegalQueryIntent.CONTRACT_RISK_ANALYSIS, True, "Kiểm tra hợp đồng", True),
        (LegalQueryIntent.CONTRACT_RISK_ANALYSIS, False, "Kiểm tra hợp đồng", False),
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
