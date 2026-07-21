import sys
import json
from app.graph.repository import GraphRepository
from app.services.retrieval_service import RetrievalService
from app.services.query_builder import build_legal_search_query, build_legal_text_query

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

retrieval_service = RetrievalService()
repo = GraphRepository()

question = "Giá thuê nhà ở là bao nhiêu?"
print("Step 1: Mocking user hits...")
try:
    user_hits = retrieval_service.search_user_chunks(
        question=question,
        user_id="2",
        workspace_id="ws_ad423692a224463c8824327d8a07b323",
        top_k=5
    )
    print(f"Matched user hits: {len(user_hits)}")
except Exception as e:
    import traceback
    traceback.print_exc()
    sys.exit(1)

print("Step 2: Building legal search query...")
legal_search_query = build_legal_search_query(question, user_hits)
print(f"Legal search query length: {len(legal_search_query)}")

print("Step 3: Embedding legal search query...")
embedding = retrieval_service.embed_question(legal_search_query)
print(f"Embedding length: {len(embedding)}")

print("Step 4: Building legal text query...")
query_text = build_legal_text_query(question)
print(f"Legal text query: {query_text}")

metadata_filter = {"source_type": {None, "SYSTEM_KB"}}

print("Step 5: Executing vector search component...")
with repo.driver.session() as session:
    result = session.run(
        "CALL db.index.vector.queryNodes($index_name, 2000, $embedding) YIELD node, score RETURN node.node_id AS chunk_id, node.title AS title, node.text AS text, coalesce(node.metadata_json, '{}') AS metadata_json, score AS score",
        index_name=repo.vector_index_name,
        embedding=embedding
    )
    records = list(result)
    print(f"Raw vector records: {len(records)}")
    matched_vector_hits = []
    for rec in records:
        if repo._record_matches_metadata(rec, metadata_filter):
            matched_vector_hits.append(rec)
    print(f"Filtered vector hits count: {len(matched_vector_hits)}")

print("Step 6: Executing text search component...")
text_hits = repo._search_chunks_by_text(
    query_text,
    top_k=5,
    metadata_filter=metadata_filter,
)
print(f"Matched text hits count: {len(text_hits)}")

print("Step 7: Merging hits...")
merged = repo.search_knowledge_chunks(
    embedding,
    top_k=5,
    query_text=query_text
)
print(f"Final merged hits count: {len(merged)}")
