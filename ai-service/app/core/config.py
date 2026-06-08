from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    neo4j_uri: str = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    neo4j_user: str = os.getenv("NEO4J_USER", "neo4j")
    neo4j_password: str = os.getenv("NEO4J_PASSWORD", "password")
    chunk_target_min_tokens: int = int(os.getenv("CHUNK_TARGET_MIN_TOKENS", "400"))
    chunk_target_max_tokens: int = int(os.getenv("CHUNK_TARGET_MAX_TOKENS", "600"))
    chunk_hard_max_tokens: int = int(os.getenv("CHUNK_HARD_MAX_TOKENS", "800"))
    embedding_dimensions: int = int(os.getenv("EMBEDDING_DIMENSIONS", "384"))
    vector_index_name: str = os.getenv("VECTOR_INDEX_NAME", "chunk_embedding_index")


settings = Settings()
