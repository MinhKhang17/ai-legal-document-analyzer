from app.services.rag_query_service import RagQueryService
from app.services.retrieval_service import RagChunkHit


def test_internal_citation_ids_are_preserved_for_claim_level_grounding() -> None:
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

    rendered = service._enrich_answer_with_download_links(answer, "ws-1", hits[1:])

    assert rendered == answer
    assert "[USER-1]" in rendered
    assert "[KB-1]" in rendered


def test_repeated_claim_level_citations_are_not_deduplicated() -> None:
    service = RagQueryService.__new__(RagQueryService)
    answer = (
        "Làm thêm quá mức [USER-1], miễn trách nhiệm an toàn [USER-2]. "
        "Cần tuân thủ thời giờ làm việc [KB-1] và an toàn lao động [KB-2]."
    )
    hits = [
        RagChunkHit(
            citationId="USER-1", sourceType="USER_DOCUMENT", score=0.9,
            chunkText="Thời giờ làm việc", documentId="doc-1", fileName="hop-dong.docx",
        ),
        RagChunkHit(
            citationId="USER-2", sourceType="USER_DOCUMENT", score=0.89,
            chunkText="An toàn lao động", documentId="doc-1", fileName="hop-dong.docx",
        ),
        RagChunkHit(
            citationId="KB-1", sourceType="SYSTEM_KB", score=0.88,
            chunkText="Thời giờ làm việc", knowledgeDocumentId="law-1", lawCode="84.2015.QH13",
        ),
        RagChunkHit(
            citationId="KB-2", sourceType="SYSTEM_KB", score=0.87,
            chunkText="An toàn lao động", knowledgeDocumentId="law-1", lawCode="84.2015.QH13",
        ),
    ]

    rendered = service._enrich_answer_with_download_links(answer, "ws-1", hits[2:])

    assert rendered == answer
    assert rendered.count("[USER-") == 2
    assert rendered.count("[KB-") == 2
