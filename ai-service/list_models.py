"""List available Gemini models."""
import json
import urllib.request
import urllib.error

api_key = "YOUR_GEMINI_API_KEY_HERE"  # Replace with your actual API key
base_url = "https://generativelanguage.googleapis.com/v1beta"

# List all models
print("Fetching list of available models...")
print("="*70)

req = urllib.request.Request(
    f"{base_url}/models",
    headers={
        "x-goog-api-key": api_key,
        "Content-Type": "application/json"
    },
    method="GET"
)

try:
    with urllib.request.urlopen(req, timeout=30) as response:
        result = json.loads(response.read().decode("utf-8"))
        models = result.get("models", [])
        
        print(f"✅ Found {len(models)} models:\n")
        
        for model in models:
            name = model.get("name", "")
            display_name = model.get("displayName", "")
            supported_methods = model.get("supportedGenerationMethods", [])
            
            # Chi hiển thị models hỗ trợ generateContent
            if "generateContent" in supported_methods:
                print(f"✅ {name}")
                print(f"   Display Name: {display_name}")
                print(f"   Methods: {', '.join(supported_methods)}")
                print()
        
except urllib.error.HTTPError as e:
    print(f"❌ HTTP Error {e.code}: {e.reason}")
    try:
        error_body = e.read().decode("utf-8")
        print(error_body)
    except:
        pass
except Exception as e:
    print(f"❌ Error: {e}")
