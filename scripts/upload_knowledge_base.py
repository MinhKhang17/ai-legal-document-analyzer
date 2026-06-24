"""
Script to upload legal documents to knowledge base.
This will extract all PDF and DOCX files from ZIP archives and upload them to AI service.
"""
import os
import zipfile
import requests
from pathlib import Path
import time
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('upload_knowledge_base.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# Configuration
AI_SERVICE_URL = "http://localhost:8000"
KNOWLEDGE_INGEST_ENDPOINT = f"{AI_SERVICE_URL}/api/knowledge/ingest"
SOURCE_DIR = r"C:\Users\DELL\Documents\VBPL"
TEMP_EXTRACT_DIR = r"C:\Users\DELL\Documents\VBPL\extracted"
SUPPORTED_EXTENSIONS = {'.pdf', '.docx', '.doc', '.txt'}

# Statistics
stats = {
    'total_files': 0,
    'uploaded': 0,
    'failed': 0,
    'skipped': 0
}


def extract_all_zips(source_dir: str, extract_dir: str):
    """Extract all ZIP files in source directory."""
    Path(extract_dir).mkdir(parents=True, exist_ok=True)
    
    logger.info(f"Scanning for ZIP files in: {source_dir}")
    
    zip_files = list(Path(source_dir).rglob("*.zip"))
    logger.info(f"Found {len(zip_files)} ZIP files")
    
    for zip_path in zip_files:
        try:
            logger.info(f"Extracting: {zip_path.name}")
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                # Extract to a subfolder named after the zip file
                extract_subfolder = Path(extract_dir) / zip_path.stem
                zip_ref.extractall(extract_subfolder)
                logger.info(f"  Extracted to: {extract_subfolder}")
        except Exception as e:
            logger.error(f"  Failed to extract {zip_path.name}: {e}")


def collect_documents(base_dir: str) -> list[Path]:
    """Collect all PDF, DOCX, DOC, TXT files from directory."""
    logger.info(f"Collecting documents from: {base_dir}")
    
    documents = []
    for ext in SUPPORTED_EXTENSIONS:
        files = list(Path(base_dir).rglob(f"*{ext}"))
        documents.extend(files)
        logger.info(f"  Found {len(files)} {ext} files")
    
    logger.info(f"Total documents collected: {len(documents)}")
    return documents


def upload_document(file_path: Path) -> bool:
    """Upload a single document to knowledge base."""
    max_retries = 2
    retry_count = 0
    
    while retry_count < max_retries:
        try:
            # Check file size
            file_size_mb = file_path.stat().st_size / (1024 * 1024)
            
            # Prepare file and metadata
            with open(file_path, 'rb') as f:
                files = {
                    'file': (file_path.name, f, get_mime_type(file_path))
                }
                data = {
                    'title': file_path.stem  # Use filename without extension as title
                }
                
                logger.info(f"Uploading: {file_path.name} ({file_size_mb:.2f} MB)")
                
                # Dynamic timeout based on file size: 10 minutes + 1 minute per MB
                timeout = max(600, int(600 + file_size_mb * 60))
                
                # Upload to AI service
                response = requests.post(
                    KNOWLEDGE_INGEST_ENDPOINT,
                    files=files,
                    data=data,
                    timeout=timeout
                )
                
                if response.status_code == 200:
                    result = response.json()
                    chunks = result.get('total_chunks', result.get('chunks_created', 0))
                    logger.info(f"  ✓ Success: {chunks} chunks created")
                    stats['uploaded'] += 1
                    return True
                else:
                    logger.error(f"  ✗ Failed: HTTP {response.status_code} - {response.text[:200]}")
                    stats['failed'] += 1
                    return False
                    
        except requests.exceptions.Timeout:
            retry_count += 1
            if retry_count < max_retries:
                logger.warning(f"  ⚠ Timeout (attempt {retry_count}/{max_retries}), retrying...")
                time.sleep(5)
            else:
                logger.error(f"  ✗ Timeout after {max_retries} attempts")
                stats['failed'] += 1
                return False
        except Exception as e:
            logger.error(f"  ✗ Error uploading {file_path.name}: {e}")
            stats['failed'] += 1
            return False
    
    return False


def get_mime_type(file_path: Path) -> str:
    """Get MIME type based on file extension."""
    ext = file_path.suffix.lower()
    mime_types = {
        '.pdf': 'application/pdf',
        '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        '.doc': 'application/msword',
        '.txt': 'text/plain'
    }
    return mime_types.get(ext, 'application/octet-stream')


def check_service_health() -> bool:
    """Check if AI service is running."""
    try:
        response = requests.get(f"{AI_SERVICE_URL}/", timeout=5)
        return response.status_code == 200
    except:
        return False


def main():
    """Main execution function."""
    logger.info("=" * 80)
    logger.info("KNOWLEDGE BASE UPLOAD SCRIPT")
    logger.info("=" * 80)
    
    # Skip health check - just try to upload
    logger.info("\n1. Starting upload process...")
    logger.info(f"   Target endpoint: {KNOWLEDGE_INGEST_ENDPOINT}")
    
    # Extract ZIP files (skip if already extracted)
    logger.info("\n2. Extracting ZIP files...")
    if not Path(TEMP_EXTRACT_DIR).exists() or len(list(Path(TEMP_EXTRACT_DIR).rglob("*.pdf"))) == 0:
        extract_all_zips(SOURCE_DIR, TEMP_EXTRACT_DIR)
    else:
        logger.info("  Already extracted, skipping...")
    
    # Collect all documents from extracted folder first (nested structure)
    logger.info("\n3. Collecting documents...")
    documents = collect_documents(TEMP_EXTRACT_DIR)
    
    # Limit to first 50 files for testing
    LIMIT = 50
    if len(documents) > LIMIT:
        logger.info(f"⚠ Limiting upload to first {LIMIT} files (out of {len(documents)} total)")
        documents = documents[:LIMIT]
    
    stats['total_files'] = len(documents)
    
    if stats['total_files'] == 0:
        logger.warning("⚠ No documents found!")
        return
    
    # Upload documents
    logger.info(f"\n4. Uploading {stats['total_files']} documents to knowledge base...")
    logger.info("-" * 80)
    
    for i, doc_path in enumerate(documents, 1):
        logger.info(f"\n[{i}/{stats['total_files']}]")
        success = upload_document(doc_path)
        
        # Delay between uploads: 2 seconds on success, 5 seconds on failure
        time.sleep(2 if success else 5)
    
    # Print summary
    logger.info("\n" + "=" * 80)
    logger.info("UPLOAD SUMMARY")
    logger.info("=" * 80)
    logger.info(f"Total files:    {stats['total_files']}")
    logger.info(f"✓ Uploaded:     {stats['uploaded']}")
    logger.info(f"✗ Failed:       {stats['failed']}")
    logger.info(f"⊘ Skipped:      {stats['skipped']}")
    logger.info(f"Success rate:   {(stats['uploaded'] / stats['total_files'] * 100) if stats['total_files'] > 0 else 0:.1f}%")
    logger.info("=" * 80)
    
    if stats['failed'] > 0:
        logger.warning(f"\n⚠ {stats['failed']} files failed to upload. Check the log for details.")
    
    logger.info("\n✓ Done! AI now has access to these legal documents.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        logger.info("\n\n⚠ Upload interrupted by user")
    except Exception as e:
        logger.exception(f"\n\n❌ Fatal error: {e}")
