# 📦 HƯỚNG DẪN IMPORT TỪ ZIP FILES

## 🎯 Tình Huống

Bạn có **420 ZIP files** trong folder `C:\Users\DELL\Documents\VBPLHHL`

Mỗi ZIP chứa 1-3 văn bản pháp luật (PDF/DOCX).

**Tổng cộng: 494 documents** (448 PDF + 46 DOCX)

---

## ✅ CHUẨN BỊ (Đã xong)

1. ✅ AI Service đang chạy
2. ✅ Neo4j đã sẵn sàng
3. ✅ Đã kiểm tra ZIP files: `python scripts/check_zip_files.py`

---

## 🚀 IMPORT NGAY (3 Options)

### **Option 1: Import TẤT CẢ (Recommended)**

```bash
cd C:\Users\DELL\Documents\findRisk\ai-service

python scripts/import_from_zip.py --source "C:\Users\DELL\Documents\VBPLHHL" --max-workers 3
```

⏱️ **Thời gian:** ~11 phút  
📦 **Kết quả:** 494 documents → ~2,000-3,000 chunks

---

### **Option 2: Test với 5 ZIP đầu**

```bash
python scripts/import_from_zip.py --source "C:\Users\DELL\Documents\VBPLHHL" --limit 5
```

⏱️ **Thời gian:** ~1 phút  
✅ **Để kiểm tra:** Mọi thứ hoạt động OK trước khi import hết

---

### **Option 3: Import chậm (1 worker)**

Nếu server yếu:

```bash
python scripts/import_from_zip.py --source "C:\Users\DELL\Documents\VBPLHHL" --max-workers 1
```

⏱️ **Thời gian:** ~33 phút  
💻 **Ưu điểm:** Ít tốn CPU/RAM

---

## 📊 QUÁ TRÌNH IMPORT

Khi chạy script, bạn sẽ thấy:

```
======================================================================
🚀 STARTING ZIP IMPORT
======================================================================
Source:      C:\Users\DELL\Documents\VBPLHHL
API:         http://localhost:8000
ZIP files:   420
Max workers: 3
API version: v2
======================================================================

======================================================================
📦 EXTRACTING ZIP FILES
======================================================================
Extracting: 100%|████████████████████| 420/420 [00:45<00:00,  9.33zip/s]

✅ Extracted 494 documents from 420 ZIPs

======================================================================
📤 IMPORTING DOCUMENTS
======================================================================
Importing: 100%|████████████████████| 494/494 [09:30<00:00,  1.15s/file]

======================================================================
📊 IMPORT SUMMARY
======================================================================
✅ Success: 485/494
❌ Failed:  9/494
⏭️  Skipped: 0/494

📦 Total chunks created: 2,847

❌ Failed files: 9
   1. corrupted_document.pdf
      Error: HTTP 500: OCR failed
   ...

💾 Full log saved to: import_from_zip.log
======================================================================

⏱️  Total time: 645.23 seconds
📈 Average: 1.31 seconds/document
```

---

## 🔍 KIỂM TRA KẾT QUẢ

### 1. Xem logs

```bash
# Xem toàn bộ log
type import_from_zip.log

# Xem chỉ errors
findstr "ERROR" import_from_zip.log
findstr "Failed" import_from_zip.log
```

---

### 2. Xem JSON results

```bash
type import_zip_results.json
```

**Format:**
```json
{
  "success": [
    {
      "status": "success",
      "file": "C:/Users/.../temp/doc1.pdf",
      "title": "Nghi dinh 123/2024",
      "result": {
        "document_id": "abc123",
        "chunks_created": 6
      }
    }
  ],
  "failed": [...]
}
```

---

### 3. Test Knowledge Base

```bash
# Test xem AI đã học được gì
python scripts/test_knowledge_base.py
```

**Expected:**
```
✅ Neo4j connection OK
✅ Bot trích dẫn BLDS 2015!
✅ Knowledge Base is good!
Overall Coverage: 90.0%
```

---

### 4. Kiểm tra Neo4j

Mở browser: http://localhost:7474

```cypher
// Đếm chunks
MATCH (c:Chunk)
RETURN count(c) as total

// Xem 10 chunks mới
MATCH (c:Chunk)
RETURN c.title, c.source_path, c.token_count
ORDER BY c.updated_at DESC
LIMIT 10

// Search Nghị định
MATCH (c:Chunk)
WHERE c.text CONTAINS "nghị định" OR c.text CONTAINS "nghi dinh"
RETURN c.title, c.file_type
LIMIT 10
```

---

## 🧪 TEST CHATBOT

Sau khi import xong, test ngay:

```bash
python test_chatbot.py
```

**Hoặc test manual:**

```bash
curl -X POST "http://localhost:8000/v2/chatbot/upload" \
  -F "file=@test_contract.pdf" \
  -F "session_id=test-001" \
  -F "initial_question=Phân tích hợp đồng này theo Nghị định mới nhất"
```

**Bot giờ sẽ trích dẫn chính xác:**
```
"Theo Nghị định 123/2024/NĐ-CP ngày 15/06/2024 về hợp đồng thuê nhà,
Điều 5 quy định:

1. Hợp đồng thuê nhà phải được lập thành văn bản...
2. Nội dung tối thiểu gồm:
   - Thông tin các bên...
   - Tài sản cho thuê...
   
Căn cứ: Nghị định 123/2024/NĐ-CP, Điều 5
Nguồn: files_100627_1780570311746.zip/nghi_dinh_123.pdf"
```

---

## 🐛 XỬ LÝ LỖI

### Lỗi 1: "Cannot connect to AI service"

```bash
# Check Docker
docker ps

# Restart nếu cần
cd ai-service
docker-compose restart
docker-compose logs -f
```

---

### Lỗi 2: "Failed to extract ZIP"

**Nguyên nhân:** ZIP bị corrupt

**Fix:** Script tự động skip, xem log để biết ZIP nào lỗi

---

### Lỗi 3: "HTTP 500: OCR failed"

**Nguyên nhân:** PDF scan chất lượng thấp

**Fix:** Bỏ qua OK, script đã retry 2 lần rồi

---

### Lỗi 4: "Out of memory"

**Nguyên nhân:** Quá nhiều files extract cùng lúc

**Fix:** Giảm workers:

```bash
python scripts/import_from_zip.py --source "..." --max-workers 1
```

---

### Lỗi 5: Import chậm / timeout

**Fix:** Tăng timeout trong script:

```python
# Line ~130 trong import_from_zip.py
response = requests.post(url, files=files, data=data, timeout=300)  # 5 phút
```

---

## 📈 MONITORING

Trong khi import:

**Terminal 1** (Import):
```bash
python scripts/import_from_zip.py --source "..."
```

**Terminal 2** (Monitor):
```bash
# Watch Docker stats
docker stats

# Watch API logs
docker-compose logs -f api

# Watch Neo4j logs
docker-compose logs -f neo4j
```

---

## 💾 BACKUP (Optional nhưng recommended)

Trước khi import nhiều, backup Neo4j:

```bash
# Backup
docker exec neo4j neo4j-admin database dump neo4j --to-path=/data

# Copy backup ra ngoài
docker cp neo4j:/data/neo4j.dump ./backup-$(date +%Y%m%d).dump
```

---

## 🎯 KẾT QUẢ MONG ĐỢI

Sau import thành công:

### **Knowledge Base:**
- ✅ 485+ documents
- ✅ 2,800+ chunks
- ✅ Coverage: 90%+

### **Chatbot:**
- ✅ Trích dẫn chính xác Nghị định, Thông tư, Quyết định
- ✅ Giải thích lỗi dựa văn bản gốc
- ✅ Tư vấn pháp lý đầy đủ
- ✅ Confidence cao (>85%)

### **Search Quality:**
```
Query: "Nghị định 123 quy định gì?"
→ ✅ Trả về đúng Nghị định 123
→ ✅ Highlight điều khoản liên quan
→ ✅ Score > 0.85
```

---

## 🚀 BẮT ĐẦU NGAY

```bash
cd C:\Users\DELL\Documents\findRisk\ai-service

# Option A: Import hết (11 phút)
python scripts/import_from_zip.py --source "C:\Users\DELL\Documents\VBPLHHL" --max-workers 3

# Option B: Test 5 ZIP trước (1 phút)
python scripts/import_from_zip.py --source "C:\Users\DELL\Documents\VBPLHHL" --limit 5
```

---

## 📚 FILES LIÊN QUAN

- `check_zip_files.py` - Kiểm tra nội dung ZIP
- `import_from_zip.py` - Script import chính
- `test_knowledge_base.py` - Test sau import
- `import_from_zip.log` - Log chi tiết
- `import_zip_results.json` - Kết quả JSON

---

## ❓ FAQ

**Q: Import mất bao lâu?**  
A: ~11 phút cho 494 documents (với 3 workers)

**Q: ZIP files bị xóa không?**  
A: Không, ZIP files giữ nguyên. Script chỉ extract vào temp folder rồi xóa

**Q: Import bị gián đoạn giữa chừng?**  
A: Ctrl+C để dừng. Có thể import tiếp lần sau (script không duplicate)

**Q: Làm sao biết import thành công?**  
A: Chạy `python scripts/test_knowledge_base.py`

**Q: Import lỗi nhiều?**  
A: Bình thường 1-2% files lỗi (PDF corrupt). Bỏ qua OK.

**Q: Re-import được không?**  
A: Được, nhưng sẽ tạo duplicate chunks. Nên xóa old chunks trước:
```cypher
MATCH (c:Chunk) DETACH DELETE c
```

---

## 🎉 HOÀN THÀNH

Sau khi import xong, AI chatbot của bạn sẽ:

✅ Hiểu rõ 494 văn bản pháp luật Việt Nam  
✅ Trích dẫn chính xác Nghị định, Thông tư  
✅ Tư vấn pháp lý chuyên nghiệp  
✅ Phát hiện lỗi hợp đồng chính xác hơn  

**Chạy ngay:**
```bash
python scripts/import_from_zip.py --source "C:\Users\DELL\Documents\VBPLHHL" --max-workers 3
```

Chúc bạn thành công! 🚀
