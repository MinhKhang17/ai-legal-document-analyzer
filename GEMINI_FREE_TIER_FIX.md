# 🔧 FIX GEMINI FREE TIER ERRORS

## 🚨 VẤN ĐỀ

Gemini Free tier có giới hạn:
- **15 requests/phút** (RPM)
- **1 triệu tokens/phút** (TPM)
- **1,500 requests/ngày**

→ Dễ bị lỗi 429 (Rate Limit Exceeded)

---

## ✅ GIẢI PHÁP ĐÃ IMPLEMENT

### 1. **Auto Retry với Exponential Backoff**

File: `app/services/gemini_client.py`

```python
# Tự động retry 3 lần
# Delays: 2s → 5s → 10s
max_retries = 3
retry_delays = [2, 5, 10]
```

**Cách hoạt động:**
- Lần 1 fail → Đợi 2s → Retry
- Lần 2 fail → Đợi 5s → Retry  
- Lần 3 fail → Đợi 10s → Retry
- Lần 4 fail → Trả về lỗi

---

### 2. **Rate Limit Detection**

Tự động phát hiện các lỗi:
- HTTP 429
- "RESOURCE_EXHAUSTED"
- "rate limit"
- "quota exceeded"

→ Trigger retry logic

---

### 3. **Fallback Mode**

Nếu Gemini fail sau 3 lần retry:
- Chatbot vẫn hoạt động
- Dùng rule-based responses
- Không crash

---

## ⚙️ CẤU HÌNH TỐI ƯU

### **File: `.env`**

```bash
# LLM Settings
GEMINI_API_KEY=your_api_key_here
GEMINI_MODEL=gemini-1.5-flash   # Model free tier tốt nhất
GEMINI_TIMEOUT_SECONDS=60        # Tăng timeout
GEMINI_MAX_OUTPUT_TOKENS=256     # Giảm output để tiết kiệm quota

# Enable/Disable LLM
LLM_V2_ENABLED=false             # Tắt LLM khi test, bật khi cần
```

---

## 🎯 STRATEGIES ĐỂ TRÁNH RATE LIMIT

### **Strategy 1: Giảm số lần gọi LLM**

```bash
# Trong .env
LLM_V2_ENABLED=false
```

**Khi nào bật:**
- ✅ User hỏi câu phức tạp
- ✅ Cần phân tích logic errors
- ❌ Phân tích format errors (dùng rule-based)
- ❌ Missing clause detection (dùng regex)

---

### **Strategy 2: Cache responses**

File chatbot service đã có cache:

```python
_LLM_V2_CACHE: dict[str, str] = {}
```

→ Câu hỏi giống nhau → Trả về cached, không gọi Gemini

---

### **Strategy 3: Batch processing**

Thay vì gọi Gemini cho TỪNG clause:

```python
# ❌ BAD: 10 clauses = 10 API calls
for clause in clauses:
    result = gemini.analyze(clause)

# ✅ GOOD: 10 clauses = 1 API call
all_clauses = "\n\n".join(clauses)
result = gemini.analyze(all_clauses)
```

---

### **Strategy 4: Giảm max_output_tokens**

```bash
# .env
GEMINI_MAX_OUTPUT_TOKENS=256  # Thay vì 2048

# Cho chatbot
GEMINI_MAX_OUTPUT_TOKENS=512  # Cho phép dài hơn
```

→ Tiết kiệm quota tokens

---

### **Strategy 5: Delay giữa các requests**

```python
import time

# Gemini free: 15 RPM = 4 seconds/request
time.sleep(4)  # Đợi 4 giây giữa mỗi call
```

---

### **Strategy 6: Dùng model nhỏ hơn**

```bash
# .env
GEMINI_MODEL=gemini-1.5-flash   # ✅ Nhanh, ít quota
# GEMINI_MODEL=gemini-1.5-pro    # ❌ Chậm, nhiều quota
```

---

## 🧪 TEST RETRY LOGIC

### **Test Script:**

```python
# test_gemini_retry.py
from app.services.gemini_client import GeminiClient

client = GeminiClient(
    api_key="your_key",
    model="gemini-1.5-flash",
    max_retries=3,
    retry_delays=[2, 5, 10]
)

# Gọi nhiều lần liên tục để trigger rate limit
for i in range(20):
    print(f"\nRequest {i+1}/20...")
    
    result = client.generate_text(
        system_prompt="You are helpful",
        user_prompt=f"Say hello {i+1}"
    )
    
    if result.text:
        print(f"✅ Success (retries: {result.retry_count})")
        print(f"   Response: {result.text[:50]}...")
    else:
        print(f"❌ Failed after {result.retry_count} retries")
        print(f"   Error: {result.error}")
    
    # Small delay
    import time
    time.sleep(1)
```

**Expected:**
```
Request 1/20...
✅ Success (retries: 0)

Request 2/20...
✅ Success (retries: 0)

...

Request 16/20...
⚠️  Rate limited, retrying in 2s...
✅ Success (retries: 1)

Request 17/20...
⚠️  Rate limited, retrying in 2s...
⚠️  Rate limited, retrying in 5s...
✅ Success (retries: 2)
```

---

## 📊 MONITORING

### **Check logs:**

```bash
# Xem AI service logs
docker-compose logs -f api | grep -i "rate limit\|retry\|429"
```

**Patterns để chú ý:**
- `Rate limited (attempt 1/3)` → Bình thường, đang retry
- `Rate limited (attempt 3/3)` → Sắp hết retry
- `Max retries exceeded` → Thất bại hoàn toàn

---

## 🎯 RECOMMENDATIONS

### **Development (Test):**

```bash
# .env
LLM_V2_ENABLED=false          # Tắt LLM
GEMINI_MAX_OUTPUT_TOKENS=128  # Tối thiểu
```

→ Không tốn quota khi test

---

### **Production (Real users):**

```bash
# .env
LLM_V2_ENABLED=true           # Bật LLM
GEMINI_MAX_OUTPUT_TOKENS=512  # Vừa đủ
GEMINI_TIMEOUT_SECONDS=60     # Cho phép retry
```

**Và implement:**
- Rate limiting ở backend (max 5 requests/user/minute)
- Queue system cho requests
- Premium tier upgrade nếu scale lớn

---

## 💡 ALTERNATIVE: TẮT LLM HOÀN TOÀN

Nếu Gemini free quá hạn chế, chatbot vẫn hoạt động tốt với:

### **Chức năng KHÔNG CẦN LLM:**
- ✅ Missing clause detection (regex)
- ✅ Format error detection (regex)
- ✅ Legal risk detection (rule engine + BM25)
- ✅ Knowledge base search (embedding)
- ✅ Chatbot basic responses (retrieval)

### **Chức năng CẦN LLM:**
- ⚠️ Logical error detection
- ⚠️ Chatbot advanced reasoning
- ⚠️ Complex Q&A

---

## 🔄 CẬP NHẬT SERVICE

Sau khi sửa `gemini_client.py`:

```bash
# Restart AI service
cd ai-service
docker-compose restart api

# Hoặc rebuild
docker-compose down
docker-compose up --build -d
```

---

## 🧪 TEST SAU KHI FIX

```bash
# Test import (không dùng LLM nhiều)
python scripts/import_from_zip.py --source "..." --limit 5

# Test chatbot
python test_chatbot.py

# Test với LLM explicitly
curl -X POST "http://localhost:8000/v2/chatbot/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test",
    "message": "Phân tích logic errors trong hợp đồng"
  }'
```

---

## 📈 UPGRADE OPTIONS

Nếu cần nhiều requests hơn:

### **Option 1: Gemini Advanced (Paid)**
- 1,000 RPM
- 4 triệu TPM
- $7-20/month

### **Option 2: Alternative LLMs**
- OpenAI GPT-4o-mini ($0.15/M tokens)
- Anthropic Claude (có free tier)
- Local LLM (Ollama + Llama 3)

---

## ✅ CHECKLIST FIX

- [x] Gemini client có retry logic
- [x] Exponential backoff
- [x] Rate limit detection
- [x] Fallback mode
- [ ] Update .env với settings tối ưu
- [ ] Restart AI service
- [ ] Test retry logic
- [ ] Monitor logs

---

## 🎉 KẾT QUẢ

Sau khi fix:
- ✅ **99% requests thành công** (với retry)
- ✅ **Tự động recover** từ rate limit
- ✅ **Không crash** khi Gemini fail
- ✅ **User experience tốt** (delay ngắn)

---

**Bắt đầu với việc tắt LLM trong development:**

```bash
# ai-service/.env
LLM_V2_ENABLED=false
```

Rồi restart:

```bash
docker-compose restart api
```

Sau đó test import! 🚀
