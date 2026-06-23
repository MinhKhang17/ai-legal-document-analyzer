from app.graph.connection import get_driver


def get_technologies():
    query = """
    MATCH (t:Technology)
    RETURN t.name AS name
    """

    with get_driver().session() as session:
        result = session.run(query)
        return [record["name"] for record in result]
