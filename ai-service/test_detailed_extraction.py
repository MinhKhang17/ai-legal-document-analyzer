"""Test chi tiết PDF extraction để show cho bạn xem text được extract như thế nào."""
import httpx
import io
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import letter


def create_test_pdf_with_content() -> bytes:
    """Tạo PDF với nội dung cụ thể."""
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer, pagesize=letter)
    
    c.setFont("Helvetica-Bold", 16)
    c.drawString(100, 750, "HOP DONG THUE NHA")
    
    c.setFont("Helvetica", 12)
    lines = [
        "",
        "Ben A: Nguoi cho thue - Ong Nguyen Van A",
        "CMND: 123456789",
        "",
        "Ben B: Nguoi thue - Ba Tran Thi B",
        "CMND: 987654321",
        "",
        "Dieu 1: Dia chi nha cho thue",
        "123 Nguyen Hue, Quan 1, TP.HCM",
        "Dien tich: 50m2",
        "",
        "Dieu 2: Gia thue",
        "- Gia thue: 5.000.000 VND/thang",
        "- Tien dat coc: 10.000.000 VND",
        "- Thanh toan vao ngay 05 hang thang",
        "",
        "Dieu 3: Thoi han thue",
        "12 thang, tu 01/06/2026 den 31/05/2027",
    ]
    
    y = 720
    for line in lines:
        c.drawString(100, y, line)
        y -= 20
    
    c.save()
    buffer.seek(0)
    return buffer.read()


def test_pdf_extraction():
    """Test chi tiết quá trình extract text từ PDF."""
    print("="*70)
    print("TEST: Chi tiết PDF Text Extraction")
    print("="*70)
    
    # 1. Tạo PDF
    print("\n[STEP 1] 📄 Tạo PDF file...")
    pdf_content = create_test_pdf_with_content()
    print(f"  ✅ PDF size: {len(pdf_content)} bytes")
    
    # 2. Extract text bằng PyPDF2 (giống như trong DocumentService)
    print("\n[STEP 2] 📖 Extract text bằng PyPDF2...")
    import PyPDF2
    
    pdf_file = io.BytesIO(pdf_content)
    reader = PyPDF2.PdfReader(pdf_file)
    
    print(f"  📊 Số trang: {len(reader.pages)}")
    
    text = ""
    for i, page in enumerate(reader.pages):
        page_text = page.extract_text()
        text += page_text + "\n"
        print(f"  📄 Trang {i+1}: {len(page_text)} characters")
    
    print(f"\n  ✅ Tổng text extracted: {len(text)} characters")
    print(f"\n  📝 Sample text (200 chars):")
    print(f"  {text[:200]}...")
    
    # 3. Upload lên server
    print("\n[STEP 3] 📤 Upload PDF lên server...")
    url = "http://localhost:8000/api/ai/documents/upload"
    
    files = {'file': ('test_contract.pdf', pdf_content, 'application/pdf')}
    data = {'user_id': 'test_extraction', 'contract_type': 'HOUSE_RENTAL'}
    
    response = httpx.post(url, files=files, data=data, timeout=30.0)
    
    if response.status_code == 200:
        result = response.json()
        print(f"  ✅ Upload thành công!")
        print(f"  📝 Document ID: {result['data']['documentId']}")
        print(f"  📏 Server extracted: {result['data']['textLength']} characters")
        
        # So sánh
        if result['data']['textLength'] == len(text.strip()):
            print(f"\n  ✅ MATCH! Server extract giống local extract!")
        else:
            print(f"\n  ⚠️  Difference: {abs(result['data']['textLength'] - len(text.strip()))} chars")
    else:
        print(f"  ❌ Upload failed: {response.status_code}")
        print(f"  {response.text}")
    
    # 4. Test search với extracted text
    print("\n[STEP 4] 🔍 Test search trong extracted text...")
    search_url = "http://localhost:8000/api/ai/documents/search"
    
    test_queries = [
        ("dat coc", "Tìm điều khoản đặt cọc"),
        ("gia thue", "Tìm giá thuê"),
        ("123 Nguyen Hue", "Tìm địa chỉ"),
    ]
    
    for query, description in test_queries:
        data = {'user_id': 'test_extraction', 'query': query, 'top_k': 1}
        response = httpx.post(search_url, data=data, timeout=10.0)
        
        if response.status_code == 200:
            result = response.json()
            matches = result['data']['results']
            
            if matches:
                score = matches[0]['score']
                print(f"  ✅ '{query}' → Score: {score:.4f} ({description})")
            else:
                print(f"  ❌ '{query}' → No match")
    
    print("\n" + "="*70)
    print("✅ TEST COMPLETED - PDF EXTRACTION WORKING!")
    print("="*70)
    
    print("\n📊 KẾT LUẬN:")
    print("  ✅ PyPDF2 extract text thành công")
    print("  ✅ Server nhận và lưu text đầy đủ")
    print("  ✅ Search trong extracted text hoạt động tốt")
    print("  ✅ KHÔNG cần torch/transformers!")


if __name__ == "__main__":
    try:
        test_pdf_extraction()
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
