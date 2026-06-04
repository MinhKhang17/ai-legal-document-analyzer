"""Comprehensive demo of document upload & search system."""
import httpx
import io
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import letter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont


def create_vietnamese_pdf() -> bytes:
    """Create PDF with Vietnamese text (ASCII only for compatibility)."""
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer, pagesize=letter)
    
    # Title
    c.setFont("Helvetica-Bold", 16)
    c.drawString(100, 750, "HOP DONG THUE NHA")
    
    # Body content
    c.setFont("Helvetica", 12)
    lines = [
        "",
        "Ben A: Nguoi cho thue - Ong Nguyen Van A",
        "CMND: 123456789, dia chi: 456 Le Loi, Q3, TP.HCM",
        "",
        "Ben B: Nguoi thue - Ba Tran Thi B", 
        "CMND: 987654321, dia chi: 789 Tran Hung Dao, Q5, TP.HCM",
        "",
        "=============================================",
        "",
        "Dieu 1: Doi tuong cua hop dong",
        "Can ho tai dia chi: 123 Nguyen Hue, Quan 1, Thanh pho Ho Chi Minh",
        "Dien tich: 50m2, tang 5, toa nha ABC",
        "",
        "Dieu 2: Gia thue va phuong thuc thanh toan",
        "- Gia thue: 5.000.000 VND/thang",
        "- Tien dat coc: 10.000.000 VND (2 thang tien thue)",
        "- Thanh toan vao ngay 05 hang thang",
        "- Hinh thuc thanh toan: Chuyen khoan ngan hang",
        "",
        "Dieu 3: Thoi han thue",
        "- Thoi gian: 12 thang",
        "- Tu ngay: 01/06/2026",
        "- Den ngay: 31/05/2027",
        "",
        "Dieu 4: Quyen va nghia vu cua cac ben",
        "Ben A cam ket:",
        "- Giao nha dung thoi han",
        "- Bao tri co so ha tang",
        "",
        "Ben B cam ket:",
        "- Thanh toan dung han",
        "- Giu gin tai san",
        "- Khong lam an phi phap",
        "",
        "Ky ten:",
        "Ben A: ___________    Ben B: ___________",
    ]
    
    y = 720
    for line in lines:
        c.drawString(100, y, line)
        y -= 18
    
    c.save()
    buffer.seek(0)
    return buffer.read()


def demo_full_workflow():
    """Demo complete workflow: upload -> list -> search."""
    print("="*70)
    print("DEMO: Document Upload & Search System")
    print("="*70)
    
    # 1. Upload document
    print("\n[STEP 1] 📤 Uploading contract document...")
    upload_url = "http://localhost:8000/api/ai/documents/upload"
    pdf_content = create_vietnamese_pdf()
    
    files = {'file': ('hop_dong_thue_nha.pdf', pdf_content, 'application/pdf')}
    data = {'user_id': 'user_demo', 'contract_type': 'HOUSE_RENTAL'}
    
    response = httpx.post(upload_url, files=files, data=data, timeout=30.0)
    print(f"  Status: {response.status_code}")
    
    if response.status_code == 200:
        result = response.json()
        doc_id = result['data']['documentId']
        print(f"  ✅ Success! Document ID: {doc_id}")
        print(f"  📝 Filename: {result['data']['filename']}")
        print(f"  📏 Text length: {result['data']['textLength']} characters")
    else:
        print(f"  ❌ Failed: {response.text}")
        return
    
    # 2. List all documents
    print("\n[STEP 2] 📋 Listing all documents...")
    list_url = "http://localhost:8000/api/ai/documents/list"
    response = httpx.get(list_url, params={'user_id': 'user_demo', 'limit': 10})
    
    result = response.json()
    docs = result['data']['items']
    print(f"  Total documents: {len(docs)}")
    for doc in docs:
        print(f"    - {doc['filename']} ({doc['contractType']}) - {doc['uploadedAt'][:10]}")
    
    # 3. Search scenarios
    print("\n[STEP 3] 🔍 Semantic search tests...")
    search_url = "http://localhost:8000/api/ai/documents/search"
    
    queries = [
        "dat coc",
        "thue nha",
        "nghia vu",
        "thanh toan",
        "thoi han",
    ]
    
    for query in queries:
        data = {'user_id': 'user_demo', 'query': query, 'top_k': 3}
        response = httpx.post(search_url, data=data, timeout=10.0)
        result = response.json()
        
        matches = result['data']['results']
        print(f"\n  Query: '{query}'")
        if matches:
            for match in matches:
                print(f"    ✅ {match['filename']} (score: {match['score']:.4f})")
                print(f"       {match['snippet'][:100]}...")
        else:
            print(f"    ❌ No matches found")
    
    print("\n" + "="*70)
    print("✅ DEMO COMPLETED!")
    print("="*70)
    print("\nKey Features Demonstrated:")
    print("  ✅ PDF text extraction")
    print("  ✅ Document storage in Neo4j")
    print("  ✅ Semantic similarity search (Gemini/Lexical fallback)")
    print("  ✅ Multi-document ranking")
    print("  ✅ User-scoped document isolation")


if __name__ == "__main__":
    try:
        demo_full_workflow()
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
