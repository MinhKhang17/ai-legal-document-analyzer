"""Neo4j client for database operations."""
import logging
from typing import List, Dict, Any, Optional
from neo4j import GraphDatabase, Driver
from app.config import settings

logger = logging.getLogger(__name__)


class Neo4jClient:
    """Neo4j database client."""
    
    def __init__(self):
        """Initialize Neo4j client."""
        self.driver: Optional[Driver] = None
        self.uri = settings.neo4j_uri
        self.user = settings.neo4j_user
        self.password = settings.neo4j_password
    
    def connect(self):
        """Establish connection to Neo4j."""
        try:
            self.driver = GraphDatabase.driver(
                self.uri,
                auth=(self.user, self.password)
            )
            logger.info(f"Connected to Neo4j at {self.uri}")
            # Verify connection
            self.driver.verify_connectivity()
        except Exception as e:
            logger.error(f"Failed to connect to Neo4j: {e}")
            raise
    
    def close(self):
        """Close Neo4j connection."""
        if self.driver:
            self.driver.close()
            logger.info("Neo4j connection closed")
    
    def execute_query(self, query: str, parameters: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """Execute a Cypher query and return results."""
        if not self.driver:
            raise RuntimeError("Neo4j driver not initialized. Call connect() first.")
        
        with self.driver.session() as session:
            result = session.run(query, parameters or {})
            return [record.data() for record in result]
    
    def execute_write(self, query: str, parameters: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """Execute a write query."""
        return self.execute_query(query, parameters)
    
    def create_constraints(self):
        """Create necessary constraints for the database."""
        constraints = [
            # Unique constraint for ReviewChecklist
            """
            CREATE CONSTRAINT review_checklist_id_unique IF NOT EXISTS
            FOR (c:ReviewChecklist)
            REQUIRE c.checklist_id IS UNIQUE
            """
        ]
        
        for constraint in constraints:
            try:
                self.execute_write(constraint)
                logger.info("Constraint created successfully")
            except Exception as e:
                logger.warning(f"Constraint already exists or failed: {e}")
    
    def create_vector_indexes(self):
        """Create vector indexes for embeddings."""
        indexes = [
            # Vector index for ReviewChecklist
            f"""
            CREATE VECTOR INDEX review_checklist_embedding_index IF NOT EXISTS
            FOR (c:ReviewChecklist) ON (c.embedding)
            OPTIONS {{
                indexConfig: {{
                    `vector.dimensions`: {settings.embedding_dimension},
                    `vector.similarity_function`: 'cosine'
                }}
            }}
            """
        ]
        
        for index in indexes:
            try:
                self.execute_write(index)
                logger.info("Vector index created successfully")
            except Exception as e:
                logger.warning(f"Vector index already exists or failed: {e}")
    
    def merge_checklist_item(self, item: Dict[str, Any]) -> bool:
        """Merge a checklist item into Neo4j (insert or update)."""
        query = """
        MERGE (c:ReviewChecklist {checklist_id: $checklist_id})
        SET c.source_type = $source_type,
            c.document_type = $document_type,
            c.checklist_layer = $checklist_layer,
            c.category = $category,
            c.title = $title,
            c.query_text = $query_text,
            c.risk_question = $risk_question,
            c.priority = $priority,
            c.version = $version,
            c.is_active = $is_active,
            c.embedding = $embedding
        RETURN c.checklist_id as id
        """
        
        try:
            result = self.execute_write(query, item)
            return len(result) > 0
        except Exception as e:
            logger.error(f"Failed to merge checklist item {item.get('checklist_id')}: {e}")
            return False
    
    def get_active_checklists(self, document_type: str = "ANY", limit: int = 25) -> List[Dict[str, Any]]:
        """Retrieve active checklists for a specific document type."""
        query = """
        MATCH (c:ReviewChecklist)
        WHERE c.source_type = 'REVIEW_CHECKLIST'
          AND c.document_type = $document_type
          AND c.is_active = true
        RETURN c.checklist_id as checklist_id,
               c.category as category,
               c.title as title,
               c.query_text as query_text,
               c.risk_question as risk_question,
               c.priority as priority,
               c.embedding as embedding
        ORDER BY c.priority ASC
        LIMIT $limit
        """
        
        return self.execute_query(query, {"document_type": document_type, "limit": limit})
    
    def search_user_chunks_by_embedding(
        self,
        embedding: List[float],
        user_id: str,
        workspace_id: str,
        document_id: Optional[str] = None,
        top_k: int = 3
    ) -> List[Dict[str, Any]]:
        """Search user document chunks by embedding vector."""
        # Modified query to match actual Neo4j structure
        # Chunks are connected to Documents which have userId
        query = """
        MATCH (doc:Document)-[:HAS_SECTION|HAS_SUBSECTION*0..2]->(chunk:Chunk)
        WHERE doc.userId = $user_id
          AND ($document_id IS NULL OR doc.id = $document_id)
          AND chunk.embedding IS NOT NULL
        WITH chunk, doc,
             vector.similarity.cosine(chunk.embedding, $embedding) AS score
        WHERE score > 0.3
        RETURN chunk.text as text,
               chunk.title as title,
               doc.id as document_id,
               doc.filename as file_name,
               chunk.order as chunk_order,
               score
        ORDER BY score DESC
        LIMIT $top_k
        """
        
        return self.execute_query(query, {
            "embedding": embedding,
            "user_id": user_id,
            "document_id": document_id,
            "top_k": top_k
        })
    
    def search_knowledge_chunks_by_embedding(
        self,
        embedding: List[float],
        top_k: int = 5
    ) -> List[Dict[str, Any]]:
        """Search system knowledge base chunks by embedding vector."""
        # Modified to search in LegalDocument and LegalArticle relationships
        query = """
        MATCH (chunk:Chunk)
        WHERE chunk.embedding IS NOT NULL
          AND EXISTS {
              MATCH (doc:Document)
              WHERE (doc)-[:HAS_SECTION|HAS_SUBSECTION*0..2]->(chunk)
                AND doc.userId IS NULL
          }
        WITH chunk,
             vector.similarity.cosine(chunk.embedding, $embedding) AS score
        WHERE score > 0.3
        RETURN chunk.text as text,
               chunk.title as title,
               chunk.node_id as chunk_id,
               score
        ORDER BY score DESC
        LIMIT $top_k
        """
        
        return self.execute_query(query, {
            "embedding": embedding,
            "top_k": top_k
        })
    
    def check_checklist_exists(self) -> int:
        """Check if any checklist items exist in the database."""
        query = """
        MATCH (c:ReviewChecklist)
        WHERE c.source_type = 'REVIEW_CHECKLIST'
        RETURN count(c) as count
        """
        
        result = self.execute_query(query)
        return result[0]["count"] if result else 0


# Singleton instance
neo4j_client = Neo4jClient()
