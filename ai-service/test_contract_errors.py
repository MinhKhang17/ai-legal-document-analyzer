"""Test script for contract error detection API."""
import requests

# Test with existing test contract
with open("test_contracts/hop_dong_mau_thuan.txt", "rb") as f:
    files = {"file": ("hop_dong_mau_thuan.txt", f, "text/plain")}
    data = {"title": "Hợp đồng test mẫu thuẫn"}
    
    response = requests.post(
        "http://localhost:8001/v2/contracts/find-errors",
        files=files,
        data=data,
        timeout=60
    )

print(f"Status: {response.status_code}")
print(f"\nResponse:\n{response.text[:2000]}")

if response.status_code == 200:
    result = response.json()
    print(f"\n{'='*70}")
    print(f"✅ TEST TÌM LỖI HỢP ĐỒNG THÀNH CÔNG!")
    print(f"{'='*70}")
    print(f"Tên file: {result['filename']}")
    print(f"Tổng điều khoản: {result['total_clauses']}")
    print(f"Tổng lỗi tìm thấy: {result['summary']['total_errors']}")
    print(f"  - Thiếu điều khoản: {result['summary']['missing_clause_count']}")
    print(f"  - Lỗi hình thức: {result['summary']['format_error_count']}")
    print(f"  - Lỗi logic: {result['summary']['logical_error_count']}")
    print(f"  - Rủi ro pháp lý: {result['summary']['legal_risk_count']}")
    print(f"\nMức độ nghiêm trọng:")
    print(f"  - HIGH: {result['summary']['high_count']}")
    print(f"  - MEDIUM: {result['summary']['medium_count']}")
    print(f"  - LOW: {result['summary']['low_count']}")
    
    print(f"\n{'='*70}")
    print(f"CHI TIẾT CÁC LỖI:")
    print(f"{'='*70}")
    
    for idx, error in enumerate(result['errors'][:10], 1):
        print(f"\n{idx}. [{error['severity']}] {error['title']}")
        print(f"   Loại: {error['category']}")
        print(f"   Mô tả: {error['description'][:200]}...")
        if error['suggestion']:
            print(f"   Đề xuất: {error['suggestion'][:150]}...")
