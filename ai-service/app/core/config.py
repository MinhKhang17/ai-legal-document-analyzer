"""Application configuration loaded from environment variables / .env file.

No secrets are hard-coded here. Everything is sourced from the environment.
"""
from enum import Enum
from functools import lru_cache
from typing import List, Optional

from pydantic_settings import BaseSettings, SettingsConfigDict


class LLMProvider(str, Enum):
    NONE = "NONE"
    GEMINI = "GEMINI"
    OLLAMA = "OLLAMA"
    VLLM = "VLLM"


class Settings(BaseSettings):
    """Strongly-typed application settings.

    Values are read (case-insensitively) from environment variables or a local
    .env file. See .env.example for the full list.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # --- Service metadata ---
    service_name: str = "ai-service"
    service_version: str = "1.0.0"
    # Common path prefix for every router. Lets the Spring Boot gateway route
    # all AI endpoints with a single rule (e.g. /api/ai/**). Set to "" to
    # disable. No trailing slash.
    api_prefix: str = "/api/ai"

    # --- Neo4j ---
    neo4j_uri: str = "bolt://localhost:7687"
    # Accept both NEO4J_USERNAME (spec) and legacy NEO4J_USER via validation_alias-free fallback.
    neo4j_username: str = "neo4j"
    neo4j_password: str = "password"
    neo4j_max_query_limit: int = 500

    # --- LLM provider configuration ---
    llm_provider: LLMProvider = LLMProvider.NONE
    llm_api_key: Optional[str] = None
    llm_model: Optional[str] = None
    ollama_base_url: str = "http://localhost:11434"
    vllm_base_url: str = "http://localhost:8001"
    gemini_base_url: str = "https://generativelanguage.googleapis.com"

    # --- Embeddings ---
    # AUTO: use Gemini embeddings when a Gemini key is available, else lexical.
    embedding_provider: str = "AUTO"  # AUTO | GEMINI | LEXICAL
    embedding_model: str = "gemini-embedding-001"

    # --- Security ---
    # Optional internal API key. When set, protected endpoints require the
    # X-AI-Service-Key header to match.
    ai_service_api_key: Optional[str] = None

    # --- CORS ---
    # Comma-separated list of allowed origins for browser clients.
    cors_allow_origins: str = "http://localhost:8080,http://localhost:5173"

    @property
    def cors_origins_list(self) -> List[str]:
        return [o.strip() for o in self.cors_allow_origins.split(",") if o.strip()]

    @property
    def llm_configured(self) -> bool:
        return self.llm_provider != LLMProvider.NONE


@lru_cache
def get_settings() -> Settings:
    """Return a cached Settings instance.

    Some deployments use NEO4J_USER instead of NEO4J_USERNAME; honor it as a
    fallback so existing .env files keep working.
    """
    import os

    if "NEO4J_USERNAME" not in os.environ and "NEO4J_USER" in os.environ:
        os.environ["NEO4J_USERNAME"] = os.environ["NEO4J_USER"]
    return Settings()
