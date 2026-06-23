# 🚀 AI Service với Gemini Flash 2.5

## Tổng quan

AI Service đã được tích hợp **Gemini Flash 2.5** để sinh câu trả lời thông minh, chuyên nghiệp cho câu hỏi rà soát hợp đồng.

## Cải tiến chính

### Trước (Không có LLM)
```
📋 Tìm thấy 5 điểm cần lưu ý:
- Thanh toán: Tìm thấy 2 đoạn liên quan
- Thời hạn: Tìm thấy 1 đoạn liên quan
...
```

### Sau (Có Gemini)
```
# KẾT QUẢ RÀ SOÁT HỢP ĐỒNG

## ⚠️ CÁC ĐIỂM CẦN LƯU Ý

### 1. Thanh toán - RỦI RO CAO

**Nội dung:** "Thanh toán trong vòng 30 ngày. Không quy định mức phạt chậm trả"

**Phân tích:**
- ❌ Thiếu điều khoản phạt chậm thanh toán
- ⚠️  Bên mua có thể chậm thanh toán mà không chịu hậu quả

**Khuyến nghị:**
- Bổ sung mức phạt: 0.05%/ngày
- Tham khảo Điều 296 BLDS 2015
```

## Setup nhanh

### 1. Install dependencies
```bash
pip install -r requirements.txt
```

### 2. Lấy Gemini API key
1. Truy cập: https://aistudio.google.com/app/apikey
2. Tạo API key
3. Copy key

### 3. Config .env
```bash
GEMINI_API_KEY=AIzaSy...your_key_here
GEMINI_MODEL=gemini-2.0-flash-exp
```

### 4. Start service
```bash
python run_dev.py
```

### 5. Test
```bash
python comprehensive_test.py
```

## Tính năng Gemini

✅ **Phân tích chi tiết** - Giải thích từng vấn đề cụ thể
✅ **Đánh giá rủi ro** - Phân loại mức độ rủi ro (Cao/Trung bình/Thấp)
✅ **Khuyến nghị** - Đưa ra cách khắc phục
✅ **Trích dẫn** - Quote chính xác từ văn bản
✅ **Căn cứ pháp lý** - Dẫn chiếu điều luật liên quan
✅ **Ngôn ngữ** - Chuyên nghiệp nhưng dễ hiểu

## Fallback Mode

Nếu không config Gemini API key:
- ✅ Service vẫn hoạt động
- ✅ Vẫn tìm được checklists và chunks
- ⚠️  Dùng answer generation đơn giản hơn

## Test Cases

File `comprehensive_test.py` test với nhiều loại câu hỏi:

### 1. Rà soát tổng quát
- "Có vấn đề gì với văn bản này không?"
- "Có vấn đề nào cần lưu ý không?"
- "Có rủi ro gì không?"

### 2. Yêu cầu kiểm tra
- "Kiểm tra văn bản này"
- "Rà soát hợp đồng này"
- "Phân tích văn bản này"

### 3. Hợp lệ không
- "Văn bản này có hợp lệ không?"
- "Hợp đồng có ổn không?"
- "Có thể ký được không?"

### 4. Câu hỏi ngắn/mơ hồ
- "Có vấn đề j"
- "Review đi"
- "Tài liệu này thế nào?"

### 5. Tiếng Anh
- "What's wrong with this document?"
- "Any issues?"

## Chi phí

**Gemini Flash 2.5** rất rẻ:
- Free: 1500 requests/day
- Paid: ~$0.01/query

## Files quan trọng

```
ai-service/
├── app/services/gemini_service.py    # Gemini integration
├── app/services/rag_service.py        # RAG logic (updated)
├── comprehensive_test.py              # Test suite
├── GEMINI_SETUP.md                    # Setup guide
└── .env                               # Config (add GEMINI_API_KEY)
```

## Workflow

1. User hỏi: "Có vấn đề gì với văn bản này không?"
2. System detect: Global review query
3. Load 20 universal checklists
4. Search user documents với từng checklist
5. Search knowledge base
6. **Gemini sinh answer** từ context
7. Return structured response

## Monitoring

Check logs để xem Gemini status:
```bash
# Service start
✅ Gemini LLM initialized successfully

# Hoặc
⚠️  Gemini LLM not configured - using fallback
```

## Next Steps

1. ✅ Setup Gemini API key
2. ✅ Run comprehensive test
3. ✅ Review sample answers
4. 🔄 Fine-tune prompts nếu cần
5. 🔄 Add caching nếu cần optimize
6. 🔄 Monitor usage & costs

## Documentation

- **QUICKSTART.md** - Quick start guide
- **GEMINI_SETUP.md** - Gemini configuration
- **TESTING.md** - Testing guide
- **IMPLEMENTATION_SUMMARY.md** - Full implementation details
