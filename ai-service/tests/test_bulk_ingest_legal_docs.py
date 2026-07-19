from __future__ import annotations

import hashlib
import json
from pathlib import Path
from types import SimpleNamespace

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


def test_ingest_is_sequential_and_duplicate_hash_is_skipped(tmp_path: Path) -> None:
    (tmp_path / "1.txt").write_text("first", encoding="utf-8")
    (tmp_path / "2.txt").write_text("duplicate", encoding="utf-8")
    (tmp_path / "3.txt").write_text("third", encoding="utf-8")
    (tmp_path / "metadata.json").write_text(
        json.dumps({"1.txt": {"title": "First law", "code": "LAW-1"}}),
        encoding="utf-8",
    )
    duplicate_hash = hashlib.sha256(b"duplicate").hexdigest()
    events: list[str] = []

    class FakeService:
        def __init__(self, document_metadata: dict) -> None:
            self.metadata = document_metadata

        def ingest_file(self, path: str, **_: object) -> SimpleNamespace:
            name = Path(path).name
            events.append(f"ingest:{name}")
            return SimpleNamespace(document_id=f"doc-{name}", total_chunks=2)

    def service_factory(document_metadata: dict) -> FakeService:
        if document_metadata["original_file_name"] == "1.txt":
            assert document_metadata["title"] == "First law"
            assert document_metadata["code"] == "LAW-1"
            assert document_metadata["visibility"] == "PRIVATE"
            assert document_metadata["active"] is False
        return FakeService(document_metadata)

    def status_updater(_repository: object, document_id: str, status: str, **_: object) -> None:
        events.append(f"status:{document_id}:{status}")

    report = run_bulk_ingest(
        input_dir=tmp_path,
        dry_run=False,
        force=False,
        stop_on_error=False,
        service_factory=service_factory,
        repository=object(),
        completed_hash_loader=lambda _: {duplicate_hash},
        status_updater=status_updater,
    )

    assert events == [
        "ingest:1.txt",
        "status:doc-1.txt:COMPLETED",
        "ingest:3.txt",
        "status:doc-3.txt:COMPLETED",
    ]
    assert report["completed"] == 2
    assert report["skipped"] == 1
    assert report["failed"] == 0
    assert report["current_file"] is None
    assert report["files"][1]["error"] == "duplicate_hash"
