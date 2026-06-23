# 🤖 Gemini Flash 2.5 Setup Guide

## Lấy Gemini API Key

### Bước 1: Truy cập Google AI Studio
```
https://aistudio.google.com/app/apikey
```

### Bước 2: Tạo API Key
1. Click "Create API Key"
2. Chọn project (hoặc tạo mới)
3. Copy API key

### Bước 3: Cấu hình trong .env

Mở file `.env` và thêm API key:

```bash
# Gemini API
GEMINI_API_KEY=AIzaSy...your_actual_key_here
GEMINI_MODEL=gemini-2.0-flash-exp
```

**Models có sẵn:**
- `gemini-2.0-flash-exp` - Gemini 2.0 Flash (Latest, Recommended)
- `gemini-1.5-flash` - Gemini 1.5 Flash
- `gemini-1.5-pro` - Gemini 1.5 Pro (Slower but more powerful)

## Test Gemini Integration

### 1. Start service
```bash
cd ai-service
python run_dev.py
```

### 2. Check logs
Bạn sẽ thấy:
```
✅ Gemini LLM initialized successfully
```

Hoặc nếu chưa config:
```
⚠️  Gemini LLM not configured - using fallback answer generation
```

### 3. Test query
```bash
python test_simple.py
```

## Fallback Mode

Nếu không có API key, service vẫn chạy nhưng dùng **fallback answer generation** (không thông minh bằng):

- ✅ Service vẫn hoạt động bình thường
- ✅ Vẫn tìm được checklists và chunks
- ⚠️  Answer đơn giản hơn, ít insight hơn
- ⚠️  Không có phân tích sâu từ LLM

## Gemini Features

Khi có Gemini, AI sẽ:

✅ **Phân tích chi tiết** từng điều khoản
✅ **Chỉ ra rủi ro cụ thể** với giải thích
✅ **Đưa ra khuyến nghị** cải thiện
✅ **Trích dẫn chính xác** từ văn bản
✅ **Đánh giá tổng quan** mức độ rủi ro
✅ **Ngôn ngữ chuyên nghiệp** dễ hiểu

## Pricing

**Gemini Flash 2.5** rất rẻ:
- Free tier: 1500 requests/day
- Input: $0.075 / 1M tokens
- Output: $0.30 / 1M tokens

Ước tính chi phí mỗi query: **< $0.01** (rất rẻ!)

## Troubleshooting

### Error: API key not valid
```bash
# Kiểm tra lại API key trong .env
cat .env | grep GEMINI_API_KEY

# Đảm bảo không có khoảng trắng thừa
GEMINI_API_KEY=AIzaSy...  # ❌ SAI
GEMINI_API_KEY=AIzaSy...  # ✅ ĐÚNG
```

### Error: Model not found
```bash
# Thử dùng model khác
GEMINI_MODEL=gemini-1.5-flash
```

### Error: Quota exceeded
```bash
# Đợi 24h hoặc upgrade plan
# Hoặc dùng fallback mode tạm thời
```

## So sánh với/không Gemini

### Không có Gemini (Fallback)
```
📋 KẾT QUẢ RÀ SOÁT HỢP ĐỒNG

⚠️ CÁC ĐIỂM CẦN LƯU Ý

1. Thanh toán
Câu hỏi: Hợp đồng có quy định rõ số tiền, thời hạn...

Nội dung tìm thấy:
- "Giá trị hợp đồng: 500.000.000 VNĐ..."
```

### Có Gemini
```
# KẾT QUẢ RÀ SOÁT HỢP ĐỒNG

## ⚠️ CÁC ĐIỂM CẦN LƯU Ý

### 1. Thanh toán - RỦI RO CAO

**Nội dung tìm thấy:**
"Giá trị hợp đồng: 500.000.000 VNĐ. Thanh toán trong vòng 30 ngày. 
Hậu quả chậm thanh toán: Không quy định rõ mức phạt."

**Phân tích rủi ro:**
- ❌ Không có quy định về lãi suất chậm trả
- ❌ Không có mức phạt vi phạm cụ thể
- ⚠️  Bên mua có thể chậm thanh toán mà không chịu hậu quả

**Khuyến nghị:**
- Bổ sung điều khoản phạt chậm thanh toán: 0.05%/ngày
- Quy định rõ thời hạn tối đa được chậm
- Tham khảo Điều 296 BLDS 2015 về lãi chậm trả
```

## Next Steps

Sau khi setup Gemini:
1. ✅ Test với nhiều loại câu hỏi
2. ✅ Fine-tune prompt nếu cần
3. ✅ Monitor API usage
4. ✅ Cache results nếu cần optimize
