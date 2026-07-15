import os
from dotenv import load_dotenv
load_dotenv()
from app.core.config import settings
from app.services.llm_client import build_default_llm_client

def run_test():
    client = build_default_llm_client()
    print("Using client:", type(client))
    print("Configured Model:", settings.gemini_model)
    print("Configured Max Output Tokens:", settings.gemini_max_output_tokens)

    system_prompt = "You are a professional legal draft assistant."
    user_prompt = "Draft a complete lease agreement in Vietnamese based on the reference document 08/LB-TT. Write at least 2500 words, including a lot of articles and sections to test the output length capacity. Do not truncate. Generate a very long and detailed agreement."

    print("Calling Gemini...")
    res = client.generate(system_prompt=system_prompt, user_prompt=user_prompt)
    print("Error:", res.error)
    print("Raw Response Length (chars):", len(res.raw_response) if res.raw_response else 0)
    print("Sanitized Answer Length (chars):", len(res.answer) if res.answer else 0)
    if res.answer:
        print("--- LAST 200 CHARACTERS ---")
        try:
            print(res.answer[-200:])
        except Exception:
            print(res.answer[-200:].encode('ascii', 'backslashreplace').decode('ascii'))

if __name__ == "__main__":
    run_test()
