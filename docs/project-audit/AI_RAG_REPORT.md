# AI and RAG report

## Verified pipeline

The production chat entry is `POST /internal/rag/query` (`app/api/routes.py`) invoked by Spring `PythonAiClient` from `ChatMessageServiceImpl`. The request carries request/user/workspace/session/message identifiers, the current question, attached/focused/message document IDs, bounded conversation history/summary and retrieval limits. Pydantic rejects malformed context with 422; Spring must translate this without losing the machine code.

1. Rule/intent logic normalizes the question and detects shortcuts, legal intent, contract type, party role and jurisdiction.
2. `completeness_checker.py` decides whether the selected intent can proceed without user chunks and which facts are missing.
3. User-document retrieval is restricted by user/workspace/document metadata; system knowledge retrieval requires published/safe visibility (`knowledge_access.py`).
4. `GraphRepository` combines Neo4j vector search with full-text search, falls back to in-memory keyword scoring if the index is unavailable, and adds ancestor/sibling context.
5. Candidates are scored, legally boosted, deduplicated, bounded by token count and selected for the prompt.
6. Gemini generates a structured response; rule/retrieval-only answers cover disabled/unavailable LLM cases where possible.
7. Response normalization emits status, mode, answer, citations, risk, confidence, suggestions, missing information, drafting workflow and estimated usage. Conversation turns may be indexed separately for semantic memory.

## Intents and scope

`app/models/intent_enums.py::LegalQueryIntent` contains 52 values. User-visible supported families verified in pipeline/tests are greetings/capabilities/thanks; general Vietnamese legal/KB questions; document analysis; information extraction; supported student rental, part-time/internship, small service/freelance, small asset sale and personal-loan review; summary; risk; clause analysis/rewrite/revision/drafting/suggestion; missing-clause check; negotiation/signing support; drafting prompt generation; export; expert escalation; and temporal/validity/type analysis. Guard outcomes include ambiguous/underspecified, invalid, foreign law, unsupported contract, incomplete document, no relevant document, low confidence, partial/outdated/conflicting KB, unsafe/prompt injection and out-of-scope.

Contract types are `RENTAL`, `PART_TIME_EMPLOYMENT`, `INTERNSHIP`, `COLLABORATOR`, `FREELANCE_SERVICE`, `SMALL_ASSET_SALE`, `PERSONAL_LOAN`, `UNSUPPORTED`, and `UNKNOWN`. Party roles are Party A/B, tenant/landlord, borrower/lender, or unknown. Enum presence alone is not proof of equal prompt quality for every combination.

## Chunking, embeddings and retrieval

| Concern | Actual configuration/evidence |
|---|---|
| General chunking | target 400–600 tokens, hard max 800 (`core/config.py`) |
| V2 chunking | target 120–220, hard max 320 |
| Legal unit splitter | max 4,000 characters with 500 overlap (`legal_chunk_service.py`) |
| Embedding | default provider `hashing`, name `hashing-legal-v1`, 384 dimensions; sentence-transformers MiniLM and Gemini `text-embedding-004` paths exist |
| Vector store | Neo4j cosine indexes; user/general `chunk_embedding_index`, legal `legal_chunk_embedding_index` |
| Default retrieval | BM25/full-text top 8, semantic top 8, legal top 5, at most 3 LLM candidates in core settings; request models bound user chunks/checklist and KB top-k |
| Reranking | deterministic score + query-token legal boost + dedupe by legal path/text; optional LLM rerank disabled by default |
| Fallback | vector failure → full-text; full-text failure → in-memory keyword; no chunks → safe no-data/retrieval response |

The legacy `Neo4jClient` and newer `GraphRepository` coexist. Multiple retrieval and chunking implementations are a maintenance risk and can produce different source rankings.

## Prompt, context and citations

Prompts require use of provided evidence, distinguish user documents from system KB, avoid unsupported final legal conclusions, ask for missing facts, and return structured fields. `conversation_memory_service.py` bounds current/history tokens and stores an immutable-ish turn vector; `conversation_context.py` maps retrieved sources to internal citation IDs then renders readable source labels. Citations include document/chunk identifiers, excerpt and score and are persisted as `AiCitation` by Spring.

Recent code suppresses repeated raw `[USER-n]`/`[KB-n]` presentation in favor of readable document names, but internal IDs still exist for grounding. Confidence derives from classifier/rule thresholds, retrieval scores and response normalization rather than a calibrated legal correctness probability. Risk levels are `NONE/LOW/MEDIUM/HIGH/CRITICAL/UNKNOWN`; high-risk outcomes can recommend an expert.

## Missing information and drafting

`completeness_checker.py` requires an attached user document for review-like intents, a known type/role/target clause where relevant, and enough facts for signing or clause drafting. General legal guidance is permitted without attachment. `drafting_workflow.py` maps supported document types to optional questions, redacts direct identifiers into stable placeholders, returns a prompt, and supports browser copy/open-ChatGPT. It is a prompt-generation workflow; it does not submit data to ChatGPT automatically.

The separate contract-generation service builds a full document via Gemini and can retrieve references. The v2 contract endpoint currently queries knowledge with a hard-coded English residential-lease phrase before generation, so non-lease generation is **PARTIALLY_IMPLEMENTED** rather than universally grounded.

## Models, tokens, retries and errors

`core/config.py` defaults to provider `gemini`, primary `gemini-3.1-flash-lite`, fallback `gemini-3.5-flash`, 120-second timeout, 4,096 output tokens, thinking budget 512, four retries and two-second backoff. `app/config.py` differs by defaulting `llm_v2_enabled` false while core config defaults true. Deployment must establish which settings object each path consumes and verify the configured model names for the account.

Token estimation uses simple whitespace/character approximations in several services, not the Gemini tokenizer. Spring reserves estimated AI quota before the call and completes it with returned/estimated actual usage, using `AiQueryExecution` idempotency. It is suitable for billing guardrails but not exact provider billing reconciliation.

Exceptions are logged and converted to FastAPI validation/HTTP errors or safe response modes. Gemini retries/fallback are implemented; Neo4j retrieval has search fallbacks. There is no authenticated boundary on FastAPI itself, so network isolation is assumed.

## Hallucination and demo risks

- Hashing embeddings can return lexically stable but semantically weak neighbors.
- Published KB coverage, dates and visibility determine whether an answer is grounded; the system can explicitly report partial/outdated/low-confidence coverage but cannot guarantee correctness.
- Conversation summary/vector memory may carry prior assumptions; immutable document/message identifiers reduce, but do not remove, contamination.
- Multiple config/retrieval/generation paths can diverge.
- A citation proves retrieved text was supplied, not that the final inference is legally correct.
- Direct FastAPI endpoints bypass Spring plan/role/audit enforcement if exposed publicly.

## Demo-safe queries

Use a known ingested Vietnamese rental or part-time contract and ask: “Tóm tắt nghĩa vụ của bên thuê”, “Trích xuất điều khoản chấm dứt và tiền cọc”, “Điều khoản giới hạn bồi thường nào bất lợi cho bên thuê?”, and “Hợp đồng còn thiếu điều khoản quan trọng nào?”. Without attachment use: “Giải thích bảo hiểm tai nạn lao động theo cách dễ hiểu; nếu thiếu dữ kiện hãy nói rõ”. Demonstrate a follow-up that references the prior answer, then inspect readable citations. Avoid unsupported foreign law, current-law assertions without a dated published KB, or asking the AI for a definitive signing decision.
