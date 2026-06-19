"""Script kiểm tra văn bản trong folder là CÒN HIỆU LỰC hay HẾT HIỆU LỰC.

Kiểm tra dựa trên:
- Tên file (có chữ "het hieu luc", "da thay the"...)
- Metadata trong ZIP
- Ngày ban hành vs ngày hiện tại
"""
import sys
import zipfile
import re
from pathlib import Path
from collections import defaultdict
from datetime import datetime

def extract_year_from_filename(filename: str) -> int | None:
    """Trích xuất năm từ tên file."""
    # Pattern: NAM_1990, 2015, QD_2023, etc
    patterns = [
        r'NAM[_\s]*(\d{4})',
        r'[_\s](\d{4})[_\s]',
        r'^(\d{4})',
        r'[_\-](\d{4})\.',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, filename, re.IGNORECASE)
        if match:
            year = int(match.group(1))
            if 1945 <= year <= 2030:  # Valid range
                return year
    return None

def check_status_keywords(filename: str) -> str | None:
    """Kiểm tra keywords trong tên file."""
    filename_lower = filename.lower()
    
    # HẾT HIỆU LỰC keywords
    expired_keywords = [
        'het hieu luc', 'da thay the', 'bi thay the',
        'da huy bo', 'bi huy bo', 'khong con hieu luc',
        'expired', 'replaced', 'superseded', 'revoked',
    ]
    
    for keyword in expired_keywords:
        if keyword.replace(' ', '') in filename_lower.replace(' ', '').replace('_', ''):
            return 'EXPIRED'
    
    # CÒN HIỆU LỰC keywords
    active_keywords = [
        'con hieu luc', 'dang ap dung', 'hieu luc',
        'active', 'current', 'valid',
    ]
    
    for keyword in active_keywords:
        if keyword.replace(' ', '') in filename_lower.replace(' ', '').replace('_', ''):
            return 'ACTIVE'
    
    return None

def analyze_folder(folder_path: str):
    """Phân tích folder để xác định status."""
    path = Path(folder_path)
    
    print(f"Analyzing: {path}")
    print("=" * 70)
    
    if not path.exists():
        print(f"❌ Folder không tồn tại!")
        return
    
    # Find ZIPs
    zip_files = list(path.rglob("*.zip"))
    
    if not zip_files:
        print("❌ Không tìm thấy ZIP files!")
        return
    
    print(f"Found {len(zip_files)} ZIP files\n")
    
    # Analyze sample
    sample_size = min(50, len(zip_files))
    years = []
    status_hints = defaultdict(int)
    
    print(f"Analyzing {sample_size} sample files...\n")
    
    for idx, zip_file in enumerate(zip_files[:sample_size], 1):
        try:
            with zipfile.ZipFile(zip_file, 'r') as zf:
                files = zf.namelist()
                
                for file in files:
                    # Check year
                    year = extract_year_from_filename(file)
                    if year:
                        years.append(year)
                    
                    # Check status keywords
                    status = check_status_keywords(file)
                    if status:
                        status_hints[status] += 1
                
                # Show first 10
                if idx <= 10:
                    print(f"{idx:3d}. {zip_file.name[:50]:50s}")
                    for file in files[:2]:
                        year = extract_year_from_filename(file)
                        status = check_status_keywords(file)
                        print(f"     → {file[:40]:40s} Year:{year or 'N/A':4} Status:{status or 'UNKNOWN'}")
        except:
            pass
    
    if sample_size > 10:
        print(f"     ... và {sample_size - 10} files nữa\n")
    
    # Analysis
    print("\n" + "=" * 70)
    print("📊 PHÂN TÍCH")
    print("=" * 70)
    
    # Year distribution
    if years:
        avg_year = sum(years) / len(years)
        min_year = min(years)
        max_year = max(years)
        current_year = datetime.now().year
        
        print(f"\n📅 Năm ban hành:")
        print(f"  Sớm nhất:    {min_year}")
        print(f"  Muộn nhất:   {max_year}")
        print(f"  Trung bình:  {avg_year:.0f}")
        print(f"  Hiện tại:    {current_year}")
        
        old_count = sum(1 for y in years if y < 2015)
        recent_count = sum(1 for y in years if y >= 2020)
        
        print(f"\n  Trước 2015:  {old_count} ({old_count/len(years)*100:.1f}%)")
        print(f"  Từ 2020:     {recent_count} ({recent_count/len(years)*100:.1f}%)")
    
    # Status hints
    print(f"\n🏷️  Keywords phát hiện:")
    if status_hints:
        for status, count in status_hints.items():
            emoji = "❌" if status == "EXPIRED" else "✅"
            print(f"  {emoji} {status:8s}: {count} files")
    else:
        print(f"  Không tìm thấy keywords rõ ràng")
    
    # Conclusion
    print(f"\n" + "=" * 70)
    print("💡 KẾT LUẬN")
    print("=" * 70)
    
    # Folder name hint
    folder_name = path.name.upper()
    
    confidence = 0
    conclusion = "UNKNOWN"
    reasons = []
    
    # Check folder name
    if "HHL" in folder_name or "HETHIEULUC" in folder_name.replace(" ", ""):
        confidence += 50
        conclusion = "HẾT HIỆU LỰC"
        reasons.append(f"Tên folder: {path.name}")
    
    # Check years
    if years:
        avg_year = sum(years) / len(years)
        if avg_year < 2010:
            confidence += 30
            if conclusion == "UNKNOWN":
                conclusion = "HẾT HIỆU LỰC"
            reasons.append(f"Năm TB: {avg_year:.0f} (quá cũ)")
        elif avg_year >= 2020:
            confidence += 30
            if conclusion == "UNKNOWN":
                conclusion = "CÒN HIỆU LỰC"
            reasons.append(f"Năm TB: {avg_year:.0f} (gần đây)")
    
    # Check status keywords
    if status_hints.get('EXPIRED', 0) > status_hints.get('ACTIVE', 0):
        confidence += 20
        conclusion = "HẾT HIỆU LỰC"
        reasons.append(f"Keywords: EXPIRED > ACTIVE")
    elif status_hints.get('ACTIVE', 0) > 0:
        confidence += 20
        if conclusion == "UNKNOWN":
            conclusion = "CÒN HIỆU LỰC"
        reasons.append(f"Keywords: ACTIVE detected")
    
    # Final verdict
    if conclusion == "HẾT HIỆU LỰC":
        print(f"❌ Folder này chứa văn bản **HẾT HIỆU LỰC**")
        print(f"   Confidence: {confidence}%")
        print(f"\n   ⚠️  **KHÔNG NÊN IMPORT** vào AI chatbot!")
        print(f"   ⚠️  Có thể tư vấn SAI LUẬT cho người dùng!")
    elif conclusion == "CÒN HIỆU LỰC":
        print(f"✅ Folder này chứa văn bản **CÒN HIỆU LỰC**")
        print(f"   Confidence: {confidence}%")
        print(f"\n   ✅ **AN TOÀN ĐỂ IMPORT** vào AI chatbot")
    else:
        print(f"⚠️  Không thể xác định chắc chắn")
        print(f"   Cần kiểm tra thủ công")
    
    if reasons:
        print(f"\n   Lý do:")
        for reason in reasons:
            print(f"     - {reason}")
    
    print("\n" + "=" * 70)

if __name__ == "__main__":
    folders = [
        r"C:\Users\DELL\Documents\VBPLHHL",
        r"C:\Users\DELL\Documents\VBPL",
    ]
    
    if len(sys.argv) > 1:
        folders = [sys.argv[1]]
    
    for folder in folders:
        if Path(folder).exists():
            analyze_folder(folder)
            print("\n\n")
