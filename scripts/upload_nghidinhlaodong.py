import os
import requests
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
SOURCE_DIR = r"C:\Users\DELL\Documents\nghidinhlaodong"
SUPPORTED_EXTENSIONS = {'.docx', '.doc', '.txt'}

def get_mime_type(file_path: Path) -> str:
    """Get MIME type based on file extension."""
    ext = file_path.suffix.lower()
    mime_types = {
        '.pdf': 'application/pdf',
        '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        '.doc': 'application/msword',
        '.txt': 'text/plain',
        '.md': 'text/plain'
    }
    return mime_types.get(ext, 'application/octet-stream')

def upload_to_endpoint(file_path: Path, endpoint: str, version_label: str) -> bool:
    """Upload file to a specific endpoint."""
    title = file_path.stem
    try:
        file_size_mb = file_path.stat().st_size / (1024 * 1024)
        with open(file_path, 'rb') as f:
            # Read first 8 bytes to check if it's actually an old OLE2 .doc file
            magic = f.read(8)
            f.seek(0)
            
            is_old_doc = (magic == b'\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1')
            if is_old_doc and file_path.suffix.lower() == '.docx':
                upload_name = file_path.stem + ".doc"
                mime_type = 'application/msword'
                logger.info(f"  ℹ Detected OLE2 .doc format in .docx file, renaming to: {upload_name}")
            else:
                upload_name = file_path.name
                mime_type = get_mime_type(file_path)

            files = {
                'file': (upload_name, f, mime_type)
            }
            data = {
                'title': title
            }
            logger.info(f"Uploading to {version_label}: {upload_name} ({file_size_mb:.2f} MB)")
            
            # 5 minutes timeout
            response = requests.post(
                endpoint,
                files=files,
                data=data,
                timeout=300
            )
            
            if response.status_code == 200:
                result = response.json()
                chunks = result.get('total_chunks', result.get('chunks_created', 0))
                logger.info(f"  ✓ Success ({version_label}): {chunks} chunks created")
                return True
            else:
                error_text = response.text[:300] if response.text else "No error message"
                logger.error(f"  ✗ Failed ({version_label}): HTTP {response.status_code} - {error_text}")
                return False
    except Exception as e:
        logger.error(f"  ✗ Error uploading {file_path.name} to {version_label}: {e}")
        return False

def main():
    logger.info("=" * 80)
    logger.info("UPLOADING LABOR DECREE DOCUMENTS TO NEO4J")
    logger.info("=" * 80)
    
    source_path = Path(SOURCE_DIR)
    if not source_path.exists():
        logger.error(f"Source directory not found: {SOURCE_DIR}")
        return
        
    documents = []
    for ext in SUPPORTED_EXTENSIONS:
        documents.extend(list(source_path.glob(f"*{ext}")))
        
    if not documents:
        logger.warning("No supported documents found in the folder.")
        return
        
    logger.info(f"Found {len(documents)} documents to upload.")
    
    for i, doc_path in enumerate(documents, 1):
        logger.info(f"\n[{i}/{len(documents)}] Processing: {doc_path.name}")
        
        # Upload to V1
        v1_success = upload_to_endpoint(doc_path, INGEST_V1_ENDPOINT, "V1 Ingestion")
        
        # Upload to V2
        v2_success = upload_to_endpoint(doc_path, INGEST_V2_ENDPOINT, "V2 Ingestion")
        
        # Slight delay to avoid hammering the service
        time.sleep(1)
        
    logger.info("\n" + "=" * 80)
    logger.info("UPLOAD COMPLETED")
    logger.info("=" * 80)

if __name__ == "__main__":
    main()
