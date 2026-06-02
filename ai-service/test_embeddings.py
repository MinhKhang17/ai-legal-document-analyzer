"""Smoke test: embedding-based semantic similarity.

Verifies the active backend ranks a semantically-related legal sentence above
unrelated ones. Works with either the Gemini backend (when LLM_API_KEY is set)
or the lexical fallback.

Run: python test_embeddings.py
"""
from app.services.embedding_service import get_embedding_service


def main() -> None:
    svc = get_embedding_service()
    print(f"[INFO] embedding backend: {svc.backend}")

    query = "Điều khoản đặt cọc có rủi ro gì?"
    docs = [
        "Rủi ro liên quan đến điều khoản đặt cọc",  # most relevant
        "Rủi ro về cơ chế giải quyết tranh chấp",
        "Rủi ro về nghĩa vụ thuế, phí, lệ phí",
    ]
    scores = svc.rank(query, docs)
    ranked = sorted(zip(docs, scores), key=lambda x: -x[1])
    for doc, score in ranked:
        print(f"   {score:.4f}  {doc}")

    assert ranked[0][0] == docs[0], "Most relevant document was not ranked first"
    print(f"[OK] semantic ranking correct (backend={svc.backend})")


if __name__ == "__main__":
    main()
