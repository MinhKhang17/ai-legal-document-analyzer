"""RAG retrieval and contract-context tests."""
from tests.conftest import P, requires_neo4j, unwrap


@requires_neo4j
def test_retrieve_returns_deposit_risk(client, seeded):
    data = unwrap(
        client.post(
            f"{P}/rag/retrieve",
            json={
                "query": "Điều khoản đặt cọc có rủi ro gì?",
                "contractType": "HOUSE_RENTAL",
                "topK": 6,
                "filters": {"riskTypes": ["DEPOSIT_RISK"], "clauseTypes": ["DEPOSIT"]},
            },
        )
    )
    assert data["items"], "retrieve returned no items"
    ids = {it["id"] for it in data["items"]}
    assert "DEPOSIT_RISK" in ids
    # Top item should be the most relevant (highest score first).
    scores = [it["score"] for it in data["items"]]
    assert scores == sorted(scores, reverse=True)


@requires_neo4j
def test_retrieve_surfaces_legal_article(client, seeded):
    data = unwrap(
        client.post(
            f"{P}/rag/retrieve",
            json={
                "query": "căn cứ pháp lý về đặt cọc",
                "contractType": "HOUSE_RENTAL",
                "topK": 8,
                "filters": {"riskTypes": ["DEPOSIT_RISK"], "clauseTypes": ["DEPOSIT"]},
            },
        )
    )
    types = {it["type"] for it in data["items"]}
    assert "LegalArticle" in types


@requires_neo4j
def test_contract_context(client, seeded):
    data = unwrap(
        client.post(
            f"{P}/rag/contract-context",
            json={
                "contractType": "LAND_TRANSFER",
                "detectedClauseTypes": ["PAYMENT", "DEPOSIT"],
            },
        )
    )
    assert data["contractType"] == "LAND_TRANSFER"
    assert len(data["requiredChecks"]) > 0
    assert len(data["relatedRisks"]) > 0
    # PAYMENT was detected, so its check should be marked present.
    payment = next((c for c in data["requiredChecks"] if c["clauseType"] == "PAYMENT"), None)
    assert payment is not None and payment["present"] is True
