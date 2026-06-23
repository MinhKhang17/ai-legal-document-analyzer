"""Script to check Neo4j data structure and content."""
import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.database.neo4j_client import neo4j_client
from app.config import settings

def check_connection():
    """Check Neo4j connection."""
    print("=== Checking Neo4j Connection ===")
    try:
        neo4j_client.connect()
        print("✅ Connected to Neo4j successfully")
        return True
    except Exception as e:
        print(f"❌ Failed to connect: {e}")
        return False

def check_node_labels():
    """Check what node labels exist in Neo4j."""
    print("\n=== Checking Node Labels ===")
    query = "CALL db.labels()"
    try:
        result = neo4j_client.execute_query(query)
        labels = [r['label'] for r in result]
        print(f"Found {len(labels)} node labels:")
        for label in labels:
            print(f"  - {label}")
        return labels
    except Exception as e:
        print(f"❌ Error: {e}")
        return []

def check_node_count_by_label(label):
    """Count nodes for a specific label."""
    query = f"MATCH (n:{label}) RETURN count(n) as count"
    try:
        result = neo4j_client.execute_query(query)
        count = result[0]['count'] if result else 0
        print(f"  {label}: {count} nodes")
        return count
    except Exception as e:
        print(f"  {label}: Error - {e}")
        return 0

def sample_nodes(label, limit=3):
    """Get sample nodes from a label."""
    print(f"\n=== Sample {label} Nodes (limit {limit}) ===")
    query = f"MATCH (n:{label}) RETURN n LIMIT {limit}"
    try:
        result = neo4j_client.execute_query(query)
        for i, record in enumerate(result, 1):
            node = record['n']
            print(f"\n--- Node {i} ---")
            for key, value in node.items():
                if key == 'embedding':
                    print(f"  {key}: <vector of length {len(value) if value else 0}>")
                else:
                    print(f"  {key}: {value}")
    except Exception as e:
        print(f"❌ Error: {e}")

def check_properties_by_label(label):
    """Check what properties exist for a label."""
    print(f"\n=== Properties in {label} ===")
    query = f"""
    MATCH (n:{label})
    WITH keys(n) as props
    UNWIND props as prop
    RETURN DISTINCT prop
    ORDER BY prop
    """
    try:
        result = neo4j_client.execute_query(query)
        props = [r['prop'] for r in result]
        print(f"Found {len(props)} properties:")
        for prop in props:
            print(f"  - {prop}")
    except Exception as e:
        print(f"❌ Error: {e}")

def check_indexes():
    """Check what indexes exist."""
    print("\n=== Checking Indexes ===")
    query = "SHOW INDEXES"
    try:
        result = neo4j_client.execute_query(query)
        print(f"Found {len(result)} indexes:")
        for idx in result:
            print(f"  - {idx.get('name', 'unnamed')}: {idx.get('type', 'unknown type')}")
    except Exception as e:
        print(f"❌ Error: {e}")

def main():
    """Main function."""
    print("=" * 60)
    print("Neo4j Data Structure Inspector")
    print("=" * 60)
    
    # Check connection
    if not check_connection():
        return
    
    # Check labels
    labels = check_node_labels()
    
    if not labels:
        print("\n⚠️  No node labels found in Neo4j")
        neo4j_client.close()
        return
    
    # Count nodes per label
    print("\n=== Node Counts ===")
    for label in labels:
        check_node_count_by_label(label)
    
    # Check properties and samples for each label
    for label in labels:
        check_properties_by_label(label)
        sample_nodes(label, limit=2)
    
    # Check indexes
    check_indexes()
    
    # Close connection
    neo4j_client.close()
    print("\n" + "=" * 60)
    print("✅ Inspection completed")

if __name__ == "__main__":
    main()
