"""Smoke test: query ContractType nodes from the graph.

After seeding (POST /graph/seed/legal-baseline or python test_seed.py), this
must NOT return an empty list.

Run: python test_query.py
"""
from app.graph.service import GraphService


def main() -> None:
    svc = GraphService()
    result = svc.run_query(
        "MATCH (c:ContractType) RETURN c.code AS code ORDER BY code", {}
    )
    codes = [row["code"] for row in result["rows"]]
    print(f"[INFO] ContractType nodes ({len(codes)}): {codes}")
    assert codes, "No ContractType nodes found - did you run the seed?"
    print("[OK] ContractType query returned data")


if __name__ == "__main__":
    main()
