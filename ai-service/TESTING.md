# AI Service Testing Guide

## Prerequisites

1. **Neo4j running** with data at `bolt://localhost:7687`
2. **Python 3.11+** installed
3. **Dependencies installed**

## Setup

### 1. Install dependencies

```bash
pip install -r requirements.txt
```

### 2. Configure environment

```bash
cp .env.example .env
# Edit .env with your Neo4j credentials
```

### 3. Check Neo4j data

```bash
python scripts/check_neo4j_data.py
```

Expected output:
- ✅ Chunk: 10562 nodes
- ✅ Document: 991 nodes  
- ✅ At least one user with userId (e.g., `user_demo`)

## Running the Service

### Option 1: Direct Python

```bash
python run_dev.py
```

### Option 2: Uvicorn

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Option 3: Docker Compose

```bash
# From project root
docker compose up ai-service
```

## Testing

### 1. Health Check

```bash
curl http://localhost:8000/health
```

Expected:
```json
{
  "status": "healthy",
  "service": "ai-service"
}
```

### 2. Run Test Script

```bash
python scripts/test_api.py
```

### 3. Manual API Test

#### Global Review Query

```bash
curl -X POST http://localhost:8000/internal/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "test_001",
    "user_id": "user_demo",
    "workspace_id": "ws_001",
    "question": "Có vấn đề gì với văn bản này không?",
    "top_k_checklist": 10,
    "top_k_user_chunks_per_checklist": 3,
    "top_k_knowledge_chunks": 5
  }'
```

#### Specific Question

```bash
curl -X POST http://localhost:8000/internal/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "test_002",
    "user_id": "user_demo",
    "workspace_id": "ws_001",
    "question": "Hợp đồng có điều khoản thanh toán không?",
    "top_k_checklist": 10,
    "top_k_user_chunks_per_checklist": 3,
    "top_k_knowledge_chunks": 5
  }'
```

## Expected Behavior

### On Startup

1. Connects to Neo4j
2. Loads embedding model (may take 1-2 minutes first time)
3. Seeds ReviewChecklist (only first time)
4. Ready to accept requests

### On Global Query ("Có vấn đề gì...")

1. Detects as global review query
2. Loads 10-25 universal checklists
3. For each checklist, searches user's documents
4. Returns structured results with:
   - Checklist items that found relevant content
   - Extracted chunks from user's documents
   - Knowledge base recommendations

### On Specific Query

1. Detects as specific question
2. Returns error (not yet implemented - phase 2)

## Troubleshooting

### Service won't start

**Error: Cannot connect to Neo4j**
```bash
# Check Neo4j is running
docker ps | grep neo4j

# Or check locally
curl http://localhost:7474
```

**Error: Module not found**
```bash
# Make sure you're in ai-service directory
cd ai-service
pip install -r requirements.txt
```

### No results returned

**Check if checklist was seeded:**
```bash
python scripts/check_neo4j_data.py | grep ReviewChecklist
```

Should see:
```
- ReviewChecklist
ReviewChecklist: 20 nodes
```

**Check user has documents:**
```cypher
// In Neo4j Browser
MATCH (doc:Document {userId: "user_demo"})
RETURN count(doc);
```

Should return > 0

**Check embeddings exist:**
```cypher
// In Neo4j Browser  
MATCH (chunk:Chunk)
WHERE chunk.embedding IS NOT NULL
RETURN count(chunk);
```

Should return > 0

### Low quality results

1. **Increase top_k parameters** in request
2. **Lower similarity threshold** in neo4j_client.py (change `score > 0.3` to `score > 0.2`)
3. **Check embedding model** is loaded correctly

## Development Tips

### Watch logs

```bash
# Service logs will show:
# - Embedding generation
# - Checklist retrieval count
# - Chunk search results
# - Processing time
```

### Interactive testing

Use Swagger UI at http://localhost:8000/docs

### Debug mode

Set in code:
```python
logging.basicConfig(level=logging.DEBUG)
```

## Next Steps

After basic testing works:

1. Integrate LLM for answer generation
2. Implement specific query handling  
3. Add caching for embeddings
4. Connect with Backend API
5. Add authentication
