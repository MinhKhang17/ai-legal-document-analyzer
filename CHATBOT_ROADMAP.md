# 🤖 ROADMAP HOÀN THIỆN AI CHATBOT BẮT LỖI HỢP ĐỒNG

## 📊 Hiện Trạng

### ✅ Đã có (AI Service)
- ✅ API phát hiện lỗi hợp đồng (`/v2/contracts/find-errors`)
- ✅ 4 loại lỗi: Missing clauses, Format, Logical, Legal risks
- ✅ Gemini LLM integration
- ✅ Knowledge base thuê nhà/đất
- ✅ Neo4j vector search
- ✅ OCR cho PDF/DOCX

### 🆕 Vừa tạo
- ✅ Chatbot API (`/v2/chatbot`)
- ✅ ChatbotService với conversation memory
- ✅ Multi-turn conversation support

---

## 🎯 CẦN LÀM TIẾP

### **PHASE 1: Hoàn Thiện AI Service** (1-2 tuần)

#### 1.1. Nâng Cấp Chatbot Intelligence ⭐⭐⭐

**File cần sửa:** `app/services/legal_rag/chatbot_service.py`

**Thêm khả năng:**

```python
# 1. Intent Detection
def _detect_intent(self, message: str) -> str:
    """
    Detect user intent:
    - explain_error: "Giải thích lỗi FORMAT_001"
    - suggest_fix: "Làm sao sửa lỗi này?"
    - compare_standard: "So sánh với mẫu chuẩn"
    - legal_advice: "Điều luật nào quy định?"
    - severity_query: "Lỗi nào nghiêm trọng nhất?"
    """

# 2. Error Explanation Engine
def _explain_error(self, error_id: str) -> str:
    """
    Chi tiết hóa error với:
    - Tại sao đây là lỗi?
    - Hậu quả pháp lý
    - Ví dụ cụ thể
    - Cách sửa step-by-step
    """

# 3. Fix Suggestion Generator
def _generate_fix_template(self, error_id: str) -> str:
    """
    Tạo template để user copy-paste:
    - Mẫu điều khoản chuẩn
    - Điền thông tin cần thiết
    """

# 4. Compare với Contract Standards
def _compare_with_standard(self, report) -> str:
    """
    So sánh với mẫu BLDS 2015:
    - Checklist điều khoản
    - Điểm khác biệt
    - Rating (A, B, C, D, F)
    """
```

**Priority:** HIGH  
**Effort:** 3-4 days

---

#### 1.2. Thêm Vietnamese NLP Processing ⭐⭐

**Cài đặt:**
```bash
pip install underthesea transformers
```

**Thêm file:** `app/services/vietnamese_nlp.py`

```python
"""Vietnamese NLP utilities for better text understanding."""
from underthesea import word_tokenize, pos_tag, ner

class VietnameseNLP:
    def extract_named_entities(self, text: str):
        """Extract names, locations, dates from contract."""
        
    def detect_legal_terms(self, text: str):
        """Detect legal terminology."""
        
    def normalize_text(self, text: str):
        """Normalize Vietnamese text."""
```

**Use case:**
- Tự động extract thông tin: Họ tên, CCCD, địa chỉ, ngày ký
- Highlight legal terms trong contract
- Better search/matching

**Priority:** MEDIUM  
**Effort:** 2-3 days

---

#### 1.3. Thêm Session Persistence (Redis) ⭐⭐⭐

**Hiện tại:** In-memory storage (mất khi restart)  
**Cần:** Redis để persistent sessions

**File:** `app/services/legal_rag/chatbot_service.py`

```python
import redis
import json

class ChatbotService:
    def __init__(self):
        self.redis_client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            decode_responses=True,
        )
    
    def _save_session(self, session: ChatSession):
        """Save to Redis với TTL 24h."""
        key = f"chat_session:{session.session_id}"
        self.redis_client.setex(
            key,
            86400,  # 24 hours
            json.dumps(session, default=str),
        )
```

**Docker compose:**
```yaml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
```

**Priority:** HIGH (nếu deploy production)  
**Effort:** 1 day

---

#### 1.4. Streaming Response Support ⭐⭐

**Để UI mượt mà hơn, stream response từ LLM:**

```python
from fastapi.responses import StreamingResponse

@router.post("/chat/stream")
async def chat_stream(payload: ChatRequest):
    """Stream response từng chunk."""
    
    async def generate():
        response = ""
        async for chunk in chatbot_service.chat_stream(
            session_id=payload.session_id,
            message=payload.message,
        ):
            response += chunk
            yield f"data: {json.dumps({'chunk': chunk})}\n\n"
        
        yield f"data: {json.dumps({'done': True})}\n\n"
    
    return StreamingResponse(generate(), media_type="text/event-stream")
```

**Priority:** MEDIUM  
**Effort:** 1-2 days

---

### **PHASE 2: Backend API Gateway** (1 tuần)

#### 2.1. Tạo Backend Service (Node.js/Express hoặc FastAPI)

**Folder:** `backend/`

**Chức năng:**
- Authentication & Authorization
- User management
- Contract storage (PostgreSQL)
- Rate limiting
- API gateway cho AI service

**Tech stack option 1 (Node.js):**
```
- Express.js
- PostgreSQL + Prisma ORM
- JWT authentication
- Redis cache
```

**Tech stack option 2 (Python):**
```
- FastAPI
- PostgreSQL + SQLAlchemy
- JWT authentication
- Redis cache
```

**Schema:**
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE contracts (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  title VARCHAR(500),
  filename VARCHAR(255),
  file_path VARCHAR(1000),
  status VARCHAR(50),
  error_count INT,
  analyzed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chat_sessions (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  contract_id UUID REFERENCES contracts(id),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chat_messages (
  id UUID PRIMARY KEY,
  session_id UUID REFERENCES chat_sessions(id),
  role VARCHAR(20),
  content TEXT,
  created_at TIMESTAMP DEFAULT NOW()
);
```

**Priority:** HIGH  
**Effort:** 4-5 days

---

#### 2.2. File Upload & Storage

**Thêm vào backend:**
```javascript
// Express.js example
const multer = require('multer');
const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');

// Local storage (development)
const storage = multer.diskStorage({
  destination: './uploads/',
  filename: (req, file, cb) => {
    cb(null, `${Date.now()}-${file.originalname}`);
  }
});

// S3 storage (production)
async function uploadToS3(file) {
  const s3 = new S3Client({ region: 'ap-southeast-1' });
  await s3.send(new PutObjectCommand({
    Bucket: 'findrisk-contracts',
    Key: `contracts/${Date.now()}-${file.originalname}`,
    Body: file.buffer,
  }));
}

app.post('/api/contracts/upload', upload.single('file'), async (req, res) => {
  // 1. Save file
  const filePath = await uploadToS3(req.file);
  
  // 2. Call AI service
  const analysisResult = await axios.post(
    'http://ai-service:8000/v2/contracts/find-errors',
    formData
  );
  
  // 3. Save to database
  const contract = await db.contract.create({
    data: {
      userId: req.user.id,
      title: req.body.title,
      filename: req.file.originalname,
      filePath: filePath,
      errorCount: analysisResult.data.summary.total_errors,
      analyzedAt: new Date(),
    }
  });
  
  res.json(contract);
});
```

**Priority:** HIGH  
**Effort:** 2 days

---

### **PHASE 3: Frontend (React + TypeScript)** (2 tuần)

#### 3.1. Setup Frontend Project

```bash
cd frontend
npx create-react-app . --template typescript
# hoặc
npm create vite@latest . -- --template react-ts

npm install @tanstack/react-query axios zustand
npm install @chakra-ui/react @emotion/react
npm install react-markdown react-pdf
```

**Folder structure:**
```
frontend/
├── src/
│   ├── components/
│   │   ├── Chat/
│   │   │   ├── ChatWindow.tsx
│   │   │   ├── MessageList.tsx
│   │   │   ├── MessageInput.tsx
│   │   │   └── ErrorCard.tsx
│   │   ├── Contract/
│   │   │   ├── ContractUpload.tsx
│   │   │   ├── ContractViewer.tsx
│   │   │   └── ErrorHighlight.tsx
│   │   └── Layout/
│   │       ├── Navbar.tsx
│   │       └── Sidebar.tsx
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── DashboardPage.tsx
│   │   ├── ChatPage.tsx
│   │   └── ContractListPage.tsx
│   ├── hooks/
│   │   ├── useChat.ts
│   │   ├── useContract.ts
│   │   └── useAuth.ts
│   ├── services/
│   │   ├── api.ts
│   │   └── chatApi.ts
│   └── store/
│       └── authStore.ts
```

**Priority:** HIGH  
**Effort:** 7-10 days

---

#### 3.2. Chat UI Component

**File:** `src/components/Chat/ChatWindow.tsx`

```typescript
import React, { useState, useEffect, useRef } from 'react';
import { useChat } from '../../hooks/useChat';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
}

export const ChatWindow: React.FC<{ sessionId: string }> = ({ sessionId }) => {
  const [input, setInput] = useState('');
  const { messages, sendMessage, isLoading } = useChat(sessionId);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const handleSend = async () => {
    if (!input.trim()) return;
    await sendMessage(input);
    setInput('');
  };

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="chat-window">
      <div className="messages">
        {messages.map((msg, idx) => (
          <div key={idx} className={`message ${msg.role}`}>
            <div className="message-content">
              <ReactMarkdown>{msg.content}</ReactMarkdown>
            </div>
            <div className="message-time">
              {new Date(msg.timestamp).toLocaleTimeString()}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <div className="input-area">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyPress={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          placeholder="Hỏi về lỗi trong hợp đồng..."
          disabled={isLoading}
        />
        <button onClick={handleSend} disabled={isLoading || !input.trim()}>
          {isLoading ? '...' : 'Gửi'}
        </button>
      </div>

      {/* Suggestion chips */}
      <div className="suggestions">
        {['Lỗi nghiêm trọng là gì?', 'Cách sửa lỗi FORMAT_001?', 'So sánh với mẫu chuẩn'].map(
          (suggestion) => (
            <button
              key={suggestion}
              onClick={() => setInput(suggestion)}
              className="suggestion-chip"
            >
              {suggestion}
            </button>
          )
        )}
      </div>
    </div>
  );
};
```

**Priority:** HIGH  
**Effort:** 3-4 days

---

#### 3.3. Contract Upload & Preview

**File:** `src/components/Contract/ContractUpload.tsx`

```typescript
import React, { useState } from 'react';
import { useContractUpload } from '../../hooks/useContract';

export const ContractUpload: React.FC = () => {
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const { uploadContract, isUploading, error } = useContractUpload();

  const handleUpload = async () => {
    if (!file) return;

    const result = await uploadContract(file, title);
    
    if (result.success) {
      // Redirect to chat page
      window.location.href = `/chat/${result.sessionId}`;
    }
  };

  return (
    <div className="upload-container">
      <h2>Upload Hợp Đồng</h2>
      
      <div className="dropzone">
        <input
          type="file"
          accept=".pdf,.docx,.txt"
          onChange={(e) => setFile(e.target.files?.[0] || null)}
        />
        <p>Kéo thả file hoặc click để chọn</p>
        <p>Hỗ trợ: PDF, DOCX, TXT</p>
      </div>

      {file && (
        <div className="file-info">
          <p>File: {file.name}</p>
          <p>Size: {(file.size / 1024 / 1024).toFixed(2)} MB</p>
          
          <input
            type="text"
            placeholder="Tiêu đề hợp đồng (tùy chọn)"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          
          <button onClick={handleUpload} disabled={isUploading}>
            {isUploading ? 'Đang phân tích...' : 'Phân tích hợp đồng'}
          </button>
        </div>
      )}

      {error && <div className="error">{error}</div>}
    </div>
  );
};
```

**Priority:** HIGH  
**Effort:** 2 days

---

#### 3.4. React Hook: useChat

**File:** `src/hooks/useChat.ts`

```typescript
import { useState, useEffect } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { chatApi } from '../services/chatApi';

export const useChat = (sessionId: string) => {
  const [messages, setMessages] = useState<Message[]>([]);

  // Load history
  const { data: history } = useQuery({
    queryKey: ['chat-history', sessionId],
    queryFn: () => chatApi.getHistory(sessionId),
  });

  useEffect(() => {
    if (history) {
      setMessages(history);
    }
  }, [history]);

  // Send message
  const { mutateAsync: sendMessage, isLoading } = useMutation({
    mutationFn: (message: string) =>
      chatApi.sendMessage(sessionId, message),
    onSuccess: (response) => {
      setMessages((prev) => [
        ...prev,
        { role: 'user', content: response.userMessage, timestamp: new Date().toISOString() },
        { role: 'assistant', content: response.message, timestamp: new Date().toISOString() },
      ]);
    },
  });

  return {
    messages,
    sendMessage,
    isLoading,
  };
};
```

**Priority:** HIGH  
**Effort:** 1 day

---

### **PHASE 4: Tính Năng Nâng Cao** (2-3 tuần)

#### 4.1. Real-time Collaboration ⭐⭐
- Multiple users cùng xem và comment hợp đồng
- WebSocket cho real-time updates
- Annotation tools

#### 4.2. Contract Templates ⭐⭐⭐
- Library mẫu hợp đồng chuẩn
- Tự động generate hợp đồng từ template
- Fill-in-the-blank với AI assistance

#### 4.3. Document Comparison ⭐⭐
- So sánh 2 versions của hợp đồng
- Highlight changes
- Track revision history

#### 4.4. Mobile App ⭐
- React Native
- Scan contract bằng camera
- Push notification khi phân tích xong

#### 4.5. Export & Reports ⭐⭐
- Export phân tích thành PDF
- Email report
- Checklist in PDF

#### 4.6. Vietnamese Voice Input ⭐
- Speech-to-text
- Voice commands: "Tìm lỗi nghiêm trọng"

---

## 📋 CHECKLIST THEO THỨ TỰ ƯU TIÊN

### Week 1-2: Core AI & API
- [x] Chatbot API endpoints
- [x] ChatbotService with memory
- [ ] Intent detection
- [ ] Error explanation engine
- [ ] Fix suggestion templates
- [ ] Redis session storage

### Week 3: Backend
- [ ] Setup backend project
- [ ] Database schema
- [ ] Authentication (JWT)
- [ ] File upload endpoint
- [ ] Contract CRUD APIs

### Week 4-5: Frontend Core
- [ ] Setup React project
- [ ] Chat UI component
- [ ] Contract upload page
- [ ] Login/Register pages
- [ ] Dashboard page

### Week 6-7: Frontend Polish
- [ ] Contract viewer với highlight
- [ ] Error cards với details
- [ ] Suggestion chips
- [ ] Responsive design
- [ ] Loading states

### Week 8+: Advanced Features
- [ ] Streaming responses
- [ ] Templates library
- [ ] Document comparison
- [ ] Export reports
- [ ] Vietnamese NLP

---

## 🚀 DEPLOYMENT

### Development
```bash
# AI Service
cd ai-service
docker-compose up -d

# Backend
cd backend
npm run dev

# Frontend
cd frontend
npm start
```

### Production (Docker Compose)
```yaml
version: '3.8'

services:
  ai-service:
    build: ./ai-service
    environment:
      - GEMINI_API_KEY=${GEMINI_API_KEY}
      - NEO4J_URI=bolt://neo4j:7687
    depends_on:
      - neo4j
      - redis

  backend:
    build: ./backend
    environment:
      - DATABASE_URL=postgresql://postgres:password@postgres:5432/findrisk
      - REDIS_URL=redis://redis:6379
      - AI_SERVICE_URL=http://ai-service:8000
    depends_on:
      - postgres
      - redis
    ports:
      - "3000:3000"

  frontend:
    build: ./frontend
    environment:
      - REACT_APP_API_URL=http://backend:3000
    ports:
      - "80:80"

  neo4j:
    image: neo4j:5
    volumes:
      - neo4j_data:/data

  postgres:
    image: postgres:15
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data
```

---

## 📊 KPIs ĐỂ THEO DÕI

### Technical
- Response time < 3s
- Uptime > 99%
- Error rate < 1%
- LLM accuracy > 85%

### Business
- Number of contracts analyzed
- User retention rate
- Errors detected per contract
- User satisfaction (NPS)

---

## 💡 GỢI Ý MONETIZATION

1. **Freemium Model**
   - Free: 5 contracts/month
   - Pro: Unlimited + advanced features
   - Enterprise: Custom deployment

2. **Pay-per-use**
   - $0.50 per contract analysis
   - $0.10 per chat message (với LLM)

3. **SaaS Subscription**
   - Starter: $9/month
   - Professional: $29/month
   - Enterprise: Custom pricing

---

## 🎓 HỌC THÊM & TÀI NGUYÊN

### Vietnamese NLP
- https://github.com/undertheseanlp/underthesea
- VnCoreNLP
- PhoBERT model

### Legal Resources
- BLDS 2015: https://thuvienphapluat.vn/
- Mẫu hợp đồng chuẩn
- Văn bản pháp luật Việt Nam

### Tech Stack
- FastAPI docs: https://fastapi.tiangolo.com/
- React Query: https://tanstack.com/query/
- Gemini API: https://ai.google.dev/

---

Bắt đầu từ **Phase 1.1 - Chatbot Intelligence** để có chatbot thông minh hơn!
