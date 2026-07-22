import unittest
from app.services.contract_generation_service import is_contract_generation_intent

class TestRouting(unittest.TestCase):
    def test_contract_generation_triggers(self):
        # Queries that SHOULD trigger contract generation
        self.assertTrue(is_contract_generation_intent("tạo hợp đồng thuê nhà"))
        self.assertTrue(is_contract_generation_intent("soạn hợp đồng lao động"))
        self.assertTrue(is_contract_generation_intent("cho tôi mẫu hợp đồng"))
        self.assertTrue(is_contract_generation_intent("provide a lease agreement"))
        
    def test_non_generation_triggers(self):
        # Queries that SHOULD NOT trigger contract generation (QA, references, checks)
        self.assertFalse(is_contract_generation_intent("cho tôi các hợp đồng bạn đã tham khảo"))
        self.assertFalse(is_contract_generation_intent("cho tôi các file tài liệu bạn đã tham khảo"))
        self.assertFalse(is_contract_generation_intent("các tài liệu này lấy ở đâu"))
        self.assertFalse(is_contract_generation_intent("nguồn gốc của các hợp đồng này ở đâu"))
        self.assertFalse(is_contract_generation_intent("tham khảo từ luật nào"))
        self.assertFalse(is_contract_generation_intent("rà soát hợp đồng này"))
        self.assertFalse(is_contract_generation_intent("phân tích rủi ro hợp đồng"))
        self.assertFalse(is_contract_generation_intent("về hợp đồng lao động của tôi thì có vấn đề gì không"))
        self.assertFalse(is_contract_generation_intent("cho biết thông tin hợp đồng lao động của bạn"))

if __name__ == "__main__":
    unittest.main()
