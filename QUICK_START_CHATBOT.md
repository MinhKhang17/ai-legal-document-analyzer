# 🚀 QUICK START: Test Chatbot Ngay

## Bước 1: Khởi động AI Service

```bash
cd ai-service
docker-compose up -d
```

Đợi Neo4j khởi động xong (~30 giây), kiểm tra:
```bash
curl http://localhost:8000/health
# Kết quả: {"status":"ok"}
```

---

## Bước 2: Import Knowledge Base (Lần đầu)

Import dữ liệu risk knowledge vào Neo4j:

```bash
# Test import
curl -X POST "http://localhost:8000/admin/risk-knowledge/import-v2" \
  -F "file=@ai-service/docs/flow/RISK_KB_RENTAL_VI.md" \
  -F "title=Risk Knowledge Base - Thuê Nhà"
```

---

## Bước 3: Test Chatbot API

### 3.1. Upload hợp đồng + Chat

**Prepare test file:**
Tạo file `test_contract.txt` với nội dung:

```
HỢP ĐỒNG THUÊ NHÀ

Bên A: Nguyễn Văn A
Bên B: Trần Thị B

Nhà cho thuê tại 123 Đường ABC

Giá thuê: 5 triệu đồng/tháng
```

**Upload và chat:**
```bash
curl -X POST "http://localhost:8000/v2/chatbot/upload" \
  -F "file=@test_contract.txt" \
  -F "session_id=test-session-001" \
  -F "initial_question=Hợp đồng này có lỗi gì không?" \
  -F "title=Test Contract"
```

**Response mẫu:**
```json
{
  "session_id": "test-session-001",
  "message": "📋 **Phân tích hợp đồng: Test Contract**\n\n**Tổng số lỗi:** 8\n- 🔴 Nghiêm trọng (HIGH): 6\n- 🟡 Trung bình (MEDIUM): 2\n...",
  "sources": [
    {
      "error_id": "MISSING_001",
      "title": "Thiếu: Thời hạn thuê",
      "severity": "HIGH",
      "category": "missing_clause"
    }
  ],
  "suggestions": [
    "Các lỗi nghiêm trọng (HIGH) cần sửa ngay là gì?",
    "Những điều khoản bắt buộc nào đang bị thiếu?",
    "Làm sao để sửa các lỗi hình thức?"
  ],
  "context_updated": true
}
```

---

### 3.2. Continue Chat

```bash
curl -X POST "http://localhost:8000/v2/chatbot/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test-session-001",
    "message": "Giải thích chi tiết lỗi MISSING_001"
  }'
```

**Response mẫu:**
```json
{
  "session_id": "test-session-001",
  "message": "**Thiếu: Thời hạn thuê**\n\nID: MISSING_001\nLoại: missing_clause\nMức độ: HIGH\n\n**Mô tả:**\nHợp đồng thiếu điều khoản bắt buộc về 'Thời hạn thuê'...\n\n**Gợi ý sửa:**\nBổ sung thời hạn thuê rõ ràng: ngày bắt đầu, ngày kết thúc...\n\n**Căn cứ pháp lý:**\nĐiều 472 BLDS 2015",
  "sources": [...],
  "suggestions": []
}
```

---

### 3.3. Ask về cách sửa

```bash
curl -X POST "http://localhost:8000/v2/chatbot/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "session_id": "test-session-001",
    "message": "Làm sao để sửa lỗi thiếu thời hạn thuê? Cho tôi mẫu cụ thể"
  }'
```

---

### 3.4. Xem lịch sử chat

```bash
curl "http://localhost:8000/v2/chatbot/sessions/test-session-001/history"
```

---

### 3.5. Clear session

```bash
curl -X POST "http://localhost:8000/v2/chatbot/clear-session?session_id=test-session-001"
```

---

## Bước 4: Test với Postman/Insomnia

Import collection này:

```json
{
  "info": {
    "name": "FindRisk Chatbot API"
  },
  "item": [
    {
      "name": "Upload Contract & Chat",
      "request": {
        "method": "POST",
        "url": "http://localhost:8000/v2/chatbot/upload",
        "body": {
          "mode": "formdata",
          "formdata": [
            {
              "key": "file",
              "type": "file",
              "src": "/path/to/contract.pdf"
            },
            {
              "key": "session_id",
              "value": "{{session_id}}",
              "type": "text"
            },
            {
              "key": "initial_question",
              "value": "Phân tích hợp đồng này",
              "type": "text"
            }
          ]
        }
      }
    },
    {
      "name": "Continue Chat",
      "request": {
        "method": "POST",
        "url": "http://localhost:8000/v2/chatbot/chat",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"session_id\": \"{{session_id}}\",\n  \"message\": \"Giải thích lỗi FORMAT_001\"\n}"
        }
      }
    },
    {
      "name": "Get History",
      "request": {
        "method": "GET",
        "url": "http://localhost:8000/v2/chatbot/sessions/{{session_id}}/history"
      }
    }
  ]
}
```

---

## Bước 5: Test với Python Script

```python
import requests
import json

API_BASE = "http://localhost:8000"
SESSION_ID = "python-test-001"

# 1. Upload contract
with open("test_contract.pdf", "rb") as f:
    response = requests.post(
        f"{API_BASE}/v2/chatbot/upload",
        files={"file": f},
        data={
            "session_id": SESSION_ID,
            "initial_question": "Hợp đồng có lỗi gì?",
            "title": "My Contract",
        }
    )
    result = response.json()
    print("Upload result:")
    print(json.dumps(result, indent=2, ensure_ascii=False))

# 2. Continue chat
questions = [
    "Giải thích các lỗi HIGH",
    "Làm sao sửa lỗi FORMAT_001?",
    "So sánh với mẫu chuẩn BLDS 2015",
]

for question in questions:
    response = requests.post(
        f"{API_BASE}/v2/chatbot/chat",
        json={
            "session_id": SESSION_ID,
            "message": question,
        }
    )
    result = response.json()
    print(f"\nQ: {question}")
    print(f"A: {result['message'][:200]}...")

# 3. Get history
response = requests.get(f"{API_BASE}/v2/chatbot/sessions/{SESSION_ID}/history")
history = response.json()
print(f"\nTotal messages: {len(history)}")
```

---

## Bước 6: Kiểm tra Neo4j (Optional)

Mở browser: http://localhost:7474

Username: `neo4j`  
Password: `password`

Query để xem chunks:
```cypher
MATCH (c:Chunk)
RETURN c.title, c.text
LIMIT 10
```

---

## 🐛 Troubleshooting

### 1. "ModuleNotFoundError: No module named 'chatbot_service'"

**Fix:**
```bash
cd ai-service
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### 2. "Gemini not configured"

Chatbot vẫn hoạt động nhưng dùng fallback responses (không có LLM).

**Để bật LLM:**
```bash
# File: ai-service/.env
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-1.5-flash
LLM_V2_ENABLED=true
```

Restart:
```bash
docker-compose restart api
```

### 3. Neo4j connection error

```bash
# Wait for Neo4j to be ready
docker-compose logs neo4j

# Should see: "Started."
```

### 4. Session not found

Sessions lưu in-memory, mất khi restart. Implement Redis để persistent.

---

## 📊 Expected Results

**Good response characteristics:**
- ✅ Response trong 2-5 giây
- ✅ Phát hiện 5-10 lỗi cho hợp đồng đơn giản
- ✅ Giải thích rõ ràng bằng tiếng Việt
- ✅ Trích dẫn đúng BLDS 2015
- ✅ Context awareness (nhớ câu hỏi trước)

**Known limitations:**
- ⚠️ Session mất khi restart (cần Redis)
- ⚠️ No streaming (response chờ hết mới trả về)
- ⚠️ Vietnamese NLP chưa optimal
- ⚠️ No authentication

---

## 🎯 Next Steps

Sau khi test xong, tiếp tục:

1. **Improve Chatbot Intelligence** (xem CHATBOT_ROADMAP.md Phase 1.1)
2. **Add Redis** cho session persistence
3. **Build Frontend UI** (React)
4. **Add Backend API** với authentication

Có câu hỏi? Check `CHATBOT_ROADMAP.md` cho roadmap đầy đủ!
