"""Script để import hàng loạt văn bản pháp luật PDF vào Knowledge Base.

Usage:
    python scripts/import_legal_docs.py --source "C:/Users/DELL/Documents/VBPLHHL"
    
Features:
- Scan recursive tất cả PDF trong folder
- Import song song (multi-threading)
- Progress bar
- Error handling và retry
- Lưu log chi tiết
"""
import argparse
import logging
import time
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
        logging.FileHandler('import_legal_docs.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class LegalDocImporter:
    """Importer cho văn bản pháp luật."""
    
    def __init__(self, api_base: str = "http://localhost:8000", max_workers: int = 3):
        self.api_base = api_base
        self.max_workers = max_workers
        self.results: Dict[str, Any] = {
            "success": [],
            "failed": [],
            "skipped": [],
        }
    
    def find_pdf_files(self, source_dir: Path) -> List[Path]:
        """Tìm tất cả file PDF trong folder."""
        logger.info(f"Scanning for PDF files in: {source_dir}")
        
        pdf_files = list(source_dir.rglob("*.pdf"))
        
        logger.info(f"Found {len(pdf_files)} PDF files")
        return pdf_files
    
    def extract_title_from_filename(self, file_path: Path) -> str:
        """Extract title từ tên file."""
        # Remove extension and clean up
        title = file_path.stem
        
        # Clean up common patterns
        title = title.replace("_", " ")
        title = title.replace("-", " ")
        
        # Limit length
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
        """Import 1 file PDF vào knowledge base."""
        
        if title is None:
            title = self.extract_title_from_filename(file_path)
        
        endpoint = "/admin/risk-knowledge/import-v2" if use_v2 else "/admin/risk-knowledge/import"
        url = f"{self.api_base}{endpoint}"
        
        for attempt in range(retry + 1):
            try:
                with open(file_path, "rb") as f:
                    files = {"file": (file_path.name, f, "application/pdf")}
                    data = {"title": title}
                    
                    response = requests.post(url, files=files, data=data, timeout=120)
                
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
    
    def import_batch(
        self,
        pdf_files: List[Path],
        use_v2: bool = True,
        skip_existing: bool = False,
    ) -> None:
        """Import hàng loạt PDF files."""
        
        logger.info(f"Starting batch import of {len(pdf_files)} files...")
        logger.info(f"Max workers: {self.max_workers}")
        logger.info(f"Using API v2: {use_v2}")
        
        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            # Submit all tasks
            future_to_file = {
                executor.submit(self.import_single_file, pdf_file, use_v2=use_v2): pdf_file
                for pdf_file in pdf_files
            }
            
            # Process completed tasks with progress bar
            with tqdm(total=len(pdf_files), desc="Importing", unit="file") as pbar:
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
        """In tổng kết kết quả."""
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
            print("\n❌ Failed files:")
            for idx, result in enumerate(self.results["failed"][:10], 1):
                print(f"   {idx}. {Path(result['file']).name}")
                print(f"      Error: {result.get('error', 'Unknown')[:100]}")
            
            if len(self.results["failed"]) > 10:
                print(f"   ... and {len(self.results['failed']) - 10} more")
        
        print("\n💾 Full log saved to: import_legal_docs.log")
        print("=" * 70)
    
    def save_results_json(self, output_file: Path) -> None:
        """Lưu kết quả ra JSON file."""
        import json
        
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(self.results, f, indent=2, ensure_ascii=False)
        
        logger.info(f"Results saved to: {output_file}")


def main():
    parser = argparse.ArgumentParser(description="Import văn bản pháp luật PDF vào Knowledge Base")
    
    parser.add_argument(
        "--source",
        type=str,
        required=True,
        help="Đường dẫn folder chứa PDF files (e.g., C:/Users/DELL/Documents/VBPLHHL)"
    )
    parser.add_argument(
        "--api-base",
        type=str,
        default="http://localhost:8000",
        help="API base URL (default: http://localhost:8000)"
    )
    parser.add_argument(
        "--max-workers",
        type=int,
        default=3,
        help="Số lượng threads (default: 3)"
    )
    parser.add_argument(
        "--use-v1",
        action="store_true",
        help="Use v1 API instead of v2 (default: v2)"
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Giới hạn số lượng files để import (for testing)"
    )
    parser.add_argument(
        "--output",
        type=str,
        default="import_results.json",
        help="Output JSON file (default: import_results.json)"
    )
    
    args = parser.parse_args()
    
    # Validate source directory
    source_dir = Path(args.source)
    if not source_dir.exists():
        logger.error(f"Source directory does not exist: {source_dir}")
        return
    
    if not source_dir.is_dir():
        logger.error(f"Source is not a directory: {source_dir}")
        return
    
    # Create importer
    importer = LegalDocImporter(
        api_base=args.api_base,
        max_workers=args.max_workers,
    )
    
    # Find PDF files
    pdf_files = importer.find_pdf_files(source_dir)
    
    if not pdf_files:
        logger.warning("No PDF files found!")
        return
    
    # Limit for testing
    if args.limit and args.limit > 0:
        logger.info(f"Limiting to first {args.limit} files (testing mode)")
        pdf_files = pdf_files[:args.limit]
    
    # Start import
    print("\n" + "=" * 70)
    print("🚀 STARTING IMPORT")
    print("=" * 70)
    print(f"Source:      {source_dir}")
    print(f"API:         {args.api_base}")
    print(f"Files:       {len(pdf_files)}")
    print(f"Max workers: {args.max_workers}")
    print(f"API version: {'v1' if args.use_v1 else 'v2'}")
    print("=" * 70 + "\n")
    
    start_time = time.time()
    
    try:
        importer.import_batch(
            pdf_files=pdf_files,
            use_v2=not args.use_v1,
        )
        
        # Save results
        output_file = Path(args.output)
        importer.save_results_json(output_file)
        
    except KeyboardInterrupt:
        print("\n\n⚠️  Import interrupted by user")
        importer.print_summary()
    
    elapsed = time.time() - start_time
    print(f"\n⏱️  Total time: {elapsed:.2f} seconds")
    print(f"📈 Average: {elapsed / len(pdf_files):.2f} seconds/file\n")


if __name__ == "__main__":
    main()
