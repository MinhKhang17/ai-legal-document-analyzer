"""Pydantic models for the Graph API (nodes, relationships, queries)."""
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


# --- Requests -----------------------------------------------------------------


class CreateNodeRequest(BaseModel):
    label: str = Field(..., min_length=1, description="Node label, e.g. LegalConcept")
    properties: Dict[str, Any] = Field(
        default_factory=dict, description="Node properties map"
    )


class CreateRelationshipRequest(BaseModel):
    fromNodeId: str = Field(..., description="Source node element id")
    toNodeId: str = Field(..., description="Target node element id")
    type: str = Field(..., min_length=1, description="Relationship type, e.g. RELATED_TO")
    properties: Dict[str, Any] = Field(default_factory=dict)


class GraphQueryRequest(BaseModel):
    cypher: str = Field(..., min_length=1, description="Raw Cypher statement")
    params: Dict[str, Any] = Field(default_factory=dict)


# --- Responses ----------------------------------------------------------------


class NodeModel(BaseModel):
    id: str = Field(..., description="Neo4j element id")
    label: str
    labels: List[str] = Field(default_factory=list)
    properties: Dict[str, Any] = Field(default_factory=dict)


class RelationshipModel(BaseModel):
    id: str
    type: str
    fromNodeId: str
    toNodeId: str
    properties: Dict[str, Any] = Field(default_factory=dict)


class NodeListResponse(BaseModel):
    items: List[NodeModel] = Field(default_factory=list)
    count: int = 0


class GraphQueryResponse(BaseModel):
    columns: List[str] = Field(default_factory=list)
    rows: List[Dict[str, Any]] = Field(default_factory=list)
    count: int = 0


class DeleteNodeResponse(BaseModel):
    id: str
    deleted: bool


class SeedResultResponse(BaseModel):
    contractTypes: int = 0
    riskTypes: int = 0
    clauseTypes: int = 0
    relationships: int = 0
    recommendations: int = 0
    message: str = "Seed completed"
