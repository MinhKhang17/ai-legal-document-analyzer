# Integration report

| Source → destination | Protocol/config/endpoint | Auth, timeout, retry/fallback | Status and demo dependency |
|---|---|---|---|
| React → Spring Boot | HTTP JSON/multipart; `VITE_API_BASE_URL`, endpoint keys in `frontend/.env.example`; `/api/v1/*` | Bearer access JWT; refresh cookie with credentials; one 401 refresh/retry; page-specific errors | Implemented; base URL/CORS/cookie domain required |
| React → FastAPI | HTTP for risk/contract/knowledge service clients; `VITE_AI_SERVICE_BASE_URL` | No AI-layer auth; browser timeout/error handling varies | Connected for some screens but unsafe if public; prefer backend proxy |
| Spring → FastAPI | HTTP `RestClient` in `PythonAiClient`, `KnowledgeBaseAiClient` and dispatchers; base `app.ai-service.base-url`; RAG/doc/KB endpoints | No service credential; connect 5 s/read 130 s for main client; maps timeout/HTTP errors; Gemini/retrieval fallbacks live downstream | Implemented; AI container required; security blocker |
| Spring → PostgreSQL | JDBC/JPA/Flyway; `spring.datasource.*` | DB credentials; Hikari/managed pooling; transaction rollback; no alternate database | Implemented; authoritative business store |
| FastAPI → Neo4j | Bolt; `NEO4J_URI/USER/PASSWORD`; graph and vector queries | Basic Neo4j credential; driver exceptions trigger search fallbacks where possible | Implemented; RAG/ingestion dependency |
| Backend/AI → shared files | Filesystem paths; `app.storage.upload-root`, `DOCUMENT_IMPORT_DIR`, Compose `shared_uploads` | OS/container permissions only; database ownership enforced before backend access | Implemented; mount/path consistency required |
| FastAPI → Gemini | HTTPS via `google-genai`; model/key/base URL settings | API key; 120 s; up to 4 attempts, two-second backoff, primary/fallback model; retrieval/rule-only safe fallback | Implemented; model availability/quota/network required |
| Spring → Gmail/SMTP | SMTP 587 STARTTLS; `spring.mail.*`, `app.mail.*` | Username/app password; async sends; delivery status and finance retry scheduler | Implemented; SMTP credentials required |
| Spring/browser → VNPay | HTTPS sandbox pay URL; return/IPN at payment controller; VND | Merchant code/hash secret and signature verification; transaction status guards; no alternate provider | Implemented; externally dependent and callback URL must be reachable |
| Browser → ChatGPT | `window.open` with copied/URL prompt from `draftingActions.ts` | No API credential or response callback; browser popup/URL length/privacy behavior | Implemented as redirect only, not an integration that returns a contract |
| ngrok → Spring | HTTP tunnel in `docker-compose.yml`, dashboard 4040 | ngrok token/domain; exposes backend | Development-only; increases exposure of public/internal routes |
| Nginx → React assets | HTTP 5173, SPA `try_files` | None; static caching | Implemented |
| Spring → XLSX download | Apache POI in-process, HTTP binary | Role/ownership on revenue endpoints | Implemented; finance data required |

## Cross-service consistency

Document upload is a saga: relational/file creation, AI submission, Neo4j writes and callback status. KB publication similarly splits lifecycle state in PostgreSQL and searchable content in Neo4j. There is no distributed transaction; job/status/error fields and repair routes are the recovery mechanism. Generated contracts and ticket context also carry identifiers between services. Demo data should be checked in all three stores before presentation.

## Domain, proxy and CORS

Nginx serves the SPA but does not proxy `/api`; the browser therefore needs the backend URL directly. Spring CORS must list the exact frontend origin and allow credentials. VNPay, email verification, refunds and commission verification embed frontend/backend URLs, and ngrok/cloudflare URLs must match callback and cookie settings. `docker-compose.simple.yml` is not equivalent to the full stack because it omits Neo4j while retaining the AI service.

## Error behavior

Frontend `http.ts` parses the common API envelope, reports machine codes, refreshes on 401 once and avoids uncontrolled subscription retries. Spring `GlobalExceptionHandler` maps domain/validation/missing-route/AI errors to JSON. FastAPI returns Pydantic 422 for invalid request context. Main gaps are inconsistent status/envelope across direct FastAPI calls, no circuit breaker, no general Java-side retry, and no authenticated service-to-service boundary.
