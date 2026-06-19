"""Script để extract ZIP files và import PDF/DOCX vào Knowledge Base.

Usage:
    python scripts/import_from_zip.py --source "C:/Users/DELL/Documents/VBPLHHL"
"""
import argparse
import logging
import shutil
import tempfile
import time
import zipfile
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Any

import requests
from tqdm import tqdm

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('import_from_zip.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class ZipImporter:
    """Import documents từ ZIP files."""
    
    SUPPORTED_EXTENSIONS = ['.pdf', '.docx', '.doc', '.txt']
    
    def __init__(self, api_base: str = "http://localhost:8000", max_workers: int = 3):
        self.api_base = api_base
        self.max_workers = max_workers
        self.temp_dir = Path(tempfile.mkdtemp(prefix="vbpl_extract_"))
        self.results: Dict[str, Any] = {
            "success": [],
            "failed": [],
            "skipped": [],
        }
    
    def __del__(self):
        """Cleanup temp directory."""
        try:
            if self.temp_dir.exists():
                shutil.rmtree(self.temp_dir)
                logger.info(f"Cleaned up temp dir: {self.temp_dir}")
        except Exception as e:
            logger.warning(f"Failed to cleanup temp dir: {e}")
    
    def find_zip_files(self, source_dir: Path) -> List[Path]:
        """Tìm tất cả file ZIP."""
        logger.info(f"Scanning for ZIP files in: {source_dir}")
        
        zip_files = list(source_dir.rglob("*.zip"))
        
        logger.info(f"Found {len(zip_files)} ZIP files")
        return zip_files
    
    def extract_zip(self, zip_path: Path) -> List[Path]:
        """Extract ZIP và return danh sách documents."""
        extract_dir = self.temp_dir / zip_path.stem
        extract_dir.mkdir(parents=True, exist_ok=True)
        
        try:
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(extract_dir)
            
            # Tìm tất cả documents trong extracted folder
            doc_files = []
            for ext in self.SUPPORTED_EXTENSIONS:
                doc_files.extend(extract_dir.rglob(f"*{ext}"))
            
            logger.info(f"Extracted {len(doc_files)} documents from {zip_path.name}")
            return doc_files
            
        except Exception as e:
            logger.error(f"Failed to extract {zip_path.name}: {e}")
            return []
    
    def extract_title_from_filename(self, file_path: Path) -> str:
        """Extract title từ tên file."""
        title = file_path.stem
        title = title.replace("_", " ")
        title = title.replace("-", " ")
        
        if len(title) > 200:
            title = title[:200]
        
        return title.strip()
    
    def import_single_file(
        self,
        file_path: Path,
        title: str | None = None,
        use_v2: bool = True,
        retry: int = 2,
    ) -> Dict[str, Any]:
        """Import 1 file vào knowledge base."""
        
        if title is None:
            title = self.extract_title_from_filename(file_path)
        
        endpoint = "/admin/risk-knowledge/import-v2" if use_v2 else "/admin/risk-knowledge/import"
        url = f"{self.api_base}{endpoint}"
        
        # Detect content type
        ext = file_path.suffix.lower()
        content_types = {
            '.pdf': 'application/pdf',
            '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            '.doc': 'application/msword',
            '.txt': 'text/plain',
        }
        content_type = content_types.get(ext, 'application/octet-stream')
        
        for attempt in range(retry + 1):
            try:
                with open(file_path, "rb") as f:
                    files = {"file": (file_path.name, f, content_type)}
                    data = {"title": title}
                    
                    response = requests.post(url, files=files, data=data, timeout=180)
                
                if response.status_code == 200:
                    result = response.json()
                    logger.info(f"✅ Imported: {file_path.name} ({result.get('chunks_created', 0)} chunks)")
                    return {
                        "status": "success",
                        "file": str(file_path),
                        "title": title,
                        "result": result,
                    }
                else:
                    error_msg = f"HTTP {response.status_code}: {response.text[:200]}"
                    logger.warning(f"❌ Failed to import {file_path.name}: {error_msg}")
                    
                    if attempt < retry:
                        logger.info(f"   Retrying ({attempt + 1}/{retry})...")
                        time.sleep(2)
                        continue
                    
                    return {
                        "status": "failed",
                        "file": str(file_path),
                        "title": title,
                        "error": error_msg,
                    }
                    
            except Exception as e:
                error_msg = f"{type(e).__name__}: {str(e)}"
                logger.error(f"❌ Error importing {file_path.name}: {error_msg}")
                
                if attempt < retry:
                    logger.info(f"   Retrying ({attempt + 1}/{retry})...")
                    time.sleep(2)
                    continue
                
                return {
                    "status": "failed",
                    "file": str(file_path),
                    "title": title,
                    "error": error_msg,
                }
        
        return {
            "status": "failed",
            "file": str(file_path),
            "title": title,
            "error": "Max retries exceeded",
        }
    
    def process_zip_files(
        self,
        zip_files: List[Path],
        use_v2: bool = True,
        limit: int | None = None,
    ) -> None:
        """Process tất cả ZIP files."""
        
        if limit and limit > 0:
            logger.info(f"Limiting to first {limit} ZIP files")
            zip_files = zip_files[:limit]
        
        logger.info(f"Processing {len(zip_files)} ZIP files...")
        
        # Step 1: Extract all ZIPs
        print("\n" + "=" * 70)
        print("📦 EXTRACTING ZIP FILES")
        print("=" * 70)
        
        all_doc_files = []
        
        with tqdm(total=len(zip_files), desc="Extracting", unit="zip") as pbar:
            for zip_file in zip_files:
                doc_files = self.extract_zip(zip_file)
                all_doc_files.extend(doc_files)
                pbar.update(1)
        
        if not all_doc_files:
            logger.warning("No documents found in ZIP files!")
            return
        
        print(f"\n✅ Extracted {len(all_doc_files)} documents from {len(zip_files)} ZIPs")
        
        # Step 2: Import all documents
        print("\n" + "=" * 70)
        print("📤 IMPORTING DOCUMENTS")
        print("=" * 70)
        
        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            future_to_file = {
                executor.submit(self.import_single_file, doc_file, use_v2=use_v2): doc_file
                for doc_file in all_doc_files
            }
            
            with tqdm(total=len(all_doc_files), desc="Importing", unit="file") as pbar:
                for future in as_completed(future_to_file):
                    result = future.result()
                    
                    if result["status"] == "success":
                        self.results["success"].append(result)
                    elif result["status"] == "failed":
                        self.results["failed"].append(result)
                    else:
                        self.results["skipped"].append(result)
                    
                    pbar.update(1)
        
        self.print_summary()
    
    def print_summary(self) -> None:
        """In tổng kết."""
        print("\n" + "=" * 70)
        print("📊 IMPORT SUMMARY")
        print("=" * 70)
        
        total = len(self.results["success"]) + len(self.results["failed"]) + len(self.results["skipped"])
        
        print(f"✅ Success: {len(self.results['success'])}/{total}")
        print(f"❌ Failed:  {len(self.results['failed'])}/{total}")
        print(f"⏭️  Skipped: {len(self.results['skipped'])}/{total}")
        
        if self.results["success"]:
            total_chunks = sum(
                r.get("result", {}).get("chunks_created", 0)
                for r in self.results["success"]
            )
            print(f"\n📦 Total chunks created: {total_chunks}")
        
        if self.results["failed"]:
            print(f"\n❌ Failed files: {len(self.results['failed'])}")
            for idx, result in enumerate(self.results["failed"][:10], 1):
                filename = Path(result['file']).name
                print(f"   {idx}. {filename}")
                print(f"      Error: {result.get('error', 'Unknown')[:100]}")
            
            if len(self.results["failed"]) > 10:
                print(f"   ... and {len(self.results['failed']) - 10} more")
        
        print("\n💾 Full log saved to: import_from_zip.log")
        print("=" * 70)
    
    def save_results_json(self, output_file: Path) -> None:
        """Lưu kết quả."""
        import json
        
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(self.results, f, indent=2, ensure_ascii=False)
        
        logger.info(f"Results saved to: {output_file}")


def main():
    parser = argparse.ArgumentParser(description="Import văn bản từ ZIP files")
    
    parser.add_argument(
        "--source",
        type=str,
        required=True,
        help="Folder chứa ZIP files"
    )
    parser.add_argument(
        "--api-base",
        type=str,
        default="http://localhost:8000",
        help="API base URL"
    )
    parser.add_argument(
        "--max-workers",
        type=int,
        default=3,
        help="Số threads (default: 3)"
    )
    parser.add_argument(
        "--use-v1",
        action="store_true",
        help="Use v1 API"
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Limit số ZIP files (for testing)"
    )
    parser.add_argument(
        "--output",
        type=str,
        default="import_zip_results.json",
        help="Output JSON file"
    )
    
    args = parser.parse_args()
    
    # Validate
    source_dir = Path(args.source)
    if not source_dir.exists():
        logger.error(f"Source directory does not exist: {source_dir}")
        return
    
    # Create importer
    importer = ZipImporter(
        api_base=args.api_base,
        max_workers=args.max_workers,
    )
    
    # Find ZIPs
    zip_files = importer.find_zip_files(source_dir)
    
    if not zip_files:
        logger.warning("No ZIP files found!")
        return
    
    # Start
    print("\n" + "=" * 70)
    print("🚀 STARTING ZIP IMPORT")
    print("=" * 70)
    print(f"Source:      {source_dir}")
    print(f"API:         {args.api_base}")
    print(f"ZIP files:   {len(zip_files)}")
    print(f"Max workers: {args.max_workers}")
    print(f"API version: {'v1' if args.use_v1 else 'v2'}")
    print("=" * 70)
    
    start_time = time.time()
    
    try:
        importer.process_zip_files(
            zip_files=zip_files,
            use_v2=not args.use_v1,
            limit=args.limit,
        )
        
        # Save results
        output_file = Path(args.output)
        importer.save_results_json(output_file)
        
    except KeyboardInterrupt:
        print("\n\n⚠️  Import interrupted by user")
        importer.print_summary()
    
    elapsed = time.time() - start_time
    print(f"\n⏱️  Total time: {elapsed:.2f} seconds")
    
    if importer.results["success"]:
        docs_count = len(importer.results["success"])
        print(f"📈 Average: {elapsed / docs_count:.2f} seconds/document\n")


if __name__ == "__main__":
    main()
