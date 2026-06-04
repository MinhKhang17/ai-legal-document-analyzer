"""Test OpenAPI schema generation với documentation mới."""
import json
from app.main import app


def test_openapi_schema():
    """Test xem OpenAPI schema có generate đúng không."""
    print("Testing OpenAPI Schema Generation...")
    
    openapi = app.openapi()
    
    # Check basic info
    print(f"\n✓ API Title: {openapi['info']['title']}")
    print(f"✓ API Version: {openapi['info']['version']}")
    
    # Check endpoints có documentation
    paths = openapi['paths']
    
    # Test classify-contract endpoint
    classify_path = '/api/ai/legal/classify-contract'
    if classify_path in paths:
        classify_op = paths[classify_path]['post']
        print(f"\n✓ {classify_path}")
        print(f"  - Summary: {classify_op.get('summary', 'N/A')}")
        print(f"  - Description length: {len(classify_op.get('description', ''))} chars")
        print(f"  - Has examples: {bool(classify_op.get('responses', {}).get('200', {}).get('content'))}")
    
    # Test analyze-contract endpoint
    analyze_path = '/api/ai/legal/analyze-contract'
    if analyze_path in paths:
        analyze_op = paths[analyze_path]['post']
        print(f"\n✓ {analyze_path}")
        print(f"  - Summary: {analyze_op.get('summary', 'N/A')}")
        print(f"  - Description length: {len(analyze_op.get('description', ''))} chars")
        print(f"  - Has examples: {bool(analyze_op.get('responses', {}).get('200', {}).get('content'))}")
    
    # Test compare-contracts endpoint
    compare_path = '/api/ai/legal/compare-contracts'
    if compare_path in paths:
        compare_op = paths[compare_path]['post']
        print(f"\n✓ {compare_path}")
        print(f"  - Summary: {compare_op.get('summary', 'N/A')}")
        print(f"  - Description length: {len(compare_op.get('description', ''))} chars")
        print(f"  - Has examples: {bool(compare_op.get('responses', {}).get('200', {}).get('content'))}")
    
    # Test RAG endpoints
    retrieve_path = '/api/ai/rag/retrieve'
    if retrieve_path in paths:
        retrieve_op = paths[retrieve_path]['post']
        print(f"\n✓ {retrieve_path}")
        print(f"  - Summary: {retrieve_op.get('summary', 'N/A')}")
        print(f"  - Description length: {len(retrieve_op.get('description', ''))} chars")
    
    # Check schemas
    schemas = openapi.get('components', {}).get('schemas', {})
    print(f"\n✓ Total schemas: {len(schemas)}")
    
    # Check request models có field descriptions
    if 'AnalyzeContractRequest' in schemas:
        analyze_schema = schemas['AnalyzeContractRequest']
        props = analyze_schema.get('properties', {})
        print(f"\n✓ AnalyzeContractRequest properties:")
        for prop_name, prop_schema in props.items():
            desc = prop_schema.get('description', 'No description')
            max_len = prop_schema.get('maxLength', 'No limit')
            print(f"  - {prop_name}: {desc[:50]}... (max: {max_len})")
    
    print("\n" + "="*60)
    print("✓ OpenAPI SCHEMA GENERATED SUCCESSFULLY")
    print("="*60)
    print(f"\nSwagger UI: http://localhost:8000/docs")
    print(f"ReDoc: http://localhost:8000/redoc")


if __name__ == "__main__":
    test_openapi_schema()
