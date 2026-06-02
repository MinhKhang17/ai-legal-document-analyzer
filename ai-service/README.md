# LexiGuard AI — AI Service

Python FastAPI service powering LexiGuard AI's Legal-Tech features: legal
knowledge graph (Neo4j), RAG context retrieval, contract analysis and contract
comparison. Designed to be called over HTTP by the Spring Boot backend.

## Tech stack
- Python 3.11+ / FastAPI / Uvicorn
- Neo4j Python Driver
- Pydantic + pydantic-settings
- httpx (LLM HTTP clients)

## Project layout
```
app/
  main.py                      # app wiring, CORS, exception handlers, routers
  core/
    config.py                  # env-based settings (no hard-coded secrets)
    errors.py                  # error codes + AppError hierarchy
  graph/
    connection.py              # Neo4j driver lifecycle
    schema.py                  # legal taxonomy + baseline seed definitions
    legal_corpus.py            # verified legal documents + articles (citable)
    repository.py              # raw Neo4j data access
    service.py                 # graph domain logic + idempotent seed
  services/
    rag_service.py             # RAG context retrieval (graph + embeddings)
    legal_service.py           # classify / analyze / compare (LLM + fallback)
    legal_prompt_builder.py    # grounded prompts + few-shot (JSON output)
    llm_client.py              # LLM abstraction: Noop/Gemini/Ollama/vLLM
    embedding_service.py       # Gemini embeddings + lexical fallback, cached
  api/
    health.py  graph.py  rag.py  legal_analysis.py  deps.py
  models/
    common.py  graph_models.py  rag_models.py  legal_models.py
client.py                      # Python client SDK (httpx)
tests/                         # pytest suite (TestClient-based)
```

## Configuration
Copy `.env.example` to `.env` and adjust as needed. Key variables:

| Variable | Default | Notes |
|----------|---------|-------|
| `NEO4J_URI` | `bolt://localhost:7687` | |
| `NEO4J_USERNAME` | `neo4j` | `NEO4J_USER` also accepted |
| `NEO4J_PASSWORD` | `password` | |
| `LLM_PROVIDER` | `NONE` | `NONE\|GEMINI\|OLLAMA\|VLLM` |
| `LLM_API_KEY` | _(empty)_ | required for GEMINI |
| `LLM_MODEL` | _(provider default)_ | e.g. `gemini-1.5-flash`, `llama3` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | |
| `VLLM_BASE_URL` | `http://localhost:8001` | |
| `AI_SERVICE_API_KEY` | _(empty)_ | when set, guards `/graph/query` & `/graph/seed/*` via `X-AI-Service-Key` |
| `CORS_ALLOW_ORIGINS` | `http://localhost:8080,http://localhost:5173` | comma-separated |

No secret is hard-coded; everything is read from the environment.

## Run
```bash
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```
Swagger UI: http://127.0.0.1:8000/docs

All API endpoints are served under a configurable prefix (`API_PREFIX`,
default `/api/ai`), e.g. `http://127.0.0.1:8000/api/ai/health`. This lets the
Spring Boot gateway route everything with a single `/api/ai/**` rule. The
interactive docs (`/docs`) and the root (`/`) stay un-prefixed.

## Seed the knowledge graph
The baseline catalog (ContractType / RiskType / ClauseType + relationships) and
the verified legal corpus (LegalDocument / LegalArticle) are seeded
idempotently (MERGE-based, safe to run repeatedly):
```bash
# Everything in one call (baseline + corpus)
curl -X POST http://127.0.0.1:8000/api/ai/graph/seed/all
# Or individually
curl -X POST http://127.0.0.1:8000/api/ai/graph/seed/legal-baseline
curl -X POST http://127.0.0.1:8000/api/ai/graph/seed/legal-corpus
# Or via script
python test_seed.py
```

The legal corpus links real, verifiable Vietnamese statute articles (e.g.
Điều 328 Bộ luật Dân sự 2015 về đặt cọc) to the relevant ClauseType/RiskType
nodes so analysis can cite a concrete legal basis instead of inventing one.
Article text is summarized/paraphrased, not reproduced verbatim. To extend the
corpus, add verified entries to `app/graph/legal_corpus.py` and re-run the seed.

## Tests (smoke)
```bash
python test_connection.py        # Neo4j connectivity
python test_seed.py              # idempotent baseline + corpus seed
python test_query.py             # ContractType query returns data
python test_embeddings.py        # semantic ranking (Gemini or lexical)
python test_rag_retrieve.py      # RAG returns related risks + legal articles
python test_analyze_contract.py  # analyze returns structured JSON
python test_compare_contracts.py # compare returns clauseComparisons
python test_gemini.py            # live Gemini call (skips without key)
python test_llm_not_configured.py# clean fallback when no LLM
```

## Tests (pytest suite)
A structured pytest suite lives in `tests/`. It uses FastAPI's in-process
TestClient (no running server needed) and skips Neo4j/LLM tests automatically
when those dependencies are unavailable.
```bash
python -m pytest            # run everything
python -m pytest -v         # verbose
python -m pytest tests/test_units.py   # pure unit tests (no Neo4j/network)
```

## Python client SDK
`client.py` is a thin httpx wrapper that unwraps the response envelope and
raises `AiServiceError` on failures. Use it from scripts, notebooks, or other
Python services:
```python
from client import AiServiceClient

c = AiServiceClient("http://127.0.0.1:8000")
c.seed_all()
print(c.readiness())
result = c.analyze_contract(
    contract_text="HỢP ĐỒNG THUÊ NHÀ ...",
    contract_type="HOUSE_RENTAL",
    protected_party="bên thuê",
)
```

## API summary

## API summary
All paths below are relative to the prefix `API_PREFIX` (default `/api/ai`).
For example, `GET /health` is served at `GET /api/ai/health`.

### Health
- `GET /health` → `{status, service, version}`
- `GET /health/neo4j` → `{connected, uri}`
- `GET /health/readiness` → `{ready, neo4j, llm}`

### Graph
- `POST /graph/nodes` — create node
- `GET /graph/nodes?label=&keyword=&limit=` — list nodes
- `GET /graph/nodes/{id}` — get node
- `DELETE /graph/nodes/{id}` — delete node
- `POST /graph/relationships` — create relationship
- `POST /graph/query` — raw Cypher (internal/dev, limit-guarded)
- `POST /graph/seed/legal-baseline` — idempotent baseline seed (internal/dev)
- `POST /graph/seed/legal-corpus` — idempotent legal articles seed (internal/dev)
- `POST /graph/seed/all` — baseline + corpus in one call (internal/dev)

### RAG
- `POST /rag/retrieve` — ranked legal context items
- `POST /rag/contract-context` — required checks + related risks + recommendations

### Legal
- `POST /legal/classify-contract`
- `POST /legal/analyze-contract`
- `POST /legal/compare-contracts`

## Response envelope
Success:
```json
{ "success": true, "data": {}, "message": "string", "traceId": "string" }
```
Error:
```json
{ "success": false, "error": { "code": "string", "message": "string", "details": {} }, "traceId": "string" }
```
Error codes: `NEO4J_CONNECTION_FAILED`, `GRAPH_QUERY_FAILED`, `VALIDATION_ERROR`,
`NOT_FOUND`, `UNAUTHORIZED`, `LLM_NOT_CONFIGURED`, `LLM_GENERATION_FAILED`,
`RAG_RETRIEVAL_FAILED`, `INTERNAL_ERROR`.

## LLM behavior & fallback
- `LLM_PROVIDER=NONE` (default): no external LLM is called. `analyze` / `compare`
  / `classify` return deterministic, graph-grounded results flagged with
  `"fallback": true` and `"llmUsed": false`. The service never fabricates an AI
  answer.
- Set `LLM_PROVIDER` to `GEMINI`, `OLLAMA` or `VLLM` (plus the relevant URL/key)
  to enable real generation. The same response schema is returned with
  `"llmUsed": true`.

### Enabling Gemini
1. Create an API key at https://aistudio.google.com/apikey
2. In `.env` set:
   ```
   LLM_PROVIDER=GEMINI
   LLM_API_KEY=AIza...your_key
   LLM_MODEL=gemini-2.5-flash-lite
   ```
3. Verify: `python test_gemini.py`

Notes:
- The key is sent via the `x-goog-api-key` header (not in the URL).
- `gemini-2.5-flash-lite` is recommended on the free tier: it is fast and far
  less prone to `503 high demand` errors than `gemini-2.5-flash`. Any model name
  works via `LLM_MODEL`.
- Transient `429` / `5xx` responses are retried up to 3 times with backoff. If
  the model is still unavailable, the request falls back to the deterministic
  graph analysis (`"fallback": true`) instead of failing.
- When `outputMode=JSON`, Gemini is asked for `responseMimeType=application/json`
  so the structured schema parses reliably.

## How AI quality is improved (no model training)
LexiGuard does not train or fine-tune an LLM. Quality comes from grounding the
model in a curated knowledge graph plus prompt engineering — the safest,
highest-leverage approach for legal text:

1. Semantic RAG (real embeddings). Retrieval uses Gemini `gemini-embedding-001`
   vectors with cosine similarity (`EMBEDDING_PROVIDER=AUTO`), replacing naive
   keyword matching. Embeddings are cached in-memory; if the API is unavailable
   it falls back to lexical scoring so retrieval never breaks. Example: for
   "đặt cọc", `DEPOSIT_RISK` scores ~0.92 vs ~0.69 for unrelated risks.
2. Verified legal corpus. `app/graph/legal_corpus.py` links real statute
   articles to the relevant clauses/risks. Analysis surfaces these as a citable
   basis (e.g. Điều 328 BLDS 2015), and the prompt forbids citing any article
   not present in the supplied context — this curbs hallucinated law.
3. Few-shot prompting. The analyze prompt includes a worked example that anchors
   both the JSON shape and the grounded, conservative reasoning style.

To make it better over time: add more verified entries to `legal_corpus.py`
(broadest impact), expand the few-shot examples, or — only if a labelled
contract dataset becomes available — consider Gemini supervised tuning.

## Spring Boot integration
The backend typically calls (full paths with default prefix):
- `GET  /api/ai/health/readiness`
- `POST /api/ai/legal/analyze-contract`
- `POST /api/ai/legal/compare-contracts`
- `POST /api/ai/rag/retrieve`

All responses share the envelope above, so a single generic
`ApiResponse<T>` wrapper on the Java side can deserialize every endpoint.
CORS allows `localhost:8080` and `localhost:5173` by default.
