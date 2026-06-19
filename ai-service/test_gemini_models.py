"""Test different Gemini model names."""
import sys
sys.path.insert(0, 'C:\\Users\\DELL\\Documents\\findRisk\\ai-service')

from app.services.gemini_client import GeminiClient

api_key = "YOUR_GEMINI_API_KEY_HERE"  # Replace with your actual API key

# Thử các model name khác nhau
models_to_test = [
    "gemini-1.5-flash-latest",
    "gemini-1.5-flash",
    "gemini-1.5-pro",
    "gemini-1.5-pro-latest",
    "gemini-pro",
]

for model in models_to_test:
    print(f"\n{'='*70}")
    print(f"Testing model: {model}")
    print('='*70)
    
    client = GeminiClient(api_key=api_key, model=model)
    
    result = client.generate_text(
        system_prompt="Bạn là trợ lý AI.",
        user_prompt="Nói 'Hello' bằng tiếng Việt"
    )
    
    if result.text:
        print(f"✅ SUCCESS with model: {model}")
        print(f"Response: {result.text}")
        print(f"\n✅✅✅ FOUND WORKING MODEL: {model} ✅✅✅")
        break
    else:
        print(f"❌ Failed: {result.error[:200]}")
