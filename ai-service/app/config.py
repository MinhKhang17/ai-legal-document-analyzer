"""Configuration settings for AI Service."""
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings."""

    model_config = SettingsConfigDict(env_file=".env", case_sensitive=False)

    # Chunking / retrieval configuration
    chunk_target_min_tokens: int = 400
    chunk_target_max_tokens: int = 600
    chunk_hard_max_tokens: int = 800
    chunk_v2_target_min_tokens: int = 120
    chunk_v2_target_max_tokens: int = 220
    chunk_v2_hard_max_tokens: int = 320

    # Neo4j Configuration
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_user: str = "neo4j"
    neo4j_password: str = "password"

    # Embedding Configuration
    embedding_provider: str = "hashing"
    embedding_model_name: str = "hashing-legal-v1"
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    embedding_dimension: int = 384
    embedding_dimensions: int = 384
    vector_index_name: str = "chunk_embedding_index"
    legal_vector_index_name: str = "legal_chunk_embedding_index"

    # Document import configuration
    document_import_dir: Path = Path("/app/uploads")

    # Gemini Configuration
    llm_provider: str = "gemini"
    llm_v2_enabled: bool = False
    llm_v2_max_concurrency: int = 4
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.0-flash-exp"
    gemini_fallback_model: str = ""
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    gemini_timeout_seconds: float = 120.0
    gemini_max_output_tokens: int = 4096
    gemini_thinking_budget: int = 512
    gemini_max_retries: int = 4
    gemini_retry_backoff_seconds: float = 2.0

    # API Configuration
    api_host: str = "0.0.0.0"
    api_port: int = 8000


settings = Settings()
