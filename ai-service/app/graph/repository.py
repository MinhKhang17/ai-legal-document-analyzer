from __future__ import annotations

import json
import logging
import re
import unicodedata
from collections import defaultdict
from typing import Any

from app.core.config import settings
from app.graph.connection import get_driver
from app.models.knowledge_models import ChunkedDocument, HierarchyNode, RetrievedChunk


logger = logging.getLogger(__name__)

SUPPORTED_LABELS = {"Document", "Part", "Chapter", "Section", "Article", "Clause", "Point", "Chunk", "Subsection"}

FULLTEXT_INDEX_NAME = "chunk_fulltext_index"


class GraphRepository:
    def __init__(self, vector_index_name: str | None = None) -> None:
        self.driver = get_driver()
        self.vector_index_name = vector_index_name or settings.vector_index_name

    def ensure_schema(self) -> None:
        queries = [
            *[
                f"""
                CREATE CONSTRAINT {label.lower()}_node_id IF NOT EXISTS
                FOR (n:{label})
                REQUIRE n.node_id IS UNIQUE
                """
                for label in sorted(SUPPORTED_LABELS)
            ],
            """
            CREATE CONSTRAINT knowledge_node_id IF NOT EXISTS
            FOR (n:KnowledgeNode)
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
            f"""
            CREATE FULLTEXT INDEX {FULLTEXT_INDEX_NAME} IF NOT EXISTS
            FOR (n:Chunk) ON EACH [n.text, n.title]
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

        # Process 'Document' first to ensure parent node is created before child nodes reference it
        labels_order = ["Document"] + [l for l in sorted(SUPPORTED_LABELS) if l != "Document"]

        with self.driver.session() as session:
            for label in labels_order:
                for node in node_groups.get(label, []):
                    self._merge_node(session, node, document)
            self._link_chunk_sequence(session, [node for node in document.nodes if node.label == "Chunk"])

    def search_chunks(
        self,
        query_embedding: list[float],
        top_k: int = 5,
        query_text: str | None = None,
        metadata_filter: dict[str, Any] | None = None,
    ) -> list[RetrievedChunk]:
        return self._search_chunks_with_filter(
            query_embedding=query_embedding,
            top_k=top_k,
            query_text=query_text,
            metadata_filter=metadata_filter,
        )

    def search_user_chunks(
        self,
        embedding: list[float],
        user_id: str,
        workspace_id: str,
        top_k: int = 5,
        *,
        query_text: str | None = None,
        document_id: str | None = None,
        document_ids: list[str] | None = None,
    ) -> list[RetrievedChunk]:
        metadata_filter: dict[str, Any] = {
            "source_type": "USER_DOCUMENT",
            "user_id": user_id,
            "workspace_id": workspace_id,
        }
        if document_ids:
            metadata_filter["document_id"] = set(document_ids)
        elif document_id:
            metadata_filter["document_id"] = document_id
        return self._search_chunks_with_filter(
            query_embedding=embedding,
            top_k=top_k,
            query_text=query_text,
            metadata_filter=metadata_filter,
        )

    def search_knowledge_chunks(
        self,
        embedding: list[float],
        top_k: int = 5,
        *,
        query_text: str | None = None,
    ) -> list[RetrievedChunk]:
        return self._search_chunks_with_filter(
            query_embedding=embedding,
            top_k=top_k,
            query_text=query_text,
            metadata_filter={
                "source_type": {None, "SYSTEM_KB"},
            },
        )

    def _search_chunks_with_filter(
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
                candidate_limit = top_k if not metadata_filter else 2000
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
                vector_hits = [
                    self._record_to_retrieved_chunk(record, metadata_filter=metadata_filter)
                    for record in filtered_records[:top_k]
                ]
                text_hits = self._search_chunks_by_text(
                    query_text,
                    top_k=top_k,
                    metadata_filter=metadata_filter,
                )
                logger.info("DEBUG SEARCH COMPONENT: metadata_filter=%s, candidate_limit=%d, filtered_records=%d, vector_hits=%d, text_hits=%d", metadata_filter, candidate_limit, len(filtered_records), len(vector_hits), len(text_hits))
                res = self._merge_hits(vector_hits, text_hits, top_k=top_k)
                logger.info("DEBUG SEARCH COMPONENT MERGED: count=%d", len(res))
                return res
            except Exception as e:
                logger.exception("Exception in _search_chunks_with_filter: %s", e)
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
            source_type=str(metadata.get("source_type") or ""),
            metadata=metadata,
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

    def update_document_lifecycle(
        self,
        document_id: str,
        *,
        knowledge_base_id: str | None = None,
        visibility: str,
        active: bool,
        published_at: str | None,
    ) -> int:
        """Update existing JSON metadata only; vector nodes/index/schema stay unchanged."""
        rows = self.list_chunks()
        updates: list[tuple[str, str]] = []
        for row in rows:
            metadata = self._parse_metadata(row.get("metadata_json"))
            source_metadata = metadata.get("source_metadata")
            legacy_metadata = source_metadata if isinstance(source_metadata, dict) else {}
            stored_knowledge_base_id = str(
                metadata.get("knowledge_base_id")
                or legacy_metadata.get("knowledge_base_id")
                or ""
            )
            if knowledge_base_id and stored_knowledge_base_id != knowledge_base_id:
                continue
            candidate_ids = {
                str(metadata.get("document_id") or ""),
                str(metadata.get("knowledge_document_id") or ""),
            }
            if document_id not in candidate_ids:
                continue
            metadata.update({
                "knowledge_base_id": knowledge_base_id or stored_knowledge_base_id or None,
                "visibility": visibility,
                "active": active,
                "effective_status": "ACTIVE" if active and visibility == "PUBLIC" else "INACTIVE",
                "published_at": published_at,
            })
            updates.append((str(row["chunk_id"]), json.dumps(metadata, ensure_ascii=False)))

        if not updates:
            return 0
        with self.driver.session() as session:
            for chunk_id, metadata_json in updates:
                session.run(
                    "MATCH (chunk:Chunk {node_id: $chunk_id}) SET chunk.metadata_json = $metadata_json",
                    chunk_id=chunk_id,
                    metadata_json=metadata_json,
                ).consume()
        return len(updates)

    def _search_chunks_by_text(
        self,
        query_text: str,
        top_k: int = 5,
        metadata_filter: dict[str, Any] | None = None,
    ) -> list[RetrievedChunk]:
        query_tokens = self._tokenize(query_text)
        if not query_tokens:
            return []

        # Build a Lucene-compatible query string for Neo4j Full-Text Search
        lucene_query = " OR ".join(query_tokens)

        candidate_limit = 2000 if metadata_filter else top_k

        cypher = f"""
        CALL db.index.fulltext.queryNodes($index_name, $query_text, {{limit: $limit}})
        YIELD node, score
        RETURN node.node_id AS chunk_id,
               coalesce(node.title, '') AS title,
               coalesce(node.text, '') AS text,
               coalesce(node.metadata_json, '{{}}') AS metadata_json,
               score AS score
        ORDER BY score DESC
        """

        try:
            with self.driver.session() as session:
                result = session.run(
                    cypher,
                    index_name=FULLTEXT_INDEX_NAME,
                    query_text=lucene_query,
                    limit=candidate_limit,
                )
                filtered: list[RetrievedChunk] = []
                for record in result:
                    if not self._record_matches_metadata(record, metadata_filter):
                        continue
                    metadata = self._parse_metadata(record["metadata_json"])
                    text = record["text"] or ""
                    retrieval_text = str(metadata.get("retrieval_text") or "").strip()
                    if retrieval_text:
                        text = retrieval_text
                    filtered.append(
                        RetrievedChunk(
                            chunk_id=record["chunk_id"],
                            text=text,
                            score=float(record["score"]),
                            title=record["title"] or "",
                            context=self.get_context(record["chunk_id"], metadata_filter=metadata_filter),
                            source_type=str(metadata.get("source_type") or ""),
                            metadata=metadata,
                        )
                    )
                    if len(filtered) >= top_k:
                        break
                return filtered
        except Exception as exc:
            logger.warning("Full-text search failed, falling back to in-memory search: %s", exc)
            return self._search_chunks_by_text_fallback(query_text, top_k=top_k, metadata_filter=metadata_filter)

    def _search_chunks_by_text_fallback(
        self,
        query_text: str,
        top_k: int = 5,
        metadata_filter: dict[str, Any] | None = None,
    ) -> list[RetrievedChunk]:
        """Legacy in-memory keyword search. Used as fallback when full-text index is unavailable."""
        query_tokens = set(self._tokenize(query_text))
        if not query_tokens:
            return []

        def token_weight(token: str) -> float:
            if token.isdigit():
                return 8.0
            if len(token) >= 6:
                return 1.5
            return 1.0

        total_query_weight = sum(token_weight(token) for token in query_tokens)

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
            overlapping_tokens = query_tokens & chunk_tokens
            if not overlapping_tokens:
                continue
            overlap_weight = sum(token_weight(token) for token in overlapping_tokens)
            score = overlap_weight / max(1.0, total_query_weight)
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
                source_type=str(self._parse_metadata(chunk.get("metadata_json")).get("source_type") or ""),
                metadata=self._parse_metadata(chunk.get("metadata_json")),
            )
            for score, chunk in selected
        ]

    def _merge_hits(
        self,
        vector_hits: list[RetrievedChunk],
        text_hits: list[RetrievedChunk],
        top_k: int,
    ) -> list[RetrievedChunk]:
        # Lexical query expansion can identify an exact article number or legal
        # phrase that cosine similarity misses. Preserve the two strongest text
        # matches, then fill the remaining slots with semantic vector results.
        ordered_candidates = [*text_hits[:2], *vector_hits, *text_hits[2:]]
        merged: list[RetrievedChunk] = []
        seen_ids: set[str] = set()
        for hit in ordered_candidates:
            if hit.chunk_id in seen_ids:
                continue
            seen_ids.add(hit.chunk_id)
            merged.append(hit)
            if len(merged) >= top_k:
                break
        return merged

    def _record_matches_metadata(self, record: Any, metadata_filter: dict[str, Any] | None) -> bool:
        if not metadata_filter:
            return True
        metadata_json = record["metadata_json"] if hasattr(record, "__getitem__") else "{}"
        return self._matches_metadata(self._parse_metadata(metadata_json), metadata_filter)

    def _matches_metadata(self, metadata: dict[str, Any], metadata_filter: dict[str, Any] | None) -> bool:
        if not metadata_filter:
            return True
        for key, expected in metadata_filter.items():
            actual = metadata.get(key)
            if isinstance(expected, (list, tuple, set, frozenset)):
                if actual not in expected:
                    return False
                continue
            if actual != expected:
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

    def _tokenize(self, text: str | None) -> list[str]:
        if not text:
            return []
        normalized = unicodedata.normalize("NFKD", text)
        stripped = "".join(char for char in normalized if not unicodedata.combining(char))
        return re.findall(r"[a-z0-9]+", stripped.lower())

    def get_context(
        self,
        chunk_id: str,
        sibling_limit: int = 2,
        metadata_filter: dict[str, Any] | None = None,
        include_extended_context: bool = False,
    ) -> list[dict[str, Any]]:
        with self.driver.session() as session:
            ancestor_query = """
            MATCH path = (doc:Document)-[:PARENT_OF|HAS_PART|HAS_CHAPTER|HAS_SECTION|HAS_ARTICLE|HAS_CLAUSE|HAS_POINT|HAS_CHUNK*0..8]->(chunk:Chunk {node_id: $chunk_id})
            RETURN [node IN nodes(path) | {
                node_id: node.node_id,
                label: head(labels(node)),
                title: coalesce(node.title, ''),
                order: coalesce(node.order, 0)
            }] AS lineage
            """
            ancestor_result = session.run(ancestor_query, chunk_id=chunk_id).single()
            lineage = ancestor_result["lineage"] if ancestor_result else []

            if not include_extended_context:
                return [{"type": "ancestors", "nodes": lineage}]

            sibling_query = """
            MATCH (parent)-[:PARENT_OF|HAS_CHUNK]->(chunk:Chunk {node_id: $chunk_id})
            MATCH (parent)-[:PARENT_OF|HAS_CHUNK]->(sibling:Chunk)
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
            {"type": "ancestors", "nodes": lineage},
            {"type": "siblings", "nodes": siblings},
        ]

    def _merge_node(self, session, node: HierarchyNode, document: ChunkedDocument) -> None:
        label = node.label
        if label not in SUPPORTED_LABELS:
            raise ValueError(f"Unsupported label: {label}")

        query = f"""
        MERGE (n:KnowledgeNode {{node_id: $node_id}})
        SET n:{label},
            n.title = $title,
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

        session.run(query, **params)
        if node.parent_id:
            relation = self._relationship_type(label)
            relation_params: dict[str, Any] = {
                "parent_id": node.parent_id,
                "child_id": node.node_id,
            }
            session.run(
                """
                MATCH (parent:KnowledgeNode {node_id: $parent_id})
                MATCH (child:KnowledgeNode {node_id: $child_id})
                MERGE (parent)-[:PARENT_OF]->(child)
                """,
                **relation_params,
            )
            if relation:
                session.run(
                    f"""
                    MATCH (parent:KnowledgeNode {{node_id: $parent_id}})
                    MATCH (child:KnowledgeNode {{node_id: $child_id}})
                    MERGE (parent)-[:{relation}]->(child)
                    """,
                    **relation_params,
                )

    def _relationship_type(self, child_label: str) -> str | None:
        if child_label == "Chunk":
            return "HAS_CHUNK"
        if child_label == "Point":
            return "HAS_POINT"
        if child_label == "Clause":
            return "HAS_CLAUSE"
        if child_label == "Article":
            return "HAS_ARTICLE"
        if child_label == "Section":
            return "HAS_SECTION"
        if child_label == "Chapter":
            return "HAS_CHAPTER"
        if child_label == "Part":
            return "HAS_PART"
        return None

    def _link_chunk_sequence(self, session, chunks: list[HierarchyNode]) -> None:
        ordered = sorted(chunks, key=lambda node: (node.order, node.node_id))
        if not ordered:
            return
        session.run(
            """
            MATCH (doc:Document {node_id: $document_id})-[:PARENT_OF|HAS_PART|HAS_CHAPTER|HAS_SECTION|HAS_ARTICLE|HAS_CLAUSE|HAS_POINT|HAS_CHUNK*0..8]->(chunk:Chunk)
            OPTIONAL MATCH (chunk)-[r:NEXT]->()
            DELETE r
            """,
            document_id=ordered[0].metadata.get("document_id"),
        )
        for left, right in zip(ordered, ordered[1:]):
            session.run(
                """
                MATCH (a:Chunk {node_id: $left_id})
                MATCH (b:Chunk {node_id: $right_id})
                MERGE (a)-[:NEXT]->(b)
                """,
                left_id=left.node_id,
                right_id=right.node_id,
            )
