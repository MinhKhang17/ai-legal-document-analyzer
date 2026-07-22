# UI screen inventory

`frontend/src/routes/router.tsx` declares 54 route elements: **52 concrete route patterns and two wildcard fallbacks**. There are 50 page source files. Shared layout components provide responsive sidebar/topbar; pages use Tailwind breakpoints. Loading/error/empty handling is page-local rather than a common query library.

## Public and authentication

| Route(s) | Screen / role / data | Actions, validation and states | Status/demo |
|---|---|---|---|
| `/`, `/login` | `LoginPage`, guest; auth API | Email/password required, pending disables submit, API error, redirect by role | Implemented/ready |
| `/register` | `RegisterPage`; plans/policies/auth | Personal data/password/policy checkbox; opens detailed policy; pending/error/success | Implemented; SMTP setup |
| `/auth/check-email`, `/verify-email` | check/verify pages | Resend, token query, loading/success/error | Implemented; SMTP setup |
| `/forgot-password`, `/reset-password` | reset pages | Email/token/password validation and pending/error | Implemented; SMTP setup |
| `/billing/refunds/confirm` | public confirmation | Query token, confirmation state | Implemented |
| `/shared/chat/:shareToken` | `SharedChatPage`, token guest | Read-only messages/sources, loading/invalid token | Implemented; treat URL as secret |
| `/shared-conversation/:token` | `SharedTicketConversationPage` | Read-only shared ticket data | Disconnected: nested in authenticated route despite public API |

## Customer workspace, documents and chat

| Route(s) | Screen / API | Main actions and states | Status/demo |
|---|---|---|---|
| `/dashboard` | `DashboardPage`; workspace/document/subscription summaries | Navigation, refresh, loading/error/empty cards | Implemented |
| `/projects` | `ProjectsPage`; workspace service | Create/search/select workspace; guarded pending submit; duplicate/quota messages | Implemented |
| `/projects/:id` | `ProjectDetailPage`; workspace/docs/sessions | View workspace, upload/open/delete docs/chat | Implemented |
| `/documents` | `DocumentsPage`; workspace documents | Search/filter/download/delete/open; loading/empty/error | Implemented |
| `/documents/:id` | `DocumentDetailPage` | Metadata/status/download/chat | Implemented; download security issue |
| `/upload` | `UploadPage`; workspace/upload services | Workspace always selected above upload; PDF/DOC/DOCX client filter; create workspace/upload; pending/progress/error | Implemented |
| `/chat` | `LegalChatPage`; workspaces/sessions/docs/messages/subscription/tickets | Select workspace, attach only its docs, send once while pending, feedback/share/export/ticket/drafting; detailed empty/error/plan redirect | Implemented; external AI setup |
| `/chat/history` | `ChatHistoryPage` | Search, open, rename/delete/export sessions | Implemented |
| `/chat/contract-assistant` | redirect to `/chat` | No separate screen | Intentional redirect |
| `/editor` | `EditorPage` | Contract-oriented editor/navigation | Partially integrated with main chat |
| `/editor/risk-review` | `RiskReviewPage`, ADMIN guard | Direct risk KB query/import UI | Implemented but direct FastAPI/network coupling |

## Contracts and tickets

| Route(s) | Screen / API | Actions and state handling | Status/demo |
|---|---|---|---|
| `/contracts` | lazy `MyContractsPage`; contract API | List/open generated contracts, loading/empty/error | Implemented |
| `/contracts/:id` | `ContractDetailPage` | View/download/version/revert | Implemented for generated contracts |
| `/tickets`, `/tickets/create` | customer ticket list/create | Filter/list, compose title/question/category/context/docs; pending and backend validation; plan/payment errors | Implemented |
| `/tickets/:id` | customer detail | View quote/files/messages, pay/reply/cancel/close/reopen | Implemented; state-dependent |

## Billing and settings

| Route(s) | Screen / API | Actions/states | Status/demo |
|---|---|---|---|
| `/billing` | `BillingPage`; plans/effective usage/payments | Token/storage/expert-ticket cards, refresh, upgrade/cancel | Implemented; database plan values |
| `/billing/subscribe` | `SubscribePlanPage` | Select plan, create transaction/open VNPay; pending/error | Implemented; VNPay setup |
| `/billing/payment-result`, `/payment-result` | result and redirect | Parse callback status and navigate | Implemented |
| `/billing/refunds`, `/billing/refunds/:id` | history/detail | Request/list/view refund status | Implemented; workflow setup |
| `/settings`, `/profile` | settings/profile | Update profile/preferences; validation/pending | Implemented |
| `/settings/security` | `AccountSecurityPage` | Password/security controls | Route remains implemented even though product may hide links; verify desired navigation |

## Administrator

| Route(s) | Screen / data/actions | Status/demo |
|---|---|---|
| `/admin` | `AdminConsolePage`; users/experts/system summaries; create/restore/resend expert | Partial: broad console, sensitive defaults need review |
| `/admin/tickets`, `/:ticketId` | list/detail; classify/quote/payment/assign/reject/approve/SLA/close/expert payment/chat | Implemented; use prepared state |
| `/admin/feedback` | survey, AI report, chat feedback panels | Implemented |
| `/admin/refunds`, `/:id` | refund queue/decision | Implemented |
| `/admin/revenue` | settings, overview, periods/statements, commission, payout/audit/export | Implemented; data/setup heavy |
| `/admin/revenue/statements/:statementId` | shared `RevenueStatementDetailPage` | View/export statement | Implemented |
| `/admin/revenue/early-payouts/:id` | payout detail and workflow actions | Implemented |
| `/admin/revenue/commission/verify` | emailed token verification | Implemented; SMTP setup |
| `/admin/system-health` | `SystemHealthPage` | Displays health-oriented UI | UI-only: no verified aggregate health API |
| `/knowledge-base`, `/:id` | KB list/detail; upload, ingest, review, publish/unpublish/archive/source/version/status | Implemented; AI/Neo4j/files setup |

## Expert

| Route(s) | Screen / actions | Status/demo |
|---|---|---|
| `/lawyer/tickets`, `/:ticketId` | proposed/my tickets; accept/decline, assess, files/chat/start/request-info/resolve/close | Implemented; prepared assignment |
| `/lawyer/revenue` | summary, tickets, periods, commission notifications, early payout | Implemented; prepared finance data |
| `/lawyer/revenue/:statementId` | statement detail/export | Implemented |

## Permission, subscription and UX observations

`AuthenticatedRoute`, `CustomerRoute`, `AdminRoute`, and `ExpertRoute` in `components/auth/AuthGuards.tsx` wait for auth recovery and redirect wrong roles. The backend remains authoritative. `LegalChatPage` disables send while pending and uses request/idempotency guards; `http.ts` refreshes once on 401. Plan error handling maps quota/entitlement errors to billing navigation, but public-share routing is inconsistent. There is no automated responsive or accessibility browser test; responsiveness is inferred only from Tailwind classes and cannot be certified by static inspection.
