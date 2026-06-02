"""Graph CRUD, query and seed tests."""
import pytest

from app.graph import legal_corpus
from tests.conftest import P, requires_neo4j, unwrap


@requires_neo4j
def test_seed_is_idempotent(client, seeded):
    # Re-run seed; counts must stay stable.
    client.post(f"{P}/graph/seed/all")
    data = unwrap(
        client.post(
            f"{P}/graph/query",
            json={
                "cypher": "MATCH (c:ContractType) RETURN count(c) AS n",
                "params": {},
            },
        )
    )
    assert data["rows"][0]["n"] == 6


@requires_neo4j
def test_query_contract_types(client, seeded):
    data = unwrap(
        client.post(
            f"{P}/graph/query",
            json={
                "cypher": "MATCH (c:ContractType) RETURN c.code AS code ORDER BY code",
                "params": {},
            },
        )
    )
    codes = [r["code"] for r in data["rows"]]
    assert "HOUSE_RENTAL" in codes
    assert len(codes) == 6


@requires_neo4j
def test_corpus_articles_seeded(client, seeded):
    data = unwrap(
        client.post(
            f"{P}/graph/query",
            json={
                "cypher": "MATCH (a:LegalArticle) RETURN count(a) AS n",
                "params": {},
            },
        )
    )
    assert data["rows"][0]["n"] == len(legal_corpus.LEGAL_ARTICLES)


@requires_neo4j
def test_node_create_get_delete(client, seeded):
    created = unwrap(
        client.post(
            f"{P}/graph/nodes",
            json={"label": "LegalConcept", "properties": {"name": "Test concept"}},
        )
    )
    node_id = created["id"]
    assert created["label"] == "LegalConcept"

    fetched = unwrap(client.get(f"{P}/graph/nodes/{node_id}"))
    assert fetched["properties"]["name"] == "Test concept"

    deleted = unwrap(client.delete(f"{P}/graph/nodes/{node_id}"))
    assert deleted["deleted"] is True


@requires_neo4j
def test_get_missing_node_returns_not_found(client, seeded):
    resp = client.get(f"{P}/graph/nodes/4:does-not-exist:999999")
    assert resp.status_code == 404
    body = resp.json()
    assert body["success"] is False
    assert body["error"]["code"] == "NOT_FOUND"
