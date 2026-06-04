"""Test document upload endpoint.

Requires:
- Server running (uvicorn app.main:app --reload --port 8000)
- Docker/Neo4j running
"""
import httpx
import io
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import letter


def create_test_pdf() -> bytes:
    """Create a simple PDF with test content."""
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer, pagesize=letter)
    
    # Add Vietnamese contract text
    c.drawString(100, 750, "HOP DONG THUE NHA")
    c.drawString(100, 730, "")
    c.drawString(100, 710, "Ben A: Nguoi cho thue")
    c.drawString(100, 690, "Ben B: Nguoi thue")
    c.drawString(100, 670, "")
    c.drawString(100, 650, "Dieu 1: Doi tuong cua hop dong")
    c.drawString(100, 630, "Can ho tai dia chi: 123 Nguyen Hue, Q1, TP.HCM")
    c.drawString(100, 610, "")
    c.drawString(100, 590, "Dieu 2: Gia thue va phuong thuc thanh toan")
    c.drawString(100, 570, "Gia thue: 5,000,000 VND/thang")
    c.drawString(100, 550, "Tien dat coc: 10,000,000 VND")
    c.drawString(100, 530, "")
    c.drawString(100, 510, "Dieu 3: Thoi han thue")
    c.drawString(100, 490, "12 thang ke tu ngay ky hop dong")
    
    c.save()
    buffer.seek(0)
    return buffer.read()


def test_upload():
    """Test document upload endpoint."""
    url = "http://localhost:8000/api/ai/documents/upload"
    
    # Create test PDF
    pdf_content = create_test_pdf()
    
    # Prepare multipart form data
    files = {
        'file': ('test_contract.pdf', pdf_content, 'application/pdf')
    }
    
    data = {
        'user_id': 'test_user_123',
        'contract_type': 'HOUSE_RENTAL'
    }
    
    # Send request
    print("🚀 Uploading test document...")
    response = httpx.post(url, files=files, data=data, timeout=30.0)
    
    print(f"\n📊 Status Code: {response.status_code}")
    print(f"📄 Response:\n{response.json()}")
    
    if response.status_code == 200:
        print("\n✅ Upload successful!")
        result = response.json()
        doc_id = result.get('data', {}).get('documentId')
        print(f"📝 Document ID: {doc_id}")
    else:
        print("\n❌ Upload failed!")


def test_list_documents():
    """Test list documents endpoint."""
    url = "http://localhost:8000/api/ai/documents/list"
    
    params = {'user_id': 'test_user_123', 'limit': 10}
    
    print("\n📋 Listing user documents...")
    response = httpx.get(url, params=params, timeout=10.0)
    
    print(f"📊 Status Code: {response.status_code}")
    print(f"📄 Response:\n{response.json()}")


def test_search_documents():
    """Test search documents endpoint."""
    url = "http://localhost:8000/api/ai/documents/search"
    
    data = {
        'user_id': 'test_user_123',
        'query': 'tiền đặt cọc',
        'top_k': 5
    }
    
    print("\n🔍 Searching user documents...")
    response = httpx.post(url, data=data, timeout=10.0)
    
    print(f"📊 Status Code: {response.status_code}")
    print(f"📄 Response:\n{response.json()}")


if __name__ == "__main__":
    try:
        # Test 1: Upload document
        test_upload()
        
        # Test 2: List documents
        test_list_documents()
        
        # Test 3: Search documents
        test_search_documents()
        
        print("\n" + "="*60)
        print("✅ All tests completed!")
        print("="*60)
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
