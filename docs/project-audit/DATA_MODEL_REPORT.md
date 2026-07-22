# Data model report

## PostgreSQL

There are **46 `@Entity` classes** in `backend/src/main/java/com/analyzer/api/entity`. Unless noted, the primary key field is `id`; most string domain IDs use generated prefixes, while plan/role/user/notification-style records use numeric IDs. Timestamp callbacks and migration defaults are mixed, so both entity and SQL must be kept aligned.

| Entity → table | Purpose / important relationships | Constraints, audit, repository/API evidence |
|---|---|---|
| `User` → `users` | Identity, verification/reset/policy/security; many-to-one `Role` | Unique email; created/updated fields; `UserRepository`; auth/users/admin APIs |
| `Role` → `roles` | `CUSTOMER`, `ADMIN`, `EXPERT` authority | Unique role name; `RoleRepository`; initializer/auth |
| `RefreshToken` → `refresh_tokens` | Rotatable login refresh state → user | Token uniqueness/expiry/revocation; `RefreshTokenRepository`; auth refresh/logout |
| `PolicyAcceptance` → `policy_acceptances` | Versioned terms/privacy acceptance → user | Version/type/time/IP-style metadata; repository; policy APIs |
| `SubscriptionPlan` → `subscription_plans` | Price/tier/token/storage/ticket/file entitlements | Unique lower-cased plan type/name migrations; timestamps; repository; subscription APIs |
| `CustomerPlan` → `customer_plans` | Customer active subscription and quota snapshot → user/plan | Partial unique active customer constraint; billing cycle/audit timestamps; repository |
| `SubscriptionUsage` → `subscription_usage` | Period/event usage → user/customer plan | Event type/quantity/reference/time; repository; usage APIs |
| `PaymentTransaction` → `payment_transactions` | VNPay transaction → customer/plan/ticket | Reference/status/amount/callback fields; repository; payment APIs |
| `RefundRequest` → `refund_requests` | Refund workflow → transaction/customer/reviewer | Status/token/reason/amount/timestamps; repository; refund APIs |
| `Workspace` → `workspaces` | Customer document/chat container → user | Active-name duplicate logic; timestamps; `WorkspaceRepository`; workspace APIs |
| `Document` → `documents` | Uploaded/generated document → workspace/user | File path/type/size/hash/status/AI ID; repository; workspace/internal APIs |
| `ChatSession` → `chat_sessions` | Conversation → workspace/user | title/share/context fields/timestamps; repository; session APIs |
| `ChatSessionDocument` → `chat_session_documents` | Session-to-document join | Unique pair expected; repository; session-document API |
| `ChatMessage` → `chat_messages` | User/assistant message → session/user | role/status/mode/intent/risk/request ID/usage/created/updated; repository; message API |
| `ChatMessageFeedback` → `chat_message_feedbacks` | One user reaction/comment → message/user | Message/user uniqueness; feedback type migration; repository; feedback API |
| `AiCitation` → `ai_citations` | Citation → chat message/document/chunk | Source IDs/label/excerpt/score/order; repository; AI citation APIs |
| `AiQueryExecution` → `ai_query_executions` | Idempotent request/token reservation → user/session | Unique request key migration; estimated/actual tokens/status; repository; chat/quota |
| `AiReport` → `ai_reports` | User-reported AI issue → user/message | Category/content/status/timestamps; repository; feedback API |
| `ConversationShare` → `conversation_shares` | Tokenized ticket conversation share → ticket/creator | Unique token/access fields/expiry; repository; collaboration API |
| `TicketAttachment` → `ticket_attachments` | Ticket/message attachment → uploader/ticket | Path/MIME/size/hash/deleted/audit; repository; attachment APIs |
| `TicketAuditLog` → `ticket_audit_logs` | Immutable ticket transition/action → ticket/actor | action/from/to/metadata/time; repository; ticket services |
| `TicketContextSnapshot` → `ticket_context_snapshots` | Frozen AI/chat/doc context → ticket | JSON/text/source identifiers/time; repository; legal ticket creation |
| `LegalTicket` → `legal_tickets` | Main support/expert workflow → customer/admin/expert/docs/messages/payment | Extensive status/pricing/SLA/risk/payment/revenue fields and DB check constraints; repository; three ticket controller families |
| `LegalTicketMessage` → `legal_ticket_messages` | Customer/admin/expert conversation → ticket/sender | Client message idempotency migration; visibility/type/time; repository; ticket message APIs |
| `ExpertTicketCreditReservation` → `expert_ticket_credit_reservations` | Reserve/consume/release plan ticket credit → ticket/plan | Status/period/unique reservation; repository; quota/ticket workflow |
| `KnowledgeBaseEntry` → `knowledge_base_entries` | Logical admin KB item | title/type/lifecycle/current version/audit; repository; KB API |
| `KnowledgeBaseVersion` → `knowledge_base_versions` | Source version → entry/uploader | version/file/hash/AI document ID/review/publication; repository; KB lifecycle |
| `KnowledgeIngestionJob` → `knowledge_ingestion_jobs` | Async ingest state → KB version | status/progress/error/AI IDs/timestamps; repository; job/internal APIs |
| `ContractTemplate` → `contract_templates` | Admin generation template | contract type/content/version/active/audit; repository; contract template API |
| `ContractGenerationJob` → `contract_generation_jobs` | AI generation request/state → user/template | prompt/input/output/status/error/timestamps; repository; generation API |
| `UserContract` → `user_contracts` | Persisted generated contract → user/job/template | title/content/current version/status/timestamps; repository; contract APIs |
| `ContractVersion` → `contract_versions` | Immutable generated contract version → user contract | version number/content/change metadata/time; repository; version/revert APIs |
| `FeedbackSurvey` → `feedback_surveys` | Admin-defined survey | title/questions/status/window/audit; repository; feedback API |
| `FeedbackSurveyResponse` → `feedback_survey_responses` | User answers → survey/user | payload/time; repository; response API |
| `RevenueSetting` → `revenue_settings` | Singleton/active finance policy settings | payout thresholds/rates/audit/version; repository; admin revenue settings |
| `RevenuePeriod` → `revenue_periods` | Monthly payroll period | unique period/status/calculated/closed/audit; repository; revenue API/scheduler |
| `ExpertRevenueStatement` → `expert_revenue_statements` | Expert statement → period/expert | unique period+expert, totals/status/payment fields; repository |
| `ExpertRevenueStatementItem` → `expert_revenue_statement_items` | Ticket revenue line → statement/ticket | gross/commission/net/snapshot fields; repository |
| `RevenueAdjustment` → `revenue_adjustments` | Manual credit/debit → period/expert/admin | amount/reason/audit/time; repository; admin API |
| `ExpertPayoutTransaction` → `expert_payout_transactions` | Regular/early payout payment record → expert/statement/request | unique reference/status/amount/provider/time; repository |
| `EarlyPayoutRequest` → `early_payout_requests` | Expert early payout workflow → expert/reviewer | requested/approved/fee/net/status/notes/payment/time; repository; expert/admin API |
| `CommissionPolicy` → `commission_policies` | Versioned commission rate/effective window | status/rate/effective/audit; repository; commission APIs |
| `CommissionPolicyChangeRequest` → `commission_policy_change_requests` | Email-verified admin policy change → requester | hashed token/expiry/status/proposed values; repository |
| `CommissionPolicyExpertNotification` → `commission_policy_expert_notifications` | Delivery/read state → policy/expert | unique policy/expert expected, email snapshot/retry/status/time; repository |
| `FinancialAuditLog` → `financial_audit_logs` | Immutable revenue/payout action → actor/subject | action/entity/before/after/metadata/time; repository; audit API |
| `SystemNotification` → `system_notifications` | In-app/system notice → user | type/title/body/read/time; repository; no dedicated exposed notification controller found |

### Migration provenance

The 23 migrations in `backend/src/main/resources/db/migration` begin with `V20260717_00__complete_entity_baseline.sql`. Subsequent files add conversation memory, ticket collaboration, email verification, subscription/refund/KB extensions, contract type, schema/FK reconciliation, feedback, sharing, expert payment, active-plan uniqueness, revenue payroll, message/query idempotency, plan snapshots, policy acceptance, drafting persistence, expert ticket payment, and workflow status alignment through `V20260722_07__align_legal_ticket_workflow_statuses.sql`. No duplicate Flyway version exists in the inspected tree. `EntityMigrationCoverageTest` passes, but a real Flyway validation against production history was not run.

## Neo4j

`GraphRepository.SUPPORTED_LABELS` defines `Document`, `Part`, `Chapter`, `Section`, `Article`, `Clause`, `Point`, `Subsection`, and `Chunk`; every hierarchy node also carries `KnowledgeNode`. `ReviewChecklist`, `ConversationTurn`, and `Technology` are separate legacy/special-purpose labels.

| Item | Properties/use | Evidence |
|---|---|---|
| `KnowledgeNode` hierarchy | `node_id`, title, text, order, token count, source path, file type, JSON metadata, timestamps; chunks add 384-d embedding | `graph/repository.py::_merge_node` |
| Relationships | `PARENT_OF`, `HAS_PART`, `HAS_CHAPTER`, `HAS_SECTION`, `HAS_ARTICLE`, `HAS_CLAUSE`, `HAS_POINT`, `HAS_CHUNK`, `NEXT`; legacy queries also understand `HAS_SUBSECTION` | graph repository/client |
| `ConversationTurn` | session/message IDs, bounded question/answer, document/citation IDs, embedding, created time | `conversation_memory_service.py` |
| Vector indexes | `chunk_embedding_index`, `legal_chunk_embedding_index`, `review_checklist_embedding_index`, `conversation_turn_embedding_idx`; cosine, normally 384 dimensions | config, graph/client/memory services |
| Full-text index | `chunk_fulltext_index` on chunk text/title | `graph/repository.py` |
| Constraints/indexes | unique `KnowledgeNode.node_id` per supported label setup and unique `ConversationTurn.turn_id` | schema initialization code |

PostgreSQL `Document.id`, workspace/user IDs, knowledge version `aiDocumentId`, visibility and source type are serialized into node metadata and retrieval filters. This is an application-level mapping, not a database-enforced foreign key.

## Integrity risks

- PostgreSQL file record, filesystem binary and Neo4j graph update are non-atomic; failures can orphan any side. Processing states and repair endpoints mitigate but do not eliminate this.
- Deleting a document must remove graph nodes and file bytes as well as relational rows; verify each service path because JPA cascade cannot cover Neo4j/filesystem.
- The broad baseline plus reconciliation migrations raise checksum/history risk if an already-applied file is edited. Never edit applied SQL; compare `flyway_schema_history` before renaming.
- `LegalTicket` has many enum/status check constraints. Java enum evolution must be paired with SQL, as shown by the final alignment migration.
- Retired quota columns remain in `subscription_plans`; readers must use effective active rules, not infer enforcement from column presence.
- Notification, graph, generated-file and callback records can become orphaned when external delivery fails; retry/audit status should be monitored.
