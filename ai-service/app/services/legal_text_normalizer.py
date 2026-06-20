from __future__ import annotations

import re


def normalize_legal_text(text: str) -> str:
    if not text:
        return ""

    lines: list[str] = []
    for raw_line in text.splitlines():
        line = re.sub(r"\s+", " ", raw_line).strip()
        if not line:
            continue
        line = re.sub(r"\s+([,.;:!?])", r"\1", line)
        line = re.sub(r"([(\[])\s+", r"\1", line)
        line = re.sub(r"\s+([)\]])", r"\1", line)
        lines.append(line)

    return "\n".join(lines).strip()

