"""Quick test to verify Gemini API key is working."""
import google.generativeai as genai
from app.config import settings

def test_gemini_connection():
    """Test Gemini API connection."""
    print("=" * 60)
    print("Testing Gemini API Connection")
    print("=" * 60)
    
    # Load API key
    api_key = settings.gemini_api_key
    model_name = settings.gemini_model
    
    print(f"\n📋 Configuration:")
    print(f"   API Key: {api_key[:20]}...{api_key[-10:]}")
    print(f"   Model: {model_name}")
    
    # Test connection
    print(f"\n🔄 Testing connection...")
    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel(model_name)
        
        # Simple test prompt
        test_prompt = "Xin chào, bạn là ai?"
        print(f"\n📤 Sending test prompt: '{test_prompt}'")
        
        response = model.generate_content(test_prompt)
        
        if response and response.text:
            print(f"\n✅ SUCCESS! Gemini is working!")
            print(f"\n📥 Response:")
            print(f"   {response.text[:200]}...")
            print(f"\n✅ Your Gemini API key is valid and working!")
            return True
        else:
            print(f"\n❌ FAILED: Empty response from Gemini")
            return False
            
    except Exception as e:
        print(f"\n❌ FAILED: {e}")
        print(f"\nPossible issues:")
        print(f"   - API key is invalid")
        print(f"   - Model name is incorrect")
        print(f"   - Network connection issue")
        print(f"   - Quota exceeded")
        return False

if __name__ == "__main__":
    success = test_gemini_connection()
    
    if success:
        print("\n" + "=" * 60)
        print("🎉 Ready to use Gemini!")
        print("=" * 60)
        print("\nNext steps:")
        print("   1. Start AI service: python run_dev.py")
        print("   2. Run comprehensive test: python comprehensive_test.py")
        print("   3. Check service logs for: '✅ Gemini LLM initialized successfully'")
    else:
        print("\n" + "=" * 60)
        print("❌ Gemini setup failed")
        print("=" * 60)
        print("\nPlease check:")
        print("   1. API key in .env file")
        print("   2. Internet connection")
        print("   3. Google AI Studio quota")
