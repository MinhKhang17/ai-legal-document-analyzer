"""Graph API: node/relationship CRUD, raw query, and baseline seed.

The raw /graph/query endpoint and the seed endpoint are internal/dev-only and
guarded by require_internal_key (active only when AI_SERVICE_API_KEY is set).
"""
from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.api.deps import get_trace_id, require_internal_key
from app.graph.service import GraphService
from app.models.common import success_payload
from app.models.graph_models import (
    CreateNodeRequest,
    CreateRelationshipRequest,
    GraphQueryRequest,
)

router = APIRouter(prefix="/graph", tags=["graph"])


def get_graph_service() -> GraphService:
    return GraphService()


@router.post("/nodes")
def create_node(
    req: CreateNodeRequest,
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    data = svc.create_node(req.label, req.properties)
    return success_payload(data, "Node created", trace_id)


@router.get("/nodes")
def list_nodes(
    label: Optional[str] = Query(default=None),
    keyword: Optional[str] = Query(default=None),
    limit: int = Query(default=50, ge=1, le=1000),
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    data = svc.find_nodes(label, keyword, limit)
    return success_payload(data, "Nodes fetched", trace_id)


@router.get("/nodes/{node_id}")
def get_node(
    node_id: str,
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    data = svc.get_node(node_id)
    return success_payload(data, "Node fetched", trace_id)


@router.delete("/nodes/{node_id}")
def delete_node(
    node_id: str,
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    data = svc.delete_node(node_id)
    return success_payload(data, "Node deleted", trace_id)


@router.post("/relationships")
def create_relationship(
    req: CreateRelationshipRequest,
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    data = svc.create_relationship(
        req.fromNodeId, req.toNodeId, req.type, req.properties
    )
    return success_payload(data, "Relationship created", trace_id)


@router.post("/query", dependencies=[Depends(require_internal_key)])
def run_query(
    req: GraphQueryRequest,
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    """Run a raw Cypher query. Internal/dev use only."""
    data = svc.run_query(req.cypher, req.params)
    return success_payload(data, "Query executed", trace_id)


@router.post("/seed/legal-baseline", dependencies=[Depends(require_internal_key)])
def seed_legal_baseline(
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    """Idempotently seed the legal knowledge-graph baseline (MERGE-based)."""
    data = svc.seed_legal_baseline()
    return success_payload(data, "Legal baseline seeded", trace_id)


@router.post("/seed/legal-corpus", dependencies=[Depends(require_internal_key)])
def seed_legal_corpus(
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    """Idempotently seed verified legal documents + articles and link them to
    the baseline ClauseType/RiskType nodes. Run after legal-baseline."""
    data = svc.seed_legal_corpus()
    return success_payload(data, "Legal corpus seeded", trace_id)


@router.post("/seed/all", dependencies=[Depends(require_internal_key)])
def seed_all(
    trace_id: str = Depends(get_trace_id),
    svc: GraphService = Depends(get_graph_service),
):
    """Seed baseline taxonomy then the legal corpus in one call."""
    baseline = svc.seed_legal_baseline()
    corpus = svc.seed_legal_corpus()
    return success_payload(
        {"baseline": baseline, "corpus": corpus}, "All seeds completed", trace_id
    )
