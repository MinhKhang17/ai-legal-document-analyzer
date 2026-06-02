"""Pure unit tests (no Neo4j / no network) for core building blocks."""
import pytest

from app.core.errors import LLMNotConfiguredError
from app.services.embedding_service import LexicalBackend, normalize, tokenize
from app.services.legal_service import detect_clause_types, detect_parties
from app.services.llm_client import NoopLLMClient
from app.services.legal_service import _extract_json


# --- text helpers ---


def test_normalize_strips_accents():
    assert normalize("Đặt Cọc") == "dat coc"


def test_tokenize():
    assert tokenize("Tiền thuê nhà") == ["tien", "thue", "nha"]


# --- lexical similarity ---


def test_lexical_similarity_ranks_related_higher():
    b = LexicalBackend()
    related = b.similarity("đặt cọc", "rủi ro về điều khoản đặt cọc")
    unrelated = b.similarity("đặt cọc", "giải quyết tranh chấp tại tòa án")
    assert related > unrelated


# --- clause / party detection ---


def test_detect_clause_types():
    text = "Bên thuê đặt cọc 2 tháng. Thanh toán hàng tháng. Giải quyết tranh chấp tại tòa án."
    clauses = detect_clause_types(text)
    assert "DEPOSIT" in clauses
    assert "PAYMENT" in clauses
    assert "DISPUTE" in clauses


def test_detect_parties_prefers_specific():
    parties = detect_parties("Bên cho thuê và Bên thuê ký kết hợp đồng.")
    assert "bên cho thuê" in parties
    assert "bên thuê" in parties


# --- LLM noop ---


def test_noop_llm_not_configured():
    client = NoopLLMClient()
    assert client.configured is False
    with pytest.raises(LLMNotConfiguredError):
        client.generate("anything")


# --- JSON extraction from LLM output ---


def test_extract_json_plain():
    assert _extract_json('{"a": 1}') == {"a": 1}


def test_extract_json_markdown_fenced():
    raw = "```json\n{\"a\": 1}\n```"
    assert _extract_json(raw) == {"a": 1}


def test_extract_json_embedded():
    raw = 'Đây là kết quả: {"a": 1} (hết)'
    assert _extract_json(raw) == {"a": 1}


def test_extract_json_invalid_returns_none():
    assert _extract_json("không có json") is None
