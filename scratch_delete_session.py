import requests
import json

BASE_URL = "http://localhost:8080"
headers = {"Content-Type": "application/json"}

# 1. Login
print("1. Logging in...")
login_resp = requests.post(
    f"{BASE_URL}/api/v1/auth/login",
    json={"email": "user@123", "password": "pass@123"}
)
if login_resp.status_code != 200:
    print(f"Login failed: {login_resp.status_code} - {login_resp.text}")
    exit(1)

token = login_resp.json()["data"]["accessToken"]
headers["Authorization"] = f"Bearer {token}"
print("Logged in successfully.")

# 2. Get workspaces
print("2. Getting workspaces...")
ws_resp = requests.get(f"{BASE_URL}/api/v1/workspaces", headers=headers)
workspace_id = ws_resp.json()["data"][0]["workspaceId"]
print(f"Workspace ID: {workspace_id}")

# 3. Create chat session
print("3. Creating chat session...")
session_resp = requests.post(
    f"{BASE_URL}/api/v1/workspaces/{workspace_id}/chat-sessions",
    headers=headers,
    json={"title": "Delete Test Session"}
)
chat_session_id = session_resp.json()["data"]["chatSessionId"]
print(f"Chat Session ID: {chat_session_id}")

# 4. Delete chat session
print("4. Deleting chat session...")
delete_resp = requests.delete(
    f"{BASE_URL}/api/v1/chat-sessions/{chat_session_id}",
    headers=headers
)
print("Delete Response status:", delete_resp.status_code)
print("Delete Response body:", delete_resp.text)
