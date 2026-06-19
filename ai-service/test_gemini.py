"""Test Gemini API."""
import sys
sys.path.insert(0, 'C:\\Users\\DELL\\Documents\\findRisk\\ai-service')

from app.services.gemini_client import GeminiClient

api_key = "YOUR_GEMINI_API_KEY_HERE"  # Replace with your actual API key
model = "gemini-1.5-flash"

client = GeminiClient(api_key=api_key, model=model)

# Test đơn giản
print("Testing Gemini API...")
result = client.generate_text(
    system_prompt="Bạn là trợ lý AI.",
    user_prompt="Xin chào, hãy nói 'Hello' bằng tiếng Việt"
)

if result.text:
    print(f"✅ Gemini API hoạt động!")
    print(f"Response: {result.text}")
else:
    print(f"❌ Gemini API lỗi: {result.error}")

# Test phân tích hợp đồng
print("\n" + "="*70)
print("Testing contract analysis...")

contract_text = """
HỢP ĐỒNG THUÊ NHÀ
Thời hạn: 25 năm (vi phạm luật - tối đa 20 năm)
Tiền cọc: 100 triệu (10 tháng thuê) - vi phạm luật (tối đa 20% = 2 tháng)
Phạt: 20% tổng giá trị hợp đồng nếu trễ 1 ngày - vi phạm luật (tối đa 8%)
Bên cho thuê có quyền tăng giá bất cứ lúc nào - bất đối xứng, vi phạm luật
"""

result = client.generate_text(
    system_prompt="Bạn là chuyên gia pháp lý. Tìm tất cả lỗi trong hợp đồng này.",
    user_prompt=f"Phân tích hợp đồng:\n{contract_text}\n\nTrả về JSON array các lỗi."
)

if result.text:
    print(f"✅ Contract analysis response:")
    print(result.text[:500])
else:
    print(f"❌ Error: {result.error}")
