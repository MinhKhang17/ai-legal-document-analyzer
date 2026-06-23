"""Service for seeding review checklists into Neo4j."""
import json
import logging
from pathlib import Path
from typing import List, Dict, Any
from app.database.neo4j_client import neo4j_client
from app.services.embedding_service import embedding_service
from app.models.checklist import ReviewChecklistItem

logger = logging.getLogger(__name__)


class ChecklistSeedService:
    """Service for seeding checklist data."""
    
    def __init__(self):
        """Initialize seed service."""
        self.seed_file_path = Path(__file__).parent.parent.parent / "data" / "review_checklist_seed.json"
    
    def load_seed_data(self) -> List[Dict[str, Any]]:
        """Load checklist seed data from JSON file."""
        try:
            with open(self.seed_file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            logger.info(f"Loaded {len(data)} checklist items from seed file")
            return data
        except FileNotFoundError:
            logger.error(f"Seed file not found: {self.seed_file_path}")
            return []
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON in seed file: {e}")
            return []
    
    def generate_embeddings_for_items(self, items: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Generate embeddings for all checklist items."""
        logger.info("Generating embeddings for checklist items...")
        
        for item in items:
            try:
                embedding = embedding_service.embed_checklist_item(
                    title=item["title"],
                    query_text=item["query_text"],
                    risk_question=item["risk_question"]
                )
                item["embedding"] = embedding
            except Exception as e:
                logger.error(f"Failed to generate embedding for {item.get('checklist_id')}: {e}")
                item["embedding"] = None
        
        logger.info("Embeddings generated successfully")
        return items
    
    def seed_checklists(self) -> bool:
        """Seed checklist items into Neo4j."""
        try:
            # Check if checklists already exist
            existing_count = neo4j_client.check_checklist_exists()
            
            if existing_count > 0:
                logger.info(f"Found {existing_count} existing checklist items. Skipping seed.")
                return True
            
            logger.info("No existing checklists found. Starting seed process...")
            
            # Load seed data
            items = self.load_seed_data()
            if not items:
                logger.warning("No seed data to process")
                return False
            
            # Generate embeddings
            items_with_embeddings = self.generate_embeddings_for_items(items)
            
            # Merge into Neo4j
            success_count = 0
            for item in items_with_embeddings:
                if neo4j_client.merge_checklist_item(item):
                    success_count += 1
            
            logger.info(f"Successfully seeded {success_count}/{len(items)} checklist items")
            return success_count > 0
            
        except Exception as e:
            logger.error(f"Failed to seed checklists: {e}")
            return False


# Singleton instance
checklist_seed_service = ChecklistSeedService()


def seed_review_checklists():
    """Main function to seed review checklists on startup."""
    logger.info("=== Starting Review Checklist Seed Process ===")
    
    try:
        # Ensure Neo4j connection
        if not neo4j_client.driver:
            neo4j_client.connect()
        
        # Create constraints and indexes
        neo4j_client.create_constraints()
        neo4j_client.create_vector_indexes()
        
        # Seed checklists
        success = checklist_seed_service.seed_checklists()
        
        if success:
            logger.info("=== Review Checklist Seed Process Completed Successfully ===")
        else:
            logger.warning("=== Review Checklist Seed Process Completed with Warnings ===")
        
        return success
        
    except Exception as e:
        logger.error(f"=== Review Checklist Seed Process Failed: {e} ===")
        return False
