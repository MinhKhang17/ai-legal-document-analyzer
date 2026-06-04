# Document Upload & RAG System Guide

## 📚 Tổng quan

AI Service giờ đã hỗ trợ **2 loại knowledge** cho RAG system:

### 1. **System Knowledge** (Legal Corpus)
- **Nội dung**: Văn bản pháp luật, điều khoản chuẩn, luật dân sự
- **Nguồn**: Seed sẵn từ `app/graph/legal_corpus.py`
- **Scope**: Toàn hệ thống, dùng chung cho tất cả users
- **Mục đích**: Cung cấp legal context cho AI phân tích hợp đồng

### 2. **User Knowledge** (Uploaded Documents)
- **Nội dung**: Hợp đồng, tài liệu được user upload
- **Nguồn**: User upload qua API
- **Scope**: Riêng từng user (isolated by userId)
- **Mục đích**: RAG retrieval từ documents của chính user đó

---

## 🚀 API Endpoints

### 1. Upload Document
**POST** `/api/ai/documents/upload`

Upload PDF hoặc DOCX document vào hệ thống.

**Request (multipart/form-data):**
```bash
curl -X POST http://localhost:8000/api/ai/documents/upload \
  -F "file=@contract.pdf" \
  -F "user_id=user123" \
  -F "contract_type=HOUSE_RENTAL"
```

**Parameters:**
- `file`: PDF hoặc DOCX file (max 10MB)
- `user_id`: ID của user upload
- `contract_type`: Loại hợp đồng (HOUSE_RENTAL, LAND_TRANSFER, etc.)

**Response:**
```json
{
  "success": true,
  "data": {
    "documentId": "DOC_user123_a1b2c3d4",
    "filename": "contract.pdf",
    "textLength": 5430,
    "contractType": "HOUSE_RENTAL",
    "uploadedAt": "2026-06-04T12:00:00Z"
  },
  "message": "Document uploaded successfully"
}
```

**Features:**
- ✅ Extract text từ PDF/DOCX
- ✅ Store trong Neo4j với Document nodes
- ✅ Link với ContractType nodes (system knowledge)
- ✅ User-scoped isolation (mỗi user chỉ thấy documents của mình)

---

### 2. List Documents
**GET** `/api/ai/documents/list?user_id={userId}&limit={limit}`

Lấy danh sách tất cả documents của user.

**Request:**
```bash
curl "http://localhost:8000/api/ai/documents/list?user_id=user123&limit=50"
```

**Response:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "documentId": "DOC_user123_a1b2c3d4",
        "filename": "contract.pdf",
        "contractType": "HOUSE_RENTAL",
        "uploadedAt": "2026-06-04T12:00:00Z",
        "textLength": 5430
      }
    ],
    "total": 1
  }
}
```

---

### 3. Search Documents
**POST** `/api/ai/documents/search`

Semantic search trong documents của user.

**Request:**
```bash
curl -X POST http://localhost:8000/api/ai/documents/search \
  -F "user_id=user123" \
  -F "query=điều khoản đặt cọc" \
  -F "top_k=5"
```

**Parameters:**
- `user_id`: ID của user
- `query`: Search query (2-200 characters)
- `top_k`: Số lượng results (1-20, default: 5)

**Response:**
```json
{
  "success": true,
  "data": {
    "query": "điều khoản đặt cọc",
    "results": [
      {
        "documentId": "DOC_user123_a1b2c3d4",
        "filename": "contract.pdf",
        "contractType": "HOUSE_RENTAL",
        "snippet": "Điều 2: Giá thuê và phương thức thanh toán...",
        "score": 0.7845
      }
    ]
  }
}
```

**How it works:**
1. Query được normalize (remove dấu, lowercase)
2. Keyword search trong Neo4j để lấy candidate documents
3. Use embedding service (Gemini hoặc Lexical) để rank by similarity
4. Return top-k documents với scores

---

## 🛠️ Tech Stack

### Dependencies
```txt
PyPDF2            # PDF text extraction
python-docx       # DOCX text extraction
python-multipart  # File upload support
```

### Embedding Service
- **Primary**: Gemini embeddings (nếu có API key)
- **Fallback**: Lexical similarity (token overlap)
- **Features**:
  - Vietnamese text normalization (strip accents)
  - In-memory caching
  - Batch embedding support

### Storage
- **Neo4j Graph**: Document nodes với userId, text, metadata
- **Relationships**: 
  - `(Document)-[:BELONGS_TO]->(ContractType)`
  - Link user documents với system knowledge

---

## 📊 Neo4j Schema

### Document Node
```cypher
(:Document {
  id: "DOC_user123_a1b2c3d4",
  userId: "user123",
  filename: "contract.pdf",
  text: "Truncated text (5000 chars)...",
  fullText: "Complete text...",
  contractType: "HOUSE_RENTAL",
  uploadedAt: "2026-06-04T12:00:00Z"
})
```

### Relationships
```cypher
// Link document với contract type
(doc:Document)-[:BELONGS_TO]->(ct:ContractType)

// Example query
MATCH (d:Document {userId: "user123"})-[:BELONGS_TO]->(ct:ContractType)
RETURN d.filename, ct.name
```

---

## 🧪 Testing

### Quick Test
```bash
# 1. Start services
docker compose up -d
uvicorn app.main:app --reload --port 8000

# 2. Seed system knowledge
python -c "import httpx; httpx.post('http://localhost:8000/api/ai/graph/seed/all', timeout=30)"

# 3. Run comprehensive demo
python test_full_demo.py
```

### Test Scripts
- `test_upload_document.py` - Test upload, list, search
- `test_full_demo.py` - Comprehensive demo with multiple queries
- `test_search_fix.py` - Test search với query không dấu

---

## 🎯 Use Cases

### 1. Contract Analysis với Context
```python
# User upload contract
upload_response = upload_document(
    file="my_contract.pdf",
    user_id="user123",
    contract_type="HOUSE_RENTAL"
)

# AI analyze với context từ:
# - System knowledge (legal corpus)
# - User's uploaded documents
analyze_response = analyze_contract(
    contract_text="...",
    user_id="user123"  # để retrieve user documents
)
```

### 2. Semantic Search
```python
# Tìm tất cả contracts có điều khoản thanh toán
results = search_documents(
    user_id="user123",
    query="phương thức thanh toán",
    top_k=5
)

# Returns ranked results với similarity scores
```

### 3. Multi-Document Comparison
```python
# Upload multiple contracts
upload_document(file="contract_v1.pdf", user_id="user123")
upload_document(file="contract_v2.pdf", user_id="user123")

# Search across all versions
results = search_documents(
    user_id="user123",
    query="điều khoản đặt cọc"
)
```

---

## 🔮 Future Enhancements

### Phase 1: Vector Storage (Optional)
- Store embeddings trong Neo4j vector index
- Hoặc external vector DB (Qdrant, Weaviate)
- Enable true semantic vector search

### Phase 2: Chunking
- Split long documents thành chunks
- Better retrieval cho long documents
- Context window optimization

### Phase 3: Metadata Filtering
- Filter by date range, contract type, etc.
- Combined keyword + semantic search
- Advanced ranking strategies

### Phase 4: Document Management
- Update/replace documents
- Delete documents
- Version control for contracts

---

## 📝 Notes

### Vietnamese Text Handling
- Embedding service **tự động normalize** Vietnamese text:
  - Strip accents: "đặt" → "dat"
  - Lowercase: "ĐẶT" → "dat"
- Search works với hoặc không dấu:
  - "tiền đặt cọc" ✅
  - "tien dat coc" ✅

### File Size Limits
- Max: 10MB per file
- Configurable trong `app/api/documents.py`
- Text truncated to 5000 chars for storage (full text available)

### Security
- No authentication yet (Spring Boot sẽ handle)
- User isolation by userId (trust frontend)
- Input validation (file type, size, query length)

---

## 🎉 Summary

**Document Upload System giờ đã FULLY FUNCTIONAL!**

✅ PDF/DOCX upload  
✅ Text extraction  
✅ Neo4j storage  
✅ Semantic search  
✅ User isolation  
✅ System knowledge integration  
✅ Vietnamese text support  
✅ Similarity scoring  

Access Swagger UI: http://localhost:8000/docs
