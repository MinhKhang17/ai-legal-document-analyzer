from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone

from neo4j import GraphDatabase


VERIFIED_LEGAL_DOCUMENT_IDS = {
    "fbfcfaf40e7f8b38e358acc9da6fc033",
    "c3079c1fd0b764c34b034d6ba1a4273b",
    "160cd749ba292713f9bf8f086240f624",
    "6a43556dcde7cee930e93263fea1bbc3",
    "467e34b74c24ac0ecc662ef49ab6160c",
}


def _document_id(metadata: dict[str, object]) -> str:
    return str(metadata.get("knowledge_document_id") or metadata.get("document_id") or "")


def main() -> None:
    parser = argparse.ArgumentParser(description="Mark the five verified legal documents as active admin KB data.")
    parser.add_argument("--apply", action="store_true", help="Persist changes; otherwise only print a dry-run summary.")
    parser.add_argument("--admin-user-id", default="1")
    parser.add_argument("--admin-email", default="admin@123")
    args = parser.parse_args()

    uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    username = os.getenv("NEO4J_USER", "neo4j")
    password = os.getenv("NEO4J_PASSWORD", "password")
    migrated_at = datetime.now(timezone.utc).isoformat()

    driver = GraphDatabase.driver(uri, auth=(username, password))
    try:
        records = driver.execute_query(
            "MATCH (node) "
            "WHERE node.metadata_json IS NOT NULL "
            "AND any(document_id IN $document_ids WHERE node.metadata_json CONTAINS document_id) "
            "RETURN node.node_id AS node_id, labels(node) AS labels, node.metadata_json AS metadata_json",
            document_ids=sorted(VERIFIED_LEGAL_DOCUMENT_IDS),
            database_="neo4j",
        ).records

        updates: list[dict[str, str]] = []
        document_counts: dict[str, int] = {document_id: 0 for document_id in VERIFIED_LEGAL_DOCUMENT_IDS}
        for record in records:
            try:
                metadata = json.loads(record["metadata_json"] or "{}")
            except (TypeError, json.JSONDecodeError):
                continue
            if not isinstance(metadata, dict):
                continue
            document_id = _document_id(metadata)
            if document_id not in VERIFIED_LEGAL_DOCUMENT_IDS:
                continue

            metadata.update(
                {
                    "source_type": "SYSTEM_KB",
                    "ingest_source": "INGEST_V2",
                    "effective_status": "ACTIVE",
                    "ingested_by_role": "ADMIN",
                    "ingested_by_user_id": str(args.admin_user_id),
                    "ingested_by_email": args.admin_email,
                    "ingested_at": metadata.get("ingested_at") or migrated_at,
                    "metadata_migrated_at": migrated_at,
                }
            )
            source_metadata = metadata.get("source_metadata")
            if isinstance(source_metadata, dict):
                source_metadata.update(
                    {
                        "source_type": "SYSTEM_KB",
                        "ingest_source": "INGEST_V2",
                        "effective_status": "ACTIVE",
                        "ingested_by_role": "ADMIN",
                        "ingested_by_user_id": str(args.admin_user_id),
                    }
                )

            updates.append(
                {
                    "node_id": str(record["node_id"]),
                    "metadata_json": json.dumps(metadata, ensure_ascii=False),
                }
            )
            document_counts[document_id] += 1

        missing = sorted(document_id for document_id, count in document_counts.items() if count == 0)
        if missing:
            raise RuntimeError(f"Verified documents missing from Neo4j: {missing}")

        print(json.dumps({"apply": args.apply, "nodes": len(updates), "documents": document_counts}, ensure_ascii=False))
        if not args.apply:
            return

        for start in range(0, len(updates), 500):
            driver.execute_query(
                "UNWIND $updates AS update "
                "MATCH (node {node_id: update.node_id}) "
                "SET node.metadata_json = update.metadata_json",
                updates=updates[start : start + 500],
                database_="neo4j",
            )
    finally:
        driver.close()


if __name__ == "__main__":
    main()
