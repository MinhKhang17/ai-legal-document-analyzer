"""Neo4j data-access layer.

Pure database operations only: no HTTP concerns, no response envelopes. All
methods raise domain errors (GraphQueryError / Neo4jConnectionError) on failure
so the service layer can translate them uniformly.
"""
from typing import Any, Dict, List, Optional

from neo4j import Driver
from neo4j.exceptions import Neo4jError
from neo4j.graph import Node, Relationship

from app.core.errors import GraphQueryError, Neo4jConnectionError, NotFoundError
from app.graph.connection import get_driver


def _serialize_value(value: Any) -> Any:
    """Convert Neo4j graph entities into plain JSON-friendly structures."""
    if isinstance(value, Node):
        return {
            "id": value.element_id,
            "labels": list(value.labels),
            "properties": dict(value),
        }
    if isinstance(value, Relationship):
        return {
            "id": value.element_id,
            "type": value.type,
            "fromNodeId": value.start_node.element_id if value.start_node else None,
            "toNodeId": value.end_node.element_id if value.end_node else None,
            "properties": dict(value),
        }
    if isinstance(value, (list, tuple)):
        return [_serialize_value(v) for v in value]
    if isinstance(value, dict):
        return {k: _serialize_value(v) for k, v in value.items()}
    return value


class GraphRepository:
    def __init__(self, driver: Optional[Driver] = None) -> None:
        self._driver = driver or get_driver()

    # --- generic execution ----------------------------------------------------

    def run(self, cypher: str, params: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """Execute a Cypher statement and return serialized rows."""
        params = params or {}
        try:
            with self._driver.session() as session:
                result = session.run(cypher, params)
                rows: List[Dict[str, Any]] = []
                for record in result:
                    rows.append({k: _serialize_value(v) for k, v in record.items()})
                return rows
        except Neo4jError as exc:
            raise GraphQueryError(
                "Cypher execution failed",
                details={"cause": exc.message, "code": exc.code},
            ) from exc
        except Exception as exc:  # noqa: BLE001 - connection-level failures
            raise Neo4jConnectionError(
                "Failed to execute query against Neo4j", details={"cause": str(exc)}
            ) from exc

    def run_columns(
        self, cypher: str, params: Optional[Dict[str, Any]] = None
    ) -> tuple[List[str], List[Dict[str, Any]]]:
        """Execute a statement returning both column keys and rows."""
        params = params or {}
        try:
            with self._driver.session() as session:
                result = session.run(cypher, params)
                keys = list(result.keys())
                rows = [{k: _serialize_value(v) for k, v in r.items()} for r in result]
                return keys, rows
        except Neo4jError as exc:
            raise GraphQueryError(
                "Cypher execution failed",
                details={"cause": exc.message, "code": exc.code},
            ) from exc
        except Exception as exc:  # noqa: BLE001
            raise Neo4jConnectionError(
                "Failed to execute query against Neo4j", details={"cause": str(exc)}
            ) from exc

    # --- node operations -------------------------------------------------------

    def create_node(self, label: str, properties: Dict[str, Any]) -> Dict[str, Any]:
        cypher = f"CREATE (n:`{label}`) SET n = $props RETURN n"
        rows = self.run(cypher, {"props": properties})
        if not rows:
            raise GraphQueryError("Node creation returned no result")
        return rows[0]["n"]

    def get_node(self, element_id: str) -> Dict[str, Any]:
        cypher = "MATCH (n) WHERE elementId(n) = $id RETURN n"
        rows = self.run(cypher, {"id": element_id})
        if not rows:
            raise NotFoundError(f"Node not found: {element_id}", details={"id": element_id})
        return rows[0]["n"]

    def find_nodes(
        self,
        label: Optional[str] = None,
        keyword: Optional[str] = None,
        limit: int = 50,
    ) -> List[Dict[str, Any]]:
        match_clause = f"MATCH (n:`{label}`)" if label else "MATCH (n)"
        where_clause = ""
        params: Dict[str, Any] = {"limit": limit}
        if keyword:
            where_clause = (
                " WHERE any(k IN keys(n) WHERE toString(n[k]) "
                "CONTAINS $keyword OR toLower(toString(n[k])) CONTAINS toLower($keyword))"
            )
            params["keyword"] = keyword
        cypher = f"{match_clause}{where_clause} RETURN n LIMIT $limit"
        rows = self.run(cypher, params)
        return [r["n"] for r in rows]

    def delete_node(self, element_id: str) -> bool:
        cypher = (
            "MATCH (n) WHERE elementId(n) = $id "
            "WITH n, count(n) AS c DETACH DELETE n RETURN c AS deleted"
        )
        rows = self.run(cypher, {"id": element_id})
        deleted = bool(rows and rows[0].get("deleted", 0) > 0)
        if not deleted:
            raise NotFoundError(f"Node not found: {element_id}", details={"id": element_id})
        return True

    # --- relationship operations -----------------------------------------------

    def create_relationship(
        self,
        from_id: str,
        to_id: str,
        rel_type: str,
        properties: Dict[str, Any],
    ) -> Dict[str, Any]:
        cypher = (
            "MATCH (a) WHERE elementId(a) = $from_id "
            "MATCH (b) WHERE elementId(b) = $to_id "
            f"CREATE (a)-[r:`{rel_type}`]->(b) SET r = $props RETURN r"
        )
        rows = self.run(
            cypher,
            {"from_id": from_id, "to_id": to_id, "props": properties},
        )
        if not rows:
            raise NotFoundError(
                "Source or target node not found for relationship",
                details={"fromNodeId": from_id, "toNodeId": to_id},
            )
        return rows[0]["r"]
