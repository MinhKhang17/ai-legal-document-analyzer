"""Smoke test: RAG retrieve returns related risks from the graph.

Run: python test_rag_retrieve.py
"""
from app.services.rag_service import RagService


def main() -> None:
    svc = RagService()
    result = svc.retrieve(
        query="Điều khoản đặt cọc có rủi ro gì?",
        contract_type="HOUSE_RENTAL",
        top_k=5,
        risk_filters=["DEPOSIT_RISK"],
        clause_filters=["DEPOSIT"],
    )
    items = result["items"]
    print(f"[INFO] retrieved {len(items)} items")
    for it in items:
        print(f"   - [{it['type']}] {it['title']} score={it['score']}")

    assert items, "RAG retrieve returned no items - did you seed the graph?"
    assert any(it["id"] == "DEPOSIT_RISK" for it in items), \
        "Expected DEPOSIT_RISK in retrieved context"
    print("[OK] RAG retrieve returned related risks")


if __name__ == "__main__":
    main()
