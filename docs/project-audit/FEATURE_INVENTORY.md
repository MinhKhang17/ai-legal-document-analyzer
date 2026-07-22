# Feature inventory

Status means end-to-end evidence, not naming. `READY` assumes required external services are configured.

| Role | Feature | Frontend / API / implementation evidence | Plan | Status | Limitation / demo |
|---|---|---|---|---|---|
| Guest | Registration | `RegisterPage`; `POST /api/v1/auth/register`; `AuthServiceImpl`; `UserRepository` | None | IMPLEMENTED | Requires policy versions and email delivery; setup |
| Guest | Email verification/resend | `VerifyEmailPage`; auth verify/resend endpoints | None | IMPLEMENTED | SMTP dependent; setup |
| Guest | Login/refresh/logout | `LoginPage`, `AppStore`, `http.ts`; `AuthController` | None | IMPLEMENTED | Refresh cookie/domain configuration; ready |
| Guest | Forgot/reset password | auth pages/endpoints; hashed expiring token in `User` | None | IMPLEMENTED | SMTP dependent; setup |
| Guest | Public shared chat | `/shared/chat/:shareToken`; `GET /api/v1/shared/chat/{token}` | None | IMPLEMENTED | Token possession grants view; ready |
| Guest | Public shared ticket conversation | UI `/shared-conversation/:token` is under authenticated guard while API `/api/shared-conversation/{token}` is public | None | DISCONNECTED | Route/contract mismatch; do not demo as public |
| User | Terms acceptance | registration versions plus `/api/v1/policies/current|accept` | Any | IMPLEMENTED | Current version config required; ready |
| User | Profile/password | `ProfilePage`; user profile/password endpoints | Any | IMPLEMENTED | General `GET /users` is authenticated, not admin-only; security review |
| Customer | Subscription package display | billing pages; subscription plan endpoints/repository | Any | IMPLEMENTED | Admin-managed DB values may differ from seeds |
| Customer | Effective Free entitlement | `CustomerPlanServiceImpl.createDefaultFreePlan`; effective plan usage | Free | IMPLEMENTED | Requires active `FREE` plan row |
| Customer | Quotas and usage | `BillingPage`; usage endpoints; `SubscriptionQuotaServiceImpl` | Any | IMPLEMENTED | Only token/storage/tickets remain metered |
| Customer | Upgrade/change/cancel | Subscribe page; change/subscribe/cancel endpoints | Any | IMPLEMENTED | VNPay/external setup for paid activation |
| Customer | Downgrade semantics | change/cancel service schedules/end-of-cycle Free handling | Paid | PARTIALLY_IMPLEMENTED | No full E2E run; setup/test before demo |
| Customer | Refund request/history/detail/email confirm | refund pages/controller/service | Paid | IMPLEMENTED | Email/payment state dependent |
| Customer | Workspace create/list/detail | projects pages; `WorkspaceController/ServiceImpl` | Any | IMPLEMENTED | Duplicate name returns `WORKSPACE_ALREADY_EXISTS`; ready |
| Customer | Document upload | `UploadPage`; workspace multipart endpoint; validator | Storage/file-size quota | IMPLEMENTED | PDF/DOC/DOCX only; AI ingestion async |
| Customer | Document list/detail/download/delete | document pages and workspace endpoints | Any | IMPLEMENTED | Download routes are publicly matched; security blocker |
| Customer | Document version management | No customer document-version entity/API; KB and generated contracts have versions | Any | NOT_FOUND | Do not claim uploaded-document versioning |
| Customer | Chat sessions/history/rename/delete | chat/history pages; session endpoints/services/entities | Any | IMPLEMENTED | AI not required for history operations |
| Customer | Add/remove chat documents | workspace selector + `ChatSessionDocumentController` | attachment limit configured | IMPLEMENTED | Workspace ownership enforced in service; ready |
| Customer | General legal question without attachment | `LegalChatPage`; chat message API; RAG general intent | AI tokens | IMPLEMENTED | Gemini/KB dependency; fallback can be low-confidence |
| Customer | Contract summary | RAG `CONTRACT_SUMMARY` | AI tokens | IMPLEMENTED | Needs attached/selected document |
| Customer | Contract risk analysis | RAG risk intents/checklists and `RiskReviewPage` | AI tokens | IMPLEMENTED | Direct AI risk page increases deployment coupling |
| Customer | Clause extraction | `INFORMATION_EXTRACTION` / `CONTRACT_INFORMATION_EXTRACTION` | AI tokens | IMPLEMENTED | Intent classification accuracy needs demo prompt rehearsal |
| Customer | Missing clause detection | `MISSING_CLAUSE_CHECK`, completeness checker | AI tokens | IMPLEMENTED | Supported contract scope only |
| Customer | Clause rewrite/suggestion/negotiation | intent enums, prompt/pipeline, chat renderer | AI tokens | IMPLEMENTED | Generated guidance, not final legal advice |
| Customer | Multi-document analysis | active/attached/message document IDs in request/context | plan attachment limit | IMPLEMENTED | Context token budget can omit lower-ranked chunks |
| Customer | Readable source citations | `AiCitation`, conversation context, `ChatMessageContent` | Any | IMPLEMENTED | Cross-source dedupe; retrieval-dependent |
| Customer | Chat share and Markdown export | share/export endpoints, `SharedChatPage` | Any | IMPLEMENTED | Public token must be treated as a secret |
| Customer | Drafting intent and prompt generation | `DraftingWorkflowCard`, `drafting_workflow.py` | AI tokens | IMPLEMENTED | Prompt handoff is distinct from persisted contract generation |
| Customer | Copy prompt / Open ChatGPT | `draftingActions.ts` and tests | None | IMPLEMENTED | Browser popup/clipboard policy dependent |
| Customer | Persisted contract generation | contract pages/controller/services + AI `/v2/contracts/generate` | AI/storage | PARTIALLY_IMPLEMENTED | Overlaps chat drafting; AI endpoint contains hard-coded lease retrieval query |
| Customer | Contract versions/revert | contract detail and version service/repository | Any | IMPLEMENTED | Generated contracts only |
| Customer | Create support ticket | ticket pages; `LegalTicketController/ServiceImpl` | Free max 3 system/query-error | IMPLEMENTED | Correct category required |
| Customer | Create expert ticket | ticket composer; legal ticket/payment APIs | Premium or paid path | IMPLEMENTED | Quote/payment/credit state must be seeded |
| Admin | Ticket review/classification/quote | admin pages and `AdminTicketManagementController` | Admin | IMPLEMENTED | Complex state machine; use prepared ticket |
| Admin | Lawyer assignment/reassignment | admin ticket detail/service | Admin | IMPLEMENTED | Needs active expert account |
| Expert | Accept/decline assignment | lawyer pages and assignment-decision endpoint | Expert | IMPLEMENTED | Email notification external |
| Expert/Customer | Lawyer-user chat and files | ticket detail pages, message/file services | Ticket access | IMPLEMENTED | 500 KB/file configured default |
| Customer | Ticket cancel/close/reopen | legal-ticket endpoints | Owner | IMPLEMENTED | State-dependent |
| System | SLA/overdue handling | `ExpertTicketSlaScheduler`, extend-SLA endpoint/audit | N/A | IMPLEMENTED | Clock/scheduler behavior not E2E exercised |
| Admin | Knowledge upload/ingestion/review/publish | KB pages/controllers/services | Admin | IMPLEMENTED | Neo4j/filesystem/AI dependent; setup |
| Admin | KB version lifecycle/archive/repair | KB detail, version/job/repair endpoints | Admin | IMPLEMENTED | Repair endpoint is publicly allowed in one route; blocker |
| Admin | User/expert administration | admin console/user controller/service | Admin | PARTIALLY_IMPLEMENTED | Some general user endpoints lack admin role guard |
| Admin | Feedback surveys/AI reports | admin feedback page/controllers | Admin | IMPLEMENTED | Customer response has auth but sparse UI discovery |
| Expert | Revenue summary/tickets/period statements/export | lawyer revenue pages, `ExpertRevenueController` | Expert | IMPLEMENTED | Requires calculated period data |
| Admin | Revenue periods/adjustments/statements/export | admin revenue page/controller/services | Admin | IMPLEMENTED | Financial operations need prepared data and review |
| Admin | Commission policy email verification/notifications | commission page/service/entities | Admin/Expert | IMPLEMENTED | SMTP dependent; scheduled change semantics |
| Expert/Admin | Early payout request/decision/payment | expert/admin pages and endpoints | Expert/Admin | IMPLEMENTED | Requires eligible balance/settings |
| System | Financial/ticket audit logs | `FinancialAuditLog`, `TicketAuditLog`, services | Privileged | IMPLEMENTED | No general audit-log UI beyond revenue audit |
| Admin | System health page | `SystemHealthPage` | Admin | UI_ONLY | No dedicated verified backend health aggregation endpoint |
| System | CI/CD | `.github/workflows/*-ci.yml` | N/A | MOCKED | Echo-only workflows; no checkout/build/test/deploy |
| System | Neo4j mock vector record path | `document_processor.py` calls `save_mock_vector_records` | N/A | PARTIALLY_IMPLEMENTED | Name and implementation require care; production graph pipeline is separate |

## Role and plan conclusions

Spring roles are `CUSTOMER`, `ADMIN`, `EXPERT`. Subscription plan types seeded by `DataInitializer` are `FREE`, `STANDARD`, `PREMIUM`. Workspace/document-count/analysis/draft limits are retained as schema fields but deliberately not metered; AI tokens, storage, file size, chat attachment count, support tickets and Premium expert access are the active restrictions. Status totals in this inventory are computed in `PROJECT_AUDIT_SUMMARY.md`.
