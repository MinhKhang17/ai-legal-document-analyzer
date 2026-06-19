"""Test Gemini with v1 API."""
import sys
sys.path.insert(0, 'C:\\Users\\DELL\\Documents\\findRisk\\ai-service')

from app.services.gemini_client import GeminiClient

api_key = "YOUR_GEMINI_API_KEY_HERE"  # Replace with your actual API key

# Thử v1 API
models_to_test = [
    "gemini-1.5-flash",
    "gemini-1.5-flash-latest",
    "gemini-1.5-pro",
]

base_url = "https://generativelanguage.googleapis.com/v1"

for model in models_to_test:
    print(f"\n{'='*70}")
    print(f"Testing v1 API with model: {model}")
    print('='*70)
    
    client = GeminiClient(
        api_key=api_key,
        model=model,
        base_url=base_url
    )
    
    result = client.generate_text(
        system_prompt="Bạn là trợ lý AI.",
        user_prompt="Nói 'Xin chào' bằng tiếng Anh"
    )
    
    if result.text:
        print(f"✅ SUCCESS with v1 API + model: {model}")
        print(f"Response: {result.text}")
        print(f"\n🎉 WORKING CONFIGURATION:")
        print(f"   BASE_URL: {base_url}")
        print(f"   MODEL: {model}")
        break
    else:
        error_snippet = result.error[:300] if result.error else "Unknown error"
        print(f"❌ Failed: {error_snippet}")
