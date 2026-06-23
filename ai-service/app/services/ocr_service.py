from __future__ import annotations

import logging
import tempfile
import importlib.util
from pathlib import Path
from typing import Any


logger = logging.getLogger(__name__)


class OCRService:
    def __init__(self) -> None:
        try:
            from paddleocr import PaddleOCR
        except ImportError as exc:
            raise RuntimeError(
                "OCR backend is not available. Install 'paddleocr' (and its runtime dependencies) "
                "in the container to process scanned PDFs or image-based DOCX files."
            ) from exc

        self.ocr = PaddleOCR(
            lang="vi",
            device="cpu",
            engine="paddle_static",
            enable_mkldnn=False,
            cpu_threads=1,
            enable_cinn=False,
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
        )

    @staticmethod
    def is_available() -> bool:
        return (
            importlib.util.find_spec("paddleocr") is not None
            and importlib.util.find_spec("paddle") is not None
        )

    def _collect_texts(self, payload: Any) -> list[str]:
        texts: list[str] = []

        if payload is None:
            return texts

        if isinstance(payload, str):
            text = payload.strip()
            if text:
                texts.append(text)
            return texts

        if isinstance(payload, dict):
            rec_texts = payload.get("rec_texts")
            if isinstance(rec_texts, list):
                for text in rec_texts:
                    if isinstance(text, str) and text.strip():
                        texts.append(text.strip())
                return texts

            text = payload.get("text")
            if isinstance(text, str) and text.strip():
                texts.append(text.strip())
                return texts

            for value in payload.values():
                texts.extend(self._collect_texts(value))
            return texts

        if hasattr(payload, "rec_texts"):
            rec_texts = getattr(payload, "rec_texts")
            if isinstance(rec_texts, list):
                for text in rec_texts:
                    if isinstance(text, str) and text.strip():
                        texts.append(text.strip())
                return texts

        if isinstance(payload, (list, tuple)):
            # PaddleOCR ocr() often returns nested line items of the form:
            # [box, (text, score)]
            if payload and all(
                isinstance(item, (list, tuple))
                and len(item) >= 2
                and isinstance(item[1], (list, tuple))
                and len(item[1]) >= 1
                and isinstance(item[1][0], str)
                for item in payload
            ):
                for item in payload:
                    text = item[1][0].strip()
                    if text:
                        texts.append(text)
                return texts

            for item in payload:
                texts.extend(self._collect_texts(item))
            return texts

        json_value = getattr(payload, "json", None)
        if isinstance(json_value, str) and json_value.strip():
            texts.append(json_value.strip())
            return texts
        if callable(json_value):
            try:
                return self._collect_texts(json_value())
            except Exception:
                logger.exception("Failed to extract texts from OCR payload JSON value")
                return texts

        return texts

    def _run_ocr(self, image_input: Any) -> str:
        try:
            if hasattr(self.ocr, "predict"):
                result = self.ocr.predict(image_input)
            else:
                result = self.ocr.ocr(image_input, cls=False)
        except Exception:
            logger.exception("OCR execution failed")
            return ""

        texts = self._collect_texts(result)
        return "\n".join(texts)

    def extract_text_from_image(self, image_path: str | Path) -> str:
        return self._run_ocr(str(image_path))

    def extract_text_from_array(self, image: Any) -> str:
        return self._run_ocr(image)

    def extract_text_from_pil_image(self, image: Any) -> str:
        if image is None:
            return ""

        temp_path: Path | None = None
        with tempfile.NamedTemporaryFile(
            suffix=".png",
            delete=False,
            dir=str(Path.cwd()),
        ) as temp:
            temp_path = Path(temp.name)

        try:
            image.save(temp_path)
            return self.extract_text_from_image(temp_path)
        finally:
            if temp_path is not None and temp_path.exists():
                try:
                    temp_path.unlink()
                except OSError:
                    logger.warning("Failed to remove temporary OCR image file: %s", temp_path)
                    pass
