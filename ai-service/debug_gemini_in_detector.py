"""Debug Gemini API trong LogicalErrorDetector."""
import sys
import logging
sys.path.insert(0, 'C:\\Users\\DELL\\Documents\\findRisk\\ai-service')

# Enable logging
logging.basicConfig(level=logging.INFO, format='%(levelname)s - %(message)s')

from app.services.legal_rag.contract_error_detector import LogicalErrorDetector

contract_text = """
HỢP ĐỒNG THUÊ NHÀ

Điều 1. Thời hạn thuê
Thời hạn thuê là 25 năm, từ ngày 01/01/2024 đến ngày 01/01/2049.
(Vi phạm: Theo Điều 469 BLDS 2015, thời hạn thuê tài sản tối đa là 20 năm)

Điều 2. Tiền đặt cọc
Bên thuê phải đặt cọc 100 triệu đồng (tương đương 10 tháng tiền thuê).
(Vi phạm: Theo Luật Nhà ở 2023, tiền cọc không được vượt quá 20% giá trị hợp đồng = khoảng 2 tháng thuê)

Điều 3. Phạt chậm thanh toán  
Nếu bên thuê trễ thanh toán 1 ngày, sẽ bị phạt 20% tổng giá trị hợp đồng.
(Vi phạm: Theo Điều 351 BLDS 2015, mức phạt vi phạm tối đa là 8% giá trị hợp đồng)

Điều 4. Điều chỉnh giá thuê
Bên cho thuê có quyền tăng giá thuê bất cứ lúc nào mà không cần thông báo trước.
(Vi phạm: Bất đối xứng nghiêm trọng, vi phạm nguyên tắc bình đẳng - Điều 401 BLDS 2015)

Điều 5. Chấm dứt hợp đồng  
Bên thuê KHÔNG được quyền đơn phương chấm dứt hợp đồng trong MỌI trường hợp.
(Vi phạm: Điều 428 BLDS 2015 cho phép đơn phương chấm dứt khi có lý do chính đáng)
"""

print("="*80)
print("DEBUG LOGICAL ERROR DETECTOR")
print("="*80)

detector = LogicalErrorDetector()

print(f"\n✅ Detector initialized")
print(f"   - Is available: {detector.is_available}")
print(f"   - Client: {detector._client}")

if not detector.is_available:
    print("\n❌ Gemini LLM not configured!")
    sys.exit(1)

print(f"\n🔍 Detecting logical errors...")
errors = detector.detect(contract_text, clauses=[])

print(f"\n📊 Results:")
print(f"   - Errors found: {len(errors)}")

if errors:
    print(f"\n✅ SUCCESS! Logical errors detected:\n")
    for i, err in enumerate(errors, 1):
        print(f"{i}. [{err.severity}] {err.title}")
        print(f"   {err.description[:150]}...")
        print()
else:
    print(f"\n⚠️  WARNING: No logical errors detected!")
    print("   This contract clearly has multiple legal violations.")
    print("   Gemini might not be working properly.")
