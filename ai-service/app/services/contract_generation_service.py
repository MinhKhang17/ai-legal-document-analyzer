from __future__ import annotations

import logging
import os
import shutil
import uuid

import httpx

from app.schemas import RagCitation, RagQueryRequest, RagQueryResponse
from app.services.retrieval_service import RagChunkHit, RetrievalService

logger = logging.getLogger(__name__)


def is_contract_generation_intent(question: str) -> bool:
    """Detect whether the user is asking for a contract/template file download.

    Uses fast keyword matching — no LLM call needed.
    """
    question_lower = question.lower().strip()

    # Informational or QA keywords that should bypass contract generation
    qa_keywords = [
        "what is", "why ", "how to", "how do", "how can", "is there", "are there",
        "là gì", "như thế nào", "làm sao", "làm thế nào", "có được", "có nên",
        "tại sao", "giải thích", "khái niệm", "quy định", "luật nào", "thủ tục",
        "cần lưu ý gì", "rủi ro gì", "bắt buộc", "yêu cầu", "điều kiện",
        "liệt kê", "danh sách", "kể tên", "cho biết các", "những hợp đồng nào",
        "gồm những", "có bao nhiêu", "ở đâu", "từ đâu", "phân tích", "so sánh",
        "kiểm tra", "đánh giá", "nhận xét", "góp ý", "sửa lỗi", "rà soát",
        "nguồn nào", "file nào", "tệp nào", "thông tin lấy từ", "lấy từ đâu",
        "tham khảo", "đã tham khảo", "tham chiếu", "đã dùng", "đã sử dụng", "nguồn gốc",
    ]
    if any(kw in question_lower for kw in qa_keywords):
        return False

    creation_keywords = [
        "generate", "create", "draft", "provide", "give", "make", "write",
        "tạo", "soạn", "soạn thảo", "viết", "cung cấp", "bản thảo", "lập",
        "in", "xuất", "tải", "lấy", "mẫu", "bản", "cho tôi",
    ]
    contract_keywords = [
        "contract", "agreement", "lease", "tenancy", "rental",
        "hợp đồng", "thoả thuận", "thuê nhà", "thuê trọ", "thuê văn phòng", "thuê mặt bằng",
        "lao động", "mua bán", "dịch vụ", "chuyển nhượng", "tặng cho", "vay tiền", "ủy quyền",
    ]

    has_creation = any(kw in question_lower for kw in creation_keywords)
    has_contract = any(kw in question_lower for kw in contract_keywords)

    direct_patterns = [
        "lease agreement", "tenancy agreement", "rental agreement",
        "rental contract", "lease contract", "tenancy contract",
        "mẫu hợp đồng", "bản hợp đồng", "hợp đồng lao động", "hợp đồng thuê",
        "hợp đồng mua bán", "hợp đồng dịch vụ",
    ]
    has_direct = any(pat in question_lower for pat in direct_patterns)

    return (has_creation and has_contract) or has_direct


# ---------------------------------------------------------------------------
# Template-based contract serving (replaces LLM-based generation)
# ---------------------------------------------------------------------------

class ContractGenerationService:
    """Serve pre-existing legal template files from the knowledge base.

    Instead of calling an LLM to *generate* a contract from scratch, this
    service finds the best-matching template document already stored in
    Neo4j, copies (or reconstructs) the original file, registers it with
    the backend, and returns a download link.
    """

    def __init__(
        self,
        *,
        retrieval_service: RetrievalService | None = None,
        llm_client=None,  # kept for interface compat; unused
    ) -> None:
        self.retrieval_service = retrieval_service or RetrievalService()

    # ------------------------------------------------------------------
    # Public entry point
    # ------------------------------------------------------------------

    def generate_contract(self, request: RagQueryRequest) -> RagQueryResponse:
        """Find the best matching template and return a download link."""

        # 1. Search ALL chunks (not just SYSTEM_KB) for relevant template documents.
        #    We bypass search_knowledge_chunks() because its metadata filter
        #    (source_type=SYSTEM_KB, ingest_source=INGEST_V2) excludes most
        #    knowledge base chunks that were ingested without those metadata fields.
        embedding = self.retrieval_service.embed_question(request.question)
        from app.models.knowledge_models import RetrievedChunk
        raw_chunks = self.retrieval_service.repository.search_chunks(
            embedding,
            top_k=request.topKKnowledgeChunks or 5,
            query_text=request.question,
        )
        # Convert raw RetrievedChunk objects to RagChunkHit for uniform handling
        retrieved_chunks = [
            RagChunkHit(
                citationId=f"T{i}",
                sourceType="SYSTEM_KB",
                score=float(ch.score),
                chunkText=ch.text,
                documentId=str((ch.metadata or {}).get("document_id", "")),
                knowledgeDocumentId=str((ch.metadata or {}).get("document_id", "")),
                fileName=(ch.metadata or {}).get("file_name", ""),
                title=ch.title,
                rawChunkId=ch.chunk_id,
                metadata=ch.metadata,
            )
            for i, ch in enumerate(raw_chunks, start=1)
        ]

        if not retrieved_chunks:
            return self._no_template_response(request, retrieved_chunks=[])

        # 2. Identify the best-matched document
        best_chunk = retrieved_chunks[0]
        best_doc_id = best_chunk.knowledgeDocumentId or best_chunk.documentId

        source_path, doc_title = self._get_source_document(best_doc_id)
        logger.info(
            "Best template match: doc_id=%s  title=%s  source_path=%s  exists=%s",
            best_doc_id, doc_title, source_path,
            os.path.exists(source_path) if source_path else False,
        )

        # 3. Prepare the template file for download
        download_links: list[str] = []
        served_path: str | None = None

        # Try to resolve path
        resolved_path = None
        if source_path and os.path.exists(source_path):
            resolved_path = source_path
        else:
            resolved_path = self._find_original_file_by_title(doc_title)
            if not resolved_path and source_path:
                # Also try matching using the filename inside the source_path
                filename = os.path.basename(source_path)
                name_only = os.path.splitext(filename)[0]
                resolved_path = self._find_original_file_by_title(name_only)

        if resolved_path and os.path.exists(resolved_path):
            # File resolved on disk — copy it to uploads
            served_path = self._copy_source_file(resolved_path, doc_title)
        else:
            # Original file is gone — reconstruct from chunks as a fallback
            served_path = self._reconstruct_docx_from_chunks(best_doc_id, doc_title)

        if served_path and os.path.exists(served_path):
            link = self._register_and_get_link(request, served_path, doc_title)
            if link:
                download_links.append(link)

        # 4. Build chat answer
        summary = self._build_template_summary(doc_title, retrieved_chunks)

        if download_links:
            answer = (
                f"{summary}\n\n---\n\n"
                "📄 **File mẫu đã sẵn sàng để tải về:**\n"
                + "\n".join(download_links)
            )
        else:
            answer = (
                f"{summary}\n\n"
                "⚠️ Không thể tạo file tải về. Vui lòng liên hệ quản trị viên."
            )

        # 5. Build citations
        citations = [
            RagCitation(
                citationId=hit.citationId,
                sourceType=hit.sourceType,
                score=hit.score,
                documentId=hit.documentId,
                workspaceId=hit.workspaceId,
                userId=hit.userId,
                fileName=hit.fileName,
                knowledgeDocumentId=hit.knowledgeDocumentId,
                lawName=hit.lawName,
                lawCode=hit.lawCode,
                legalDomain=hit.legalDomain,
                pageNumber=hit.pageNumber,
                articleNumber=hit.articleNumber,
                clauseNumber=hit.clauseNumber,
                sectionTitle=hit.sectionTitle,
            )
            for hit in retrieved_chunks
        ]

        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=1.0,
            shouldSuggestTicket=False,
            suggestionType="NONE",
            suggestionReason=None,
            missingInformation=None,
            riskLevel="LOW",
            legalDomain="Contract Law",
            userActionHint="CONTINUE_CHAT",
            citations=citations,
            retrievedUserChunks=0,
            retrievedKnowledgeChunks=len(retrieved_chunks),
        )

    # ------------------------------------------------------------------
    # Helpers — source document lookup
    # ------------------------------------------------------------------

    def _get_source_document(self, doc_id: str) -> tuple[str | None, str]:
        """Return (source_path, title) for the given Neo4j Document node."""
        if not doc_id:
            return None, "Tài liệu"
        try:
            from app.database.neo4j_client import neo4j_client

            if not neo4j_client.driver:
                neo4j_client.connect()

            records = neo4j_client.execute_query(
                "MATCH (d:Document {node_id: $doc_id}) "
                "RETURN d.source_path AS sp, d.title AS title LIMIT 1",
                {"doc_id": doc_id},
            )
            if records:
                return records[0].get("sp"), records[0].get("title") or "Tài liệu"
        except Exception as exc:
            logger.error("Failed to look up document %s: %s", doc_id, exc)
        return None, "Tài liệu"

    # ------------------------------------------------------------------
    # Helpers — file preparation
    # ------------------------------------------------------------------

    def _copy_source_file(self, source_path: str, doc_title: str) -> str | None:
        """Copy an existing source file into /app/uploads/ with a unique name."""
        try:
            ext = os.path.splitext(source_path)[1] or ".docx"
            safe_title = "".join(c if c.isalnum() or c in "._- " else "_" for c in doc_title)[:80]
            new_filename = f"template_{uuid.uuid4().hex}_{safe_title}{ext}"
            dest = f"/app/uploads/{new_filename}"
            shutil.copy2(source_path, dest)
            logger.info("Copied template file: %s -> %s", source_path, dest)
            return dest
        except Exception as exc:
            logger.error("Failed to copy source file %s: %s", source_path, exc)
            return None

    def _reconstruct_docx_from_chunks(self, doc_id: str, doc_title: str) -> str | None:
        """Reconstruct a DOCX file from text chunks stored in Neo4j.

        This is a fallback for when the original source file no longer exists
        on disk (e.g. /tmp files cleaned after container restart).
        """
        try:
            from app.database.neo4j_client import neo4j_client

            if not neo4j_client.driver:
                neo4j_client.connect()

            records = neo4j_client.execute_query(
                "MATCH (d:Document {node_id: $doc_id}) "
                "MATCH (c:Chunk {source_path: d.source_path}) "
                "RETURN c.text AS text, c.order AS order "
                "ORDER BY c.order ASC, c.node_id ASC",
                {"doc_id": doc_id},
            )
            if not records:
                logger.warning("No chunks found for document %s", doc_id)
                return None

            full_text = "\n\n".join(r["text"] for r in records if r.get("text"))
            if not full_text.strip():
                return None

            # Build a simple DOCX
            import docx
            from docx.shared import Pt, Cm
            from docx.enum.text import WD_ALIGN_PARAGRAPH

            document = docx.Document()
            section = document.sections[0]
            section.page_width = Cm(21.0)
            section.page_height = Cm(29.7)
            section.top_margin = Cm(2.0)
            section.bottom_margin = Cm(2.0)
            section.left_margin = Cm(3.5)
            section.right_margin = Cm(2.0)

            # Default style
            style = document.styles["Normal"]
            style.font.name = "Times New Roman"
            style.font.size = Pt(13)
            style.paragraph_format.line_spacing = 1.5
            style.paragraph_format.space_after = Pt(6)

            for line in full_text.split("\n"):
                stripped = line.strip()
                if not stripped:
                    document.add_paragraph("")
                    continue
                para = document.add_paragraph()
                # Bold for lines that look like headings/titles
                if stripped.isupper() and len(stripped) < 100:
                    run = para.add_run(stripped)
                    run.bold = True
                    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
                else:
                    para.add_run(stripped)

            safe_title = "".join(c if c.isalnum() or c in "._- " else "_" for c in doc_title)[:80]
            new_filename = f"template_{uuid.uuid4().hex}_{safe_title}.docx"
            dest = f"/app/uploads/{new_filename}"
            document.save(dest)
            logger.info("Reconstructed DOCX from chunks: %s (%d chars)", dest, len(full_text))
            return dest
        except Exception as exc:
            logger.error("Failed to reconstruct DOCX for doc %s: %s", doc_id, exc)
            return None

    # ------------------------------------------------------------------
    # Helpers — backend registration
    # ------------------------------------------------------------------

    def _register_and_get_link(self, request: RagQueryRequest, file_path: str, doc_title: str) -> str | None:
        """Register the file with the Spring Boot backend and return a download link."""
        backend_base_url = os.getenv("BACKEND_BASE_URL", "http://backend:8080")
        try:
            file_size = os.path.getsize(file_path)
            stored_filename = os.path.basename(file_path)
            ext = os.path.splitext(stored_filename)[1].lstrip(".")

            payload = {
                "workspaceId": request.workspaceId,
                "userId": request.userId,
                "originalFileName": f"{doc_title}.{ext}",
                "storedFileName": stored_filename,
                "filePath": file_path,
                "fileSize": file_size,
            }

            with httpx.Client(timeout=10.0) as client:
                resp = client.post(
                    f"{backend_base_url}/api/internal/documents/register-generated",
                    json=payload,
                )
                if resp.status_code == 200:
                    reg_id = resp.json().get("data", {}).get("documentId") or stored_filename
                    icon = "📝" if ext in ("docx", "doc") else "📥"
                    return (
                        f"{icon} [Tải về mẫu văn bản ({ext.upper()})]"
                        f"(http://localhost:8080/api/v1/workspaces/{request.workspaceId}"
                        f"/documents/{reg_id}/download)"
                    )
                else:
                    logger.error("Backend registration failed (%d): %s", resp.status_code, resp.text)
        except Exception as exc:
            logger.error("Failed to register template file: %s", exc)
        return None

    # ------------------------------------------------------------------
    # Helpers — response building
    # ------------------------------------------------------------------

    def _build_template_summary(self, doc_title: str, retrieved_chunks) -> str:
        """Build a brief summary describing the matched template."""
        # Provide first ~500 chars of the best chunk as a preview
        preview = ""
        if retrieved_chunks:
            raw = (retrieved_chunks[0].chunkText or "")[:500].strip()
            if raw:
                preview = f"\n\n**Nội dung tóm tắt:**\n> {raw}..."

        return (
            f"📋 **Tìm thấy mẫu văn bản phù hợp:** *{doc_title}*\n\n"
            "Hệ thống đã tìm được mẫu văn bản từ cơ sở tri thức pháp lý. "
            "Bạn có thể tải file về và chỉnh sửa theo nhu cầu thực tế."
            f"{preview}"
        )

    def _no_template_response(self, request: RagQueryRequest, *, retrieved_chunks=None) -> RagQueryResponse:
        """Return a response when no matching template is found."""
        answer = (
            "⚠️ **Không tìm thấy mẫu văn bản phù hợp**\n\n"
            "Hệ thống chưa có mẫu văn bản khớp với yêu cầu của bạn trong cơ sở tri thức. "
            "Vui lòng thử mô tả cụ thể hơn loại văn bản cần tìm, ví dụ:\n"
            "- Mẫu hợp đồng thuê nhà\n"
            "- Mẫu quyết định của UBND\n"
            "- Mẫu hợp đồng lao động\n"
        )
        return RagQueryResponse(
            requestId=request.requestId,
            chatSessionId=request.chatSessionId,
            answer=answer,
            confidenceScore=0.5,
            shouldSuggestTicket=False,
            suggestionType="ASK_MORE_INFO",
            suggestionReason="Không tìm thấy mẫu văn bản phù hợp.",
            missingInformation=None,
            riskLevel="LOW",
            legalDomain="Contract Law",
            userActionHint="CONTINUE_CHAT",
            citations=[],
            retrievedUserChunks=0,
            retrievedKnowledgeChunks=0,
        )

    def _find_original_file_by_title(self, title: str, search_dir: str = "/app/uploads") -> str | None:
        """Find the original file path on disk by searching for a matching title."""
        if not title:
            return None

        import re
        def clean(t):
            # Keep alphanumeric characters and convert to lower case for comparison
            return re.sub(r'[^a-zA-Z0-9]', '', t).lower()

        target = clean(title)
        if not target:
            return None

        for root, dirs, files in os.walk(search_dir):
            # Skip generated templates to avoid self-reference loops
            if "template_" in root:
                continue
            for f in files:
                if f.startswith("template_"):
                    continue
                name, ext = os.path.splitext(f)
                cleaned_name = clean(name)
                # Matches if equal or target is substring of filename
                if cleaned_name == target or target in cleaned_name or cleaned_name in target:
                    resolved_path = os.path.join(root, f)
                    logger.info("Resolved original template file for '%s' -> %s", title, resolved_path)
                    return resolved_path
        return None

