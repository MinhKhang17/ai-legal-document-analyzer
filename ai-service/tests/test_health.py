"""Health, Neo4j and readiness endpoint tests."""
from tests.conftest import P, requires_neo4j, unwrap


def test_health_up(client):
    data = unwrap(client.get(f"{P}/health"))
    assert data["status"] == "UP"
    assert data["service"] == "ai-service"
    assert "version" in data


def test_root(client):
    body = client.get("/").json()
    assert body["service"] == "ai-service"
    assert any("/health" in e for e in body["endpoints"])


@requires_neo4j
def test_health_neo4j(client):
    data = unwrap(client.get(f"{P}/health/neo4j"))
    assert data["connected"] is True
    assert data["uri"].startswith("bolt://")


@requires_neo4j
def test_readiness(client):
    data = unwrap(client.get(f"{P}/health/readiness"))
    assert data["ready"] is True
    assert data["neo4j"] == "UP"
    assert data["llm"] in {"UP", "DOWN", "NOT_CONFIGURED"}
