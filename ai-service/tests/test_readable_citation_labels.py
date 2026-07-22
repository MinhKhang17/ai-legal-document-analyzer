from app.services.rag_query_service import RagQueryService
from app.services.retrieval_service import RagChunkHit


def test_internal_citation_ids_are_replaced_with_source_names() -> None:
    service = RagQueryService.__new__(RagQueryService)
    answer = "Điều khoản có rủi ro [USER-1] và cần đối chiếu pháp luật [KB-1]."
    hits = [
        RagChunkHit(
            citationId="USER-1",
            sourceType="USER_DOCUMENT",
            score=0.9,
            chunkText="Điều 9.1",
            fileName="04-2026-HDTN-HCM.pdf",
        ),
        RagChunkHit(
            citationId="KB-1",
            sourceType="SYSTEM_KB",
            score=0.88,
            chunkText="Trách nhiệm bồi thường",
            lawName="Bộ luật Dân sự 2015",
            articleNumber="13",
        ),
    ]

    rendered = service._replace_citation_markers_with_source_names(answer, hits)

    assert "[USER-1]" not in rendered
    assert "[KB-1]" not in rendered
    assert "04-2026-HDTN-HCM.pdf" in rendered
    assert "Bộ luật Dân sự 2015" in rendered
    assert "Điều 13" in rendered
