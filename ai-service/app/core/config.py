from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


load_dotenv()


@dataclass(frozen=True)
class Settings:
    neo4j_uri: str = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    neo4j_user: str = os.getenv("NEO4J_USER", "neo4j")
    neo4j_password: str = os.getenv("NEO4J_PASSWORD", "password")
    chunk_target_min_tokens: int = int(os.getenv("CHUNK_TARGET_MIN_TOKENS", "400"))
    chunk_target_max_tokens: int = int(os.getenv("CHUNK_TARGET_MAX_TOKENS", "600"))
    chunk_hard_max_tokens: int = int(os.getenv("CHUNK_HARD_MAX_TOKENS", "800"))
    chunk_v2_target_min_tokens: int = int(os.getenv("CHUNK_V2_TARGET_MIN_TOKENS", "120"))
    chunk_v2_target_max_tokens: int = int(os.getenv("CHUNK_V2_TARGET_MAX_TOKENS", "220"))
    chunk_v2_hard_max_tokens: int = int(os.getenv("CHUNK_V2_HARD_MAX_TOKENS", "320"))
    embedding_dimensions: int = int(os.getenv("EMBEDDING_DIMENSIONS", "384"))
    embedding_model_name: str = os.getenv("EMBEDDING_MODEL_NAME", "hashing-legal-v1")
    embedding_provider: str = os.getenv("EMBEDDING_PROVIDER", "hashing")
    vector_store_provider: str = os.getenv("VECTOR_STORE_PROVIDER", "neo4j")
    vector_index_name: str = os.getenv("VECTOR_INDEX_NAME", "chunk_embedding_index")
    legal_vector_index_name: str = os.getenv("LEGAL_VECTOR_INDEX_NAME", "legal_chunk_embedding_index")
    document_import_dir: Path = Path(
        os.getenv(
            "DOCUMENT_IMPORT_DIR",
            str(Path(__file__).resolve().parents[3] / ".tmp" / "test-files"),
        )
    )
    docs_flow_dir: Path = Path(
        os.getenv(
            "LEGAL_DOCS_FLOW_DIR",
            str(Path(__file__).resolve().parents[2] / "docs" / "flow"),
        )
    )
    risk_kb_source_file: str = os.getenv("RISK_KB_SOURCE_FILE", "RISK_KB_VI.md")
    risk_strategy_source_file: str = os.getenv("RISK_STRATEGY_SOURCE_FILE", "Legal Risk Strategy.md")
    system_prompt_source_file: str = os.getenv("SYSTEM_PROMPT_SOURCE_FILE", "SYSTEM_PROMPT_LEGAL_RAG.md")
    legal_structure_source_file: str = os.getenv("LEGAL_STRUCTURE_SOURCE_FILE", "CAU_TRUC_TAI_LIEU_PHAP_LY.md")
    contract_v2_route_prefix: str = os.getenv("CONTRACT_V2_ROUTE_PREFIX", "/v2/contracts")
    rule_confidence_threshold: float = float(os.getenv("RULE_CONFIDENCE_THRESHOLD", "0.85"))
    llm_confidence_threshold: float = float(os.getenv("LLM_CONFIDENCE_THRESHOLD", "0.85"))
    bm25_top_k: int = int(os.getenv("BM25_TOP_K", "8"))
    semantic_top_k: int = int(os.getenv("SEMANTIC_TOP_K", "8"))
    legal_top_k: int = int(os.getenv("LEGAL_TOP_K", "5"))
    max_llm_candidates: int = int(os.getenv("MAX_LLM_CANDIDATES", "3"))
    llm_provider: str = os.getenv("LLM_PROVIDER", "gemini")
    llm_query_enabled: bool = os.getenv("LLM_QUERY_ENABLED", "true").strip().lower() in {"1", "true", "yes", "on"}
    llm_v2_enabled: bool = os.getenv("LLM_V2_ENABLED", "true").strip().lower() in {"1", "true", "yes", "on"}
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-3.5-flash")
    gemini_base_url: str = os.getenv("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta")
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    gemini_fallback_model: str = os.getenv("GEMINI_FALLBACK_MODEL", "")
    gemini_timeout_seconds: float = float(os.getenv("GEMINI_TIMEOUT_SECONDS", "120"))
    gemini_max_output_tokens: int = int(os.getenv("GEMINI_MAX_OUTPUT_TOKENS", "4096"))
    gemini_thinking_budget: int = int(os.getenv("GEMINI_THINKING_BUDGET", "512"))
    gemini_max_retries: int = int(os.getenv("GEMINI_MAX_RETRIES", "4"))
    gemini_retry_backoff_seconds: float = float(os.getenv("GEMINI_RETRY_BACKOFF_SECONDS", "2"))
    gemini_embedding_model: str = os.getenv("GEMINI_EMBEDDING_MODEL", "text-embedding-004")
    gemini_embedding_dimensions: int = int(os.getenv("GEMINI_EMBEDDING_DIMENSIONS", "384"))
    llm_intent_enabled: bool = os.getenv("LLM_INTENT_ENABLED", "false").strip().lower() in {"1", "true", "yes", "on"}
    llm_rerank_enabled: bool = os.getenv("LLM_RERANK_ENABLED", "false").strip().lower() in {"1", "true", "yes", "on"}


settings = Settings()
