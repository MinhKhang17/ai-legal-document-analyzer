"""
Script to upload legal documents to knowledge base.
This will collect all PDF, DOCX, DOC files from the extracted VBPL directory
and upload them to AI service's knowledge ingestion endpoint.

Also uploads FAQ knowledge base files from the data directory.
"""
import os
import sys
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
SOURCE_DIR = r"C:\Users\DELL\Documents\VBPL\extracted"
FAQ_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ai-service", "data")
SUPPORTED_EXTENSIONS = {'.pdf', '.docx', '.doc', '.txt'}
SKIP_FILES = {'Template.pdf'}  # Skip known template files

# Statistics
stats = {
    'total_files': 0,
    'uploaded': 0,
    'failed': 0,
    'skipped': 0,
    'already_ingested': 0
}


def collect_documents(base_dir: str) -> list[Path]:
    """Collect all PDF, DOCX, DOC files from directory, excluding templates."""
    logger.info(f"Collecting documents from: {base_dir}")

    documents = []
    for ext in SUPPORTED_EXTENSIONS:
        files = list(Path(base_dir).rglob(f"*{ext}"))
        # Filter out Template.pdf and other skip files
        files = [f for f in files if f.name not in SKIP_FILES]
        documents.extend(files)
        if files:
            logger.info(f"  Found {len(files)} {ext} files")

    logger.info(f"Total documents collected: {len(documents)}")
    return documents


def collect_faq_files(faq_dir: str) -> list[Path]:
    """Collect FAQ knowledge base files."""
    logger.info(f"Collecting FAQ files from: {faq_dir}")
    faq_path = Path(faq_dir)
    if not faq_path.exists():
        logger.warning(f"FAQ directory not found: {faq_dir}")
        return []

    faqs = list(faq_path.glob("FAQ_*.txt")) + list(faq_path.glob("FAQ_*.md"))
    logger.info(f"Found {len(faqs)} FAQ files")
    return faqs


def check_already_ingested(title: str) -> bool:
    """Check if a document with this title already exists in the knowledge base."""
    try:
        response = requests.post(
            f"{AI_SERVICE_URL}/api/knowledge/search",
            json={"query": title, "top_k": 1},
            timeout=10
        )
        if response.status_code == 200:
            results = response.json()
            if results and len(results) > 0:
                for chunk in results:
                    if chunk.get("title", "").strip().lower() == title.strip().lower():
                        return True
        return False
    except Exception:
        return False


def upload_document(file_path: Path, skip_check: bool = False) -> bool:
    """Upload a single document to knowledge base."""
    title = file_path.stem
    max_retries = 2
    retry_count = 0

    # Check if already ingested (optional)
    if not skip_check and check_already_ingested(title):
        logger.info(f"  ⊘ Already ingested: {file_path.name}")
        stats['already_ingested'] += 1
        return True

    while retry_count < max_retries:
        try:
            # Check file size
            file_size_mb = file_path.stat().st_size / (1024 * 1024)

            # Skip very large files (>50MB)
            if file_size_mb > 50:
                logger.warning(f"  ⊘ Skipping large file ({file_size_mb:.1f} MB): {file_path.name}")
                stats['skipped'] += 1
                return False

            # Prepare file and metadata
            with open(file_path, 'rb') as f:
                files = {
                    'file': (file_path.name, f, get_mime_type(file_path))
                }
                data = {
                    'title': title
                }

                logger.info(f"Uploading: {file_path.name} ({file_size_mb:.2f} MB)")

                # Dynamic timeout: 10 minutes + 2 minutes per MB
                timeout = max(600, int(600 + file_size_mb * 120))

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
                    error_text = response.text[:300] if response.text else "No error message"
                    logger.error(f"  ✗ Failed: HTTP {response.status_code} - {error_text}")
                    stats['failed'] += 1
                    return False

        except requests.exceptions.Timeout:
            retry_count += 1
            if retry_count < max_retries:
                logger.warning(f"  ⚠ Timeout (attempt {retry_count}/{max_retries}), retrying in 10s...")
                time.sleep(10)
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
        '.txt': 'text/plain',
        '.md': 'text/plain'
    }
    return mime_types.get(ext, 'application/octet-stream')


def check_service_health() -> bool:
    """Check if AI service is running."""
    try:
        response = requests.get(f"{AI_SERVICE_URL}/", timeout=5)
        return response.status_code == 200
    except Exception:
        return False


def main():
    """Main execution function."""
    logger.info("=" * 80)
    logger.info("KNOWLEDGE BASE UPLOAD SCRIPT - FULL INGESTION")
    logger.info("=" * 80)

    # Check AI service health
    logger.info("\n1. Checking AI service health...")
    if check_service_health():
        logger.info("   ✓ AI service is running")
    else:
        logger.warning("   ⚠ Cannot confirm AI service health, will try to upload anyway")

    # Collect FAQ files first (small, quick to upload)
    logger.info("\n2. Collecting FAQ knowledge files...")
    faq_files = collect_faq_files(FAQ_DIR)

    # Collect legal documents from VBPL
    logger.info("\n3. Collecting legal documents from VBPL...")
    legal_docs = collect_documents(SOURCE_DIR)

    # Combine all documents: FAQs first, then legal docs
    all_documents = faq_files + legal_docs
    stats['total_files'] = len(all_documents)

    if stats['total_files'] == 0:
        logger.warning("⚠ No documents found!")
        return

    logger.info(f"\n4. Uploading {stats['total_files']} documents to knowledge base...")
    logger.info(f"   - {len(faq_files)} FAQ files")
    logger.info(f"   - {len(legal_docs)} legal documents")
    logger.info("-" * 80)

    for i, doc_path in enumerate(all_documents, 1):
        logger.info(f"\n[{i}/{stats['total_files']}]")
        success = upload_document(doc_path)

        # Delay between uploads: 1 second on success, 3 seconds on failure
        time.sleep(1 if success else 3)

    # Print summary
    logger.info("\n" + "=" * 80)
    logger.info("UPLOAD SUMMARY")
    logger.info("=" * 80)
    logger.info(f"Total files:         {stats['total_files']}")
    logger.info(f"✓ Uploaded:          {stats['uploaded']}")
    logger.info(f"⊘ Already ingested:  {stats['already_ingested']}")
    logger.info(f"✗ Failed:            {stats['failed']}")
    logger.info(f"⊘ Skipped:           {stats['skipped']}")
    success_rate = ((stats['uploaded'] + stats['already_ingested']) / stats['total_files'] * 100) if stats['total_files'] > 0 else 0
    logger.info(f"Success rate:        {success_rate:.1f}%")
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
