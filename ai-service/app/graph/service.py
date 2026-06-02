"""Graph service layer.

Sits between the API routers and the repository. Responsibilities:
  * Validate labels / relationship types against the known taxonomy.
  * Enforce result limits for raw queries.
  * Provide the idempotent baseline seed.
  * Expose domain queries (lookup of risks / clauses / recommendations) used by
    the RAG and legal-analysis services.
"""
from typing import Any, Dict, List, Optional

from app.core.config import get_settings
from app.core.errors import GraphQueryError, ValidationError
from app.graph import schema
from app.graph.repository import GraphRepository


class GraphService:
    def __init__(self, repository: Optional[GraphRepository] = None) -> None:
        self.repo = repository or GraphRepository()
        self.settings = get_settings()

    # --- node / relationship CRUD ---------------------------------------------

    def create_node(self, label: str, properties: Dict[str, Any]) -> Dict[str, Any]:
        if not label or not label.strip():
            raise ValidationError("Node label is required")
        node = self.repo.create_node(label.strip(), properties or {})
        return self._node_view(node)

    def get_node(self, element_id: str) -> Dict[str, Any]:
        return self._node_view(self.repo.get_node(element_id))

    def find_nodes(
        self, label: Optional[str], keyword: Optional[str], limit: int
    ) -> Dict[str, Any]:
        limit = self._clamp_limit(limit)
        nodes = self.repo.find_nodes(label, keyword, limit)
        items = [self._node_view(n) for n in nodes]
        return {"items": items, "count": len(items)}

    def delete_node(self, element_id: str) -> Dict[str, Any]:
        deleted = self.repo.delete_node(element_id)
        return {"id": element_id, "deleted": deleted}

    def create_relationship(
        self,
        from_id: str,
        to_id: str,
        rel_type: str,
        properties: Dict[str, Any],
    ) -> Dict[str, Any]:
        if not rel_type or not rel_type.strip():
            raise ValidationError("Relationship type is required")
        rel = self.repo.create_relationship(
            from_id, to_id, rel_type.strip(), properties or {}
        )
        return rel

    # --- raw query (internal/dev only) ----------------------------------------

    def run_query(self, cypher: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if not cypher or not cypher.strip():
            raise ValidationError("Cypher statement is required")
        guarded = self._enforce_limit(cypher)
        columns, rows = self.repo.run_columns(guarded, params or {})
        return {"columns": columns, "rows": rows, "count": len(rows)}

    # --- baseline seed (idempotent via MERGE) ---------------------------------

    def seed_legal_baseline(self) -> Dict[str, Any]:
        repo = self.repo

        # 1) Contract types
        for code, desc in schema.CONTRACT_TYPES.items():
            repo.run(
                "MERGE (c:ContractType {code: $code}) "
                "SET c.name = $code, c.description = $desc",
                {"code": code, "desc": desc},
            )

        # 2) Risk types
        for code, desc in schema.RISK_TYPES.items():
            repo.run(
                "MERGE (r:RiskType {code: $code}) "
                "SET r.name = $code, r.description = $desc",
                {"code": code, "desc": desc},
            )

        # 3) Clause types
        for code, desc in schema.CLAUSE_TYPES.items():
            repo.run(
                "MERGE (cl:ClauseType {code: $code}) "
                "SET cl.name = $code, cl.description = $desc",
                {"code": code, "desc": desc},
            )

        # 4) Recommendations (one per risk) + MITIGATED_BY relationship
        for risk_code, rec_text in schema.RISK_RECOMMENDATIONS.items():
            rec_code = f"REC_{risk_code}"
            repo.run(
                "MERGE (rec:Recommendation {code: $rec_code}) SET rec.content = $text",
                {"rec_code": rec_code, "text": rec_text},
            )
            repo.run(
                "MATCH (r:RiskType {code: $risk_code}) "
                "MATCH (rec:Recommendation {code: $rec_code}) "
                "MERGE (r)-[:MITIGATED_BY]->(rec)",
                {"risk_code": risk_code, "rec_code": rec_code},
            )

        rel_count = 0

        # 5) ClauseType -[:APPLIES_TO]-> ContractType + REQUIRED_FOR
        for contract_code, clauses in schema.CONTRACT_REQUIRED_CLAUSES.items():
            for clause_code in clauses:
                repo.run(
                    "MATCH (cl:ClauseType {code: $clause_code}) "
                    "MATCH (c:ContractType {code: $contract_code}) "
                    "MERGE (cl)-[:APPLIES_TO]->(c) "
                    "MERGE (cl)-[:REQUIRED_FOR]->(c)",
                    {"clause_code": clause_code, "contract_code": contract_code},
                )
                rel_count += 1

        # 6) ClauseType -[:HAS_RISK]-> RiskType
        for clause_code, risk_code in schema.CLAUSE_RISK_MAP.items():
            repo.run(
                "MATCH (cl:ClauseType {code: $clause_code}) "
                "MATCH (r:RiskType {code: $risk_code}) "
                "MERGE (cl)-[:HAS_RISK]->(r)",
                {"clause_code": clause_code, "risk_code": risk_code},
            )
            rel_count += 1

        # 7) ContractType -[:HAS_RISK]-> RiskType
        for contract_code, risks in schema.CONTRACT_RISK_MAP.items():
            for risk_code in risks:
                repo.run(
                    "MATCH (c:ContractType {code: $contract_code}) "
                    "MATCH (r:RiskType {code: $risk_code}) "
                    "MERGE (c)-[:HAS_RISK]->(r)",
                    {"contract_code": contract_code, "risk_code": risk_code},
                )
                rel_count += 1

        return {
            "contractTypes": len(schema.CONTRACT_TYPES),
            "riskTypes": len(schema.RISK_TYPES),
            "clauseTypes": len(schema.CLAUSE_TYPES),
            "recommendations": len(schema.RISK_RECOMMENDATIONS),
            "relationships": rel_count,
            "message": "Legal baseline seeded (idempotent)",
        }

    def seed_legal_corpus(self) -> Dict[str, Any]:
        """Seed verified legal documents + articles and link them to the
        existing ClauseType / RiskType nodes. Idempotent (MERGE-based).

        Run after seed_legal_baseline so the clause/risk targets exist.
        """
        from app.graph import legal_corpus as corpus

        repo = self.repo

        for doc in corpus.LEGAL_DOCUMENTS:
            repo.run(
                "MERGE (d:LegalDocument {code: $code}) "
                "SET d.title = $title, d.reference = $reference",
                doc,
            )

        article_count = 0
        link_count = 0
        for art in corpus.LEGAL_ARTICLES:
            repo.run(
                "MERGE (a:LegalArticle {code: $code}) "
                "SET a.title = $title, a.summary = $summary",
                {"code": art["code"], "title": art["title"], "summary": art["summary"]},
            )
            article_count += 1

            # LegalDocument -[:CONTAINS]-> LegalArticle
            repo.run(
                "MATCH (d:LegalDocument {code: $doc}) "
                "MATCH (a:LegalArticle {code: $code}) "
                "MERGE (d)-[:CONTAINS]->(a)",
                {"doc": art["document"], "code": art["code"]},
            )
            link_count += 1

            # LegalArticle -[:APPLIES_TO]-> ClauseType
            if art.get("clauseType"):
                repo.run(
                    "MATCH (a:LegalArticle {code: $code}) "
                    "MATCH (cl:ClauseType {code: $clause}) "
                    "MERGE (a)-[:APPLIES_TO]->(cl)",
                    {"code": art["code"], "clause": art["clauseType"]},
                )
                link_count += 1

            # LegalArticle -[:REFERENCES]-> RiskType
            if art.get("riskType"):
                repo.run(
                    "MATCH (a:LegalArticle {code: $code}) "
                    "MATCH (r:RiskType {code: $risk}) "
                    "MERGE (a)-[:REFERENCES]->(r)",
                    {"code": art["code"], "risk": art["riskType"]},
                )
                link_count += 1

        return {
            "documents": len(corpus.LEGAL_DOCUMENTS),
            "articles": article_count,
            "relationships": link_count,
            "message": "Legal corpus seeded (idempotent)",
        }

    # --- domain lookups (for RAG / analysis) ----------------------------------

    def get_risks_for_contract(self, contract_code: str) -> List[Dict[str, Any]]:
        rows = self.repo.run(
            "MATCH (c:ContractType {code: $code})-[:HAS_RISK]->(r:RiskType) "
            "OPTIONAL MATCH (r)-[:MITIGATED_BY]->(rec:Recommendation) "
            "RETURN r.code AS code, r.description AS description, "
            "collect(rec.content) AS recommendations",
            {"code": contract_code},
        )
        return rows

    def get_clause_risks(self, clause_codes: List[str]) -> List[Dict[str, Any]]:
        if not clause_codes:
            return []
        rows = self.repo.run(
            "MATCH (cl:ClauseType)-[:HAS_RISK]->(r:RiskType) "
            "WHERE cl.code IN $codes "
            "OPTIONAL MATCH (r)-[:MITIGATED_BY]->(rec:Recommendation) "
            "RETURN cl.code AS clause, r.code AS code, r.description AS description, "
            "collect(rec.content) AS recommendations",
            {"codes": clause_codes},
        )
        return rows

    def get_required_clauses(self, contract_code: str) -> List[Dict[str, Any]]:
        rows = self.repo.run(
            "MATCH (cl:ClauseType)-[:REQUIRED_FOR]->(c:ContractType {code: $code}) "
            "RETURN cl.code AS code, cl.description AS description",
            {"code": contract_code},
        )
        return rows

    def search_knowledge(self, keyword: str, limit: int = 10) -> List[Dict[str, Any]]:
        """Keyword search across legal-domain nodes (no embeddings required)."""
        limit = self._clamp_limit(limit)
        rows = self.repo.run(
            "MATCH (n) WHERE (n:RiskType OR n:ClauseType OR n:ContractType "
            "OR n:LegalConcept OR n:Recommendation) "
            "AND any(k IN keys(n) WHERE toLower(toString(n[k])) CONTAINS toLower($kw)) "
            "RETURN labels(n) AS labels, n AS node LIMIT $limit",
            {"kw": keyword, "limit": limit},
        )
        return rows

    def recommendations_for_risk(self, risk_code: str) -> List[str]:
        rows = self.repo.run(
            "MATCH (r:RiskType {code: $code})-[:MITIGATED_BY]->(rec:Recommendation) "
            "RETURN rec.content AS content",
            {"code": risk_code},
        )
        return [r["content"] for r in rows if r.get("content")]

    def get_legal_articles(
        self,
        clause_codes: Optional[List[str]] = None,
        risk_codes: Optional[List[str]] = None,
        limit: int = 20,
    ) -> List[Dict[str, Any]]:
        """Fetch verified legal articles linked to given clauses or risks."""
        limit = self._clamp_limit(limit)
        rows = self.repo.run(
            "MATCH (a:LegalArticle) "
            "OPTIONAL MATCH (a)-[:APPLIES_TO]->(cl:ClauseType) "
            "OPTIONAL MATCH (a)-[:REFERENCES]->(r:RiskType) "
            "OPTIONAL MATCH (d:LegalDocument)-[:CONTAINS]->(a) "
            "WITH a, d, collect(DISTINCT cl.code) AS clauses, "
            "collect(DISTINCT r.code) AS risks "
            "WHERE ($clauses IS NULL AND $risks IS NULL) "
            "OR ($clauses IS NOT NULL AND any(c IN clauses WHERE c IN $clauses)) "
            "OR ($risks IS NOT NULL AND any(rr IN risks WHERE rr IN $risks)) "
            "RETURN a.code AS code, a.title AS title, a.summary AS summary, "
            "d.title AS document, d.reference AS reference, "
            "clauses, risks LIMIT $limit",
            {
                "clauses": clause_codes or None,
                "risks": risk_codes or None,
                "limit": limit,
            },
        )
        return rows

    # --- helpers ---------------------------------------------------------------

    def _node_view(self, node: Dict[str, Any]) -> Dict[str, Any]:
        labels = node.get("labels", [])
        return {
            "id": node.get("id"),
            "label": labels[0] if labels else "",
            "labels": labels,
            "properties": node.get("properties", {}),
        }

    def _clamp_limit(self, limit: int) -> int:
        max_limit = self.settings.neo4j_max_query_limit
        if limit is None or limit <= 0:
            return min(50, max_limit)
        return min(limit, max_limit)

    def _enforce_limit(self, cypher: str) -> str:
        """Append a LIMIT to read queries that don't already have one.

        This is a guard against accidentally heavy queries from internal tools.
        It only appends when the statement looks like a RETURN without LIMIT.
        """
        lowered = cypher.lower()
        if " limit " in lowered or lowered.strip().endswith("limit"):
            return cypher
        if "return" in lowered:
            return f"{cypher.rstrip().rstrip(';')} LIMIT {self.settings.neo4j_max_query_limit}"
        return cypher
