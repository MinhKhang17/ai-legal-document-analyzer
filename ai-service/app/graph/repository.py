from __future__ import annotations

import json
from collections import defaultdict
from typing import Any

from app.core.config import settings
from app.graph.connection import get_driver
from app.models.knowledge_models import ChunkedDocument, HierarchyNode, RetrievedChunk


class GraphRepository:
    def __init__(self) -> None:
        self.driver = get_driver()

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
            CREATE VECTOR INDEX {settings.vector_index_name} IF NOT EXISTS
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
                session.run(query)

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

    def search_chunks(self, query_embedding: list[float], top_k: int = 5) -> list[RetrievedChunk]:
        self.ensure_schema()
        cypher = f"""
        CALL db.index.vector.queryNodes($index_name, $top_k, $embedding)
        YIELD node, score
        RETURN node.node_id AS chunk_id,
               node.title AS title,
               node.text AS text,
               score AS score
        ORDER BY score DESC
        """

        with self.driver.session() as session:
            result = session.run(
                cypher,
                index_name=settings.vector_index_name,
                top_k=top_k,
                embedding=query_embedding,
            )
            return [
                RetrievedChunk(
                    chunk_id=record["chunk_id"],
                    text=record["text"] or "",
                    score=float(record["score"]),
                    title=record["title"] or "",
                    context=self.get_context(record["chunk_id"]),
                )
                for record in result
            ]

    def get_context(self, chunk_id: str, sibling_limit: int = 2) -> list[dict[str, Any]]:
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
