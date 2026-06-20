from __future__ import annotations

import re
import unicodedata

from app.schemas import ExtractedPage, LegalUnit
from app.services.legal_text_normalizer import normalize_legal_text


ARTICLE_PATTERN = re.compile(r"^\s*dieu\s+([0-9]+[a-zA-Z0-9.-]*)\b(?:[.:\-]?\s*(.*))?$", re.IGNORECASE)
CLAUSE_PATTERN = re.compile(r"^\s*khoan\s+([0-9]+(?:\.[0-9]+)*)\b(?:[.:\-]?\s*(.*))?$", re.IGNORECASE)
SECTION_PATTERN = re.compile(r"^\s*(muc|chuong)\s+([ivxlcdm0-9]+)\b(?:[.:\-]?\s*(.*))?$", re.IGNORECASE)
PARAGRAPH_PATTERN = re.compile(r"^\s*([0-9]+(?:\.[0-9]+)*)\s+[^\s].*$")

COMMON_SECTION_HEADINGS = {
    "doi tuong hop dong",
    "doi tuong hop dong",
    "gia tri hop dong",
    "gia tri hop dong",
    "thanh toan",
    "quyen va nghia vu cua cac ben",
    "quyen va nghia vu cua cac ben",
    "phat vi pham",
    "boi thuong thiet hai",
    "cham dut hop dong",
    "bao mat",
    "giai quyet tranh chap",
    "hieu luc hop dong",
}


def _fold(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text)
    stripped = "".join(char for char in normalized if not unicodedata.combining(char))
    stripped = stripped.replace("Đ", "D").replace("đ", "d")
    return re.sub(r"\s+", " ", stripped).strip().lower()


class LegalStructureExtractor:
    def extract_legal_units(self, pages: list[ExtractedPage]) -> list[LegalUnit]:
        units: list[LegalUnit] = []
        current: dict[str, object] | None = None

        def flush() -> None:
            nonlocal current
            if not current:
                return
            text = str(current.get("text") or "").strip()
            if text:
                units.append(
                    LegalUnit(
                        unit_type=current["unit_type"],  # type: ignore[index]
                        title=current.get("title") or None,
                        article_number=current.get("article_number") or None,
                        clause_number=current.get("clause_number") or None,
                        section_title=current.get("section_title") or None,
                        page_number=current.get("page_number") or None,
                        text=text,
                    )
                )
            current = None

        for page in pages:
            text = normalize_legal_text(page.text)
            if not text:
                continue

            for line in text.splitlines():
                line = line.strip()
                if not line:
                    continue

                match = self._detect_heading(line)
                if match is not None:
                    flush()
                    unit_type, payload = match
                    current = {
                        "unit_type": unit_type,
                        "title": line,
                        "article_number": payload.get("article_number"),
                        "clause_number": payload.get("clause_number"),
                        "section_title": payload.get("section_title"),
                        "page_number": page.page_number,
                        "text": line,
                    }
                    continue

                if current is None:
                    current = {
                        "unit_type": "PARAGRAPH",
                        "title": None,
                        "article_number": None,
                        "clause_number": None,
                        "section_title": None,
                        "page_number": page.page_number,
                        "text": line,
                    }
                else:
                    current["text"] = f'{current["text"]}\n{line}'
                    if current.get("page_number") is None:
                        current["page_number"] = page.page_number

        flush()

        if not units and pages:
            merged_text = normalize_legal_text("\n".join(page.text for page in pages if page.text))
            if merged_text:
                units.append(
                    LegalUnit(
                        unit_type="UNKNOWN",
                        title=None,
                        article_number=None,
                        clause_number=None,
                        section_title=None,
                        page_number=pages[0].page_number,
                        text=merged_text,
                    )
                )

        return units

    def _detect_heading(self, line: str) -> tuple[str, dict[str, str | None]] | None:
        normalized = line.strip()
        folded = _fold(normalized)
        for heading in COMMON_SECTION_HEADINGS:
            if folded == heading:
                return "SECTION", {"section_title": normalized}

        for pattern, unit_type in (
            (ARTICLE_PATTERN, "ARTICLE"),
            (CLAUSE_PATTERN, "CLAUSE"),
            (SECTION_PATTERN, "SECTION"),
            (PARAGRAPH_PATTERN, "PARAGRAPH"),
        ):
            match = pattern.match(normalized)
            if match:
                groups = match.groups(default="")
                article_number = None
                clause_number = None
                section_title = None
                if unit_type == "ARTICLE":
                    article_number = groups[0].strip() or None
                    section_title = (groups[1].strip() if len(groups) > 1 and groups[1] else None) or None
                elif unit_type == "CLAUSE":
                    clause_number = groups[0].strip() or None
                elif unit_type == "SECTION":
                    section_title = normalized
                elif unit_type == "PARAGRAPH":
                    clause_number = groups[0].strip() or None
                return unit_type, {
                    "article_number": article_number,
                    "clause_number": clause_number,
                    "section_title": section_title,
                }

        return None
