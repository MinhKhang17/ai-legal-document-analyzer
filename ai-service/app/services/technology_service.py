from app.graph.connection import driver


def get_technologies():
    query = """
    MATCH (t:Technology)
    RETURN t.name AS name
    """

    with driver.session() as session:
        result = session.run(query)
        return [record["name"] for record in result]
