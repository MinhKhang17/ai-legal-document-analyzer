"""Script để kiểm tra các file PDF trong folder VBPLHHL."""
import sys
from pathlib import Path

def scan_pdf_files(source_dir: str):
    """Scan và list tất cả PDF files."""
    source_path = Path(source_dir)
    
    print(f"Scanning: {source_path}")
    print("=" * 70)
    
    if not source_path.exists():
        print(f"❌ Folder không tồn tại: {source_path}")
        return
    
    if not source_path.is_dir():
        print(f"❌ Đây không phải folder: {source_path}")
        return
    
    # Tìm tất cả PDF
    pdf_files = list(source_path.rglob("*.pdf"))
    
    if not pdf_files:
        print("❌ Không tìm thấy file PDF nào!")
        print("\nKiểm tra lại:")
        print(f"  - Đường dẫn đúng chưa: {source_path}")
        print(f"  - Có file .pdf trong folder không?")
        return
    
    print(f"✅ Tìm thấy {len(pdf_files)} file PDF\n")
    
    # Group by size
    total_size = 0
    size_groups = {
        "small": [],   # < 1MB
        "medium": [],  # 1-10MB
        "large": [],   # > 10MB
    }
    
    for pdf_file in pdf_files:
        size_mb = pdf_file.stat().st_size / (1024 * 1024)
        total_size += size_mb
        
        if size_mb < 1:
            size_groups["small"].append((pdf_file, size_mb))
        elif size_mb < 10:
            size_groups["medium"].append((pdf_file, size_mb))
        else:
            size_groups["large"].append((pdf_file, size_mb))
    
    # Print summary
    print("📊 Tổng quan:")
    print(f"  Tổng số files: {len(pdf_files)}")
    print(f"  Tổng dung lượng: {total_size:.2f} MB")
    print(f"  File nhỏ (<1MB): {len(size_groups['small'])}")
    print(f"  File vừa (1-10MB): {len(size_groups['medium'])}")
    print(f"  File lớn (>10MB): {len(size_groups['large'])}")
    
    # Show first 20 files
    print(f"\n📄 Danh sách files (showing first 20):")
    print("-" * 70)
    
    for idx, pdf_file in enumerate(sorted(pdf_files)[:20], 1):
        size_mb = pdf_file.stat().st_size / (1024 * 1024)
        rel_path = pdf_file.relative_to(source_path)
        print(f"{idx:3d}. {rel_path.name[:50]:50s} {size_mb:6.2f} MB")
    
    if len(pdf_files) > 20:
        print(f"     ... và {len(pdf_files) - 20} files nữa")
    
    print("-" * 70)
    
    # Estimate time
    avg_time_per_file = 4  # seconds
    estimated_time = len(pdf_files) * avg_time_per_file / 60
    
    print(f"\n⏱️  Ước tính thời gian import:")
    print(f"  Với 3 workers: ~{estimated_time / 3:.1f} phút")
    print(f"  Với 1 worker:  ~{estimated_time:.1f} phút")
    
    # Check largest files
    if size_groups["large"]:
        print(f"\n⚠️  File lớn (có thể import chậm):")
        for pdf_file, size_mb in sorted(size_groups["large"], key=lambda x: x[1], reverse=True)[:5]:
            print(f"  - {pdf_file.name[:50]} ({size_mb:.2f} MB)")
    
    print("\n" + "=" * 70)
    print("✅ Sẵn sàng import!")
    print("\nĐể import, chạy:")
    print(f'  python scripts/import_legal_docs.py --source "{source_dir}" --max-workers 3')

if __name__ == "__main__":
    source = r"C:\Users\DELL\Documents\VBPLHHL"
    
    if len(sys.argv) > 1:
        source = sys.argv[1]
    
    scan_pdf_files(source)
