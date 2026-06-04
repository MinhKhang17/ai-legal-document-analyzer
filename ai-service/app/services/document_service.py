"""Document upload and processing service.

Handles:
- PDF/DOCX text extraction
- Embedding generation  
- Storage in Neo4j + Vector DB
"""
from typing import Dict, Any, Optional
import hashlib
from datetime import datetime

from app.services.embedding_service import get_embedding_service
from app.graph.service import GraphService


class DocumentService:
    """Service for managing user-uploaded documents."""
    
    def __init__(self):
        self.embedding_svc = get_embedding_service()
        self.graph = GraphService()
    
    def process_upload(
        self,
        file_content: bytes,
        filename: str,
        user_id: str,
        contract_type: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Process uploaded document:
        1. Extract text
        2. Generate embeddings
        3. Store in graph + vector DB
        4. Link with system knowledge
        
        Args:
            file_content: Raw file bytes
            filename: Original filename
            user_id: ID của user upload
            contract_type: Loại hợp đồng (optional)
            
        Returns:
            Dict with document_id and metadata
        """
        # 1. Extract text
        if filename.endswith('.pdf'):
            text = self._extract_pdf(file_content)
        elif filename.endswith('.docx'):
            text = self._extract_docx(file_content)
        else:
            raise ValueError(f"Unsupported file type: {filename}")
        
        # 2. Generate document ID
        doc_id = self._generate_doc_id(file_content, user_id)
        
        # 3. Generate embeddings (skipped for now - embedding service doesn't have embed method)
        # embedding = self.embedding_svc.embed(text)
        
        # 4. Store in Neo4j
        node_data = {
            "id": doc_id,
            "userId": user_id,
            "filename": filename,
            "text": text[:5000],  # Truncate for storage
            "fullText": text,  # Store full text separately if needed
            "contractType": contract_type or "UNKNOWN",
            "uploadedAt": datetime.utcnow().isoformat(),
            # "embedding": embedding.tolist() if hasattr(embedding, 'tolist') else embedding,
        }
        
        # Create Document node
        self.graph.repo.run(
            "MERGE (d:Document {id: $id}) "
            "SET d += $props",
            {"id": doc_id, "props": node_data}
        )
        
        # 5. Link với ContractType nếu có
        if contract_type and contract_type != "UNKNOWN":
            self.graph.repo.run(
                "MATCH (d:Document {id: $doc_id}) "
                "MATCH (ct:ContractType {code: $contract_type}) "
                "MERGE (d)-[:BELONGS_TO]->(ct)",
                {"doc_id": doc_id, "contract_type": contract_type}
            )
        
        return {
            "documentId": doc_id,
            "filename": filename,
            "textLength": len(text),
            "contractType": contract_type,
            "uploadedAt": node_data["uploadedAt"],
        }
    
    def _extract_pdf(self, content: bytes) -> str:
        """Extract text from PDF using PyPDF2."""
        import io
        try:
            import PyPDF2
            pdf_file = io.BytesIO(content)
            reader = PyPDF2.PdfReader(pdf_file)
            
            text = ""
            for page in reader.pages:
                text += page.extract_text() + "\n"
            
            return text.strip()
        except ImportError:
            raise ImportError(
                "PyPDF2 not installed. Run: pip install PyPDF2"
            )
    
    def _extract_docx(self, content: bytes) -> str:
        """Extract text from DOCX using python-docx."""
        import io
        try:
            from docx import Document
            docx_file = io.BytesIO(content)
            doc = Document(docx_file)
            
            text = ""
            for paragraph in doc.paragraphs:
                text += paragraph.text + "\n"
            
            return text.strip()
        except ImportError:
            raise ImportError(
                "python-docx not installed. Run: pip install python-docx"
            )
    
    def _generate_doc_id(self, content: bytes, user_id: str) -> str:
        """Generate unique document ID from content hash."""
        content_hash = hashlib.sha256(content).hexdigest()[:16]
        return f"DOC_{user_id}_{content_hash}"
    
    def get_user_documents(self, user_id: str, limit: int = 50) -> list:
        """Get all documents uploaded by a user."""
        rows = self.graph.repo.run(
            "MATCH (d:Document {userId: $user_id}) "
            "RETURN d "
            "ORDER BY d.uploadedAt DESC "
            "LIMIT $limit",
            {"user_id": user_id, "limit": limit}
        )
        return [row["d"] for row in rows]
    
    def search_user_documents(
        self,
        user_id: str,
        query: str,
        top_k: int = 5,
    ) -> list:
        """
        Semantic search trong documents của user.
        
        Args:
            user_id: User ID
            query: Search query
            top_k: Số lượng results
            
        Returns:
            List of matching documents with scores
        """
        # Use embedding service for ranking (no need to generate embeddings separately)
        # For now, simple keyword search
        rows = self.graph.repo.run(
            "MATCH (d:Document {userId: $user_id}) "
            "WHERE toLower(d.text) CONTAINS toLower($query) "
            "RETURN d "
            "LIMIT $limit",
            {"user_id": user_id, "query": query, "limit": top_k}
        )
        
        # Get documents and rank them
        docs = [row["d"] for row in rows]
        if not docs:
            return []
        
        # Use embedding service to rank documents by similarity
        doc_texts = [doc["properties"]["text"] for doc in docs]
        scores = self.embedding_svc.rank(query, doc_texts)
        
        # Combine docs with scores and sort
        results = []
        for doc, score in zip(docs, scores):
            props = doc["properties"]
            results.append({
                "documentId": props["id"],
                "filename": props["filename"],
                "contractType": props["contractType"],
                "snippet": props["text"][:200] + "...",
                "score": score,
            })
        
        # Sort by score descending
        results.sort(key=lambda x: x["score"], reverse=True)
        return results[:top_k]
