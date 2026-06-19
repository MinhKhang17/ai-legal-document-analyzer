"""Script kiểm tra nội dung ZIP files."""
import sys
import zipfile
from pathlib import Path
from collections import defaultdict

def check_zip_files(source_dir: str):
    """Kiểm tra ZIP files và list nội dung."""
    source_path = Path(source_dir)
    
    print(f"Scanning: {source_path}")
    print("=" * 70)
    
    if not source_path.exists():
        print(f"❌ Folder không tồn tại: {source_path}")
        return
    
    # Tìm ZIP files
    zip_files = list(source_path.rglob("*.zip"))
    
    if not zip_files:
        print("❌ Không tìm thấy file ZIP nào!")
        return
    
    print(f"✅ Tìm thấy {len(zip_files)} file ZIP\n")
    
    # Analyze content
    total_docs = 0
    doc_by_type = defaultdict(int)
    total_zip_size = 0
    
    print("📦 Analyzing ZIP contents...\n")
    
    for idx, zip_file in enumerate(sorted(zip_files)[:20], 1):
        zip_size_mb = zip_file.stat().st_size / (1024 * 1024)
        total_zip_size += zip_size_mb
        
        try:
            with zipfile.ZipFile(zip_file, 'r') as zf:
                files = zf.namelist()
                
                # Count by extension
                pdf_count = sum(1 for f in files if f.lower().endswith('.pdf'))
                docx_count = sum(1 for f in files if f.lower().endswith(('.docx', '.doc')))
                txt_count = sum(1 for f in files if f.lower().endswith('.txt'))
                other_count = len(files) - pdf_count - docx_count - txt_count
                
                total_docs += pdf_count + docx_count + txt_count
                doc_by_type['pdf'] += pdf_count
                doc_by_type['docx'] += docx_count
                doc_by_type['txt'] += txt_count
                
                print(f"{idx:3d}. {zip_file.name[:45]:45s} {zip_size_mb:6.2f} MB")
                print(f"     → PDF: {pdf_count}, DOCX: {docx_count}, TXT: {txt_count}, Other: {other_count}")
                
        except Exception as e:
            print(f"{idx:3d}. {zip_file.name[:45]:45s} ❌ ERROR: {e}")
    
    if len(zip_files) > 20:
        print(f"\n     ... và {len(zip_files) - 20} ZIP files nữa")
        
        # Analyze remaining files
        for zip_file in zip_files[20:]:
            zip_size_mb = zip_file.stat().st_size / (1024 * 1024)
            total_zip_size += zip_size_mb
            
            try:
                with zipfile.ZipFile(zip_file, 'r') as zf:
                    files = zf.namelist()
                    pdf_count = sum(1 for f in files if f.lower().endswith('.pdf'))
                    docx_count = sum(1 for f in files if f.lower().endswith(('.docx', '.doc')))
                    txt_count = sum(1 for f in files if f.lower().endswith('.txt'))
                    
                    total_docs += pdf_count + docx_count + txt_count
                    doc_by_type['pdf'] += pdf_count
                    doc_by_type['docx'] += docx_count
                    doc_by_type['txt'] += txt_count
            except:
                pass
    
    # Summary
    print("\n" + "=" * 70)
    print("📊 SUMMARY")
    print("=" * 70)
    print(f"Total ZIP files:  {len(zip_files)}")
    print(f"Total ZIP size:   {total_zip_size:.2f} MB")
    print(f"\nDocuments found:")
    print(f"  PDF files:      {doc_by_type['pdf']}")
    print(f"  DOCX files:     {doc_by_type['docx']}")
    print(f"  TXT files:      {doc_by_type['txt']}")
    print(f"  TOTAL:          {total_docs}")
    
    # Estimate
    if total_docs > 0:
        avg_time = 4  # seconds per doc
        estimated_time = total_docs * avg_time / 60
        
        print(f"\n⏱️  Ước tính thời gian import:")
        print(f"  Với 3 workers: ~{estimated_time / 3:.1f} phút")
        print(f"  Với 1 worker:  ~{estimated_time:.1f} phút")
    
    print("\n" + "=" * 70)
    print("✅ Sẵn sàng import!")
    print("\nĐể import, chạy:")
    print(f'  python scripts/import_from_zip.py --source "{source_dir}" --max-workers 3')
    print("\nĐể test với 5 ZIP đầu:")
    print(f'  python scripts/import_from_zip.py --source "{source_dir}" --limit 5')

if __name__ == "__main__":
    source = r"C:\Users\DELL\Documents\VBPLHHL"
    
    if len(sys.argv) > 1:
        source = sys.argv[1]
    
    check_zip_files(source)
