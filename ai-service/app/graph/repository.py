from __future__ import annotations

import json
import re
import unicodedata
from collections import defaultdict
from typing import Any

from app.core.config import settings
from app.graph.connection import get_driver
from app.models.knowledge_models import ChunkedDocument, HierarchyNode, RetrievedChunk


class GraphRepository:
    def __init__(self, vector_index_name: str | None = None) -> None:
        self.driver = get_driver()
        self.vector_index_name = vector_index_name or settings.vector_index_name

    def ensure_schema(self) -> None:
        queries = [
            """
            CREATE CONSTRAINT document_node_id IF NOT EXISTS
            FOR (n:Document)
            REQUIRE n.node_id IS UNIQUE
            """,
            """
            CREATE CONSTRAINT section_node_id IF NOT EXISTS
            FOR (n:Section)
            REQUIRE n.node_id IS UNIQUE
            """,
            """
            CREATE CONSTRAINT subsection_node_id IF NOT EXISTS
            FOR (n:Subsection)
            REQUIRE n.node_id IS UNIQUE
            """,
            """
            CREATE CONSTRAINT chunk_node_id IF NOT EXISTS
            FOR (n:Chunk)
            REQUIRE n.node_id IS UNIQUE
            """,
            f"""
            CREATE VECTOR INDEX {self.vector_index_name} IF NOT EXISTS
            FOR (n:Chunk) ON (n.embedding)
            OPTIONS {{
              indexConfig: {{
                `vector.dimensions`: {settings.embedding_dimensions},
                `vector.similarity_function`: 'cosine'
              }}
            }}
            """,
        ]

        with self.driver.session() as session:
            for query in queries:
                session.run(query).consume()

    def upsert_document(self, document: ChunkedDocument) -> None:
        self.ensure_schema()
        node_groups: dict[str, list[HierarchyNode]] = defaultdict(list)
        for node in document.nodes:
            node_groups[node.label].append(node)

        with self.driver.session() as session:
            for node in node_groups.get("Document", []):
                self._merge_node(session, node, document)
            for node in node_groups.get("Section", []):
                self._merge_node(session, node, document)
            for node in node_groups.get("Subsection", []):
                self._merge_node(session, node, document)
            for node in node_groups.get("Chunk", []):
                self._merge_node(session, node, document)

    def search_chunks(
        self,
        query_embedding: list[float],
        top_k: int = 5,
        query_text: str | None = None,
        metadata_filter: dict[str, Any] | None = None,
    ) -> list[RetrievedChunk]:
        self.ensure_schema()
        cypher = f"""
        CALL db.index.vector.queryNodes($index_name, $top_k, $embedding)
        YIELD node, score
        RETURN node.node_id AS chunk_id,
               node.title AS title,
               node.text AS text,
               coalesce(node.metadata_json, '{{}}') AS metadata_json,
               score AS score
        ORDER BY score DESC
        """

        with self.driver.session() as session:
            try:
                candidate_limit = top_k if not metadata_filter else max(top_k * 5, top_k + 10)
                result = session.run(
                    cypher,
                    index_name=self.vector_index_name,
                    top_k=candidate_limit,
                    embedding=query_embedding,
                )
                filtered_records = [
                    record
                    for record in result
                    if self._record_matches_metadata(record, metadata_filter)
                ]
                return [self._record_to_retrieved_chunk(record, metadata_filter=metadata_filter) for record in filtered_records[:top_k]]
            except Exception:
                if not query_text:
                    return []
                return self._search_chunks_by_text(query_text, top_k=top_k, metadata_filter=metadata_filter)

    def _record_to_retrieved_chunk(self, record, metadata_filter: dict[str, Any] | None = None) -> RetrievedChunk:
        metadata_json = record["metadata_json"] or "{}"
        metadata: dict[str, Any] = {}
        if isinstance(metadata_json, str):
            try:
                metadata = json.loads(metadata_json)
            except Exception:
                metadata = {}

        text = record["text"] or ""
        retrieval_text = str(metadata.get("retrieval_text") or "").strip()
        if retrieval_text:
            text = retrieval_text

        return RetrievedChunk(
            chunk_id=record["chunk_id"],
            text=text,
            score=float(record["score"]),
            title=record["title"] or "",
            context=self.get_context(record["chunk_id"], metadata_filter=metadata_filter),
        )

    def list_chunks(self) -> list[dict[str, Any]]:
        self.ensure_schema()
        cypher = """
        MATCH (node:Chunk)
        RETURN node.node_id AS chunk_id,
               coalesce(node.title, '') AS title,
               coalesce(node.text, '') AS text,
               coalesce(node.source_path, '') AS source_path,
               coalesce(node.file_type, '') AS file_type,
               coalesce(node.metadata_json, '{}') AS metadata_json,
               coalesce(node.order, 0) AS order
        ORDER BY node.order ASC, node.node_id ASC
        """

        with self.driver.session() as session:
            result = session.run(cypher)
            return [
                {
                    "chunk_id": record["chunk_id"],
                    "title": record["title"] or "",
                    "text": record["text"] or "",
                    "source_path": record["source_path"] or "",
                    "file_type": record["file_type"] or "",
                    "metadata_json": record["metadata_json"] or "{}",
                    "order": int(record["order"] or 0),
                }
                for record in result
            ]

    def _search_chunks_by_text(
        self,
        query_text: str,
        top_k: int = 5,
        metadata_filter: dict[str, Any] | None = None,
    ) -> list[RetrievedChunk]:
        query_tokens = set(self._tokenize(query_text))
        if not query_tokens:
            return []

        chunks = self.list_chunks()
        scored: list[tuple[float, dict[str, Any]]] = []
        for chunk in chunks:
            metadata = self._parse_metadata(chunk.get("metadata_json"))
            if not self._matches_metadata(metadata, metadata_filter):
                continue
            search_text = " ".join(
                part
                for part in [
                    chunk.get("title", ""),
                    chunk.get("text", ""),
                    str(metadata.get("retrieval_text") or ""),
                    str(metadata.get("embedding_text") or ""),
                ]
                if part
            )
            chunk_tokens = set(self._tokenize(search_text))
            if not chunk_tokens:
                continue
            overlap = len(query_tokens & chunk_tokens)
            if overlap == 0:
                continue
            score = overlap / max(1, len(query_tokens))
            scored.append((score, chunk))

        scored.sort(key=lambda item: item[0], reverse=True)
        selected = scored[:top_k]
        return [
            RetrievedChunk(
                chunk_id=chunk["chunk_id"],
                text=self._chunk_text_with_metadata(chunk),
                score=score,
                title=chunk["title"],
                context=self.get_context(chunk["chunk_id"]),
            )
            for score, chunk in selected
        ]

    def _record_matches_metadata(self, record: Any, metadata_filter: dict[str, Any] | None) -> bool:
        if not metadata_filter:
            return True
        metadata_json = record["metadata_json"] if hasattr(record, "__getitem__") else "{}"
        return self._matches_metadata(self._parse_metadata(metadata_json), metadata_filter)

    def _matches_metadata(self, metadata: dict[str, Any], metadata_filter: dict[str, Any] | None) -> bool:
        if not metadata_filter:
            return True
        for key, expected in metadata_filter.items():
            if metadata.get(key) != expected:
                return False
        return True

    def _chunk_text_with_metadata(self, chunk: dict[str, Any]) -> str:
        metadata = self._parse_metadata(chunk.get("metadata_json"))
        retrieval_text = str(metadata.get("retrieval_text") or "").strip()
        if retrieval_text:
            return retrieval_text
        return str(chunk.get("text") or "")

    def _parse_metadata(self, metadata_json: Any) -> dict[str, Any]:
        if not isinstance(metadata_json, str):
            return {}
        try:
            return json.loads(metadata_json)
        except Exception:
            return {}

    def _tokenize(self, text: str) -> list[str]:
        normalized = unicodedata.normalize("NFKD", text)
        stripped = "".join(char for char in normalized if not unicodedata.combining(char))
        return re.findall(r"[a-z0-9]+", stripped.lower())

    def get_context(
        self,
        chunk_id: str,
        sibling_limit: int = 2,
        metadata_filter: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        with self.driver.session() as session:
            ancestor_query = """
            MATCH path = (doc:Document)-[:PARENT_OF*0..5]->(chunk:Chunk {node_id: $chunk_id})
            RETURN [node IN nodes(path) | {
                node_id: node.node_id,
                label: head(labels(node)),
                title: coalesce(node.title, ''),
                text: coalesce(node.text, '')
            }] AS lineage
            """
            ancestor_result = session.run(ancestor_query, chunk_id=chunk_id).single()
            lineage = ancestor_result["lineage"] if ancestor_result else []

            sibling_query = """
            MATCH (parent)-[:PARENT_OF]->(chunk:Chunk {node_id: $chunk_id})
            MATCH (parent)-[:PARENT_OF]->(sibling:Chunk)
            WHERE sibling.node_id <> $chunk_id
            RETURN sibling.node_id AS node_id,
                   sibling.title AS title,
                   sibling.text AS text,
                   coalesce(sibling.metadata_json, '{}') AS metadata_json,
                   sibling.order AS order
            ORDER BY sibling.order
            LIMIT $limit
            """
            sibling_records = session.run(sibling_query, chunk_id=chunk_id, limit=sibling_limit)
            siblings = [
                {
                    "node_id": record["node_id"],
                    "label": "Chunk",
                    "title": record["title"] or "",
                    "text": record["text"] or "",
                    "order": record["order"] or 0,
                }
                for record in sibling_records
                if self._matches_metadata(self._parse_metadata(record["metadata_json"]), metadata_filter)
            ]

        return [
            {"type": "lineage", "nodes": lineage},
            {"type": "siblings", "nodes": siblings},
        ]

    def _merge_node(self, session, node: HierarchyNode, document: ChunkedDocument) -> None:
        label = node.label
        if label not in {"Document", "Section", "Subsection", "Chunk"}:
            raise ValueError(f"Unsupported label: {label}")

        query = f"""
        MERGE (n:{label} {{node_id: $node_id}})
        SET n.title = $title,
            n.text = $text,
            n.order = $order,
            n.token_count = $token_count,
            n.source_path = $source_path,
            n.file_type = $file_type,
            n.metadata_json = $metadata_json,
            n.updated_at = datetime()
        """
        params: dict[str, Any] = {
            "node_id": node.node_id,
            "title": node.title,
            "text": node.text,
            "order": node.order,
            "token_count": node.token_count,
            "source_path": str(document.source_path),
            "file_type": document.file_type,
            "metadata_json": json.dumps({**document.metadata, **node.metadata}, ensure_ascii=False),
        }
        if node.embedding is not None:
            params["embedding"] = node.embedding
            query += ", n.embedding = $embedding"

        if node.parent_id:
            query += """
            WITH n
            MATCH (parent {node_id: $parent_id})
            MERGE (parent)-[:PARENT_OF]->(n)
            """
            params["parent_id"] = node.parent_id

        session.run(query, **params)
