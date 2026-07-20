from __future__ import annotations

import json
from pathlib import Path

from scripts.bulk_ingest_legal_docs import run_bulk_ingest, scan_files


def test_scan_is_recursive_sorted_and_excludes_control_files(tmp_path: Path) -> None:
    (tmp_path / "nested").mkdir()
    (tmp_path / "nested" / "B.docx").write_text("b", encoding="utf-8")
    (tmp_path / "a.pdf").write_bytes(b"a")
    (tmp_path / "metadata.json").write_text("{}", encoding="utf-8")
    (tmp_path / "ingest-report.json").write_text("{}", encoding="utf-8")

    assert [path.relative_to(tmp_path).as_posix() for path in scan_files(tmp_path)] == [
        "a.pdf",
        "nested/B.docx",
    ]


def test_ingest_is_sequential_and_uses_backend_status(tmp_path: Path) -> None:
    (tmp_path / "1.txt").write_text("first", encoding="utf-8")
    (tmp_path / "2.txt").write_text("duplicate", encoding="utf-8")
    (tmp_path / "3.txt").write_text("third", encoding="utf-8")
    (tmp_path / "metadata.json").write_text(
        json.dumps({"1.txt": {"title": "First law", "code": "LAW-1"}}),
        encoding="utf-8",
    )
    events: list[str] = []

    def backend_call(payload: dict) -> dict:
        relative_path = payload["relativePath"]
        events.append(f"start:{relative_path}")
        if relative_path == "1.txt":
            assert payload["title"] == "First law"
            assert payload["code"] == "LAW-1"
        events.append(f"finish:{relative_path}")
        if relative_path == "2.txt":
            return {
                "status": "SKIPPED", "backendMetadataStatus": "EXISTS",
                "postgresStatus": "INGESTED", "neo4jStatus": "COMPLETED",
                "chunkCount": 2, "errorMessage": "duplicate_hash",
            }
        return {
            "status": "COMPLETED", "backendMetadataStatus": "CREATED",
            "postgresStatus": "INGESTED", "neo4jStatus": "COMPLETED",
            "chunkCount": 2,
        }

    report = run_bulk_ingest(
        input_dir=tmp_path,
        dry_run=False,
        force=False,
        stop_on_error=False,
        backend_call=backend_call,
    )

    assert events == [
        "start:1.txt", "finish:1.txt",
        "start:2.txt", "finish:2.txt",
        "start:3.txt", "finish:3.txt",
    ]
    assert report["completed"] == 2
    assert report["skipped"] == 1
    assert report["failed"] == 0
    assert report["current_file"] is None
    assert report["files"][1]["error"] == "duplicate_hash"
