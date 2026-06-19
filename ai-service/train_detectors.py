"""Train và cải thiện các detector không cần LLM."""
import sys
sys.path.insert(0, 'C:\\Users\\DELL\\Documents\\findRisk\\ai-service')

from pathlib import Path
from app.services.legal_rag.contract_error_detector import (
    ContractErrorDetectionService,
    MissingClauseDetector,
    FormatErrorDetector,
    LegalRiskDetector
)
from app.services.legal_rag.pipeline import ContractClauseExtractor
from app.services.loader.text_document_loader import TextDocumentLoader

print("="*70)
print("TRAINING AI - PHÁT HIỆN LỖI HỢP ĐỒNG")
print("="*70)

# Load hợp đồng test
test_file = Path("test_contracts/hop_dong_mau_thuan.txt")
loader = TextDocumentLoader()
doc = loader.load(test_file)

# Extract clauses
extractor = ContractClauseExtractor()
clauses = extractor.extract(doc)

full_text = "\n".join([block.text for block in doc.blocks])

print(f"\n📄 Hợp đồng: {test_file.name}")
print(f"📋 Số điều khoản: {len(clauses)}")
print(f"📝 Độ dài văn bản: {len(full_text)} ký tự")

# Test từng detector
print("\n" + "="*70)
print("1. MISSING CLAUSE DETECTOR")
print("="*70)
missing_detector = MissingClauseDetector()
missing_errors = missing_detector.detect(full_text, clauses)
print(f"✅ Phát hiện: {len(missing_errors)} lỗi thiếu điều khoản")
for error in missing_errors[:3]:
    print(f"  - [{error.severity}] {error.title}")

print("\n" + "="*70)
print("2. FORMAT ERROR DETECTOR")
print("="*70)
format_detector = FormatErrorDetector()
format_errors = format_detector.detect(full_text, clauses)
print(f"✅ Phát hiện: {len(format_errors)} lỗi hình thức")
for error in format_errors:
    print(f"  - [{error.severity}] {error.title}")

print("\n" + "="*70)
print("3. LEGAL RISK DETECTOR")
print("="*70)
risk_detector = LegalRiskDetector()
risk_errors = risk_detector.detect(full_text, clauses)
print(f"✅ Phát hiện: {len(risk_errors)} rủi ro pháp lý")
for error in risk_errors[:5]:
    print(f"  - [{error.severity}] {error.title} (confidence: {error.confidence:.2f})")

print("\n" + "="*70)
print("TỔNG KẾT")
print("="*70)
total = len(missing_errors) + len(format_errors) + len(risk_errors)
print(f"🎯 Tổng cộng: {total} lỗi được phát hiện")
print(f"   - Thiếu điều khoản: {len(missing_errors)}")
print(f"   - Lỗi hình thức: {len(format_errors)}")
print(f"   - Rủi ro pháp lý: {len(risk_errors)}")
print(f"   - Lỗi logic (cần Gemini): 0 (API key chưa hợp lệ)")

print("\n💡 Để phát hiện thêm lỗi logic/mâu thuẫn:")
print("   1. Lấy API key mới từ: https://aistudio.google.com/app/apikey")
print("   2. Cập nhật vào file .env: GEMINI_API_KEY=...")
print("   3. Restart AI service")
