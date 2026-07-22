# Project audit summary

## Executive view

LexiGuard is a three-service legal-document platform: React SPA, Spring Boot business API and FastAPI AI/RAG service, backed by PostgreSQL, Neo4j and shared file storage. It implements identity, policy acceptance, plan/quota/payment/refund, workspaces/documents, cited legal chat, contract generation, knowledge publication, expert tickets, revenue/commission/payout and feedback administration.

The repository is substantially implemented but not production-ready. Frontend and backend compile/tests pass; AI has four correctness/regression failures. Security currently assumes trusted container networking while Compose/ngrok can expose services, and several internal/download routes are explicitly public. External email, Gemini and VNPay were not live-validated. CI is only a placeholder.

## Quantitative inventory

| Measure | Result |
|---|---:|
| Frontend route elements | 54: 52 concrete patterns + 2 wildcard fallbacks |
| Frontend page files | 50 |
| Spring handler methods / controllers | 212 / 31 |
| FastAPI route handlers | 25 |
| Total API handler endpoints | 237 (before Spring alias expansion) |
| JPA entities | 46 |
| Flyway migrations | 23; no duplicate version detected |
| Feature rows | 53 |
| Fully implemented features | 45 |
| Partially implemented | 4 |
| Disconnected/broken | 1 disconnected, 0 explicitly broken |
| Other gaps | 1 mocked, 1 UI-only, 1 not found |

## Stack and architecture

Frontend uses React 18, TypeScript 5.7, Vite 6, React Router, Axios/fetch, Tailwind and Vitest. Backend uses Java 21, Spring Boot 4.0.6, Security/JWT, JPA/Hibernate, PostgreSQL, Flyway, Spring Mail, VNPay, Apache POI and springdoc. AI uses Python 3.11, FastAPI, Pydantic, Gemini, hashing/MiniLM/Gemini embeddings, PDF/DOCX/OCR parsers, Neo4j vector/full-text search and pytest. Docker Compose adds Nginx, PostgreSQL 16, Neo4j 5.26 and ngrok.

PostgreSQL owns transactional identities and business state. Neo4j owns legal hierarchies (`KnowledgeNode` plus `Document`, `Part`, `Chapter`, `Section`, `Article`, `Clause`, `Point`, `Subsection`, `Chunk`), `ReviewChecklist`, `ConversationTurn` and `Technology`. Detected indexes are `chunk_embedding_index`, `legal_chunk_embedding_index`, `review_checklist_embedding_index`, `conversation_turn_embedding_idx`, and full-text `chunk_fulltext_index`.

## Feature conclusions

Ready at code level: auth/email/token lifecycle, effective Free plan, token/storage usage, workspace/document operations, sessions/document scoping, ticket workflows, KB lifecycle, feedback, and finance/revenue operations. Ready only with external/data setup: SMTP flows, paid VNPay flows, live AI analysis, ingestion/publication, expert assignment and revenue/payout.

Partial: downgrade semantics without E2E, persisted contract generation with overlapping/hard-coded retrieval behavior, broad admin-user management, and the mock-named vector record path. Missing uploaded-document versioning. Public shared-ticket UI is disconnected from its public API. System health is UI-only. CI is mocked.

## Security findings

Critical issues are unauthenticated document downloads, unauthenticated internal/admin-ingest callbacks, no FastAPI service authentication, raw seeded-password logging and unsafe fallback credentials. High risks are direct browser-to-AI bypass, broadly authenticated user listing, bearer share URLs, and incomplete external-AI privacy governance. File signature/MIME checks are good but no malware scanner exists. SQL/prompt logs require redaction review.

## Migration/data findings

The prior Flyway duplicate has been resolved in source: versions are unique through `V20260722_07`. `EntityMigrationCoverageTest` passes. This audit did not connect to or mutate any database, so production `flyway_schema_history` checksum/application state is unknown. PostgreSQL, filesystem and Neo4j writes are non-atomic and can drift; ingestion state and repair routes are the recovery mechanism.

## Test status

- Frontend typecheck PASS; 25/25 tests PASS; production build PASS with a 966.78 kB main-JS warning.
- Backend 128 tests run: 120 PASS and 8 PostgreSQL E2E SKIPPED because Testcontainers found no Docker environment; Maven build PASS.
- AI: 88 PASS, 20 subtests PASS, 4 FAIL. Failures cover structured follow-up source ledger, a `None.strip()` exception, acceptance of an ungrounded legal recommendation, and failure to remove an uncited recommendation bullet.
- `docker compose config --quiet` PASS.

## Demo readiness

Safe demonstrations are identity, plan display, workspaces, prepared document operations, public chat share, support tickets, and prepared admin/expert/finance state. Live RAG multi-turn follow-up is **NEEDS_FIX/DO_NOT_DEMO** until the four AI failures pass. External-service flows require rehearsed configuration and preloaded fallback data.

## Priority actions

1. Fix the four AI tests, especially `bounded_history_text is None`, then add an end-to-end attached-document follow-up/citation test.
2. Require authenticated ownership or signed expiring download links; secure `/api/internal/**`, bulk ingest and FastAPI with service credentials/network policy.
3. Remove raw password logging and development secret fallbacks from non-development startup.
4. Run PostgreSQL/Flyway/Testcontainers E2E and a Neo4j/Gemini integration suite in CI; replace echo-only workflows.
5. Rehearse and verify Free/Standard/Premium data, usage counters, VNPay callbacks, SMTP links and KB publication before demo.
6. Consolidate legacy aliases/direct AI access and clarify the two contract-generation/drafting paths without unrelated refactoring.
7. Add browser E2E for auth refresh, one-submit chat, workspace-scoped attachment, plan error routing, shares and ticket workflow; split the frontend bundle.

## Audit integrity

Only the 13 Markdown files in `docs/project-audit/` were created. No application source code, configuration, migration, test, PostgreSQL data, Neo4j data, Docker volume or uploaded file was modified or deleted. Build tools generated their normal ignored `frontend/dist` and `backend/target` artifacts while running the user-requested safe checks; no runtime container or database was started.
