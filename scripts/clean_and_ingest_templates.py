import os
import shutil
import requests
import subprocess
from pathlib import Path
import time
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Configuration
AI_SERVICE_URL = "http://localhost:8000"
INGEST_V1_ENDPOINT = f"{AI_SERVICE_URL}/api/knowledge/ingest"
INGEST_V2_ENDPOINT = f"{AI_SERVICE_URL}/api/knowledge/ingest-v2"
SOURCE_DIR = r"C:\Users\DELL\Documents\Hopdongthuenhaf"
CONTAINER_NAME = "ai-legal-document-analyzer-ai-service-1"

# Document mapping
TEMPLATES = [
    {
        "original_file": "Phu_luc_II._Hop_dong_thue_nha_o_1505152407 (2).docx",
        "target_file": "Mẫu Hợp đồng Thuê nhà ở.docx",
        "title": "Mẫu Hợp đồng Thuê nhà ở"
    },
    {
        "original_file": "hop-dong-lao-dong.docx",
        "target_file": "Mẫu Hợp đồng Lao động.docx",
        "title": "Mẫu Hợp đồng Lao động"
    }
]

def clean_old_neo4j_documents():
    """Connect to Neo4j and delete all old document/chunk nodes to avoid search conflicts."""
    logger.info("Connecting to Neo4j to delete old templates...")
    from neo4j import GraphDatabase
    
    # We can fetch Neo4j config from app config or use standard settings
    neo4j_uri = "bolt://localhost:7687"
    neo4j_user = "neo4j"
    neo4j_password = "password"
    
    titles_to_delete = [
        "hop-dong-thue-nha-o",
        "hop-dong-lao-dong",
        "Mẫu Hợp đồng Thuê nhà ở",
        "Mẫu Hợp đồng Lao động",
        "Phu_luc_II._Hop_dong_thue_nha_o_1505152407 (1)",
        "Phu_luc_II._Hop_dong_thue_nha_o_1505152407 (2)"
    ]
    
    try:
        driver = GraphDatabase.driver(neo4j_uri, auth=(neo4j_user, neo4j_password))
        with driver.session() as session:
            # 1. Delete documents and their chunks
            query = """
            MATCH (d:Document)
            WHERE d.title IN $titles OR d.name IN $titles
            OPTIONAL MATCH (c:Chunk {source_path: d.source_path})
            DETACH DELETE d, c
            """
            result = session.run(query, titles=titles_to_delete)
            summary = result.consume()
            logger.info(f"  ✓ Deleted old template entries from Neo4j (nodes deleted: {summary.counters.nodes_deleted})")
            
            # 2. Also delete any orphaned Chunks with similar names
            query_orphaned = """
            MATCH (c:Chunk)
            WHERE c.title IN $titles OR c.law_name IN $titles
            DETACH DELETE c
            """
            result_orphaned = session.run(query_orphaned, titles=titles_to_delete)
            summary_orphaned = result_orphaned.consume()
            if summary_orphaned.counters.nodes_deleted > 0:
                logger.info(f"  ✓ Deleted orphaned chunk entries (nodes deleted: {summary_orphaned.counters.nodes_deleted})")
                
        driver.close()
    except Exception as e:
        logger.error(f"Failed to clean Neo4j database: {e}")

def get_mime_type(filename: str) -> str:
    if filename.endswith(".docx"):
        return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    return 'application/msword'

def upload_template(file_path: Path, title: str):
    """Upload template to both V1 and V2 endpoints."""
    # V1 Upload
    try:
        with open(file_path, 'rb') as f:
            files = {'file': (file_path.name, f, get_mime_type(file_path.name))}
            data = {'title': title}
            logger.info(f"Uploading to V1: {file_path.name} with title '{title}'")
            r = requests.post(INGEST_V1_ENDPOINT, files=files, data=data, timeout=300)
            if r.status_code == 200:
                logger.info(f"  ✓ V1 success: {r.json().get('total_chunks')} chunks")
            else:
                logger.error(f"  ✗ V1 failed: {r.status_code} - {r.text[:200]}")
    except Exception as e:
        logger.error(f"V1 upload error: {e}")
        
    # V2 Upload
    try:
        with open(file_path, 'rb') as f:
            files = {'file': (file_path.name, f, get_mime_type(file_path.name))}
            data = {'title': title}
            logger.info(f"Uploading to V2: {file_path.name} with title '{title}'")
            r = requests.post(INGEST_V2_ENDPOINT, files=files, data=data, timeout=300)
            if r.status_code == 200:
                logger.info(f"  ✓ V2 success: {r.json().get('total_chunks')} chunks")
            else:
                logger.error(f"  ✗ V2 failed: {r.status_code} - {r.text[:200]}")
    except Exception as e:
        logger.error(f"V2 upload error: {e}")

def main():
    logger.info("=" * 80)
    logger.info("CLEANING AND INGESTING PERFECT TEMPLATE FILES")
    logger.info("=" * 80)
    
    # 1. Clean Neo4j
    clean_old_neo4j_documents()
    
    # 2. Process each template
    source_dir = Path(SOURCE_DIR)
    for t in TEMPLATES:
        orig_path = source_dir / t["original_file"]
        target_path = source_dir / t["target_file"]
        
        if not orig_path.exists():
            logger.warning(f"Original file not found: {orig_path}")
            continue
            
        # Rename/copy locally
        logger.info(f"Copying {orig_path.name} -> {target_path.name}...")
        shutil.copy2(orig_path, target_path)
        
        # Copy to container's /app/uploads/ using docker cp
        try:
            logger.info(f"Copying {target_path.name} to container {CONTAINER_NAME}:/app/uploads/...")
            subprocess.run([
                "docker", "cp", 
                str(target_path), 
                f"{CONTAINER_NAME}:/app/uploads/"
            ], check=True)
            logger.info("  ✓ Successfully copied to container uploads folder")
        except Exception as e:
            logger.error(f"Failed to copy to container: {e}")
            
        # Ingest
        upload_template(target_path, t["title"])
        
        # Clean up the renamed target file on the host if wanted
        # (We can keep it or delete it. Keeping it is fine)
        time.sleep(1)

    logger.info("\n" + "=" * 80)
    logger.info("ALL DONE! TEMPLATE FILES ARE READY")
    logger.info("=" * 80)

if __name__ == "__main__":
    main()
