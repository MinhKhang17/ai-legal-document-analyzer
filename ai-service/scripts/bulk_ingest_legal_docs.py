#!/usr/bin/env python3
"""Sequentially ingest a server folder into the existing legal knowledge pipeline."""

from __future__ import annotations

import argparse
import gc
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable


AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(AI_SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_ROOT))

SUPPORTED_EXTENSIONS = {".pdf", ".txt", ".docx", ".doc"}
CONTROL_FILES = {"metadata.json", "ingest-report.json", ".ingest-report.json.tmp"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while block := handle.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def scan_files(input_dir: Path) -> list[Path]:
    return sorted(
        (
            path
            for path in input_dir.rglob("*")
            if path.is_file() and path.name not in CONTROL_FILES
        ),
        key=lambda path: path.relative_to(input_dir).as_posix().casefold(),
    )


def load_metadata(input_dir: Path) -> dict[str, dict[str, Any]]:
    metadata_path = input_dir / "metadata.json"
    if not metadata_path.exists():
        return {}
    payload = json.loads(metadata_path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("metadata.json must contain a JSON object")
    return {str(key): value for key, value in payload.items() if isinstance(value, dict)}


def metadata_for(path: Path, input_dir: Path, metadata: dict[str, dict[str, Any]]) -> dict[str, Any]:
    relative_path = path.relative_to(input_dir).as_posix()
    return dict(metadata.get(relative_path) or metadata.get(path.name) or {})


def write_report(report_path: Path, report: dict[str, Any]) -> None:
    temporary_path = report_path.with_name(f".{report_path.name}.tmp")
    temporary_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, default=str) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary_path, report_path)


def load_completed_hashes(repository: Any) -> set[str]:
    completed_hashes: set[str] = set()
    with repository.driver.session() as session:
        records = session.run(
            "MATCH (document:Document) RETURN document.metadata_json AS metadata_json"
        )
        for record in records:
            try:
                metadata = json.loads(record["metadata_json"] or "{}")
            except (TypeError, json.JSONDecodeError):
                continue
            file_hash = str(metadata.get("file_hash") or "").strip()
            status = str(metadata.get("bulk_ingest_status") or metadata.get("status") or "").upper()
            ingest_status = str(metadata.get("ingest_status") or "").upper()
            if file_hash and (status == "COMPLETED" or (not status and ingest_status == "INGESTED")):
                completed_hashes.add(file_hash)
    return completed_hashes


def update_document_status(
    repository: Any,
    document_id: str,
    status: str,
    *,
    error: str | None = None,
) -> None:
    with repository.driver.session() as session:
        records = list(session.run(
            """
            MATCH (document:Document {node_id: $document_id})
            OPTIONAL MATCH (document)-[:PARENT_OF*1..8]->(child:KnowledgeNode)
            RETURN document.node_id AS document_id,
                   document.metadata_json AS document_metadata_json,
                   child.node_id AS child_id,
                   child.metadata_json AS child_metadata_json
            """,
            document_id=document_id,
        ))
        if not records:
            return

        updates: dict[str, str] = {}
        for record in records:
            candidates = (
                (record["document_id"], record["document_metadata_json"]),
                (record["child_id"], record["child_metadata_json"]),
            )
            for node_id, metadata_json in candidates:
                if not node_id:
                    continue
                try:
                    node_metadata = json.loads(metadata_json or "{}")
                except (TypeError, json.JSONDecodeError):
                    node_metadata = {}
                node_metadata["status"] = status
                node_metadata["bulk_ingest_status"] = status
                node_metadata["bulk_ingest_error"] = error
                node_metadata["bulk_ingest_updated_at"] = utc_now()
                updates[str(node_id)] = json.dumps(node_metadata, ensure_ascii=False)

        session.run(
            """
            UNWIND $updates AS update
            MATCH (node:KnowledgeNode {node_id: update.node_id})
            SET node.metadata_json = update.metadata_json,
                node.updated_at = datetime()
            """,
            updates=[
                {"node_id": node_id, "metadata_json": metadata_json}
                for node_id, metadata_json in updates.items()
            ],
        ).consume()


def run_bulk_ingest(
    *,
    input_dir: Path,
    dry_run: bool,
    force: bool,
    stop_on_error: bool,
    service_factory: Callable[[dict[str, Any]], Any] | None = None,
    repository: Any | None = None,
    completed_hash_loader: Callable[[Any], set[str]] = load_completed_hashes,
    status_updater: Callable[..., None] = update_document_status,
) -> dict[str, Any]:
    if not input_dir.is_dir():
        raise FileNotFoundError(f"Input directory does not exist: {input_dir}")

    files = scan_files(input_dir)
    metadata = load_metadata(input_dir)
    report_path = input_dir / "ingest-report.json"
    report: dict[str, Any] = {
        "total": len(files),
        "completed": 0,
        "skipped": 0,
        "failed": 0,
        "current_file": None,
        "started_at": utc_now(),
        "finished_at": None,
        "files": [],
    }
    write_report(report_path, report)

    if not dry_run and (service_factory is None or repository is None):
        from app.graph.repository import GraphRepository
        from app.services.knowledge_service import KnowledgeServiceV2

        repository = repository or GraphRepository()
        service_factory = service_factory or (
            lambda document_metadata: KnowledgeServiceV2(
                repository=repository,
                document_metadata=document_metadata,
            )
        )

    completed_hashes = set() if dry_run else completed_hash_loader(repository)

    for index, path in enumerate(files, start=1):
        relative_path = path.relative_to(input_dir).as_posix()
        display_path = relative_path
        report["current_file"] = relative_path
        entry: dict[str, Any] = {
            "file": path.name,
            "relative_path": relative_path,
            "file_type": path.suffix.lower(),
            "file_hash": None,
            "status": "PROCESSING",
            "chunks": 0,
            "started_at": utc_now(),
            "finished_at": None,
            "error": None,
        }
        report["files"].append(entry)
        write_report(report_path, report)
        print(f"[{index}/{len(files)}] START {display_path}", flush=True)

        suffix = path.suffix.lower()
        if suffix not in SUPPORTED_EXTENSIONS:
            entry.update(status="SKIPPED", error=f"unsupported_file_type:{suffix or '[none]'}", finished_at=utc_now())
            report["skipped"] += 1
            print(f"[{index}/{len(files)}] SKIPPED {display_path} reason=unsupported_file_type", flush=True)
            write_report(report_path, report)
            continue

        file_hash = sha256_file(path)
        entry["file_hash"] = file_hash
        if not force and file_hash in completed_hashes:
            entry.update(status="SKIPPED", error="duplicate_hash", finished_at=utc_now())
            report["skipped"] += 1
            print(f"[{index}/{len(files)}] SKIPPED {display_path} reason=duplicate_hash", flush=True)
            write_report(report_path, report)
            continue

        if dry_run:
            entry.update(status="SKIPPED", error="dry_run", finished_at=utc_now())
            report["skipped"] += 1
            print(f"[{index}/{len(files)}] SKIPPED {display_path} reason=dry_run", flush=True)
            write_report(report_path, report)
            continue

        file_metadata = metadata_for(path, input_dir, metadata)
        title = str(file_metadata.get("title") or path.stem).strip()
        created_at = utc_now()
        document_metadata = {
            **file_metadata,
            "original_file_name": path.name,
            "relative_path": relative_path,
            "file_hash": file_hash,
            "file_type": suffix.lstrip("."),
            "title": title,
            "source": str(file_metadata.get("source") or "bulk-server"),
            "source_type": "SYSTEM_KB",
            "visibility": "PRIVATE",
            "active": False,
            "effective_status": "INACTIVE",
            "status": "PROCESSING",
            "bulk_ingest_status": "PROCESSING",
            "created_at": created_at,
            "bulk_ingest_started_at": created_at,
        }

        result = None
        try:
            service = service_factory(document_metadata)
            result = service.ingest_file(
                str(path),
                title=title,
                filename=path.name,
                ingestion_version=2,
            )
            status_updater(repository, result.document_id, "COMPLETED")
            entry.update(
                status="COMPLETED",
                chunks=result.total_chunks,
                document_id=result.document_id,
                finished_at=utc_now(),
            )
            report["completed"] += 1
            completed_hashes.add(file_hash)
            print(f"[{index}/{len(files)}] SUCCESS {display_path} chunks={result.total_chunks}", flush=True)
        except Exception as exc:  # keep the batch alive unless explicitly configured otherwise
            reason = str(exc) or exc.__class__.__name__
            entry.update(status="FAILED", error=reason, finished_at=utc_now())
            report["failed"] += 1
            print(f"[{index}/{len(files)}] FAILED {display_path} reason={reason}", flush=True)
            if result is not None:
                try:
                    status_updater(repository, result.document_id, "FAILED", error=reason)
                except Exception as status_exc:
                    entry["status_update_error"] = str(status_exc) or status_exc.__class__.__name__
            write_report(report_path, report)
            if stop_on_error:
                break
        finally:
            result = None
            if "service" in locals():
                del service
            gc.collect()

        write_report(report_path, report)

    report["current_file"] = None
    report["finished_at"] = utc_now()
    write_report(report_path, report)
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sequential bulk legal knowledge ingestion")
    parser.add_argument("--input-dir", required=True, type=Path)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--stop-on-error", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        report = run_bulk_ingest(
            input_dir=args.input_dir.resolve(),
            dry_run=args.dry_run,
            force=args.force,
            stop_on_error=args.stop_on_error,
        )
    except Exception as exc:
        print(f"Bulk ingest aborted: {exc}", file=sys.stderr, flush=True)
        return 1
    return 1 if report["failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
