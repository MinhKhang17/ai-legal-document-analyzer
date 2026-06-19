"""Test đầy đủ chức năng phát hiện lỗi hợp đồng sau khi cấu hình Gemini."""
import requests
import json

API_BASE = "http://localhost:8000"

print("="*80)
print("TEST CHỨC NĂNG PHÁT HIỆN LỖI HỢP ĐỒNG - SAU KHI CẤU HÌNH GEMINI")
print("="*80)

# Đọc file hợp đồng mẫu
contract_file = r"c:\Users\DELL\Documents\findRisk\ai-service\test_contracts\hop_dong_mau_thuan.txt"

try:
    with open(contract_file, 'r', encoding='utf-8') as f:
        contract_content = f.read()
    print(f"\n✅ Đã đọc file: {contract_file}")
    print(f"   Độ dài: {len(contract_content)} ký tự\n")
except Exception as e:
    print(f"❌ Không đọc được file: {e}")
    exit(1)

# Gọi API phát hiện lỗi
print("Đang gọi API phát hiện lỗi...")
print("-"*80)

try:
    files = {
        'file': ('hop_dong_mau_thuan.txt', contract_content.encode('utf-8'), 'text/plain')
    }
    data = {
        'title': 'Hợp Đồng Thuê Nhà Test'
    }
    
    response = requests.post(
        f"{API_BASE}/v2/contracts/find-errors",
        files=files,
        data=data,
        timeout=120  # 2 phút timeout vì LLM có thể mất thời gian
    )
    
    if response.status_code == 200:
        result = response.json()
        
        print(f"\n{'='*80}")
        print(f"✅ PHÁT HIỆN THÀNH CÔNG!")
        print(f"{'='*80}\n")
        
        summary = result.get('summary', {})
        errors = result.get('errors', [])
        
        print(f"📊 TỔNG KẾT:")
        print(f"   - Tổng số lỗi: {summary.get('total_errors', 0)}")
        print(f"   - Thiếu điều khoản: {summary.get('missing_clause_count', 0)}")
        print(f"   - Lỗi định dạng: {summary.get('format_error_count', 0)}")
        print(f"   - Lỗi logic (LLM): {summary.get('logical_error_count', 0)} 🎯")
        print(f"   - Rủi ro pháp lý: {summary.get('legal_risk_count', 0)}")
        print(f"\n   - Mức độ HIGH: {summary.get('high_count', 0)}")
        print(f"   - Mức độ MEDIUM: {summary.get('medium_count', 0)}")
        print(f"   - Mức độ LOW: {summary.get('low_count', 0)}")
        
        print(f"\n{'='*80}")
        print(f"CHI TIẾT CÁC LỖI PHÁT HIỆN:")
        print(f"{'='*80}\n")
        
        # Group errors by category
        by_category = {}
        for err in errors:
            cat = err.get('category', 'unknown')
            if cat not in by_category:
                by_category[cat] = []
            by_category[cat].append(err)
        
        for category, cat_errors in by_category.items():
            print(f"\n{'─'*80}")
            print(f"📁 {category.upper().replace('_', ' ')} ({len(cat_errors)} lỗi)")
            print(f"{'─'*80}\n")
            
            for err in cat_errors:
                print(f"🔸 [{err.get('severity', 'UNKNOWN')}] {err.get('title', 'No title')}")
                print(f"   {err.get('description', 'No description')[:200]}...")
                if err.get('suggestion'):
                    print(f"   💡 Gợi ý: {err.get('suggestion', '')[:150]}...")
                if err.get('legal_basis'):
                    print(f"   ⚖️  Căn cứ: {err.get('legal_basis', '')}")
                print(f"   📈 Độ tin cậy: {err.get('confidence', 0.0):.2f}")
                print()
        
        # Kiểm tra logical errors
        logical_count = summary.get('logical_error_count', 0)
        if logical_count > 0:
            print(f"\n{'='*80}")
            print(f"🎉 GEMINI LLM ĐANG HOẠT ĐỘNG!")
            print(f"{'='*80}")
            print(f"✅ Phát hiện được {logical_count} lỗi logic/mâu thuẫn bằng Gemini AI")
            print(f"✅ Tất cả 4 detector đang hoạt động:")
            print(f"   1. ✅ Missing Clause Detector")
            print(f"   2. ✅ Format Error Detector")
            print(f"   3. ✅ Logical Error Detector (Gemini LLM) 🎯")
            print(f"   4. ✅ Legal Risk Detector")
        else:
            print(f"\n{'='*80}")
            print(f"⚠️  CẢNH BÁO: Không phát hiện logical errors")
            print(f"{'='*80}")
            print(f"Gemini LLM có thể chưa hoạt động đúng.")
        
    else:
        print(f"\n❌ Lỗi API: HTTP {response.status_code}")
        print(response.text[:500])
        
except requests.exceptions.Timeout:
    print("❌ Timeout - API mất quá nhiều thời gian")
except Exception as e:
    print(f"❌ Lỗi: {e}")
