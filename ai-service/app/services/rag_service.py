"""RAG service for query processing."""
import logging
import time
from typing import List, Dict, Any
from app.database.neo4j_client import neo4j_client
from app.services.embedding_service import embedding_service
from app.services.gemini_service import gemini_service
from app.services.retrieval_service import RetrievalService, RagChunkHit
from app.models.query import RAGQueryRequest, RAGQueryResponse, ChecklistResult

logger = logging.getLogger(__name__)


class RAGService:
    """Service for RAG query processing."""
    
    def __init__(self):
        """Initialize RAG service."""
        self.retrieval_service = RetrievalService()

    def process_query(self, request: RAGQueryRequest) -> RAGQueryResponse:
        """Process a direct query against user documents first, then knowledge base."""
        start_time = time.time()
        try:
            user_hits, legal_search_query, knowledge_hits = self._retrieve_query_context(request)
            if not user_hits and not knowledge_hits:
                return RAGQueryResponse(
                    request_id=request.request_id,
                    success=True,
                    answer="Không đủ thông tin để kết luận.",
                    checklist_results=[],
                    knowledge_chunks=[],
                    total_checklist_items=0,
                    total_user_chunks=0,
                    total_knowledge_chunks=0,
                    processing_time_ms=(time.time() - start_time) * 1000,
                )

            answer = gemini_service.generate_query_answer(
                question=request.question,
                user_chunks=[self._hit_to_context_dict(hit) for hit in user_hits],
                knowledge_chunks=[self._hit_to_context_dict(hit) for hit in knowledge_hits],
            )
            processing_time = (time.time() - start_time) * 1000
            return RAGQueryResponse(
                request_id=request.request_id,
                success=True,
                answer=answer,
                checklist_results=[],
                knowledge_chunks=[self._hit_to_context_dict(hit) for hit in knowledge_hits],
                total_checklist_items=0,
                total_user_chunks=len(user_hits),
                total_knowledge_chunks=len(knowledge_hits),
                processing_time_ms=processing_time,
            )
        except Exception as e:
            logger.error(f"Failed to process query: {e}", exc_info=True)
            return RAGQueryResponse(
                request_id=request.request_id,
                success=False,
                answer="Không đủ thông tin để kết luận.",
                checklist_results=[],
                knowledge_chunks=[],
                total_checklist_items=0,
                total_user_chunks=0,
                total_knowledge_chunks=0,
                processing_time_ms=(time.time() - start_time) * 1000,
                error_message=f"Lỗi xử lý: {str(e)}",
            )

    def process_query_stream(self, request: RAGQueryRequest):
        """Process a direct query and stream the response as SSE text."""
        start_time = time.time()
        try:
            user_hits, legal_search_query, knowledge_hits = self._retrieve_query_context(request)
            yield self._encode_sse(
                "meta",
                {
                    "request_id": request.request_id,
                    "total_user_chunks": len(user_hits),
                    "total_knowledge_chunks": len(knowledge_hits),
                    "legal_search_query": legal_search_query,
                },
            )

            if not user_hits and not knowledge_hits:
                final_answer = "Không đủ thông tin để kết luận."
                yield self._encode_sse("chunk", {"delta": final_answer, "content": final_answer})
                yield self._encode_sse(
                    "done",
                    {
                        "answer": final_answer,
                        "processing_time_ms": (time.time() - start_time) * 1000,
                    },
                )
                return

            user_context = [self._hit_to_context_dict(hit) for hit in user_hits]
            knowledge_context = [self._hit_to_context_dict(hit) for hit in knowledge_hits]

            for event_text in gemini_service.stream_query_answer(
                question=request.question,
                user_chunks=user_context,
                knowledge_chunks=knowledge_context,
            ):
                yield event_text

            processing_time = (time.time() - start_time) * 1000
            logger.info("Streamed query successfully in %.2fms", processing_time)
        except Exception as exc:
            logger.error("Failed to stream query: %s", exc, exc_info=True)
            yield self._encode_sse("error", {"message": f"Lỗi xử lý: {str(exc)}"})

    def _retrieve_query_context(self, request: RAGQueryRequest) -> tuple[list[RagChunkHit], str, list[RagChunkHit]]:
        user_hits = self.retrieval_service.search_user_chunks(
            request.question,
            user_id=request.user_id,
            workspace_id=request.workspace_id,
            top_k=request.top_k_user_chunks_per_checklist,
            document_id=request.document_id,
        )
        legal_search_query = self.retrieval_service.build_legal_search_query(request.question, user_hits)
        knowledge_hits = self.retrieval_service.search_knowledge_chunks(
            legal_search_query,
            top_k=request.top_k_knowledge_chunks,
            query_text=request.question,
        )
        return user_hits, legal_search_query, knowledge_hits

    def _hit_to_context_dict(self, hit: RagChunkHit) -> Dict[str, Any]:
        return {
            "citationId": hit.citationId,
            "sourceType": hit.sourceType,
            "score": hit.score,
            "chunkText": hit.chunkText,
            "text": hit.chunkText,
            "documentId": hit.documentId,
            "workspaceId": hit.workspaceId,
            "userId": hit.userId,
            "fileName": hit.fileName,
            "knowledgeDocumentId": hit.knowledgeDocumentId,
            "lawName": hit.lawName,
            "lawCode": hit.lawCode,
            "legalDomain": hit.legalDomain,
            "pageNumber": hit.pageNumber,
            "articleNumber": hit.articleNumber,
            "clauseNumber": hit.clauseNumber,
            "sectionTitle": hit.sectionTitle,
            "title": hit.title,
            "metadata": hit.metadata or {},
        }
    
    def is_global_review_query(self, question: str) -> bool:
        """Determine if the question is a global review query."""
        # Enhanced heuristic - can be enhanced with NLU
        global_keywords = [
            # Vietnamese with diacritics - vấn đề
            "có vấn đề gì",
            "có vấn đề nào",
            "có vấn đề j",
            "có điểm nào",
            "có điểm gì",
            
            # Vietnamese with diacritics - rủi ro & thiếu sót
            "có rủi ro gì",
            "có rủi ro nào",
            "có nguy cơ gì",
            "có nguy cơ nào",
            "có thiếu sót gì",
            "có thiếu sót nào",
            "có lỗi gì",
            "có lỗi nào",
            "có sai sót gì",
            "có sai sót nào",
            "có gì sai",
            
            # Vietnamese with diacritics - yêu cầu rà soát
            "rà soát",
            "kiểm tra",
            "đánh giá",
            "phân tích",
            "xem xét",
            "nhận xét",
            
            # Vietnamese with diacritics - hợp lệ
            "có hợp lệ không",
            "có hợp lệ ko",
            "có đúng không",
            "có đúng ko",
            "có ổn không",
            "có ổn ko",
            "có được không",
            "có được ko",
            "có thể ký",
            
            # Vietnamese with diacritics - câu hỏi mơ hồ
            "thế nào",
            "như thế nào",
            "ra sao",
            "có gì đặc biệt",
            "cần chú ý",
            "cần lưu ý",
            "cần kiểm tra",
            
            # Vietnamese without diacritics - vấn đề
            "co van de gi",
            "co van de nao",
            "co van de j",
            "co diem nao",
            "co diem gi",
            
            # Vietnamese without diacritics - rủi ro & thiếu sót
            "co rui ro gi",
            "co rui ro nao",
            "co nguy co gi",
            "co nguy co nao",
            "co thieu sot gi",
            "co thieu sot nao",
            "co loi gi",
            "co loi nao",
            "co sai sot gi",
            "co sai sot nao",
            "co gi sai",
            
            # Vietnamese without diacritics - yêu cầu rà soát
            "ra soat",
            "kiem tra",
            "danh gia",
            "phan tich",
            "xem xet",
            "nhan xet",
            
            # Vietnamese without diacritics - hợp lệ
            "co hop le khong",
            "co hop le ko",
            "co dung khong",
            "co dung ko",
            "co on khong",
            "co on ko",
            "co duoc khong",
            "co duoc ko",
            "co the ky",
            
            # Vietnamese without diacritics - câu hỏi mơ hồ
            "the nao",
            "nhu the nao",
            "co gi dac biet",
            "can chu y",
            "can luu y",
            "can kiem tra",
            
            # English
            "review",
            "check",
            "any issues",
            "any problems",
            "what's wrong",
            "is valid",
            "is this valid",
            "how is",
            "what about",
        ]
        
        question_lower = question.lower().strip()
        
        # Check for global keywords
        has_global_keyword = any(keyword in question_lower for keyword in global_keywords)
        
        # Check if question is very short and general (< 50 chars with document reference)
        is_short_general = len(question_lower) < 50 and (
            "văn bản" in question_lower or 
            "hợp đồng" in question_lower or 
            "van ban" in question_lower or 
            "hop dong" in question_lower or
            "tài liệu" in question_lower or
            "tai lieu" in question_lower
        )
        
        return has_global_keyword or is_short_general
    
    def process_global_review_query(self, request: RAGQueryRequest) -> RAGQueryResponse:
        """Process a global review query using checklist-based retrieval."""
        start_time = time.time()
        
        try:
            # Step 1: Retrieve active checklists
            logger.info(f"Retrieving checklists for document_type=ANY")
            checklists = neo4j_client.get_active_checklists(
                document_type="ANY",
                limit=request.top_k_checklist
            )
            
            if not checklists:
                logger.warning("No active checklists found")
                return RAGQueryResponse(
                    request_id=request.request_id,
                    success=True,
                    answer="Hệ thống chưa có bộ tiêu chí rà soát. Vui lòng chạy seed checklist trước.",
                    checklist_results=[],
                    knowledge_chunks=[],
                    total_checklist_items=0,
                    total_user_chunks=0,
                    total_knowledge_chunks=0,
                    processing_time_ms=(time.time() - start_time) * 1000
                )
            
            logger.info(f"Retrieved {len(checklists)} checklist items")
            
            # Step 2: For each checklist, retrieve relevant user chunks
            checklist_results = []
            total_user_chunks = 0
            
            for i, checklist in enumerate(checklists, 1):
                try:
                    # Use checklist's embedding to search user documents
                    embedding = checklist.get("embedding")
                    if not embedding:
                        logger.warning(f"Checklist {checklist['checklist_id']} has no embedding, skipping")
                        continue
                    
                    logger.debug(f"Processing checklist {i}/{len(checklists)}: {checklist['title']}")
                    
                    user_chunks = neo4j_client.search_user_chunks_by_embedding(
                        embedding=embedding,
                        user_id=request.user_id,
                        workspace_id=request.workspace_id,
                        document_id=request.document_id,
                        top_k=request.top_k_user_chunks_per_checklist
                    )
                    
                    # Always add checklist result, even if no chunks found
                    checklist_results.append(ChecklistResult(
                        checklist_id=checklist["checklist_id"],
                        category=checklist["category"],
                        title=checklist["title"],
                        risk_question=checklist["risk_question"],
                        priority=checklist["priority"],
                        user_chunks_found=user_chunks
                    ))
                    
                    if user_chunks:
                        total_user_chunks += len(user_chunks)
                        logger.debug(f"  Found {len(user_chunks)} relevant chunks")
                    else:
                        logger.debug(f"  No relevant chunks found")
                
                except Exception as e:
                    logger.error(f"Error processing checklist {checklist.get('checklist_id')}: {e}")
                    # Continue processing other checklists
                    continue
            
            logger.info(f"Processed {len(checklist_results)} checklists, found {total_user_chunks} total chunks")
            
            # Step 3: Retrieve knowledge base chunks based on question
            logger.info("Retrieving knowledge base chunks")
            question_embedding = embedding_service.embed_text(request.question)
            knowledge_chunks = neo4j_client.search_knowledge_chunks_by_embedding(
                embedding=question_embedding,
                top_k=request.top_k_knowledge_chunks
            )
            logger.info(f"Retrieved {len(knowledge_chunks)} knowledge chunks")
            
            # Step 4: Build context and generate answer with Gemini
            logger.info("Building answer from results")
            
            # Convert ChecklistResult to dict for Gemini
            checklist_dicts = [
                {
                    "checklist_id": r.checklist_id,
                    "category": r.category,
                    "title": r.title,
                    "risk_question": r.risk_question,
                    "priority": r.priority,
                    "user_chunks_found": r.user_chunks_found
                }
                for r in checklist_results
            ]
            
            # Generate answer using Gemini
            answer = gemini_service.generate_review_answer(
                question=request.question,
                checklist_results=checklist_dicts,
                knowledge_chunks=knowledge_chunks
            )
            
            processing_time = (time.time() - start_time) * 1000  # Convert to ms
            logger.info(f"Query processed successfully in {processing_time:.2f}ms")
            
            return RAGQueryResponse(
                request_id=request.request_id,
                success=True,
                answer=answer,
                checklist_results=checklist_results,
                knowledge_chunks=knowledge_chunks,
                total_checklist_items=len(checklists),
                total_user_chunks=total_user_chunks,
                total_knowledge_chunks=len(knowledge_chunks),
                processing_time_ms=processing_time
            )

        except Exception as e:
            logger.error(f"Failed to process global review query: {e}", exc_info=True)
            return RAGQueryResponse(
                request_id=request.request_id,
                success=False,
                error_message=f"Lỗi xử lý: {str(e)}"
            )

    def process_global_review_query_stream(self, request: RAGQueryRequest):
        """Process a global review query and stream the response as SSE text."""
        start_time = time.time()

        try:
            logger.info("Retrieving checklists for streamed response")
            checklists = neo4j_client.get_active_checklists(
                document_type="ANY",
                limit=request.top_k_checklist
            )

            if not checklists:
                yield self._encode_sse(
                    "done",
                    {
                        "answer": "Hệ thống chưa có bộ tiêu chí rà soát. Vui lòng chạy seed checklist trước.",
                        "processing_time_ms": (time.time() - start_time) * 1000,
                    },
                )
                return

            checklist_results = []
            total_user_chunks = 0
            for checklist in checklists:
                try:
                    embedding = checklist.get("embedding")
                    if not embedding:
                        continue

                    user_chunks = neo4j_client.search_user_chunks_by_embedding(
                        embedding=embedding,
                        user_id=request.user_id,
                        workspace_id=request.workspace_id,
                        document_id=request.document_id,
                        top_k=request.top_k_user_chunks_per_checklist
                    )

                    checklist_results.append(ChecklistResult(
                        checklist_id=checklist["checklist_id"],
                        category=checklist["category"],
                        title=checklist["title"],
                        risk_question=checklist["risk_question"],
                        priority=checklist["priority"],
                        user_chunks_found=user_chunks
                    ))

                    total_user_chunks += len(user_chunks)
                except Exception as exc:
                    logger.error("Error processing checklist %s: %s", checklist.get("checklist_id"), exc)
                    continue

            question_embedding = embedding_service.embed_text(request.question)
            knowledge_chunks = neo4j_client.search_knowledge_chunks_by_embedding(
                embedding=question_embedding,
                top_k=request.top_k_knowledge_chunks
            )

            yield self._encode_sse(
                "meta",
                {
                    "request_id": request.request_id,
                    "total_checklist_items": len(checklists),
                    "total_user_chunks": total_user_chunks,
                    "total_knowledge_chunks": len(knowledge_chunks),
                },
            )

            checklist_dicts = [
                {
                    "checklist_id": r.checklist_id,
                    "category": r.category,
                    "title": r.title,
                    "risk_question": r.risk_question,
                    "priority": r.priority,
                    "user_chunks_found": r.user_chunks_found
                }
                for r in checklist_results
            ]

            for event_text in gemini_service.stream_review_answer(
                question=request.question,
                checklist_results=checklist_dicts,
                knowledge_chunks=knowledge_chunks,
            ):
                yield event_text

            processing_time = (time.time() - start_time) * 1000
            logger.info("Streamed global review query successfully in %.2fms", processing_time)
        except Exception as exc:
            logger.error("Failed to stream global review query: %s", exc, exc_info=True)
            yield self._encode_sse("error", {"message": f"Lỗi xử lý: {str(exc)}"})

    def _encode_sse(self, event_type: str, payload: Dict[str, Any]) -> str:
        import json

        return f"event: {event_type}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
    
    def _build_preliminary_answer(
        self,
        checklist_results: List[ChecklistResult],
        knowledge_chunks: List[Dict[str, Any]]
    ) -> str:
        """Build a detailed answer from checklist results."""
        
        if not checklist_results:
            return "Không thể phân tích văn bản vì không tìm thấy nội dung phù hợp hoặc chưa có dữ liệu."
        
        # Categorize findings
        findings_with_content = []
        findings_without_content = []
        
        for result in checklist_results:
            if result.user_chunks_found:
                findings_with_content.append(result)
            else:
                findings_without_content.append(result)
        
        # Build structured answer
        answer_parts = []
        
        # Header
        answer_parts.append("📋 **KẾT QUẢ RÀ SOÁT HỢP ĐỒNG**\n")
        
        # Section 1: Issues found
        if findings_with_content:
            answer_parts.append("## ⚠️ CÁC ĐIỂM CẦN LƯU Ý\n")
            
            for i, result in enumerate(findings_with_content[:10], 1):
                answer_parts.append(f"### {i}. {result.title} (Mức độ ưu tiên: {result.priority})")
                answer_parts.append(f"**Câu hỏi kiểm tra:** {result.risk_question}\n")
                
                # Show relevant content found
                if result.user_chunks_found:
                    answer_parts.append("**Nội dung liên quan tìm thấy:**")
                    for j, chunk in enumerate(result.user_chunks_found[:2], 1):
                        text = chunk.get('text', '')[:200]  # Limit to 200 chars
                        score = chunk.get('score', 0)
                        answer_parts.append(f"  {j}. \"{text}...\" (độ liên quan: {score:.2%})")
                
                answer_parts.append("")  # Empty line
        else:
            answer_parts.append("## ✅ KHÔNG TÌM THẤY VẤN ĐỀ RÕ RÀNG\n")
            answer_parts.append("Dựa trên các tiêu chí rà soát, không phát hiện vấn đề cụ thể trong văn bản.\n")
        
        # Section 2: Not found but should check manually
        if findings_without_content and len(findings_without_content) <= 15:
            answer_parts.append("## 📝 CÁC ĐIỂM CHƯA TÌM THẤY (Cần kiểm tra thủ công)\n")
            answer_parts.append("Các điều khoản sau không được tìm thấy rõ ràng trong văn bản:")
            
            for result in findings_without_content[:10]:
                answer_parts.append(f"- **{result.title}**: {result.risk_question}")
        
        # Section 3: Summary
        answer_parts.append("\n---")
        answer_parts.append(f"**Tổng kết:**")
        answer_parts.append(f"- Đã rà soát: {len(checklist_results)} tiêu chí")
        answer_parts.append(f"- Phát hiện vấn đề: {len(findings_with_content)} điểm")
        answer_parts.append(f"- Cần kiểm tra thêm: {len(findings_without_content)} điểm")
        
        # Disclaimer
        answer_parts.append("\n⚠️ *Đây là kết quả phân tích tự động. Khuyến nghị nên có chuyên gia pháp lý xem xét kỹ hơn trước khi ký kết.*")
        
        return "\n".join(answer_parts)


# Singleton instance
rag_service = RAGService()
