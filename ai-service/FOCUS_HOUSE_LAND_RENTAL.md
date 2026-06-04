# Focus: Hợp đồng Thuê Nhà & Thuê Đất

## 🎯 Mục tiêu

**Simplify AI Service** để chỉ focus vào **2 loại hợp đồng chính:**

1. **HOUSE_RENTAL** - Hợp đồng thuê nhà ở
2. **LAND_RENTAL** - Hợp đồng thuê đất (THÊM MỚI)

**Lý do:**
- Giảm complexity của system
- Focus vào use case chính của project
- Dễ maintain và test hơn

---

## 📊 Hiện tại vs Sau khi Filter

### Hiện tại (6 loại hợp đồng)
```python
CONTRACT_TYPES = {
    "HOUSE_PURCHASE": "Hợp đồng mua bán nhà ở",
    "HOUSE_RENTAL": "Hợp đồng thuê nhà ở",           # ✅ GIỮ LẠI
    "LAND_TRANSFER": "Hợp đồng chuyển nhượng đất",
    "LAND_DEPOSIT": "Hợp đồng đặt cọc chuyển nhượng",
    "SERVICE_CONTRACT": "Hợp đồng dịch vụ",
    "COMMERCIAL_CONTRACT": "Hợp đồng thương mại",
}
```

### Sau khi Filter (2 loại)
```python
CONTRACT_TYPES = {
    "HOUSE_RENTAL": "Hợp đồng thuê nhà ở",           # ✅ Focus chính
    "LAND_RENTAL": "Hợp đồng thuê đất",              # ✅ Focus chính (NEW)
}
```

**Note:** `LAND_RENTAL` chưa có trong baseline, cần thêm vào!

---

## 🔧 Changes Needed

### 1. Update `app/graph/schema.py`

#### Filter CONTRACT_TYPES
```python
CONTRACT_TYPES: Dict[str, str] = {
    "HOUSE_RENTAL": "Hợp đồng thuê nhà ở",
    "LAND_RENTAL": "Hợp đồng thuê đất",  # NEW
}
```

#### Update CONTRACT_REQUIRED_CLAUSES
```python
CONTRACT_REQUIRED_CLAUSES: Dict[str, List[str]] = {
    "HOUSE_RENTAL": [
        "PARTY_INFO", "OBJECT", "PAYMENT", "DEPOSIT", "HANDOVER",
        "RIGHTS_OBLIGATIONS", "TERMINATION", "PENALTY", "DISPUTE",
    ],
    "LAND_RENTAL": [  # NEW
        "PARTY_INFO", "OBJECT", "PAYMENT", "DEPOSIT", "HANDOVER",
        "RIGHTS_OBLIGATIONS", "TERMINATION", "TAX_FEE", "PENALTY", "DISPUTE",
    ],
}
```

#### Update CONTRACT_RISK_MAP
```python
CONTRACT_RISK_MAP: Dict[str, List[str]] = {
    "HOUSE_RENTAL": [
        "DEPOSIT_RISK", "TERMINATION_RISK", "HANDOVER_RISK"
    ],
    "LAND_RENTAL": [  # NEW
        "DEPOSIT_RISK", "TERMINATION_RISK", "HANDOVER_RISK",
        "LAND_LEGAL_STATUS_RISK", "TAX_FEE_RISK"
    ],
}
```

---

### 2. Filter Legal Corpus (Optional)

**Hiện tại:** 21 legal articles cover tất cả 6 loại hợp đồng

**Có thể giữ nguyên** vì:
- Legal corpus là system knowledge chung
- Các điều luật vẫn apply cho thuê nhà/đất
- Không cần filter ra

**Hoặc filter nếu muốn:**
- Chỉ giữ articles liên quan đến thuê/rental
- Remove articles về mua bán, chuyển nhượng

---

### 3. Update Validation trong `app/api/documents.py`

#### Thêm validation cho contract_type
```python
from enum import Enum

class AllowedContractType(str, Enum):
    HOUSE_RENTAL = "HOUSE_RENTAL"
    LAND_RENTAL = "LAND_RENTAL"

@router.post("/upload")
async def upload_document(
    file: UploadFile = File(...),
    user_id: str = Form(...),
    contract_type: AllowedContractType = Form(
        default=AllowedContractType.HOUSE_RENTAL,
        description="Loại hợp đồng: HOUSE_RENTAL hoặc LAND_RENTAL"
    ),
    ...
):
    ...
```

---

### 4. Update Swagger Documentation

#### Update description trong legal_analysis.py
```python
@router.post(
    "/classify-contract",
    summary="Classify contract type (HOUSE_RENTAL or LAND_RENTAL only)",
    description="""
    **Supported types:**
    - HOUSE_RENTAL: Hợp đồng thuê nhà ở
    - LAND_RENTAL: Hợp đồng thuê đất
    
    Other contract types are not supported in this version.
    """,
)
```

---

## 🧪 Testing Plan

### 1. Re-seed với 2 loại contract types
```bash
# Drop existing data
docker compose down -v
docker compose up -d

# Re-seed với 2 loại mới
python -c "import httpx; httpx.post('http://localhost:8000/api/ai/graph/seed/all')"
```

### 2. Test classification
```bash
# Should work
classify("Hợp đồng thuê nhà")  # → HOUSE_RENTAL ✅
classify("Hợp đồng thuê đất")  # → LAND_RENTAL ✅

# Should still classify but may not analyze well
classify("Hợp đồng mua bán")   # → UNKNOWN or best-effort
```

### 3. Test document upload
```bash
# Should work
POST /documents/upload {contract_type: "HOUSE_RENTAL"} ✅
POST /documents/upload {contract_type: "LAND_RENTAL"} ✅

# Should reject (if we add validation)
POST /documents/upload {contract_type: "LAND_TRANSFER"} ❌
```

---

## ⚠️ Về Thư Viện "Nặng"

### Hiện tại AI Service KHÔNG dùng thư viện nặng!

**KHÔNG có:**
- ❌ `torch` (hàng trăm MB)
- ❌ `transformers` (hàng GB)
- ❌ `sentence-transformers`
- ❌ Local embedding models

**Chỉ dùng:**
- ✅ `PyPDF2` (~2MB) - Đọc PDF
- ✅ `python-docx` (~1MB) - Đọc DOCX
- ✅ `python-multipart` (~100KB) - Upload files

**Embedding Service:**
- **Primary**: Gemini API (cloud) - KHÔNG download model
- **Fallback**: Lexical matching (pure Python) - KHÔNG cần dependencies

### Proof
```bash
# Check requirements.txt
cat ai-service/requirements.txt

# Output:
fastapi
uvicorn[standard]
neo4j
python-dotenv
pydantic
pydantic-settings
httpx
pytest
PyPDF2            # <-- Nhẹ, chỉ 2MB
python-docx       # <-- Nhẹ, chỉ 1MB
python-multipart  # <-- Siêu nhẹ
```

**Kết luận:** Bạn của bạn lo lắng thừa! Không có thư viện nặng nào cả 😊

---

## 🎯 Recommended Actions

### Option A: Minimal Changes (RECOMMENDED)
**Chỉ update validation, giữ nguyên baseline:**

1. ✅ Update `app/api/documents.py` - Add enum validation cho 2 loại
2. ✅ Update Swagger docs - Clarify only 2 types supported
3. ✅ Update `app/graph/schema.py` - Add LAND_RENTAL
4. ✅ Keep legal corpus as-is (vẫn hữu ích)

**Advantages:**
- Ít code changes
- Backward compatible (existing seeds vẫn work)
- Legal corpus vẫn comprehensive

---

### Option B: Full Cleanup
**Remove tất cả contract types không dùng:**

1. ⚠️ Filter `CONTRACT_TYPES` trong schema.py
2. ⚠️ Remove unused mappings
3. ⚠️ Filter legal corpus
4. ⚠️ Re-seed Neo4j from scratch

**Advantages:**
- Codebase cleaner
- Ít confusion

**Disadvantages:**
- Breaking change (cần re-seed tất cả)
- Mất flexibility (khó mở rộng sau này)

---

## 💡 My Recommendation

**Go with Option A:**

1. **Add LAND_RENTAL** vào schema
2. **Add validation** trong documents.py
3. **Update docs** để rõ ràng
4. **Keep baseline as-is** (legal corpus vẫn hữu ích cho context)

**Why?**
- Ít breaking changes
- Flexible cho tương lai
- Legal corpus general vẫn có giá trị

---

## 🚀 Implementation

Bạn muốn tôi implement Option A hay Option B?

**Option A** - Quick, safe, backward compatible  
**Option B** - Clean, minimal, nhưng breaking changes

Cho tôi biết bạn chọn option nào! 😊
