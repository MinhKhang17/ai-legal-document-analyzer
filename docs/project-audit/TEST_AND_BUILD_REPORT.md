# Test and build report

Audit date: 2026-07-23. Commands were non-destructive; no database reset, migration, container startup or volume operation was performed.

## Commands and results

| Check | Command | Result |
|---|---|---|
| Frontend type check | `cd frontend; npm run typecheck` | PASS, `tsc --noEmit` |
| Frontend tests | `cd frontend; npm test -- --run` | PASS, 8 files / 25 tests |
| Frontend production build | `cd frontend; npm run build` | PASS, 1,774 modules; warning: main JS 966.78 kB (253.78 kB gzip), above 500 kB |
| Backend compile/unit/controller tests | `cd backend; .\mvnw.cmd test` | PASS, 128 run / 0 failures / 0 errors / 8 skipped |
| Backend PostgreSQL E2E | included in Maven suite | SKIPPED, all 8 `PlanFlowE2ETest` cases because Testcontainers could not find a valid Docker environment |
| AI tests | `cd ai-service; .venv\Scripts\python.exe -m pytest tests -q` | FAIL, 4 failed / 88 passed / 20 subtests passed; 1 deprecation warning |
| Compose validation | `docker compose config --quiet` | PASS |

An initial parallel check wrapper reached its 120-second orchestration timeout because the AI suite takes about 100 seconds alongside other builds. Each relevant check was rerun separately, producing the results above.

## Test assets

- Frontend: eight focused unit tests for HTTP refresh/error mapping, submission guard, workspace/policy callers, ticket validation/display, drafting actions and chat-message rendering. No React browser/E2E route coverage.
- Backend: 38 Java test source files covering service state machines, quotas, auth refresh/policy registration, upload validation, chat/citations, knowledge lifecycle, revenue, tickets, exception mapping, migration/entity alignment and an expert revenue controller contract.
- AI: 16 Python test source files covering RAG, schema, safe shortcuts, supported scope, citations, prompt document scope, legal RAG v2, drafting, ingestion/admin lifecycle, conversation memory and Gemini SDK migration.
- No coverage report/threshold plugin was found. The three GitHub workflow files are echo-only placeholders and run none of these checks.

## AI failures requiring action

1. `test_follow_up_uses_latest_structured_snapshot_without_full_history`: the first claim ledger no longer records `basedOnUserSources=("USER-1",)`.
2. `test_follow_up_without_snapshot_returns_need_more_information`: `RagQueryService.query` calls `.strip()` on `None` from `_request_history_text`; this is a real 500-risk for follow-up questions without stored history.
3. `test_legal_recommendation_without_kb_citation_is_rejected`: an ungrounded recommendation is returned `ANSWERABLE` instead of `OUT_OF_KNOWLEDGE_BASE`.
4. `test_ungrounded_recommendation_bullet_is_removed`: the recommendation filter fails to remove an uncited bullet.

The AI test log also repeatedly attempts `localhost:7687` even when fake retrieval services are supplied; Neo4j absence is logged and slows/noises unit tests. The four assertions are not explained solely by Neo4j being offline.

## Backend warnings

- Testcontainers could not reach Docker, so real PostgreSQL/Flyway/plan E2E behavior remains unverified.
- The expert revenue MVC test created a generated security password and warned that two `UserDetailsService` beans exist in that test slice.
- Hibernate warns explicit `PostgreSQLDialect` is unnecessary.
- Mockito self-attaches a Byte Buddy agent, a future-JDK compatibility warning.
- The audit host ran Java 23.0.2 although the project targets Java 21; Docker uses Java 21.

## Coverage gaps and demo blockers

No automated browser E2E covers auth cookies, upload-to-ingestion, plan/VNPay callbacks, public shares, full ticket assignment, email delivery or revenue workflows. No real Neo4j/Gemini/SMTP/VNPay integration test ran. The AI follow-up/grounding failures are a direct demo blocker for conversational legal Q&A. Skipped PostgreSQL E2E and placeholder CI are release blockers even though local compile/unit tests pass.
