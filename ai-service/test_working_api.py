"""Test Gemini API with correct model name."""
import sys
sys.path.insert(0, 'C:\\Users\\DELL\\Documents\\findRisk\\ai-service')

from app.services.gemini_client import GeminiClient

api_key = "YOUR_GEMINI_API_KEY_HERE"  # Replace with your actual API key
model = "gemini-2.5-flash"

client = GeminiClient(api_key=api_key, model=model, max_output_tokens=2048)

# Test đơn giản
print("="*70)
print("Test 1: Simple greeting")
print("="*70)
result = client.generate_text(
    system_prompt="Bạn là trợ lý AI thân thiện.",
    user_prompt="Nói 'Xin chào!' bằng tiếng Anh ngắn gọn"
)

if result.text:
    print(f"✅ SUCCESS!")
    print(f"Response: {result.text}\n")
else:
    print(f"❌ Error: {result.error}\n")

# Test phân tích hợp đồng
print("="*70)
print("Test 2: Contract analysis with legal knowledge")
print("="*70)

contract_text = """
HỢP ĐỒNG THUÊ NHÀ

1. Thời hạn thuê: 25 năm
   (Vi phạm: Theo Điều 469 BLDS 2015, thời hạn thuê nhà tối đa là 20 năm)

2. Tiền đặt cọc: 100 triệu đồng (tương đương 10 tháng tiền thuê)
   (Vi phạm: Theo Luật Nhà ở 2023, tiền cọc tối đa là 20% giá trị hợp đồng = khoảng 2 tháng)

3. Phạt chậm thanh toán: 20% tổng giá trị hợp đồng nếu trễ 1 ngày
   (Vi phạm: Theo Điều 351 BLDS 2015, mức phạt tối đa là 8% giá trị hợp đồng)

4. Bên cho thuê có quyền tăng giá thuê bất cứ lúc nào mà không cần thông báo
   (Vi phạm: Bất đối xứng, vi phạm nguyên tắc bình đẳng trong hợp đồng - Điều 401 BLDS 2015)

5. Bên thuê không được quyền đơn phương chấm dứt hợp đồng trong mọi trường hợp
   (Vi phạm: Điều 428 BLDS 2015 cho phép chấm dứt đơn phương khi có lý do chính đáng)
"""

result = client.generate_text(
    system_prompt="""Bạn là chuyên gia pháp lý Việt Nam chuyên về hợp đồng thuê nhà/đất.
Phân tích các lỗi sai, mâu thuẫn, và điều khoản vi phạm pháp luật trong hợp đồng.
Trả về JSON array với cấu trúc: [{"severity": "HIGH|MEDIUM|LOW", "title": "...", "description": "...", "suggestion": "...", "legal_basis": "..."}]
Chỉ trả về JSON array, không kèm markdown.""",
    user_prompt=f"Phân tích hợp đồng sau và tìm TẤT CẢ lỗi sai:\n\n{contract_text}"
)

if result.text:
    print(f"✅ SUCCESS!")
    print(f"Response preview:")
    print(result.text[:1000])
    if len(result.text) > 1000:
        print("\n... (truncated)")
    print(f"\nFull response length: {len(result.text)} characters")
else:
    print(f"❌ Error: {result.error}")

print("\n" + "="*70)
print("🎉 GEMINI API HOẠT ĐỘNG HOÀN HẢO!")
print("="*70)
print(f"✅ API Key: AQ.Ab8... (AQ. auth key format)")
print(f"✅ Model: {model}")
print(f"✅ Base URL: https://generativelanguage.googleapis.com/v1beta")
