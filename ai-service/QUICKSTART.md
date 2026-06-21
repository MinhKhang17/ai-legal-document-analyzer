# 🚀 Quick Start Guide - AI Service Testing

## Mục tiêu
Test AI service để xử lý câu hỏi chung chung như "Có vấn đề gì với văn bản này không?"

## Yêu cầu
- ✅ Neo4j đang chạy tại `bolt://localhost:7687`
- ✅ Neo4j có dữ liệu (Chunks, Documents)
- ✅ Python 3.11+

## Bước 1: Chuẩn bị

### Windows
```bash
cd ai-service
start.bat
```

### Linux/Mac
```bash
cd ai-service
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python run_dev.py
```

## Bước 2: Đợi service khởi động

Service sẽ:
1. ✅ Kết nối Neo4j
2. ✅ Load embedding model (mất ~1-2 phút lần đầu)
3. ✅ Seed checklist (20 items - chỉ lần đầu)
4. ✅ Sẵn sàng nhận request

Khi thấy dòng này là OK:
```
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:8000
```

## Bước 3: Test

### Cách 1: Browser (Đơn giản nhất)

Mở browser, truy cập:
```
http://localhost:8000/test/query?user_id=user_demo&question=Có vấn đề gì với văn bản này không?
```

### Cách 2: Script

Mở terminal mới:
```bash
cd ai-service
python test_simple.py
```

### Cách 3: Swagger UI

Mở browser:
```
http://localhost:8000/docs
```

Thử endpoint `/test/query` hoặc `/internal/rag/query`

### Cách 4: curl

```bash
curl "http://localhost:8000/test/query?user_id=user_demo&question=Có vấn đề gì với văn bản này không?"
```

## Kết quả mong đợi

```json
{
  "request_id": "test_...",
  "success": true,
  "answer": "📋 **KẾT QUẢ RÀ SOÁT HỢP ĐỒNG**...",
  "total_checklist_items": 20,
  "total_user_chunks": 15,
  "total_knowledge_chunks": 5,
  "processing_time_ms": 1234.56
}
```

## Các câu hỏi test khác

1. **Câu hỏi chung**:
   - "Có vấn đề gì với văn bản này không?"
   - "Rà soát tài liệu này"
   - "Kiểm tra hợp đồng"
   - "Đánh giá văn bản"

2. **Câu hỏi cụ thể**:
   - "Hợp đồng có điều khoản thanh toán không?"
   - "Thời hạn hợp đồng là bao lâu?"
   - "Các bên có được xác định rõ ràng không?"

## Troubleshooting

### Service không start
```bash
# Check Neo4j
curl http://localhost:7474

# Check port 8000 có bị chiếm
netstat -ano | findstr :8000
```

### Không có kết quả
```bash
# Check data trong Neo4j
python scripts/check_neo4j_data.py
```

### Lỗi module
```bash
# Reinstall
pip install -r requirements.txt --force-reinstall
```

## Next Steps

Sau khi test OK:
1. ✅ Cải thiện answer quality (integrate LLM)
2. ✅ Thêm xử lý câu hỏi cụ thể
3. ✅ Integrate với Backend Spring Boot
4. ✅ Add caching
