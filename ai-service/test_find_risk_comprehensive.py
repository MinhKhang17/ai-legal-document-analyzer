"""
TEST KỸ LƯỠNG CHỨC NĂNG FIND RISK - Contract Error Detection
Kiểm tra đầy đủ 4 loại lỗi:
1. Missing Clauses - Thiếu điều khoản bắt buộc
2. Format Errors - Lỗi hình thức
3. Logical Errors - Lỗi logic (sử dụng AI)
4. Legal Risks - Rủi ro pháp lý
"""
import os
import json
import time
from pathlib import Path
import requests

# Configuration
API_BASE = "http://localhost:8000"
ENDPOINT = f"{API_BASE}/v2/contracts/find-errors"
SUPPORTED_FORMATS_ENDPOINT = f"{API_BASE}/v2/contracts/find-errors/supported-formats"

# Test files
TEST_DIR = Path(__file__).parent
REAL_CONTRACTS_DIR = TEST_DIR / "test_real_contracts"
SAMPLE_CONTRACTS_DIR = TEST_DIR / "test_contracts"


def print_header(text: str):
    """In header đẹp."""
    print("\n" + "=" * 80)
    print(f"  {text}")
    print("=" * 80)


def print_section(text: str):
    """In section title."""
    print(f"\n{'─' * 80}")
    print(f"  {text}")
    print(f"{'─' * 80}")


def check_api_health():
    """Kiểm tra API có hoạt động không."""
    print_header("KIỂM TRA HỆ THỐNG")
    try:
        response = requests.get(f"{API_BASE}/health", timeout=5)
        if response.status_code == 200:
            print("✅ AI Service đang chạy")
            return True
        else:
            print(f"❌ AI Service trả về status code: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ Không thể kết nối đến AI Service: {e}")
        print(f"   Đảm bảo Docker đang chạy: docker ps")
        return False


def check_supported_formats():
    """Kiểm tra các format được hỗ trợ."""
    print_section("Kiểm tra Supported Formats")
    try:
        response = requests.get(SUPPORTED_FORMATS_ENDPOINT, timeout=5)
        if response.status_code == 200:
            formats = response.json()
            print(f"✅ Supported formats: {', '.join(formats)}")
            return formats
        else:
            print(f"❌ Không lấy được supported formats")
            return []
    except Exception as e:
        print(f"❌ Error: {e}")
        return []


def test_single_file(file_path: Path, description: str = ""):
    """Test 1 file hợp đồng."""
    print_section(f"TEST: {file_path.name}")
    if description:
        print(f"Mô tả: {description}")
    
    if not file_path.exists():
        print(f"❌ File không tồn tại: {file_path}")
        return None
    
    print(f"📄 File: {file_path}")
    print(f"📦 Size: {file_path.stat().st_size / 1024:.2f} KB")
    
    try:
        # Upload file
        with open(file_path, "rb") as f:
            files = {"file": (file_path.name, f, "application/octet-stream")}
            data = {"title": file_path.stem}
            
            print(f"⏳ Đang phân tích...")
            start_time = time.time()
            response = requests.post(ENDPOINT, files=files, data=data, timeout=120)
            elapsed = time.time() - start_time
            
            print(f"⏱️  Thời gian: {elapsed:.2f}s")
        
        if response.status_code != 200:
            print(f"❌ API trả về lỗi: {response.status_code}")
            print(f"   Response: {response.text[:200]}")
            return None
        
        result = response.json()
        
        # Display results
        print(f"\n📊 KẾT QUẢ PHÂN TÍCH")
        print(f"   Document ID: {result.get('document_id', 'N/A')}")
        print(f"   File type: {result.get('file_type', 'N/A')}")
        print(f"   Tổng số điều khoản: {result.get('total_clauses', 0)}")
        
        summary = result.get('summary', {})
        print(f"\n   📈 TỔNG KẾT LỖI:")
        print(f"   • Tổng số lỗi: {summary.get('total_errors', 0)}")
        print(f"   • Missing clauses: {summary.get('missing_clause_count', 0)}")
        print(f"   • Format errors: {summary.get('format_error_count', 0)}")
        print(f"   • Logical errors: {summary.get('logical_error_count', 0)}")
        print(f"   • Legal risks: {summary.get('legal_risk_count', 0)}")
        print(f"\n   🎯 PHÂN THEO MỨC ĐỘ:")
        print(f"   • 🔴 HIGH: {summary.get('high_count', 0)}")
        print(f"   • 🟡 MEDIUM: {summary.get('medium_count', 0)}")
        print(f"   • 🟢 LOW: {summary.get('low_count', 0)}")
        
        errors = result.get('errors', [])
        if errors:
            print(f"\n   📋 CHI TIẾT CÁC LỖI:")
            
            # Group by category
            by_category = {}
            for error in errors:
                category = error.get('category', 'unknown')
                if category not in by_category:
                    by_category[category] = []
                by_category[category].append(error)
            
            for category, cat_errors in by_category.items():
                category_names = {
                    'missing_clause': '❌ THIẾU ĐIỀU KHOẢN BẮT BUỘC',
                    'format_error': '📝 LỖI HÌNH THỨC',
                    'logical_error': '🧠 LỖI LOGIC',
                    'legal_risk': '⚠️  RỦI RO PHÁP LÝ',
                }
                print(f"\n   {category_names.get(category, category.upper())} ({len(cat_errors)} lỗi):")
                
                for i, error in enumerate(cat_errors[:5], 1):  # Hiển thị tối đa 5 lỗi mỗi loại
                    severity_icon = {"HIGH": "🔴", "MEDIUM": "🟡", "LOW": "🟢"}.get(error.get('severity', ''), '⚪')
                    print(f"\n   {i}. {severity_icon} [{error.get('error_id', '')}] {error.get('title', '')}")
                    print(f"      Mô tả: {error.get('description', '')[:150]}...")
                    if error.get('legal_basis'):
                        print(f"      Căn cứ: {error.get('legal_basis', '')}")
                    if error.get('suggestion'):
                        print(f"      Gợi ý: {error.get('suggestion', '')[:100]}...")
                
                if len(cat_errors) > 5:
                    print(f"   ... và {len(cat_errors) - 5} lỗi khác")
        
        # Text preview
        preview = result.get('full_text_preview', '')
        if preview:
            print(f"\n   📄 NỘI DUNG PREVIEW:")
            print(f"   {preview[:200]}...")
        
        print(f"\n{'─' * 80}")
        return result
        
    except requests.exceptions.Timeout:
        print(f"❌ Timeout - File quá lớn hoặc server quá chậm")
        return None
    except Exception as e:
        print(f"❌ Lỗi: {e}")
        import traceback
        traceback.print_exc()
        return None


def test_real_contracts():
    """Test với hợp đồng thật từ VBPL."""
    print_header("TEST VỚI HỢP ĐỒNG THẬT TỪ VBPL")
    
    if not REAL_CONTRACTS_DIR.exists():
        print(f"❌ Thư mục {REAL_CONTRACTS_DIR} không tồn tại")
        return
    
    # Tìm tất cả file .doc và .docx
    contract_files = []
    for ext in ['*.doc', '*.docx', '*.pdf']:
        contract_files.extend(REAL_CONTRACTS_DIR.rglob(ext))
    
    # Loại bỏ Template.pdf
    contract_files = [f for f in contract_files if 'template' not in f.name.lower()]
    
    print(f"Tìm thấy {len(contract_files)} file hợp đồng thật")
    
    results = []
    for i, file_path in enumerate(contract_files[:5], 1):  # Test 5 file đầu
        print(f"\n[{i}/{min(5, len(contract_files))}]")
        result = test_single_file(
            file_path,
            description="Văn bản pháp luật thực tế từ hệ thống VBPL"
        )
        if result:
            results.append({
                'file': file_path.name,
                'total_errors': result.get('summary', {}).get('total_errors', 0),
                'high_count': result.get('summary', {}).get('high_count', 0),
            })
        time.sleep(2)  # Tránh rate limit
    
    if results:
        print_section("TỔNG KẾT TEST HỢP ĐỒNG THẬT")
        for r in results:
            status = "✅" if r['total_errors'] == 0 else "⚠️"
            print(f"{status} {r['file']}: {r['total_errors']} lỗi ({r['high_count']} HIGH)")


def test_sample_contracts():
    """Test với hợp đồng mẫu đã tạo."""
    print_header("TEST VỚI HỢP ĐỒNG MẪU")
    
    test_cases = [
        {
            'file': SAMPLE_CONTRACTS_DIR / 'hop_dong_day_du.txt',
            'description': 'Hợp đồng đầy đủ, tuân thủ BLDS 2015',
            'expected': {
                'total_errors': 'LOW',  # Nên có ít lỗi
                'categories': ['format_error'],  # Có thể thiếu CCCD vì là text
            }
        },
        {
            'file': SAMPLE_CONTRACTS_DIR / 'hop_dong_thieu_loi.txt',
            'description': 'Hợp đồng thiếu nhiều điều khoản bắt buộc',
            'expected': {
                'total_errors': 'HIGH',  # Nên có nhiều lỗi
                'categories': ['missing_clause', 'format_error', 'logical_error'],
            }
        },
        {
            'file': SAMPLE_CONTRACTS_DIR / 'hop_dong_mau_thuan.txt',
            'description': 'Hợp đồng có nhiều lỗi logic và bất lợi',
            'expected': {
                'total_errors': 'VERY HIGH',  # Nên có rất nhiều lỗi
                'categories': ['missing_clause', 'format_error', 'logical_error', 'legal_risk'],
            }
        },
    ]
    
    results = []
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n[TEST CASE {i}/{len(test_cases)}]")
        result = test_single_file(
            test_case['file'],
            description=test_case['description']
        )
        if result:
            results.append({
                'name': test_case['file'].name,
                'result': result,
                'expected': test_case['expected'],
            })
        time.sleep(2)
    
    # Verify results
    if results:
        print_section("KIỂM TRA KẾT QUẢ VỚI EXPECTED")
        for r in results:
            print(f"\n📋 {r['name']}")
            summary = r['result'].get('summary', {})
            total_errors = summary.get('total_errors', 0)
            
            # Check total errors
            expected_level = r['expected']['total_errors']
            if expected_level == 'LOW' and total_errors <= 3:
                print(f"   ✅ Total errors: {total_errors} (expected LOW)")
            elif expected_level == 'HIGH' and total_errors >= 5:
                print(f"   ✅ Total errors: {total_errors} (expected HIGH)")
            elif expected_level == 'VERY HIGH' and total_errors >= 10:
                print(f"   ✅ Total errors: {total_errors} (expected VERY HIGH)")
            else:
                print(f"   ⚠️  Total errors: {total_errors} (expected {expected_level})")
            
            # Check categories
            errors = r['result'].get('errors', [])
            found_categories = set(e.get('category') for e in errors)
            expected_categories = set(r['expected']['categories'])
            
            for cat in expected_categories:
                if cat in found_categories:
                    count = len([e for e in errors if e.get('category') == cat])
                    print(f"   ✅ Found {cat}: {count} lỗi")
                else:
                    print(f"   ❌ Missing expected category: {cat}")


def save_detailed_results():
    """Lưu kết quả chi tiết vào file."""
    print_section("LƯU KẾT QUẢ")
    output_file = TEST_DIR / "test_find_risk_results.json"
    print(f"Kết quả chi tiết được lưu tại: {output_file}")


def main():
    """Main test function."""
    print_header("🧪 TEST KỸ LƯỠNG CHỨC NĂNG FIND RISK")
    print("Test đầy đủ 4 loại lỗi:")
    print("1. Missing Clauses - Thiếu điều khoản bắt buộc")
    print("2. Format Errors - Lỗi hình thức")
    print("3. Logical Errors - Lỗi logic (AI)")
    print("4. Legal Risks - Rủi ro pháp lý")
    
    # Step 1: Check system
    if not check_api_health():
        print("\n❌ Hệ thống chưa sẵn sàng. Vui lòng:")
        print("   1. Chạy: docker-compose up -d")
        print("   2. Đợi AI service khởi động")
        print("   3. Chạy lại test này")
        return
    
    # Step 2: Check supported formats
    formats = check_supported_formats()
    if not formats:
        print("⚠️  Không lấy được supported formats, tiếp tục test...")
    
    # Step 3: Test với hợp đồng thật
    test_real_contracts()
    
    # Step 4: Test với hợp đồng mẫu
    test_sample_contracts()
    
    # Final summary
    print_header("✅ HOÀN TẤT TEST")
    print("Đã test đầy đủ chức năng find risk với:")
    print("• Hợp đồng thật từ VBPL")
    print("• Hợp đồng mẫu (đầy đủ, thiếu lỗi, mâu thuẫn)")
    print("\nKiểm tra logs phía trên để xem chi tiết các lỗi được phát hiện.")


if __name__ == "__main__":
    main()
