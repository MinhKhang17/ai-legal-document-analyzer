"""RAG context retrieval service.

Retrieves grounded legal context from the Neo4j knowledge graph. Until a real
vector index is wired in, retrieval combines:
  * Graph traversal (contract type -> risks, clause -> risks, mitigations).
  * Lexical similarity scoring (EmbeddingService) to rank by relevance.

This guarantees /rag/retrieve returns real graph-backed context, not mock data.
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from app.core.errors import AppError, RagRetrievalError
from app.graph import schema
from app.graph.service import GraphService
from app.services.embedding_service import EmbeddingService, get_embedding_service


class RagService:
    def __init__(
        self,
        graph_service: Optional[GraphService] = None,
        embedding_service: Optional[EmbeddingService] = None,
    ) -> None:
        self.graph = graph_service or GraphService()
        self.embeddings = embedding_service or get_embedding_service()

    # --- /rag/retrieve --------------------------------------------------------

    def retrieve(
        self,
        query: str,
        contract_type: Optional[str],
        top_k: int,
        risk_filters: List[str],
        clause_filters: List[str],
    ) -> Dict[str, Any]:
        try:
            candidates: List[Dict[str, Any]] = []
            seen: set[str] = set()

            def add(item: Dict[str, Any]) -> None:
                key = f"{item['type']}::{item['id']}"
                if key not in seen:
                    seen.add(key)
                    candidates.append(item)

            # 1) Risks tied to the contract type.
            if contract_type and contract_type in schema.CONTRACT_TYPES:
                for row in self.graph.get_risks_for_contract(contract_type):
                    recs = [r for r in row.get("recommendations", []) if r]
                    add(
                        {
                            "id": row["code"],
                            "type": "RiskType",
                            "title": row["code"],
                            "content": row.get("description", ""),
                            "score": 0.0,
                            "metadata": {
                                "contractType": contract_type,
                                "recommendations": recs,
                            },
                        }
                    )

            # 2) Risks tied to detected/filtered clause types.
            if clause_filters:
                for row in self.graph.get_clause_risks(clause_filters):
                    recs = [r for r in row.get("recommendations", []) if r]
                    add(
                        {
                            "id": row["code"],
                            "type": "RiskType",
                            "title": row["code"],
                            "content": row.get("description", ""),
                            "score": 0.0,
                            "metadata": {
                                "clauseType": row.get("clause"),
                                "recommendations": recs,
                            },
                        }
                    )

            # 3) Explicit risk filters.
            for risk_code in risk_filters:
                if risk_code in schema.RISK_TYPES:
                    recs = self.graph.recommendations_for_risk(risk_code)
                    add(
                        {
                            "id": risk_code,
                            "type": "RiskType",
                            "title": risk_code,
                            "content": schema.RISK_TYPES[risk_code],
                            "score": 0.0,
                            "metadata": {"recommendations": recs},
                        }
                    )

            # 4) Keyword search across knowledge nodes for broader recall.
            for row in self.graph.search_knowledge(query, limit=top_k * 2):
                node = row.get("node", {})
                props = node.get("properties", {})
                labels = row.get("labels", [])
                node_type = labels[0] if labels else "LegalConcept"
                code = props.get("code") or props.get("name") or node.get("id", "")
                content = props.get("description") or props.get("content", "")
                add(
                    {
                        "id": code,
                        "type": node_type,
                        "title": props.get("name") or code,
                        "content": content,
                        "score": 0.0,
                        "metadata": {"source": "keyword"},
                    }
                )

            # 4b) Verified legal articles tied to the filtered clauses/risks.
            #     These give the answer a concrete, citable legal basis.
            article_clauses = clause_filters or None
            article_risks = risk_filters or None
            if article_clauses or article_risks:
                for row in self.graph.get_legal_articles(
                    clause_codes=article_clauses,
                    risk_codes=article_risks,
                    limit=top_k,
                ):
                    add(
                        {
                            "id": row["code"],
                            "type": "LegalArticle",
                            "title": row.get("title", row["code"]),
                            "content": row.get("summary", ""),
                            "score": 0.0,
                            "metadata": {
                                "document": row.get("document"),
                                "reference": row.get("reference"),
                                "clauses": row.get("clauses", []),
                                "risks": row.get("risks", []),
                            },
                        }
                    )

            # 5) Score by semantic similarity to the query and sort.
            #    Use a single batched embedding call for all candidates.
            if candidates:
                texts = [f"{c['title']} {c['content']}" for c in candidates]
                scores = self.embeddings.rank(query, texts)
                for item, score in zip(candidates, scores):
                    item["score"] = score

            candidates.sort(key=lambda x: x["score"], reverse=True)
            top = candidates[: max(top_k, 1)]

            return {"query": query, "items": top}
        except AppError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise RagRetrievalError(
                "Failed to retrieve RAG context", details={"cause": str(exc)}
            ) from exc

    # --- /rag/contract-context ------------------------------------------------

    def contract_context(
        self,
        contract_type: str,
        question: Optional[str],
        detected_clause_types: List[str],
    ) -> Dict[str, Any]:
        try:
            required = self.graph.get_required_clauses(contract_type)
            detected = set(detected_clause_types)
            required_checks = [
                {
                    "clauseType": row["code"],
                    "title": row.get("description", row["code"]),
                    "present": row["code"] in detected,
                    "description": row.get("description", ""),
                }
                for row in required
            ]

            related_risks: List[Dict[str, Any]] = []
            for row in self.graph.get_risks_for_contract(contract_type):
                related_risks.append(
                    {
                        "id": row["code"],
                        "type": "RiskType",
                        "title": row["code"],
                        "content": row.get("description", ""),
                        "score": 0.0,
                        "metadata": {
                            "recommendations": [r for r in row.get("recommendations", []) if r]
                        },
                    }
                )

            recommendations: List[Dict[str, Any]] = []
            seen_rec: set[str] = set()
            for risk in related_risks:
                for rec in risk["metadata"].get("recommendations", []):
                    if rec and rec not in seen_rec:
                        seen_rec.add(rec)
                        recommendations.append(
                            {
                                "id": f"REC::{len(recommendations)}",
                                "type": "Recommendation",
                                "title": "Khuyến nghị",
                                "content": rec,
                                "score": 0.0,
                                "metadata": {"forRisk": risk["id"]},
                            }
                        )

            return {
                "contractType": contract_type,
                "requiredChecks": required_checks,
                "relatedRisks": related_risks,
                "recommendations": recommendations,
            }
        except AppError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise RagRetrievalError(
                "Failed to build contract context", details={"cause": str(exc)}
            ) from exc
