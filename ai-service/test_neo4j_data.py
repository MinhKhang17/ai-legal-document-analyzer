"""Quick script to check if data is in Neo4j."""
import os
from neo4j import GraphDatabase

# Neo4j connection
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USER = os.getenv("NEO4J_USER", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "password")

driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))

def check_data():
    with driver.session() as session:
        # Count chunks
        result = session.run("MATCH (c:Chunk) RETURN count(c) as count")
        chunk_count = result.single()["count"]
        print(f"✅ Total chunks in Neo4j: {chunk_count}")
        
        # Count documents
        result = session.run("MATCH (d:Document) RETURN count(d) as count")
        doc_count = result.single()["count"]
        print(f"✅ Total documents in Neo4j: {doc_count}")
        
        # Sample chunks
        result = session.run("""
            MATCH (c:Chunk)
            RETURN c.text as text, c.chunk_id as id
            LIMIT 3
        """)
        
        print("\n📄 Sample chunks:")
        for i, record in enumerate(result, 1):
            text = record["text"] or ""
            chunk_id = record["id"]
            preview = text[:100] + "..." if len(text) > 100 else text
            print(f"\n{i}. Chunk ID: {chunk_id}")
            print(f"   Text: {preview}")
        
        # Check .doc files
        result = session.run("""
            MATCH (d:Document)
            WHERE d.file_type = 'doc'
            RETURN d.title as title, d.file_type as type
            LIMIT 5
        """)
        
        print("\n📑 .doc files imported:")
        for record in result:
            print(f"   - {record['title']} ({record['type']})")

if __name__ == "__main__":
    try:
        check_data()
        driver.close()
    except Exception as e:
        print(f"❌ Error: {e}")
