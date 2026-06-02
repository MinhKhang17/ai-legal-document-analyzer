"""Legal classify / analyze / compare tests.

These verify the standard response shape and that, with or without an LLM, the
service returns structured JSON (never crashes).
"""
from tests.conftest import P, requires_neo4j, unwrap

HOUSE_RENTAL = (
    "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê: Nguyễn Văn A. Bên thuê: Trần Thị B. "
    "Căn hộ tại Hà Nội. Tiền thuê 10 triệu/tháng. Đặt cọc 2 tháng. "
    "Không có điều khoản chấm dứt hợp đồng."
)


@requires_neo4j
def test_classify_land_transfer(client, seeded):
    data = unwrap(
        client.post(
            f"{P}/legal/classify-contract",
            json={
                "text": "Hợp đồng chuyển nhượng quyền sử dụng đất, thửa đất số 123, "
                "sổ đỏ, bên chuyển nhượng và bên nhận chuyển nhượng."
            },
        )
    )
    assert data["contractType"] == "LAND_TRANSFER"
    assert 0.0 <= data["confidence"] <= 1.0


@requires_neo4j
def test_analyze_returns_structured_json(client, seeded):
    data = unwrap(
        client.post(
            f"{P}/legal/analyze-contract",
            json={
                "contractText": HOUSE_RENTAL,
                "contractType": "HOUSE_RENTAL",
                "protectedParty": "bên thuê",
                "question": "Hợp đồng này có rủi ro gì?",
                "options": {"outputMode": "JSON", "includeGraphContext": True},
            },
        )
    )
    assert data["contractType"] == "HOUSE_RENTAL"
    assert data["overallRiskLevel"] in {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
    assert isinstance(data["riskItems"], list)
    # llmUsed/fallback are mutually consistent.
    assert data["llmUsed"] != data["fallback"]


@requires_neo4j
def test_compare_returns_clause_comparisons(client, seeded):
    doc_a = (
        "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê và bên thuê. Căn hộ. Tiền thuê hàng tháng. "
        "Đặt cọc 2 tháng. Chấm dứt hợp đồng. Phạt vi phạm. Tranh chấp tại tòa án."
    )
    doc_b = (
        "HỢP ĐỒNG THUÊ NHÀ. Bên cho thuê và bên thuê. Căn hộ. Tiền thuê hàng tháng. "
        "Bàn giao nhà."
    )
    data = unwrap(
        client.post(
            f"{P}/legal/compare-contracts",
            json={
                "documentAText": doc_a,
                "documentBText": doc_b,
                "contractType": "HOUSE_RENTAL",
                "protectedParty": "bên thuê",
                "options": {"outputMode": "JSON", "includeGraphContext": True},
            },
        )
    )
    assert isinstance(data["clauseComparisons"], list)
    assert len(data["clauseComparisons"]) > 0
    assert data["moreFavorableVersion"] in {"A", "B", "EQUAL", "INSUFFICIENT_DATA"}


def test_analyze_validation_error(client):
    # Empty contract text must fail validation with the standard envelope.
    resp = client.post(
        f"{P}/legal/analyze-contract",
        json={"contractText": "", "contractType": "HOUSE_RENTAL"},
    )
    assert resp.status_code == 422
    body = resp.json()
    assert body["success"] is False
    assert body["error"]["code"] == "VALIDATION_ERROR"
