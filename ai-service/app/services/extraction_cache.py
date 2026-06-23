from __future__ import annotations

import json
import logging
from hashlib import sha256
from pathlib import Path
from datetime import datetime, timezone

from app.schemas import ExtractedPage


logger = logging.getLogger(__name__)


class ExtractionCache:
    def __init__(self, cache_dir: Path | None = None) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        self.cache_dir = cache_dir or (repo_root / ".data" / "extraction_cache")

    def compute_file_hash(self, file_path: str) -> str:
        digest = sha256()
        with Path(file_path).open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()

    def get_cached_extraction(self, file_hash: str) -> list[ExtractedPage] | None:
        cache_file = self._cache_file(file_hash)
        if not cache_file.exists():
            return None

        try:
            payload = json.loads(cache_file.read_text(encoding="utf-8"))
            pages = payload.get("pages", [])
            return [ExtractedPage.model_validate(page) for page in pages if isinstance(page, dict)]
        except Exception:
            logger.exception("Failed to read extraction cache file: %s", cache_file)
            return None

    def save_cached_extraction(
        self,
        file_hash: str,
        document_id: str,
        file_name: str,
        pages: list[ExtractedPage],
    ) -> None:
        cache_file = self._cache_file(file_hash)
        cache_file.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "file_hash": file_hash,
            "source_document_id": document_id,
            "file_name": file_name,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "pages": [page.model_dump() for page in pages],
        }
        cache_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def _cache_file(self, file_hash: str) -> Path:
        return self.cache_dir / f"{file_hash}.json"

