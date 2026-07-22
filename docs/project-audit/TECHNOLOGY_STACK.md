# Technology stack

Versions are from manifests/images, not guesses.

## Frontend

| Technology | Version | Location/purpose | Current use and risk |
|---|---:|---|---|
| React / React DOM | 18.3.1 | `frontend/package.json`; SPA UI | Active; 50 page files |
| TypeScript | 5.7.2 | `tsconfig*.json` | Strict compile via `tsc`; passes |
| Vite | declared 6.0.3, resolved 6.4.2 | `vite.config.ts`, lockfile | Build/dev server; resolved-version drift should be understood |
| React Router | 6.28 | `frontend/src/routes/router.tsx` | 52 concrete patterns plus wildcard/fallbacks |
| Context state | React Context | `frontend/src/store/AppStore.tsx` | Auth/theme/language/sidebar; no Redux/Zustand |
| HTTP | Axios 1.18.1 plus `fetch` wrapper | `frontend/src/services/http.ts` | Bearer injection and one refresh/retry; mixed clients increase consistency risk |
| Tailwind CSS | 3.4.17 | `tailwind.config.js`, PostCSS | Primary styling; lucide-react 0.468 icons, clsx 2.1.1 |
| Forms/validation | Hand-written state/HTML rules | page/component files | No form-schema library; validation duplicated across screens |
| Vitest | 3.2.7 | eight `*.test.*` files | 25 tests pass; no browser E2E |
| Nginx | 1.27-alpine | `frontend/Dockerfile`, `nginx.conf` | Static SPA fallback; build uses `npm install`, not deterministic `npm ci` |

Auth keeps user metadata in `localStorage` but access token in module memory (`authSession.ts`/`http.ts`); refresh uses an HttpOnly-style cookie with credentials and a single-flight refresh. Vite environment keys are documented in `frontend/.env.example`.

## Backend

| Technology | Version | Location/purpose | Current use and risk |
|---|---:|---|---|
| Java | target 21; audit host 23 | `backend/pom.xml`, Dockerfile | Spring service |
| Spring Boot | 4.0.6 | parent POM | Web, Validation, Data JPA, Security, Mail |
| Maven | wrapper / 3.9.9 image | `mvnw*`, Dockerfile | Build/test/package |
| Spring Security | Boot-managed | `security/SecurityConfig.java` | Stateless JWT, method security, BCrypt |
| JJWT | 0.12.5 | POM, `JwtTokenProvider` | Signed access tokens; refresh tokens persisted/hashed by service |
| Hibernate/JPA | ORM 7.2.12 seen in tests | entities/repositories | PostgreSQL persistence, schema validation |
| PostgreSQL JDBC | Boot-managed | POM/application config | Primary relational database |
| Flyway | Boot-managed | 23 files in `db/migration` | Versioned migrations; no duplicate version detected |
| Jackson 3 | Boot 4 `tools.jackson.*` | chat JSON mapping | Migrated application code; avoid reintroducing Jackson 2 bean |
| MapStruct / Lombok | 1.6.3 / managed | POM | DTO mapping/boilerplate |
| Apache POI | 5.4.1 | POM, revenue export | XLSX exports |
| Spring Mail | managed | `EmailServiceImpl`, `application.yml` | Gmail SMTP default; external credentials required |
| VNPay | custom | payment service/controller | VND checkout, return and IPN; sandbox default |
| springdoc OpenAPI | 3.0.3 | POM/config | `/v3/api-docs`, `/swagger-ui.html` |
| JUnit/Mockito/Testcontainers | Boot test / 2.0.5 | 38 test source files | 128 run, 8 E2E skipped without Docker |

Files are stored on the filesystem under `app.storage.upload-root`; ticket attachments have a separate root and size/count settings. Logging is SLF4J/Logback. Risks include SQL logging enabled by default, hard-coded development JWT/database/admin defaults, raw seeded-password logging, and broad public route matchers.

## AI service

| Technology | Version/config | Location/purpose | Current use and risk |
|---|---|---|---|
| Python | 3.11 image | `ai-service/Dockerfile` | FastAPI runtime |
| FastAPI / Uvicorn | 0.109 / 0.27 | `requirements.txt`, `app/main.py` | 25 route handlers |
| Pydantic | 2.12.5 | schemas/config | Request/response validation |
| Neo4j driver | 5.16 | requirements | Talks to Neo4j 5.26; compatible family but version drift |
| Gemini SDK | google-genai 2.11 | `gemini_client.py`, config | Primary/fallback generation, retry/backoff |
| Embeddings | hashing default; MiniLM available; Gemini fallback | `core/config.py`, `embedding_service.py` | 384 dimensions; deterministic hashing is not semantic-quality equivalent |
| Parsing/OCR | pypdf 6, python-docx 1.2, Pillow 11.3, pdf2image 1.17, PaddleOCR/PaddlePaddle | ingestion services | PDF/DOCX/image extraction; large image/runtime footprint |
| Chunking | 400–600 target, 800 hard; v2 120–220/320; legal char splitter 4000/500 overlap | config/chunk services | Multiple chunkers can yield inconsistent retrieval granularity |
| Retrieval | vector + Neo4j full-text + legal score/dedupe | `graph/repository.py`, `knowledge_api.py`, `legal_rag` | Top-k configurable; keyword fallback on index failure |
| Prompt/context | code templates and `docs/flow` source files | RAG/drafting services | Structured history, citations, missing-info checks |
| Tests | pytest | 16 Python test files | Full run is reported in `TEST_AND_BUILD_REPORT.md` |

Two settings classes exist (`app/config.py` and `app/core/config.py`) with different `llm_v2_enabled` defaults; this is a material configuration ambiguity. The configured model names must be verified against the actual Gemini account at deployment.

## Data/storage and infrastructure

PostgreSQL 16 owns transactional records; Neo4j 5.26-community owns `KnowledgeNode` hierarchies/vectors and `ConversationTurn`; filesystem volumes own binaries. PostgreSQL `Document.id`/knowledge version `aiDocumentId` is carried in Neo4j metadata, so updates are cross-database and non-atomic.

Compose builds three application containers and two databases; Nginx fronts only the SPA and ngrok tunnels the backend. Health checks exist for Neo4j and some services, while `docker-compose.simple.yml` omits Neo4j despite retaining AI service configuration. CI files under `.github/workflows` only echo placeholder commands and do not perform checkout/build/test. No monitoring/APM stack is present beyond container/application logs.
