# Security and permission report

## Authentication

`SecurityConfig` is stateless, disables CSRF, registers CORS, uses `DaoAuthenticationProvider`, `UserDetailsServiceImpl` and BCrypt. `JwtAuthenticationFilter` extracts `Authorization: Bearer`, validates JJWT signatures/expiry in `JwtTokenProvider`, reloads the user and sets `SecurityContextHolder`. Access JWT defaults to 15 minutes; refresh state lasts seven days and is persisted. The frontend keeps access JWT in memory, user metadata in local storage, sends refresh cookies with credentials, deduplicates concurrent refreshes and retries a failed request once.

Passwords are BCrypt-hashed. Verification and reset tokens are random then hashed before persistence, have expiry/reuse state, and login rejects unverified accounts. Registration records accepted terms/policy versions; `/api/v1/policies/accept` supports later version acceptance.

## Authorization model

Spring roles are `ROLE_CUSTOMER`, `ROLE_ADMIN`, `ROLE_EXPERT`. `SecurityConfig` authenticates all non-public routes; controllers use `@PreAuthorize` extensively. Subscription entitlements are service checks, not authorities. `CustomerPlanServiceImpl` resolves/creates an effective Free plan idempotently; `SubscriptionQuotaServiceImpl` enforces token, storage, file/attachment and ticket rules.

Ownership checks are visible in workspace/chat/document and ticket services: customer IDs/workspace ownership, ticket creator/assigned expert/admin visibility, and session-document membership are evaluated before state changes. `ChatMessageFeedbackAuthorizationTest`, workspace tests and ticket tests cover representative paths. Public share tokens intentionally grant sanitized read access.

## File and data handling

`DocumentUploadValidator` accepts only PDF, DOC and DOCX, compares extension to declared MIME and verifies PDF/OLE/ZIP signatures. Plan file-size/storage limits are enforced separately. Ticket files have default 500 KB per file, five per message and 30 per ticket. Paths are stored in PostgreSQL and binaries in mounted directories; source files are shared with AI for parsing. No malware scanner or content-disarm pipeline was found.

Questions/documents are sent from Spring to the AI service and selected context/prompt can be sent to Google Gemini. This is an external privacy transfer; no explicit DLP, per-tenant encryption key, consent-at-query, regional routing, or provider-retention control is evident in source. Logs include IDs and operational details; SQL logging is enabled by default and prompt logging helpers exist, so production log redaction must be reviewed.

## CORS and secrets

CORS uses a configured allow-list plus a Vercel origin, allows credentials and standard methods/headers. Compose/application examples include development database/Neo4j passwords, a fallback JWT secret, default admin credentials and network URLs. `DataInitializer.seedUser` logs the seeded email **and raw password**. Environment examples are not proof of production secrets, but unsafe defaults can become real credentials if deployment omits overrides.

## Findings ordered by severity

### Critical

1. **Unauthenticated document downloads.** `SecurityConfig.java:82-83` permits `/api/v1/workspaces/*/documents/*/download` and system downloads. Controller service ownership checks cannot be assumed for a route expressly designed for tokenless links; verify and replace with signed, scoped, expiring access or authentication.
2. **Internal/admin operations trust the network.** `/api/internal/**` and admin bulk-ingest POST are `permitAll` (`SecurityConfig.java:70-84`), while FastAPI routes have no service authentication. An exposed backend/AI port could allow callback forgery, generated-document registration, ingestion or resource consumption.
3. **Raw default credentials can leak.** `DataInitializer.java:137` logs `email / rawPassword`; default admin/password and fallback JWT/database/Neo4j secrets appear in configuration/examples. Remove password logging and fail securely outside development.

### High

4. **Direct AI exposure bypasses business controls.** FastAPI has no JWT/role/plan layer, and frontend service files can address direct AI routes. Network policy or signed service tokens are required.
5. **Broad user-list access.** `UserController` list/id routes are merely authenticated and lack an ADMIN method annotation. If service does not restrict fields/roles, any authenticated account can enumerate users.
6. **Shared URLs are bearer credentials.** Public chat/ticket share tokens need high entropy, revocation, expiry, access-level filtering, no indexing and careful referrer policy. Ticket UI/API routing is inconsistent.
7. **External AI privacy governance is incomplete.** Uploaded legal documents may contain sensitive personal data; provider contract/retention, disclosure, deletion and logging controls are not encoded.

### Medium

8. **No malware scanning** beyond format/signature checks; malicious but structurally valid Office/PDF content can be stored/parsed.
9. **Potential sensitive logs.** default `show-sql: true`, exception messages, prompt debug logging and IDs can reveal user content or filesystem paths.
10. **CSRF is globally disabled.** Stateless Bearer requests are appropriate, but refresh/logout use cookies; SameSite/Secure/HttpOnly cookie settings must be verified in `AuthController/AuthServiceImpl` for every deployment domain.
11. **API aliases increase attack surface** and can receive different proxy/security treatment (`/api` vs `/api/v1`).
12. **No rate-limiter/WAF implementation found** for login, resend, password reset, public shares, AI queries or callbacks; some service throttling exists but not a general control.

### Low/operational

13. The backend test logs a generated Spring security password and two `UserDetailsService` beans in a sliced test context; production configuration uses the explicit provider, but test security fidelity should improve.
14. Explicit PostgreSQL dialect and verbose SQL are unnecessary operational settings.

## Permission matrix

| Resource | Customer | Expert | Admin | Guest |
|---|---|---|---|---|
| Own workspace/doc/chat | Own only | No general access | Limited/admin-specific | Shared chat token only |
| Legal ticket | Own/state-limited | Assigned/proposed only | Workflow administration | Shared token route only |
| Knowledge lifecycle | No | Document download where allowed | Full | No; one bulk route mistakenly public |
| Subscription/payment | Own | No customer plan flow | Plan/refund administration | VNPay/refund callbacks |
| Revenue | No | Own statements/payouts | All periods/policies/audit | No |

The matrix describes intended controller/service behavior; the public matchers above override the intended boundary and are blockers.
