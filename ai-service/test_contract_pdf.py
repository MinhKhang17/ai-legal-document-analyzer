import logging
import httpx

logging.basicConfig(level=logging.INFO)

def test_contract_pdf():
    # Call the FastAPI local endpoint
    payload = {
        "request_id": "test_pdf_req_1",
        "user_id": "1",
        "workspace_id": "ws_test_123",
        "question": "tạo cho tôi 1 hợp đồng tương tự như này bằng file pdf được không",
        "top_k_knowledge_chunks": 3
    }
    
    print("Calling FastAPI /internal/rag/query...")
    try:
        resp = httpx.post("http://localhost:8000/internal/rag/query", json=payload, timeout=60.0)
        print("Status Code:", resp.status_code)
        if resp.status_code == 200:
            data = resp.json()
            print("\nSUCCESS!")
            print("Answer length:", len(data.get("answer", "")))
            print("Answer:\n", data.get("answer"))
        else:
            print("\nFAILED:", resp.status_code, resp.text)
    except Exception as e:
        print("\nERROR:", e)

if __name__ == "__main__":
    test_contract_pdf()
