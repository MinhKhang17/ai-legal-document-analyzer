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


def test_each_readable_source_is_rendered_only_once() -> None:
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

    rendered = service._replace_citation_markers_with_source_names(answer, hits)

    assert rendered.count("Nguồn hợp đồng/tài liệu người dùng") == 1
    assert rendered.count("hop-dong.docx") == 1
    assert rendered.count("Nguồn tài liệu hệ thống") == 1
    assert rendered.count("84.2015.QH13") == 1
    assert "[USER-" not in rendered
    assert "[KB-" not in rendered
