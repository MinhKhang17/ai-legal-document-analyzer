# Project overview

## Scope and evidence

This is a read-only audit of the repository at 2026-07-23. Evidence comes from `README.md`, `docker-compose.yml`, `frontend/src`, `backend/src`, `ai-service/app`, tests, and build manifests. A route, DTO, entity, or TODO alone is not treated as a working feature.

## Product

The repository calls the product **AI Legal Document Analyzer / LexiGuard AI Legal Suite** (`README.md`, `frontend/package.json`). It addresses the difficulty of storing, finding, understanding, comparing, and escalating questions about Vietnamese legal documents. Its core proposition is a workspace-oriented document repository, legal RAG chat with citations, subscription quotas, and a paid expert-review workflow.

Main actors are guest, authenticated customer, customer on Free/Standard/Premium, administrator, legal expert (`EXPERT`), and internal/background processes. Persistent application roles are `CUSTOMER`, `ADMIN`, and `EXPERT`; Free/Standard/Premium are subscription states, not Spring Security roles.

## Main modules and journeys

| Module | Verified journey | Principal evidence | Status |
|---|---|---|---|
| Identity | Register with policy versions, verify email, login, refresh cookie, reset password | `AuthController`, `PolicyAcceptanceController`, `AuthServiceImpl`, auth pages | Implemented |
| Subscription | See plans/usage, subscribe/change/cancel, VNPay, refund | `SubscriptionManagementController`, `PaymentTransactionController`, billing pages | Implemented; external setup required |
| Workspace/documents | Create/list workspace; upload, list, download, delete PDF/DOC/DOCX | `WorkspaceController`, `WorkspaceServiceImpl`, project/upload/document pages | Implemented |
| Chat/RAG | Create session, scope documents, ask, retain context, cite, share/export | chat controllers, `ChatMessageServiceImpl`, `/internal/rag/query`, `LegalChatPage` | Implemented; Gemini/Neo4j dependent |
| Contract generation | Templates, generate job, versions/revert, drafting prompt card | contract controllers/services and AI `/v2/contracts` | Partially implemented; two overlapping generation paths |
| Knowledge base | Admin upload, ingest, review, publish/unpublish/archive, Neo4j repair | knowledge controllers/services and AI knowledge APIs | Implemented; operationally complex |
| Expert ticket | Customer request, admin classify/quote/assign, expert accept/chat/resolve | legal-ticket, admin-ticket, lawyer controllers/pages | Implemented |
| Revenue | Periods/statements, commission policy verification, payout/export/audit | admin/expert revenue controllers and pages | Implemented; scheduled/external-email setup required |
| Feedback/reporting | Message feedback, surveys, AI reports, admin panels | feedback controllers/pages | Implemented |

## Repository and boundaries

```text
frontend/       React SPA; owns browser UX and access-token memory
backend/        Spring Boot API; owns identity, authorization, business state and PostgreSQL
ai-service/     FastAPI service; owns parsing, embeddings, retrieval, prompts and Neo4j graph
docs/           design/flow material and this audit
data/           host-side application data mounted by Compose
.github/        three placeholder CI workflows
docker-compose.yml
```

The browser should call Spring Boot at `/api/v1`; Spring Boot calls FastAPI internally. PostgreSQL is authoritative for users, subscriptions, workspaces, documents, chat metadata, tickets and finance. Neo4j is authoritative for ingested document hierarchy, embeddings and conversation-memory vectors. Shared upload storage links the backend-created file record to AI ingestion.

## Runtime architecture and external systems

`docker-compose.yml` defines `frontend` (Nginx, 5173), `backend` (8080), `ai-service` (8000), PostgreSQL 16 (5432), Neo4j 5.26 (7474/7687), and ngrok (4040). External dependencies are Google Gemini, Gmail/SMTP, VNPay sandbox, and ngrok. Named volumes are `postgres_data`, `neo4j_data`, `neo4j_logs`, and `shared_uploads`.

## Development status

The system is feature-rich and builds successfully, but is pre-production. The strongest evidence is 52 concrete frontend route patterns, 212 Spring handler methods, 25 FastAPI route handlers, 46 JPA entities, 23 Flyway migrations, 128 backend tests (8 E2E skipped), 25 frontend tests, and the AI test suite. Major readiness constraints are placeholder CI, exposed internal/download routes, secrets/development credentials in defaults, external-service dependency, dual/legacy API aliases, skipped real-database E2E, and a large frontend main bundle.
