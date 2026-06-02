"""Lightweight Python client SDK for the LexiGuard AI service.

A thin httpx wrapper around the HTTP API so other Python code (scripts, tests,
notebooks, or another microservice) can call the service without hand-rolling
requests. It unwraps the standard response envelope and raises AiServiceError on
failures.

Example:
    from client import AiServiceClient

    client = AiServiceClient("http://127.0.0.1:8000")
    print(client.health())
    client.seed_all()
    result = client.analyze_contract(
        contract_text="HỢP ĐỒNG THUÊ NHÀ ...",
        contract_type="HOUSE_RENTAL",
        protected_party="bên thuê",
    )
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

import httpx


class AiServiceError(RuntimeError):
    """Raised when the service returns an error envelope or HTTP error."""

    def __init__(self, code: str, message: str, details: Optional[Dict[str, Any]] = None):
        super().__init__(f"[{code}] {message}")
        self.code = code
        self.message = message
        self.details = details or {}


class AiServiceClient:
    def __init__(
        self,
        base_url: str = "http://127.0.0.1:8000",
        api_key: Optional[str] = None,
        timeout: float = 120.0,
        api_prefix: str = "/api/ai",
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self._prefix = api_prefix.rstrip("/")
        self._api_key = api_key
        self._timeout = timeout

    # --- internal -------------------------------------------------------------

    def _headers(self) -> Dict[str, str]:
        headers = {"Content-Type": "application/json; charset=utf-8"}
        if self._api_key:
            headers["X-AI-Service-Key"] = self._api_key
        return headers

    def _request(self, method: str, path: str, json: Optional[dict] = None) -> Any:
        url = f"{self.base_url}{self._prefix}{path}"
        try:
            with httpx.Client(timeout=self._timeout) as client:
                resp = client.request(method, url, json=json, headers=self._headers())
        except httpx.HTTPError as exc:
            raise AiServiceError("CONNECTION_FAILED", str(exc)) from exc

        try:
            body = resp.json()
        except ValueError:
            resp.raise_for_status()
            raise AiServiceError("INVALID_RESPONSE", "Non-JSON response from service")

        if isinstance(body, dict) and body.get("success") is False:
            err = body.get("error", {})
            raise AiServiceError(
                err.get("code", "UNKNOWN"),
                err.get("message", "Unknown error"),
                err.get("details"),
            )
        if isinstance(body, dict) and "data" in body:
            return body["data"]
        return body

    # --- health ---------------------------------------------------------------

    def health(self) -> Dict[str, Any]:
        return self._request("GET", "/health")

    def health_neo4j(self) -> Dict[str, Any]:
        return self._request("GET", "/health/neo4j")

    def readiness(self) -> Dict[str, Any]:
        return self._request("GET", "/health/readiness")

    # --- graph / seed ---------------------------------------------------------

    def seed_baseline(self) -> Dict[str, Any]:
        return self._request("POST", "/graph/seed/legal-baseline")

    def seed_corpus(self) -> Dict[str, Any]:
        return self._request("POST", "/graph/seed/legal-corpus")

    def seed_all(self) -> Dict[str, Any]:
        return self._request("POST", "/graph/seed/all")

    def query(self, cypher: str, params: Optional[dict] = None) -> Dict[str, Any]:
        return self._request("POST", "/graph/query", {"cypher": cypher, "params": params or {}})

    def create_node(self, label: str, properties: dict) -> Dict[str, Any]:
        return self._request("POST", "/graph/nodes", {"label": label, "properties": properties})

    # --- rag ------------------------------------------------------------------

    def rag_retrieve(
        self,
        query: str,
        contract_type: Optional[str] = None,
        top_k: int = 8,
        risk_types: Optional[List[str]] = None,
        clause_types: Optional[List[str]] = None,
    ) -> Dict[str, Any]:
        return self._request(
            "POST",
            "/rag/retrieve",
            {
                "query": query,
                "contractType": contract_type,
                "topK": top_k,
                "filters": {
                    "riskTypes": risk_types or [],
                    "clauseTypes": clause_types or [],
                },
            },
        )

    def contract_context(
        self,
        contract_type: str,
        question: Optional[str] = None,
        detected_clause_types: Optional[List[str]] = None,
    ) -> Dict[str, Any]:
        return self._request(
            "POST",
            "/rag/contract-context",
            {
                "contractType": contract_type,
                "question": question,
                "detectedClauseTypes": detected_clause_types or [],
            },
        )

    # --- legal ----------------------------------------------------------------

    def classify_contract(self, text: str) -> Dict[str, Any]:
        return self._request("POST", "/legal/classify-contract", {"text": text})

    def analyze_contract(
        self,
        contract_text: str,
        contract_type: str = "UNKNOWN",
        protected_party: Optional[str] = None,
        question: Optional[str] = None,
        output_mode: str = "JSON",
        include_graph_context: bool = True,
    ) -> Dict[str, Any]:
        return self._request(
            "POST",
            "/legal/analyze-contract",
            {
                "contractText": contract_text,
                "contractType": contract_type,
                "protectedParty": protected_party,
                "question": question,
                "options": {
                    "outputMode": output_mode,
                    "includeGraphContext": include_graph_context,
                },
            },
        )

    def compare_contracts(
        self,
        document_a: str,
        document_b: str,
        contract_type: str = "UNKNOWN",
        protected_party: Optional[str] = None,
        output_mode: str = "JSON",
        include_graph_context: bool = True,
    ) -> Dict[str, Any]:
        return self._request(
            "POST",
            "/legal/compare-contracts",
            {
                "documentAText": document_a,
                "documentBText": document_b,
                "contractType": contract_type,
                "protectedParty": protected_party,
                "options": {
                    "outputMode": output_mode,
                    "includeGraphContext": include_graph_context,
                },
            },
        )


if __name__ == "__main__":
    # Quick manual smoke check against a running service.
    c = AiServiceClient()
    print("health:", c.health())
    print("readiness:", c.readiness())
