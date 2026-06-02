"""Neo4j driver lifecycle management.

A single driver instance is shared for the whole process (the official driver
is thread-safe and manages its own connection pool). The driver is created
lazily so importing this module never fails even if Neo4j is down.
"""
from typing import Optional

from neo4j import Driver, GraphDatabase

from app.core.config import get_settings
from app.core.errors import Neo4jConnectionError

_driver: Optional[Driver] = None


def get_driver() -> Driver:
    """Return the shared Neo4j driver, creating it on first use."""
    global _driver
    if _driver is None:
        settings = get_settings()
        _driver = GraphDatabase.driver(
            settings.neo4j_uri,
            auth=(settings.neo4j_username, settings.neo4j_password),
        )
    return _driver


def verify_connection() -> None:
    """Verify connectivity to Neo4j. Raises Neo4jConnectionError on failure."""
    try:
        get_driver().verify_connectivity()
    except Exception as exc:  # noqa: BLE001 - surface as domain error
        raise Neo4jConnectionError(
            "Neo4j connectivity check failed", details={"cause": str(exc)}
        ) from exc


def ping() -> bool:
    """Run a trivial `RETURN 1` query to confirm the database answers."""
    try:
        driver = get_driver()
        with driver.session() as session:
            record = session.run("RETURN 1 AS ok").single()
            return bool(record and record["ok"] == 1)
    except Exception as exc:  # noqa: BLE001
        raise Neo4jConnectionError(
            "Neo4j ping failed", details={"cause": str(exc)}
        ) from exc


def close_driver() -> None:
    """Close the driver on application shutdown."""
    global _driver
    if _driver is not None:
        _driver.close()
        _driver = None


# Backwards-compatible alias for the legacy `from app.graph.connection import driver`
# usage in the old technology_service. New code should call get_driver().
def __getattr__(name: str):  # pragma: no cover - compatibility shim
    if name == "driver":
        return get_driver()
    raise AttributeError(name)
