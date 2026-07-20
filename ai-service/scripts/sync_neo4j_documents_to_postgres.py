#!/usr/bin/env python3
"""Repair missing Postgres knowledge metadata from existing Neo4j documents."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

import httpx


AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(AI_SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_ROOT))


def parse_metadata(value: Any) -> dict[str, Any]:
    if not value:
        return {}
    try:
        parsed = json.loads(value) if isinstance(value, str) else value
        return parsed if isinstance(parsed, dict) else {}
    except (TypeError, json.JSONDecodeError):
        return {}


def load_neo4j_documents() -> list[dict[str, Any]]:
    from app.graph.repository import GraphRepository

    repository = GraphRepository()
    with repository.driver.session() as session:
        records = session.run(
            """
            MATCH (document:Document)
            OPTIONAL MATCH (document)-[:PARENT_OF*1..8]->(chunk:Chunk)
            RETURN document.node_id AS document_id,
                   document.title AS title,
                   document.source_path AS source_path,
                   document.file_type AS file_type,
                   document.metadata_json AS metadata_json,
                   count(DISTINCT chunk) AS chunk_count
            ORDER BY document.node_id
            """
        )
        return [dict(record) for record in records]


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync legacy Neo4j knowledge documents into Backend/Postgres")
    parser.add_argument("--backend-url", default=os.getenv("BACKEND_BASE_URL", "http://backend:8080"))
    parser.add_argument("--token", default=os.getenv("BACKEND_ADMIN_TOKEN", ""))
    parser.add_argument("--internal-key", default=os.getenv("KNOWLEDGE_BULK_INTERNAL_KEY", ""))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.token.strip() and not args.internal_key.strip():
        print("Backend admin token or KNOWLEDGE_BULK_INTERNAL_KEY is required", file=sys.stderr)
        return 1

    try:
        scanned_documents = load_neo4j_documents()
        documents = []
        for document in scanned_documents:
            metadata = parse_metadata(document.get("metadata_json"))
            source_type = str(metadata.get("source_type") or "").upper()
            # Never turn user workspace documents into system knowledge entries.
            if source_type == "USER_DOCUMENT":
                continue
            documents.append(document)
    except Exception as exc:
        print(f"Unable to scan Neo4j documents: {exc}", file=sys.stderr)
        return 1

    if args.internal_key.strip():
        endpoint = args.backend_url.rstrip("/") + "/api/internal/knowledge-bulk/repair-neo4j-document"
        headers = {"X-Internal-Bulk-Key": args.internal_key.strip()}
    else:
        endpoint = args.backend_url.rstrip("/") + "/api/v1/admin/knowledge-base/repair-neo4j-document"
        headers = {"Authorization": f"Bearer {args.token.strip()}"}
    counters = {"found": len(documents), "created": 0, "skipped": 0, "failed": 0}
    with httpx.Client(headers=headers, timeout=None) as client:
        for index, document in enumerate(documents, start=1):
            metadata = parse_metadata(document.get("metadata_json"))
            document_id = str(document.get("document_id") or "").strip()
            file_name = str(
                metadata.get("original_file_name")
                or metadata.get("file_name")
                or Path(str(document.get("source_path") or document_id)).name
            )
            payload = {
                "neo4jDocumentId": document_id,
                "fileHash": metadata.get("file_hash"),
                "fileName": file_name,
                "title": metadata.get("document_title") or metadata.get("title") or document.get("title"),
                "code": metadata.get("document_code") or metadata.get("code"),
                "category": metadata.get("category") or "LEGAL_SOURCE",
                "source": metadata.get("source") or "bulk-server",
                "chunkCount": int(document.get("chunk_count") or 0),
                "dryRun": args.dry_run,
            }
            try:
                response = client.post(endpoint, json=payload)
                response.raise_for_status()
                body = response.json()
                result = body.get("data") if isinstance(body, dict) else None
                if not isinstance(result, dict):
                    raise RuntimeError("Backend returned invalid repair response")
                status = str(result.get("status") or "FAILED").upper()
                if status == "COMPLETED":
                    counters["created"] += 1
                    print(f"[{index}/{len(documents)}] CREATED {file_name} chunks={payload['chunkCount']}", flush=True)
                elif status in {"SKIPPED", "DRY_RUN"}:
                    counters["skipped"] += 1
                    print(f"[{index}/{len(documents)}] SKIPPED {file_name} reason={result.get('errorMessage') or status.lower()}", flush=True)
                else:
                    counters["failed"] += 1
                    print(f"[{index}/{len(documents)}] FAILED {file_name} reason={result.get('errorMessage') or 'repair_failed'}", flush=True)
            except Exception as exc:
                counters["failed"] += 1
                print(f"[{index}/{len(documents)}] FAILED {file_name} reason={exc}", flush=True)

    print(
        "Repair summary: "
        f"found={counters['found']} created={counters['created']} "
        f"skipped={counters['skipped']} failed={counters['failed']}",
        flush=True,
    )
    return 1 if counters["failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
