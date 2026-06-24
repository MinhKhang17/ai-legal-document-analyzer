import unittest
import sys
from app.services.llm_client import sanitize_response, extract_risk_level

class TestSanitizeAndExtract(unittest.TestCase):
    def test_clean_markdown_fences(self):
        raw = "```json\nKết luận: Hợp đồng hợp pháp.\n```"
        self.assertEqual(sanitize_response(raw), "Kết luận: Hợp đồng hợp pháp.")

        raw2 = "```\nKết luận: Hợp đồng hợp pháp.\n```"
        self.assertEqual(sanitize_response(raw2), "Kết luận: Hợp đồng hợp pháp.")

    def test_valid_json(self):
        raw = '{"answer": "Kết luận: Hợp đồng tốt.", "risk_level": "LOW"}'
        self.assertEqual(sanitize_response(raw), "Kết luận: Hợp đồng tốt.")

    def test_truncated_json_with_closing_quote(self):
        raw = '{\n  "answer": "Kết luận: Hợp đồng có rủi ro.",\n  "risk_level": "HIGH"'
        self.assertEqual(sanitize_response(raw), "Kết luận: Hợp đồng có rủi ro.")

    def test_truncated_json_no_closing_quote(self):
        raw = '{\n  "answer": "Kết luận: Hợp đồng có rủi ro.'
        self.assertEqual(sanitize_response(raw), "Kết luận: Hợp đồng có rủi ro.")

    def test_plain_text(self):
        raw = "Kết luận: Không có rủi ro gì."
        self.assertEqual(sanitize_response(raw), "Kết luận: Không có rủi ro gì.")

    def test_risk_level_extraction(self):
        text1 = "KẾT LUẬN:\nHợp đồng bình thường.\nRỦI RO:\nCao do vi phạm điều khoản bảo mật."
        self.assertEqual(extract_risk_level(text1), "HIGH")

        text2 = "Rủi Ro: Thấp."
        self.assertEqual(extract_risk_level(text2), "LOW")

        text3 = "RỦI RO: TRUNG BÌNH"
        self.assertEqual(extract_risk_level(text3), "MEDIUM")

        text4 = "Không nói gì về mức độ."
        self.assertEqual(extract_risk_level(text4), "UNKNOWN")

if __name__ == "__main__":
    unittest.main()
