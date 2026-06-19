# 🎓 HƯỚNG DẪN TRAIN AI VỚI VĂN BẢN PHÁP LUẬT

## 🎯 Mục Tiêu

Import tất cả file PDF trong folder `C:\Users\DELL\Documents\VBPLHHL` vào Knowledge Base để:
- ✅ Chatbot trích dẫn chính xác điều luật
- ✅ Error detection dựa trên văn bản gốc BLDS 2015
- ✅ Legal advice đầy đủ và chính xác hơn

---

## 📋 QUICK START (3 Bước)

### Bước 1: Khởi động AI Service

```bash
cd ai-service
docker-compose up -d
```

Đợi 30 giây để Neo4j sẵn sàng.

---

### Bước 2: Import PDF Files

**Option A: Script Python (Recommended)**

```bash
cd ai-service

# Install dependencies (lần đầu)
pip install requests tqdm

# Import ALL PDFs
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \
  --max-workers 3
```

**Option B: Double-click batch file (Windows)**

```
1. Mở folder: ai-service/scripts/
2. Double-click: import_vbpl.bat
3. Đợi hoàn thành
```

---

### Bước 3: Test Knowledge Base

```bash
cd ai-service

# Test xem AI đã học được chưa
python scripts/test_knowledge_base.py
```

**Expected output:**
```
✅ Neo4j connection OK
✅ Bot trích dẫn BLDS 2015!
✅ Knowledge Base is good!
Overall Coverage: 85.0%
```

---

## 📊 Kết Quả Mong Đợi

### Trước khi import:

```
User: "Điều 472 BLDS 2015 quy định gì?"

Bot: "Không đủ dữ liệu."
```

### Sau khi import:

```
User: "Điều 472 BLDS 2015 quy định gì?"

Bot: "Điều 472 BLDS 2015 quy định về hợp đồng cho thuê tài sản:

**1. Khái niệm:**
Hợp đồng cho thuê tài sản là sự thỏa thuận giữa các bên, theo đó 
bên cho thuê giao tài sản cho bên thuê để sử dụng trong thời hạn 
nhất định; bên thuê phải trả tiền thuê và trả lại tài sản khi hết 
hạn thuê.

**2. Nội dung hợp đồng phải có:**
- Tên, đặc điểm của tài sản thuê
- Thời hạn thuê
- Giá cho thuê và phương thức thanh toán
- Quyền và nghĩa vụ các bên
- Phương thức giao nhận tài sản

**Căn cứ pháp lý:**
- Bộ Luật Dân Sự 2015, Điều 472
- Nguồn: Bo_Luat_Dan_Su_2015.pdf (trang 156)"
```

---

## 🔧 Chi Tiết Kỹ Thuật

### Các tham số quan trọng:

```bash
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL" \  # Folder chứa PDF
  --max-workers 3 \                             # Số threads (1-5)
  --limit 10 \                                  # Test với 10 files đầu
  --use-v1 \                                    # Dùng v1 API (nếu v2 lỗi)
  --output results.json                         # File kết quả
```

### Performance:

- **1 file PDF:** ~3-5 giây
- **100 files:** ~8-12 phút (với 3 workers)
- **150 files:** ~12-18 phút

### Disk Space:

- **Neo4j data:** +1-2GB (cho 150 PDFs)
- **Docker images:** ~3GB

---

## 📁 Files Được Tạo

Sau khi chạy script:

```
ai-service/
├── import_legal_docs.log       ← Logs chi tiết
├── import_results.json         ← Kết quả JSON
└── scripts/
    ├── import_legal_docs.py    ← Import script
    ├── test_knowledge_base.py  ← Test script
    └── import_vbpl.bat         ← Windows batch file
```

---

## 🐛 Troubleshooting

### 1. "Cannot connect to AI service"

```bash
# Check if running
docker-compose ps

# Restart
docker-compose restart

# View logs
docker-compose logs -f
```

---

### 2. "HTTP 500: OCR failed"

**Nguyên nhân:** PDF bị corrupt hoặc scan chất lượng thấp

**Fix:** Script tự động retry và skip. File lỗi sẽ trong `import_results.json`:

```json
{
  "failed": [
    {
      "file": "corrupted.pdf",
      "error": "HTTP 500: OCR failed"
    }
  ]
}
```

Bỏ qua files lỗi hoặc pre-process PDF trước.

---

### 3. "Timeout"

**Fix:** Tăng timeout trong `import_legal_docs.py`:

```python
# Line ~90
response = requests.post(url, files=files, data=data, timeout=300)  # 5 phút
```

---

### 4. Import chậm

**Nguyên nhân:** PaddleOCR đang download models lần đầu

**Fix:** Đợi lần đầu xong, lần sau sẽ nhanh hơn.

---

### 5. Neo4j out of memory

**Fix:** Tăng memory:

```yaml
# ai-service/docker-compose.yml
neo4j:
  environment:
    - NEO4J_dbms_memory_heap_max__size=4G  # Tăng từ 2G lên 4G
```

Restart:
```bash
docker-compose down
docker-compose up -d
```

---

## 📈 Monitoring

### Xem progress real-time:

**Terminal 1** (Import):
```bash
python scripts/import_legal_docs.py --source "..."
```

**Terminal 2** (Monitor):
```bash
# Docker stats
docker stats

# Neo4j logs
docker-compose logs -f neo4j

# API logs
docker-compose logs -f api
```

---

### Xem chunks trong Neo4j:

Mở browser: http://localhost:7474

```cypher
// Đếm chunks
MATCH (c:Chunk)
RETURN count(c)

// Xem 10 chunks mới nhất
MATCH (c:Chunk)
RETURN c.title, c.source_path
ORDER BY c.updated_at DESC
LIMIT 10

// Search
MATCH (c:Chunk)
WHERE c.text CONTAINS "hợp đồng thuê"
RETURN c.title, c.text
LIMIT 5
```

---

## 🎓 Advanced Usage

### 1. Import từng batch

```bash
# Batch 1: 50 files đầu
python scripts/import_legal_docs.py \
  --source "..." \
  --limit 50 \
  --output batch1.json

# Batch 2: Move 50 files đã import, chạy lại
# (hoặc tạo subfolder)
```

---

### 2. Import chỉ một loại văn bản

```bash
# Chỉ import BLDS
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL/BLDS"

# Chỉ import Luật Nhà ở
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL/Luat_Nha_o"
```

---

### 3. Re-import nếu có update

```bash
# Xóa old chunks (nếu cần)
# Mở Neo4j browser: http://localhost:7474
# Run: MATCH (c:Chunk) DETACH DELETE c

# Import lại
python scripts/import_legal_docs.py --source "..."
```

---

## 📝 Checklist

Sau khi import xong:

- [ ] Check logs: `cat import_legal_docs.log`
- [ ] Check results: `cat import_results.json`
- [ ] Test queries: `python scripts/test_knowledge_base.py`
- [ ] Test chatbot với real contract
- [ ] Verify Neo4j chunks: http://localhost:7474
- [ ] Backup Neo4j data (nếu production)

---

## 🚀 Next Steps

Sau khi train AI thành công:

1. **Test chatbot:** `python test_chatbot.py`
2. **Improve AI:** Xem `CHATBOT_ROADMAP.md`
3. **Build frontend:** React UI
4. **Add authentication**
5. **Deploy production**

---

## 📚 Tài Liệu Liên Quan

- `IMPORT_LEGAL_DOCS.md` - Chi tiết về import script
- `QUICK_START_CHATBOT.md` - Test chatbot
- `CHATBOT_ROADMAP.md` - Roadmap phát triển
- `README.md` - Tổng quan dự án

---

## ❓ Câu Hỏi Thường Gặp

**Q: Import mất bao lâu?**  
A: ~8-15 phút cho 150 PDFs (với 3 workers)

**Q: Tốn bao nhiêu disk space?**  
A: ~1-2GB cho Neo4j data

**Q: Import có ảnh hưởng chatbot đang chạy?**  
A: Không, có thể import trong khi chatbot đang serve

**Q: Có cần re-train khi thêm PDF mới?**  
A: Không, chỉ cần import thêm là chatbot tự động dùng ngay

**Q: Làm sao biết import thành công?**  
A: Chạy `python scripts/test_knowledge_base.py`

**Q: Import lỗi nhiều, làm sao?**  
A: Check `import_legal_docs.log`, thường do PDF corrupt. Bỏ qua OK.

---

## 🎉 Kết Luận

Sau khi import thành công, AI chatbot sẽ:
- ✅ Trích dẫn chính xác điều luật từ BLDS 2015
- ✅ Giải thích lỗi dựa trên văn bản gốc
- ✅ Tư vấn pháp lý đầy đủ hơn
- ✅ Confidence cao hơn trong detection

**Bắt đầu ngay:**

```bash
cd ai-service
python scripts/import_legal_docs.py \
  --source "C:/Users/DELL/Documents/VBPLHHL"
```

Chúc bạn thành công! 🚀
