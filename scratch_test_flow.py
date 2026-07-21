import requests
import time
import os
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"
AI_URL = "http://localhost:8000"

def run_test():
    print("1. Logging in as user@123...")
    login_resp = requests.post(f"{BASE_URL}/api/v1/auth/login", json={
        "email": "user@123",
        "password": "pass@123"
    })
    if login_resp.status_code != 200:
        print(f"Login failed: {login_resp.status_code} - {login_resp.text}")
        return
    
    login_data = login_resp.json()
    token = login_data["data"]["accessToken"]
    print(f"Logged in successfully. Token starts with: {token[:20]}")

    headers = {
        "Authorization": f"Bearer {token}"
    }

    print("2. Fetching workspaces...")
    workspaces_resp = requests.get(f"{BASE_URL}/api/v1/workspaces", headers=headers)
    if workspaces_resp.status_code != 200:
        print(f"Failed to fetch workspaces: {workspaces_resp.status_code} - {workspaces_resp.text}")
        return
    
    workspaces = workspaces_resp.json()["data"]
    if not workspaces:
        print("No workspaces found. Cannot proceed.")
        return
    
    workspace_id = workspaces[0]["workspaceId"]
    print(f"Using workspace: {workspace_id} ({workspaces[0]['name']})")

    import shutil
    test_file_path = "scratch_test_contract.docx"
    shutil.copy("data/test_contract.docx", test_file_path)

    try:
        print("3. Uploading document...")
        with open(test_file_path, "rb") as f:
            upload_resp = requests.post(
                f"{BASE_URL}/api/v1/workspaces/{workspace_id}/documents",
                headers=headers,
                files={"file": (os.path.basename(test_file_path), f, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")}
            )
        
        if upload_resp.status_code not in (200, 202):
            print(f"Upload failed: {upload_resp.status_code} - {upload_resp.text}")
            return
        
        doc_data = upload_resp.json()["data"]
        doc_id = doc_data["documentId"]
        print(f"Uploaded successfully. Document ID: {doc_id}")

        print("4. Polling document status...")
        for i in range(15):
            time.sleep(2)
            status_resp = requests.get(f"{BASE_URL}/api/v1/workspaces/{workspace_id}/documents", headers=headers)
            docs = status_resp.json()["data"]
            doc = next((d for d in docs if d["documentId"] == doc_id), None)
            if doc:
                print(f"Document status: {doc['status']}")
                status_upper = doc["status"].upper()
                if status_upper == "READY":
                    print("Document is ready!")
                    break
                elif status_upper == "FAILED":
                    print("Document processing failed!")
                    break
        else:
            print("Timeout waiting for document processing.")
            return

        print("5. Attaching document to chat session...")
        print("Creating new chat session...")
        session_resp = requests.post(f"{BASE_URL}/api/v1/workspaces/{workspace_id}/chat-sessions", headers=headers, json={
            "title": "New Session"
        })
        session_id = session_resp.json()["data"]["chatSessionId"]
        print(f"Created new session ID: {session_id}")

        attach_resp = requests.post(f"{BASE_URL}/api/v1/chat-sessions/{session_id}/documents/{doc_id}", headers=headers)
        if attach_resp.status_code != 200:
            print(f"Attach failed: {attach_resp.status_code} - {attach_resp.text}")
            return
        print("Document attached to session.")

        print("6. Querying RAG system with a specific question...")
        # We query the AI Service directly to check the exact response mode and detected intent
        query_payload = {
            "request_id": "test_req_123",
            "user_id": "2",
            "workspace_id": workspace_id,
            "document_id": doc_id,
            "attached_document_ids": [doc_id],
            "chat_session_id": session_id,
            "question": "Giá thuê nhà ở là bao nhiêu?",
            "top_k_user_chunks": 5,
            "top_k_knowledge_chunks": 5,
            "top_k_checklist": 10,
            "top_k_user_chunks_per_checklist": 3
        }
        
        query_resp = requests.post(f"{AI_URL}/internal/rag/query", json=query_payload)
        if query_resp.status_code != 200:
            print(f"Query failed: {query_resp.status_code} - {query_resp.text}")
            return
        
        query_result = query_resp.json()
        print("=== Query Result ===")
        print(f"Detected Intent: {query_result.get('intent')}")
        print(f"Response: {query_result.get('answer')}")
        print(f"Citations count: {len(query_result.get('citations', []))}")
        for cit in query_result.get('citations', []):
            print(f"  - Citation: {cit.get('citationId')}: {(cit.get('chunkText') or cit.get('text') or '')[:60]}...")

    finally:
        if os.path.exists(test_file_path):
            os.remove(test_file_path)

if __name__ == "__main__":
    run_test()
