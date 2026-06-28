import os
from dotenv import load_dotenv
load_dotenv()
from app.core.config import settings
from app.schemas import RagQueryRequest
from app.services.retrieval_service import RetrievalService
from app.services.contract_generation_service import ContractGenerationService

def run_test():
    retrieval_service = RetrievalService()
    # Find the document ID of 08.LB-TT in Neo4j first
    from neo4j import GraphDatabase
    driver = GraphDatabase.driver(settings.neo4j_uri, auth=(settings.neo4j_user, settings.neo4j_password))
    doc_id = None
    with driver.session() as session:
        res = session.run("MATCH (d:Document) WHERE coalesce(d.name, '') CONTAINS '08.LB-TT' OR coalesce(d.title, '') CONTAINS '08.LB-TT' OR coalesce(d.source_path, '') CONTAINS '08.LB-TT' RETURN d.node_id AS id")
        row = res.single()
        if row:
            doc_id = row['id']
            
    if not doc_id:
        print("Document 08.LB-TT not found in Neo4j!")
        return
        
    print(f"Testing with Document ID: {doc_id}")
    
    req = RagQueryRequest(
        requestId="test_req_123",
        chatSessionId="test_session_123",
        workspaceId="ws_996b2b35ad2d4a13b19652de7fa0bc1c",
        userId="1",
        question="""đổi form trên hình theo prompt này 
Bạn là chuyên gia định dạng văn bản hành chính và văn bản quy phạm pháp luật Việt Nam.

Nhiệm vụ của bạn là định dạng nội dung thành đúng mẫu văn bản Quyết định của Chính phủ Việt Nam theo Nghị định 30/2020/NĐ-CP.

## MỤC TIÊU

Đầu ra phải có bố cục gần như giống hoàn toàn mẫu văn bản Quyết định của Thủ tướng Chính phủ.

Không được sáng tạo bố cục.

Không thay đổi nội dung.

Chỉ định dạng.""",
        documentId=doc_id,
        topKUserChunks=5,
        topKKnowledgeChunks=3,
        chatHistory=""
    )
    
    service = ContractGenerationService(retrieval_service=retrieval_service)
    print("Calling generate_contract...")
    res = service.generate_contract(req)
    print("Generation complete!")
    print("Answer length:", len(res.answer))
    
    with open("test_output.txt", "w", encoding="utf-8") as f:
        f.write("=== ANSWER ===\n")
        f.write(res.answer)
        f.write("\n\n=== RAW RESPONSE ===\n")
        f.write(res.raw_response if hasattr(res, 'raw_response') else "N/A")
    print("Wrote to test_output.txt")

if __name__ == "__main__":
    run_test()
