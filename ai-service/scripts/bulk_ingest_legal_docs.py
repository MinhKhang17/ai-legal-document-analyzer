#!/usr/bin/env python3
"""Sequential bulk ingest client; Backend/Postgres remains metadata source of truth."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

import httpx


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
        (path for path in input_dir.rglob("*") if path.is_file() and path.name not in CONTROL_FILES),
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


class BackendBulkClient:
    def __init__(self, backend_url: str, token: str, internal_key: str) -> None:
        if not token.strip() and not internal_key.strip():
            raise ValueError("Backend admin token or KNOWLEDGE_BULK_INTERNAL_KEY is required")
        if internal_key.strip():
            self.endpoint = backend_url.rstrip("/") + "/api/internal/knowledge-bulk/ingest-file"
            headers = {"X-Internal-Bulk-Key": internal_key.strip()}
        else:
            self.endpoint = backend_url.rstrip("/") + "/api/v1/admin/knowledge-base/bulk-ingest-server-file"
            headers = {"Authorization": f"Bearer {token.strip()}"}
        self.client = httpx.Client(
            headers=headers,
            timeout=None,
        )

    def __call__(self, payload: dict[str, Any]) -> dict[str, Any]:
        response = self.client.post(self.endpoint, json=payload)
        response.raise_for_status()
        body = response.json()
        data = body.get("data") if isinstance(body, dict) else None
        if not isinstance(data, dict):
            raise RuntimeError("Backend returned an invalid bulk ingest response")
        return data

    def close(self) -> None:
        self.client.close()


def run_bulk_ingest(
    *,
    input_dir: Path,
    dry_run: bool,
    force: bool,
    stop_on_error: bool,
    backend_call: Callable[[dict[str, Any]], dict[str, Any]],
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

    for index, path in enumerate(files, start=1):
        relative_path = path.relative_to(input_dir).as_posix()
        report["current_file"] = relative_path
        entry: dict[str, Any] = {
            "file": path.name,
            "relative_path": relative_path,
            "file_type": path.suffix.lower(),
            "file_hash": None,
            "status": "PROCESSING",
            "backend_metadata_status": "PENDING",
            "postgres_status": "PENDING",
            "neo4j_status": "PENDING",
            "chunk_count": 0,
            "started_at": utc_now(),
            "finished_at": None,
            "error": None,
        }
        report["files"].append(entry)
        write_report(report_path, report)
        print(f"[{index}/{len(files)}] START {relative_path}", flush=True)

        suffix = path.suffix.lower()
        if suffix not in SUPPORTED_EXTENSIONS:
            entry.update(
                status="SKIPPED",
                backend_metadata_status="NOT_CALLED",
                postgres_status="NOT_WRITTEN",
                neo4j_status="NOT_WRITTEN",
                error=f"unsupported_file_type:{suffix or '[none]'}",
                finished_at=utc_now(),
            )
            report["skipped"] += 1
            print(f"[{index}/{len(files)}] SKIPPED {relative_path} reason=unsupported_file_type", flush=True)
            write_report(report_path, report)
            continue

        file_hash = sha256_file(path)
        entry["file_hash"] = file_hash
        file_metadata = metadata_for(path, input_dir, metadata)
        payload = {
            "relativePath": relative_path,
            "fileHash": file_hash,
            "title": file_metadata.get("title") or path.stem,
            "code": file_metadata.get("code"),
            "version": file_metadata.get("version"),
            "effectiveDate": file_metadata.get("effective_date"),
            "category": file_metadata.get("category") or "LEGAL_SOURCE",
            "source": file_metadata.get("source") or "bulk-server",
            "dryRun": dry_run,
            "force": force,
        }
        try:
            backend_result = backend_call(payload)
            status = str(backend_result.get("status") or "FAILED").upper()
            entry.update(
                status=status,
                backend_metadata_status=backend_result.get("backendMetadataStatus") or "UNKNOWN",
                postgres_status=backend_result.get("postgresStatus") or "UNKNOWN",
                neo4j_status=backend_result.get("neo4jStatus") or "UNKNOWN",
                chunk_count=int(backend_result.get("chunkCount") or 0),
                knowledge_base_entry_id=backend_result.get("knowledgeBaseEntryId"),
                knowledge_base_version_id=backend_result.get("knowledgeBaseVersionId"),
                job_id=backend_result.get("jobId"),
                neo4j_document_id=backend_result.get("neo4jDocumentId"),
                error=backend_result.get("errorMessage"),
                finished_at=utc_now(),
            )
            if status == "COMPLETED":
                report["completed"] += 1
                print(f"[{index}/{len(files)}] SUCCESS {relative_path} chunks={entry['chunk_count']}", flush=True)
            elif status in {"SKIPPED", "DRY_RUN"}:
                report["skipped"] += 1
                reason = entry["error"] or status.lower()
                print(f"[{index}/{len(files)}] SKIPPED {relative_path} reason={reason}", flush=True)
            else:
                report["failed"] += 1
                reason = entry["error"] or "backend_ingest_failed"
                print(f"[{index}/{len(files)}] FAILED {relative_path} reason={reason}", flush=True)
                write_report(report_path, report)
                if stop_on_error:
                    break
        except Exception as exc:
            reason = str(exc) or exc.__class__.__name__
            entry.update(
                status="FAILED",
                backend_metadata_status="ERROR",
                postgres_status="UNKNOWN",
                neo4j_status="UNKNOWN",
                error=reason,
                finished_at=utc_now(),
            )
            report["failed"] += 1
            print(f"[{index}/{len(files)}] FAILED {relative_path} reason={reason}", flush=True)
            write_report(report_path, report)
            if stop_on_error:
                break

        write_report(report_path, report)

    report["current_file"] = None
    report["finished_at"] = utc_now()
    write_report(report_path, report)
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sequential bulk legal knowledge ingestion through Backend/Postgres")
    parser.add_argument("--input-dir", required=True, type=Path)
    parser.add_argument("--backend-url", default=os.getenv("BACKEND_BASE_URL", "http://backend:8080"))
    parser.add_argument("--token", default=os.getenv("BACKEND_ADMIN_TOKEN", ""))
    parser.add_argument("--internal-key", default=os.getenv("KNOWLEDGE_BULK_INTERNAL_KEY", ""))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--stop-on-error", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    client: BackendBulkClient | None = None
    try:
        client = BackendBulkClient(args.backend_url, args.token, args.internal_key)
        report = run_bulk_ingest(
            input_dir=args.input_dir.resolve(),
            dry_run=args.dry_run,
            force=args.force,
            stop_on_error=args.stop_on_error,
            backend_call=client,
        )
    except Exception as exc:
        print(f"Bulk ingest aborted: {exc}", file=sys.stderr, flush=True)
        return 1
    finally:
        if client is not None:
            client.close()
    return 1 if report["failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
