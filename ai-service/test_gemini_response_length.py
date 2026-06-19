"""Test full Gemini response length."""
import sys
sys.path.insert(0, 'C:\\Users\\DELL\\Documents\\findRisk\\ai-service')

from app.services.gemini_client import GeminiClient

api_key = "YOUR_GEMINI_API_KEY_HERE"  # Replace with your actual API key
model = "gemini-2.5-flash"

# Test with different max_output_tokens
for max_tokens in [2048, 4096, 8192]:
    print(f"\n{'='*80}")
    print(f"Testing with max_output_tokens={max_tokens}")
    print(f"{'='*80}")
    
    client = GeminiClient(
        api_key=api_key,
        model=model,
        max_output_tokens=max_tokens
    )
    
    contract = """
HỢP ĐỒNG THUÊ NHÀ

Điều 1. Thời hạn thuê: 25 năm (vi phạm: tối đa 20 năm)
Điều 2. Tiền cọc: 100 triệu (10 tháng) (vi phạm: tối đa 20% = 2 tháng)
Điều 3. Phạt: 20% (vi phạm: tối đa 8%)
Điều 4. Tăng giá bất cứ lúc nào (vi phạm: bất đối xứng)
Điều 5. Không được chấm dứt hợp đồng (vi phạm: luật cho phép)
"""
    
    result = client.generate_text(
        system_prompt="""Bạn là chuyên gia pháp lý. Tìm lỗi trong hợp đồng.
Trả về JSON array: [{"severity":"HIGH|MEDIUM|LOW","title":"...","description":"...","suggestion":"...","clause_reference":"...","legal_basis":"..."}]
Chỉ trả JSON array, không markdown.""",
        user_prompt=f"Phân tích hợp đồng:\n{contract}"
    )
    
    if result.text:
        print(f"✅ Response length: {len(result.text)} characters")
        print(f"   Response preview (first 200 chars):")
        print(f"   {result.text[:200]}")
        print(f"\n   Response end (last 100 chars):")
        print(f"   ...{result.text[-100:]}")
        
        # Check if it's valid JSON
        import json
        try:
            parsed = json.loads(result.text.strip().replace('```json\n', '').replace('\n```', ''))
            print(f"\n✅ Valid JSON! Found {len(parsed)} errors")
        except:
            print(f"\n❌ Invalid JSON - response incomplete or malformed")
    else:
        print(f"❌ Error: {result.error}")
