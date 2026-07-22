# Demo readiness report

This is readiness analysis, not a final demo script.

## Candidate flows

| Flow | Class | Setup / reason | Est. |
|---|---|---|---:|
| Login, role redirect and profile | READY | Preverified customer; refresh cookie on same deployed domain | 2 min |
| Registration and verification | READY_WITH_SETUP | Working SMTP and disposable demo inbox; fallback: show preverified account | 3 min |
| Plans and effective usage | READY_WITH_SETUP | Seed/admin-verify Free/Standard/Premium and known usage | 2 min |
| Workspace create/list | READY | Use unique deterministic name; duplicate shows explicit conflict | 2 min |
| Upload/list/download/delete document | READY_WITH_SETUP | Known small PDF/DOCX, storage volume, AI service; do not expose public download URL | 4 min |
| General legal question without document | NEEDS_FIX | AI test reveals follow-up `None.strip()` and grounding regressions | 3 min |
| Attached contract summary/risk/clause extraction | NEEDS_FIX | Same AI grounding failures; requires pre-ingested document and Neo4j/Gemini | 6 min |
| Multi-turn follow-up and citations | DO_NOT_DEMO | Two follow-up/claim-ledger tests fail; likely unstable demo behavior | 3 min |
| Share chat by URL | READY_WITH_SETUP | Generate fresh token and sanitized content; revoke after demo | 2 min |
| Drafting prompt copy/open ChatGPT | READY_WITH_SETUP | Popup/clipboard allowed; explain it is a handoff | 3 min |
| Persisted generated contract/version/revert | NEEDS_FIX | Overlapping generation path and hard-coded lease retrieval query | 5 min |
| Customer system/query-error ticket | READY_WITH_SETUP | Free account under 3-ticket limit, prepared context | 3 min |
| Expert ticket quote/payment | READY_WITH_SETUP | Premium or paid ticket, VNPay sandbox/callback | 5 min |
| Admin classify and assign expert | READY_WITH_SETUP | Prepared ticket and active expert | 4 min |
| Expert accept/chat/resolve | READY_WITH_SETUP | Expert account, prepared assignment, files below limits | 5 min |
| Reopen/close/SLA | READY_WITH_SETUP | Ticket in precise eligible state; avoid waiting for scheduler | 3 min |
| Admin KB upload/review/publish | READY_WITH_SETUP | Full stack, known source, wait for ingest; fallback use prepublished version | 6 min |
| Refund workflow | READY_WITH_SETUP | Known paid transaction and SMTP/callback state | 4 min |
| Expert/admin revenue and XLSX export | READY_WITH_SETUP | Precalculated period/statements/eligible balance | 6 min |
| Commission verification email | READY_WITH_SETUP | SMTP and valid token within 30 minutes | 4 min |
| Early payout request/decision/payment | READY_WITH_SETUP | Eligible balance and revenue settings | 5 min |
| Public shared ticket conversation | NEEDS_FIX | Frontend route requires authentication while API is public | 2 min |
| Admin system health | DO_NOT_DEMO | UI-only aggregate health behavior |
| CI/CD pipeline | DO_NOT_DEMO | Placeholder echo workflows |

## Recommended roles/accounts

Use four clearly labeled non-production accounts: one verified Free customer below all quotas, one verified Premium customer with one prepared paid/expert ticket, one active `EXPERT` with completed password change, and one `ADMIN`. Never use default initializer passwords on a public stream. Reset only demo-account business state through documented admin operations, not database scripts.

## Required data

- A small, non-personal Vietnamese rental contract with clear deposit, rent, termination, liability and dispute clauses.
- A small part-time employment sample for a second supported type.
- Published, dated system KB documents that directly support the planned legal questions.
- One fully ingested document with matching PostgreSQL `Document` status and Neo4j chunks/indexes.
- Tickets in draft/quoted/assigned/resolved states, a revenue period with statement items, an eligible early-payout balance, and a VNPay sandbox transaction if those flows are shown.
- SMTP, Gemini, Neo4j, PostgreSQL, callback URLs, CORS origins and shared volume validated before the session.

## Recommended order

1. Identity/profile and plan/usage (4 minutes).
2. Workspace/upload/document scope (5 minutes).
3. After fixing AI failures: one contract analysis with citations and one safe follow-up (6 minutes).
4. Create an expert request, switch to admin for assignment, then expert for resolution (10–12 minutes).
5. Optional revenue/export or KB publication, not both, to keep the core story coherent (5 minutes).

## Failure fallbacks

Keep a pre-ingested document/session and screenshots/API payloads for Gemini/Neo4j outage; a preverified account for SMTP failure; an already-paid sandbox transaction for VNPay failure; a preassigned ticket for email/scheduler failure; and a precomputed revenue statement for payroll failure. State clearly when showing prepared data rather than live success.

## Blockers

1. Four failing AI tests in follow-up grounding and recommendation citation enforcement.
2. Public internal/download routes and unauthenticated FastAPI boundary make public deployment unsafe.
3. Eight PostgreSQL E2E tests were skipped because Docker/Testcontainers was unavailable.
4. External Gemini/SMTP/VNPay and callback/domain configuration was not validated.
5. PostgreSQL/Neo4j/file cross-store consistency is not transactionally guaranteed.
6. Public shared-ticket UI mismatch and UI-only health page.
7. CI is mocked and the frontend main chunk is large.
