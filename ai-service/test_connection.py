"""Smoke test: Neo4j connectivity.

Run: python test_connection.py
"""
from app.graph.connection import ping, verify_connection


def main() -> None:
    verify_connection()
    ok = ping()
    assert ok, "RETURN 1 did not return ok"
    print("[OK] Neo4j connection verified (RETURN 1 -> ok)")


if __name__ == "__main__":
    main()
