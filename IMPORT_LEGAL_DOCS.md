# 📚 HƯỚNG DẪN IMPORT VĂN BẢN PHÁP LUẬT

## Tổng Quan

Script `import_legal_docs.py` giúp import hàng loạt file PDF văn bản pháp luật vào Knowledge Base của AI Service.

**Tính năng:**
- ✅ Scan recursive tất cả PDF trong folder
- ✅ Import song song (multi-threading) - nhanh
- ✅ Progress bar real-time
- ✅ Auto retry khi lỗi
- ✅ Logging chi tiết
- ✅ Summary report

---

## Bước 1: Chuẩn Bị

### 1.1. Khởi động AI Service

```bash
cd ai-service
docker-compose up -d
```

Đợi ~30 giây để Neo4j sẵn sàng:
```bash
docker-compose logs -f neo4j
# Đợi thấy: "Started."
```

### 1.2. Cài dependencies (nếu chưa có)

```bash
cd ai-service
pip install requests tqdm

# Hoặc cài từ requirements.txt
pip install -r requirements.txt
```

---

## Bước 2: Import PDF Files

### 2.1. Import TẤT CẢ PDF trong folder

```bash
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL"
```

**Output mẫu:**
```
======================================================================
🚀 STARTING IMPORT
======================================================================
Source:      C:\Users\DELL\Documents\VBPLHHL
API:         http://localhost:8000
Files:       150
Max workers: 3
API version: v2
======================================================================

Importing: 100%|████████████████████| 150/150 [08:45<00:00,  3.51s/file]

======================================================================
📊 IMPORT SUMMARY
======================================================================
✅ Success: 145/150
❌ Failed:  5/150
⏭️  Skipped: 0/150

📦 Total chunks created: 4,523

❌ Failed files:
   1. Bo_Luat_Dan_Su_2015.pdf
      Error: HTTP 500: Internal server error
   ...

💾 Full log saved to: import_legal_docs.log
======================================================================

⏱️  Total time: 525.32 seconds
📈 Average: 3.50 seconds/file
```

---

### 2.2. Test với 10 files đầu tiên

Để test trước:

```bash
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \
  --limit 10
```

---

### 2.3. Tùy chỉnh số threads

Mặc định: 3 threads song song

**Tăng tốc độ** (nếu server mạnh):
```bash
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \
  --max-workers 5
```

**Giảm load** (nếu server yếu):
```bash
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \
  --max-workers 1
```

---

### 2.4. Sử dụng v1 API (nếu v2 lỗi)

```bash
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \
  --use-v1
```

---

## Bước 3: Kiểm Tra Kết Quả

### 3.1. Xem logs

```bash
# Xem file log chi tiết
cat import_legal_docs.log

# Hoặc xem real-time
tail -f import_legal_docs.log
```

### 3.2. Xem JSON results

```bash
# Mặc định: import_results.json
cat import_results.json

# Hoặc tùy chỉnh output file
python scripts/import_legal_docs.py \
  --source "..." \
  --output my_results.json
```

**Format JSON:**
```json
{
  "success": [
    {
      "status": "success",
      "file": "C:/Users/.../BLDS_2015.pdf",
      "title": "BLDS 2015",
      "result": {
        "document_id": "abc123",
        "chunks_created": 45,
        "file_type": ".pdf"
      }
    }
  ],
  "failed": [
    {
      "status": "failed",
      "file": "C:/Users/.../corrupted.pdf",
      "title": "corrupted",
      "error": "HTTP 500: OCR failed"
    }
  ],
  "skipped": []
}
```

---

### 3.3. Kiểm tra Neo4j

Mở browser: http://localhost:7474

Query để xem chunks đã import:

```cypher
// Đếm tổng chunks
MATCH (c:Chunk)
RETURN count(c) as total_chunks

// Xem chunks theo file_type
MATCH (c:Chunk)
RETURN c.file_type, count(c) as count
ORDER BY count DESC

// Xem 10 chunks mới nhất
MATCH (c:Chunk)
RETURN c.title, c.source_path, c.token_count
ORDER BY c.updated_at DESC
LIMIT 10

// Tìm kiếm theo keyword
MATCH (c:Chunk)
WHERE c.text CONTAINS "hợp đồng thuê"
RETURN c.title, c.text
LIMIT 5
```

---

## Bước 4: Test Chatbot với Knowledge mới

### 4.1. Test search API

```bash
curl -X POST "http://localhost:8000/admin/risk-knowledge/query-v2" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Điều 472 BLDS 2015 quy định gì về hợp đồng thuê nhà?",
    "top_k": 5
  }'
```

### 4.2. Test chatbot

```bash
# Upload hợp đồng mới
curl -X POST "http://localhost:8000/v2/chatbot/upload" \
  -F "file=@test_contract.pdf" \
  -F "session_id=test-001" \
  -F "initial_question=Phân tích hợp đồng theo BLDS 2015"

# Chatbot giờ sẽ trích dẫn chính xác từ PDFs đã import!
```

---

## Xử Lý Lỗi Thường Gặp

### Lỗi 1: "Connection refused"

**Nguyên nhân:** AI Service chưa chạy

**Fix:**
```bash
cd ai-service
docker-compose up -d
docker-compose logs -f  # Đợi "Started."
```

---

### Lỗi 2: "HTTP 500: OCR failed"

**Nguyên nhân:** 
- PDF bị corrupt
- PDF là scan chất lượng thấp
- PaddleOCR gặp lỗi

**Fix:**
- Bỏ qua file lỗi (script tự động retry rồi skip)
- Hoặc pre-process PDF với Adobe/ghostscript:

```bash
# Optimize PDF trước khi import
gs -sDEVICE=pdfwrite \
   -dCompatibilityLevel=1.4 \
   -dPDFSETTINGS=/ebook \
   -dNOPAUSE -dQUIET -dBATCH \
   -sOutputFile=output.pdf \
   input.pdf
```

---

### Lỗi 3: "Timeout"

**Nguyên nhân:** PDF quá lớn (>100 trang)

**Fix:** Tăng timeout trong script:

```python
# Sửa trong import_legal_docs.py, dòng ~90
response = requests.post(url, files=files, data=data, timeout=300)  # 5 phút
```

---

### Lỗi 4: Neo4j out of memory

**Nguyên nhân:** Quá nhiều chunks

**Fix:** Tăng memory cho Neo4j:

```yaml
# Sửa docker-compose.yml
neo4j:
  environment:
    - NEO4J_dbms_memory_heap_initial__size=2G
    - NEO4J_dbms_memory_heap_max__size=4G
```

Restart:
```bash
docker-compose down
docker-compose up -d
```

---

## Tips & Best Practices

### 1. Chia nhỏ batch nếu có nhiều files

```bash
# Batch 1: 0-100
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \
  --limit 100

# Batch 2: Rename hoặc move 100 files đầu, rồi chạy tiếp
```

---

### 2. Kiểm tra disk space

Neo4j data có thể lớn:

```bash
# Check Docker volumes
docker system df -v

# Clean up nếu cần
docker volume prune
```

---

### 3. Backup Neo4j trước khi import nhiều

```bash
docker exec neo4j neo4j-admin dump \
  --database=neo4j \
  --to=/data/backup-$(date +%Y%m%d).dump
```

---

### 4. Monitor trong khi import

**Terminal 1** (import):
```bash
python scripts/import_legal_docs.py --source "..."
```

**Terminal 2** (monitor):
```bash
# Watch Docker stats
docker stats

# Watch Neo4j logs
docker-compose logs -f neo4j

# Watch API logs
docker-compose logs -f api
```

---

## Advanced: Batch Processing Script

Tạo script tự động chia batch:

```bash
#!/bin/bash
# auto_import.sh

SOURCE="C:/Users/DELL/Documents/VBPLHHL"
BATCH_SIZE=50
MAX_WORKERS=3

# Count files
TOTAL=$(find "$SOURCE" -name "*.pdf" | wc -l)
echo "Total PDFs: $TOTAL"

# Process in batches
for ((i=0; i<$TOTAL; i+=$BATCH_SIZE)); do
    echo "Processing batch: $i - $((i+BATCH_SIZE))"
    
    python scripts/import_legal_docs.py \
        --source "$SOURCE" \
        --max-workers $MAX_WORKERS \
        --output "results_batch_$i.json"
    
    echo "Batch complete. Sleeping 30s..."
    sleep 30
done

echo "All batches complete!"
```

---

## Kết Quả Mong Đợi

Sau khi import thành công:

✅ **Knowledge Base đầy đủ hơn:**
- Chatbot trích dẫn chính xác điều luật
- Error detection dựa trên văn bản gốc
- Legal basis search chính xác hơn

✅ **Chatbot thông minh hơn:**
```
User: "Điều 472 BLDS 2015 quy định gì?"

Bot: "Điều 472 BLDS 2015 quy định về hợp đồng cho thuê tài sản:

[Trích dẫn chính xác từ PDF đã import]

1. Khái niệm: Hợp đồng cho thuê tài sản là...
2. Nội dung: Các bên phải thỏa thuận về...
3. Nghĩa vụ: Bên cho thuê phải...

Căn cứ: Bộ Luật Dân Sự 2015, Điều 472, trang 145."
```

---

## Tổng Kết

**Ưu điểm:**
- ⚡ Nhanh: 3-5 giây/file với multi-threading
- 🛡️ An toàn: Auto retry, error handling
- 📊 Theo dõi: Progress bar + logs chi tiết
- 🔄 Linh hoạt: Dễ tùy chỉnh

**Lưu ý:**
- Import 150 PDFs (~2-3GB) mất khoảng 8-15 phút
- Neo4j sẽ tốn thêm ~1-2GB disk space
- Lần đầu import chậm hơn (do PaddleOCR download models)

---

Bắt đầu import ngay:

```bash
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \
  --max-workers 3
```

Có vấn đề? Check `import_legal_docs.log`!
