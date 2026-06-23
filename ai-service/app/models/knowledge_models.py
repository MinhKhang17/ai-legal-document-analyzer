from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class DocumentBlock:
    text: str
    kind: str = "paragraph"
    order: int = 0
    page_number: int | None = None
    style: str | None = None
    heading_level: int | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ExtractedDocument:
    source_path: Path
    title: str
    file_type: str
    blocks: list[DocumentBlock]
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class SemanticUnit:
    unit_id: str
    text: str
    title: str
    hierarchy_path: list[str]
    unit_type: str
    order: int
    source_block_indexes: list[int] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class HierarchyNode:
    node_id: str
    label: str
    title: str
    text: str = ""
    parent_id: str | None = None
    order: int = 0
    token_count: int = 0
    metadata: dict[str, Any] = field(default_factory=dict)
    embedding: list[float] | None = None


@dataclass(frozen=True)
class ChunkedDocument:
    document_id: str
    title: str
    source_path: Path
    file_type: str
    nodes: list[HierarchyNode]
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class RetrievedChunk:
    chunk_id: str
    text: str
    score: float
    title: str
    context: list[dict[str, Any]] = field(default_factory=list)
    source_type: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class IngestionResult:
    document_id: str
    title: str
    file_type: str
    source_path: str
    total_blocks: int
    total_units: int
    total_chunks: int
    chunk_ids: list[str] = field(default_factory=list)
    filename: str | None = None
    ingestion_version: int = 1
    chunking_strategy: str | None = None
    total_parent_nodes: int = 0
    total_child_chunks: int = 0
    embedded_chunks: int = 0
    skipped_chunks: int = 0
    avg_chunk_tokens: float = 0.0
    max_chunk_tokens: int = 0
    warnings: list[str] = field(default_factory=list)
