"""Kiểm tra dữ liệu trong Neo4j."""
from neo4j import GraphDatabase

uri = "bolt://localhost:7687"
user = "neo4j"
password = "password"

driver = GraphDatabase.driver(uri, auth=(user, password))

with driver.session() as session:
    # Đếm số chunks
    result = session.run("MATCH (c:Chunk) RETURN count(c) as count")
    chunk_count = result.single()["count"]
    print(f"📦 Số chunks trong Neo4j: {chunk_count}")
    
    # Đếm số documents
    result = session.run("MATCH (d:Document) RETURN count(d) as count")
    doc_count = result.single()["count"]
    print(f"📄 Số documents trong Neo4j: {doc_count}")
    
    # Xem 5 chunks mới nhất
    result = session.run("""
        MATCH (c:Chunk)
        RETURN c.title as title, c.text as text
        ORDER BY c.updated_at DESC
        LIMIT 5
    """)
    
    print(f"\n🔍 5 chunks mới nhất:")
    for idx, record in enumerate(result, 1):
        title = record["title"] or "N/A"
        text = record["text"] or ""
        print(f"\n{idx}. {title}")
        print(f"   Text: {text[:150]}..." if len(text) > 150 else f"   Text: {text}")

driver.close()
