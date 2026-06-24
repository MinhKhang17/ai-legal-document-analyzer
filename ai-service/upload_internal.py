"""
Upload legal documents from inside the AI service container.
"""
import os
import requests
from pathlib import Path
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

KNOWLEDGE_INGEST_ENDPOINT = "http://localhost:8000/api/knowledge/ingest"
DOCS_DIR = "/app/uploads/legal_docs"
LIMIT = 50  # Upload first 50 files

stats = {'uploaded': 0, 'failed': 0, 'total': 0}

def upload_document(file_path: Path) -> bool:
    """Upload a single document."""
    try:
        file_size_mb = file_path.stat().st_size / (1024 * 1024)
        
        with open(file_path, 'rb') as f:
            files = {'file': (file_path.name, f)}
            data = {'title': file_path.stem}
            
            logger.info(f"Uploading: {file_path.name} ({file_size_mb:.2f} MB)")
            
            response = requests.post(
                KNOWLEDGE_INGEST_ENDPOINT,
                files=files,
                data=data,
                timeout=600
            )
            
            if response.status_code == 200:
                result = response.json()
                chunks = result.get('total_chunks', result.get('chunks_created', 0))
                logger.info(f"  ✓ Success: {chunks} chunks created")
                stats['uploaded'] += 1
                return True
            else:
                logger.error(f"  ✗ Failed: HTTP {response.status_code}")
                stats['failed'] += 1
                return False
                
    except Exception as e:
        logger.error(f"  ✗ Error: {e}")
        stats['failed'] += 1
        return False

def main():
    logger.info("=" * 80)
    logger.info("KNOWLEDGE BASE UPLOAD (INTERNAL)")
    logger.info("=" * 80)
    
    # Collect documents
    docs_path = Path(DOCS_DIR)
    if not docs_path.exists():
        logger.error(f"Directory not found: {DOCS_DIR}")
        logger.info("Please mount your documents to /app/uploads/legal_docs in the container")
        return
    
    documents = []
    for ext in ['.pdf', '.docx', '.doc']:
        documents.extend(list(docs_path.rglob(f"*{ext}")))
    
    logger.info(f"Found {len(documents)} documents")
    
    # Limit to first N files
    if len(documents) > LIMIT:
        logger.info(f"⚠ Limiting to first {LIMIT} files")
        documents = documents[:LIMIT]
    
    stats['total'] = len(documents)
    
    if stats['total'] == 0:
        logger.warning("No documents found!")
        return
    
    # Upload
    logger.info(f"\nUploading {stats['total']} documents...")
    logger.info("-" * 80)
    
    for i, doc_path in enumerate(documents, 1):
        logger.info(f"\n[{i}/{stats['total']}]")
        upload_document(doc_path)
    
    # Summary
    logger.info("\n" + "=" * 80)
    logger.info("SUMMARY")
    logger.info("=" * 80)
    logger.info(f"Total:    {stats['total']}")
    logger.info(f"✓ Uploaded: {stats['uploaded']}")
    logger.info(f"✗ Failed:   {stats['failed']}")
    logger.info(f"Success:  {(stats['uploaded'] / stats['total'] * 100) if stats['total'] > 0 else 0:.1f}%")
    logger.info("=" * 80)

if __name__ == "__main__":
    main()
