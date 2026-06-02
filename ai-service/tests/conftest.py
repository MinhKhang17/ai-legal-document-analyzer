"""Shared pytest fixtures for the ai-service test suite.

Tests use FastAPI's in-process TestClient (no running server needed). Fixtures
detect whether Neo4j / an LLM are available and skip the relevant tests instead
of failing, so the suite is runnable in any environment.
"""
import os
import sys

import pytest

# Ensure the project root (containing the `app` package) is importable when
# pytest is invoked from the ai-service directory.
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from fastapi.testclient import TestClient  # noqa: E402

from app.core.config import get_settings  # noqa: E402
from app.graph import connection  # noqa: E402
from app.main import app  # noqa: E402


def _neo4j_available() -> bool:
    try:
        return connection.ping()
    except Exception:  # noqa: BLE001
        return False


NEO4J_UP = _neo4j_available()
LLM_UP = get_settings().llm_configured and bool(get_settings().llm_api_key)


@pytest.fixture(scope="session")
def client() -> TestClient:
    """In-process HTTP client for the FastAPI app."""
    with TestClient(app) as c:
        yield c


@pytest.fixture(scope="session")
def seeded(client: TestClient):
    """Seed baseline + corpus once per session (requires Neo4j)."""
    if not NEO4J_UP:
        pytest.skip("Neo4j is not available")
    prefix = get_settings().api_prefix.rstrip("/")
    resp = client.post(f"{prefix}/graph/seed/all")
    assert resp.status_code == 200, resp.text
    return resp.json()["data"]


requires_neo4j = pytest.mark.skipif(not NEO4J_UP, reason="Neo4j not available")
requires_llm = pytest.mark.skipif(not LLM_UP, reason="LLM not configured")

# Common API prefix applied to all routers (see Settings.api_prefix).
P = get_settings().api_prefix.rstrip("/")


def unwrap(resp):
    """Assert standard envelope success and return the data payload."""
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["success"] is True, body
    assert "traceId" in body
    return body["data"]
