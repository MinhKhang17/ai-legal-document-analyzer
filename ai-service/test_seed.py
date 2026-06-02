"""Smoke test: seed the legal baseline (idempotent).

Running twice must not create duplicates.

Run: python test_seed.py
"""
from app.graph.service import GraphService


def main() -> None:
    svc = GraphService()

    first = svc.seed_legal_baseline()
    print(f"[INFO] First seed: {first}")

    corpus = svc.seed_legal_corpus()
    print(f"[INFO] Corpus seed: {corpus}")

    # Idempotency check: counts of base nodes should stay stable after re-seed.
    svc.seed_legal_baseline()
    svc.seed_legal_corpus()
    counts = svc.run_query(
        "MATCH (c:ContractType) WITH count(c) AS ct "
        "MATCH (r:RiskType) WITH ct, count(r) AS rt "
        "MATCH (cl:ClauseType) RETURN ct, rt, count(cl) AS clt",
        {},
    )["rows"][0]
    print(f"[INFO] Node counts after double-seed: {counts}")

    assert counts["ct"] == 6, f"expected 6 ContractType, got {counts['ct']}"
    assert counts["rt"] == 18, f"expected 18 RiskType, got {counts['rt']}"
    assert counts["clt"] == 15, f"expected 15 ClauseType, got {counts['clt']}"

    arts = svc.run_query("MATCH (a:LegalArticle) RETURN count(a) AS c", {})["rows"][0]["c"]
    docs = svc.run_query("MATCH (d:LegalDocument) RETURN count(d) AS c", {})["rows"][0]["c"]
    print(f"[INFO] Corpus after double-seed: documents={docs} articles={arts}")
    from app.graph import legal_corpus as _corpus
    assert arts == len(_corpus.LEGAL_ARTICLES), (
        f"expected {len(_corpus.LEGAL_ARTICLES)} LegalArticle, got {arts}"
    )
    assert docs == len(_corpus.LEGAL_DOCUMENTS), (
        f"expected {len(_corpus.LEGAL_DOCUMENTS)} LegalDocument, got {docs}"
    )
    print("[OK] Seed is idempotent (no duplicates)")


if __name__ == "__main__":
    main()
