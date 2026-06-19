"""Test AQ. auth key with different header configurations."""
import json
import urllib.request
import urllib.error

api_key = "YOUR_GEMINI_API_KEY_HERE"  # Replace with your actual API key
model = "gemini-1.5-flash"
base_url = "https://generativelanguage.googleapis.com/v1beta"

# Test 1: Original header (x-goog-api-key)
print("="*70)
print("Test 1: Using x-goog-api-key header")
print("="*70)

payload = {
    "contents": [{
        "role": "user",
        "parts": [{"text": "Say 'Hello' in Vietnamese"}]
    }],
    "generationConfig": {
        "temperature": 0.0,
        "maxOutputTokens": 128
    }
}

req = urllib.request.Request(
    f"{base_url}/models/{model}:generateContent",
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "x-goog-api-key": api_key,
        "Content-Type": "application/json"
    },
    method="POST"
)

try:
    with urllib.request.urlopen(req, timeout=30) as response:
        result = json.loads(response.read().decode("utf-8"))
        print("✅ SUCCESS!")
        print(json.dumps(result, indent=2, ensure_ascii=False))
except urllib.error.HTTPError as e:
    print(f"❌ HTTP Error {e.code}: {e.reason}")
    try:
        error_body = e.read().decode("utf-8")
        print(error_body[:500])
    except:
        pass
except Exception as e:
    print(f"❌ Error: {e}")

# Test 2: Using Authorization header
print("\n" + "="*70)
print("Test 2: Using Authorization: Bearer header")
print("="*70)

req2 = urllib.request.Request(
    f"{base_url}/models/{model}:generateContent",
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    },
    method="POST"
)

try:
    with urllib.request.urlopen(req2, timeout=30) as response:
        result = json.loads(response.read().decode("utf-8"))
        print("✅ SUCCESS!")
        print(json.dumps(result, indent=2, ensure_ascii=False))
except urllib.error.HTTPError as e:
    print(f"❌ HTTP Error {e.code}: {e.reason}")
    try:
        error_body = e.read().decode("utf-8")
        print(error_body[:500])
    except:
        pass
except Exception as e:
    print(f"❌ Error: {e}")

# Test 3: Using API key in URL parameter
print("\n" + "="*70)
print("Test 3: Using API key as URL parameter")
print("="*70)

req3 = urllib.request.Request(
    f"{base_url}/models/{model}:generateContent?key={api_key}",
    data=json.dumps(payload).encode("utf-8"),
    headers={
        "Content-Type": "application/json"
    },
    method="POST"
)

try:
    with urllib.request.urlopen(req3, timeout=30) as response:
        result = json.loads(response.read().decode("utf-8"))
        print("✅ SUCCESS!")
        print(json.dumps(result, indent=2, ensure_ascii=False))
except urllib.error.HTTPError as e:
    print(f"❌ HTTP Error {e.code}: {e.reason}")
    try:
        error_body = e.read().decode("utf-8")
        print(error_body[:500])
    except:
        pass
except Exception as e:
    print(f"❌ Error: {e}")
