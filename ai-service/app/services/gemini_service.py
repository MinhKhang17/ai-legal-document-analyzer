"""Gemini LLM service for answer generation."""
import logging
from typing import List, Dict, Any, Optional
import google.generativeai as genai
from app.config import settings

logger = logging.getLogger(__name__)


class GeminiService:
    """Service for generating answers using Gemini."""
    
    def __init__(self):
        """Initialize Gemini service."""
        self.api_key = settings.gemini_api_key
        self.model_name = settings.gemini_model
        self.model = None
    
    def initialize(self):
        """Initialize Gemini API."""
        if not self.api_key or self.api_key == "your_gemini_api_key_here":
            logger.warning("Gemini API key not configured. LLM features will be disabled.")
            return False
        
        try:
            genai.configure(api_key=self.api_key)
            self.model = genai.GenerativeModel(self.model_name)
            logger.info(f"Gemini service initialized with model: {self.model_name}")
            return True
        except Exception as e:
            logger.error(f"Failed to initialize Gemini: {e}")
            return False
    
    def generate_review_answer(
        self,
        question: str,
        checklist_results: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]]
    ) -> str:
        """Generate comprehensive review answer using Gemini."""
        
        if not self.model:
            logger.warning("Gemini not initialized, using fallback answer generation")
            return self._generate_fallback_answer(checklist_results)
        
        try:
            # Build context for Gemini
            prompt = self._build_prompt(question, checklist_results, knowledge_chunks)
            
            # Generate answer
            logger.info("Generating answer with Gemini...")
            response = self.model.generate_content(prompt)
            
            if response and response.text:
                logger.info("Answer generated successfully")
                return response.text
            else:
                logger.warning("Empty response from Gemini, using fallback")
                return self._generate_fallback_answer(checklist_results)
                
        except Exception as e:
            logger.error(f"Error generating answer with Gemini: {e}")
            return self._generate_fallback_answer(checklist_results)
    
    def _build_prompt(
        self,
        question: str,
        checklist_results: List[Dict[str, Any]],
        knowledge_chunks: List[Dict[str, Any]]
    ) -> str:
        """Build structured prompt for Gemini."""
        
        prompt_parts = []
        
        # System instruction
        prompt_parts.append("""Bạn là chuyên gia phân tích hợp đồng và văn bản pháp lý với nhiều năm kinh nghiệm.
Nhiệm vụ của bạn là rà soát văn bản theo các tiêu chí được cung cấp và đưa ra nhận xét chi tiết, rõ ràng.

Yêu cầu:
- Phân tích theo từng tiêu chí cụ thể
- Chỉ ra những điểm cần lưu ý, rủi ro tiềm ẩn
- Đưa ra khuyến nghị cải thiện nếu có
- Sử dụng ngôn ngữ chuyên nghiệp nhưng dễ hiểu
- Trích dẫn đúng nội dung từ văn bản
- Không bịa đặt thông tin không có trong tài liệu
""")
        
        # User question
        prompt_parts.append(f"\n**CÂU HỎI CỦA NGƯỜI DÙNG:**\n{question}\n")
        
        # Checklist findings
        prompt_parts.append("\n**KẾT QUẢ RÀ SOÁT THEO TIÊU CHÍ:**\n")
        
        findings_with_content = [r for r in checklist_results if r.get('user_chunks_found')]
        findings_without_content = [r for r in checklist_results if not r.get('user_chunks_found')]
        
        if findings_with_content:
            prompt_parts.append("\n### Các điểm tìm thấy trong văn bản:\n")
            for i, result in enumerate(findings_with_content[:15], 1):
                prompt_parts.append(f"\n{i}. **{result.get('title', 'N/A')}** (Ưu tiên: {result.get('priority', 'N/A')})")
                prompt_parts.append(f"   Câu hỏi kiểm tra: {result.get('risk_question', 'N/A')}")
                
                chunks = result.get('user_chunks_found', [])
                if chunks:
                    prompt_parts.append(f"   Nội dung tìm thấy:")
                    for j, chunk in enumerate(chunks[:3], 1):
                        text = chunk.get('text', '')[:300]
                        score = chunk.get('score', 0)
                        prompt_parts.append(f"   - \"{text}\" (độ liên quan: {score:.1%})")
        
        if findings_without_content:
            prompt_parts.append(f"\n### Các điều khoản KHÔNG tìm thấy ({len(findings_without_content)} điểm):\n")
            for result in findings_without_content[:10]:
                prompt_parts.append(f"- {result.get('title', 'N/A')}: {result.get('risk_question', 'N/A')}")
        
        # Knowledge base context
        if knowledge_chunks:
            prompt_parts.append("\n**KIẾN THỨC PHÁP LÝ THAM KHẢO:**\n")
            for i, chunk in enumerate(knowledge_chunks[:5], 1):
                text = chunk.get('text', '')[:200]
                prompt_parts.append(f"{i}. {text}")
        
        # Instructions for output format
        prompt_parts.append("""

**YÊU CẦU ĐỊNH DẠNG TRẢ LỜI:**

Hãy trả lời theo cấu trúc sau:

# KẾT QUẢ RÀ SOÁT HỢP ĐỒNG

## ⚠️ CÁC ĐIỂM CẦN LƯU Ý

[Liệt kê các vấn đề tìm thấy, mỗi điểm gồm:]
- Tiêu đề vấn đề
- Nội dung cụ thể tìm thấy trong văn bản (trích dẫn)
- Phân tích rủi ro
- Khuyến nghị xử lý

## 📝 CÁC ĐIỀU KHOẢN CẦN BỔ SUNG

[Liệt kê các điều khoản quan trọng chưa có hoặc chưa rõ ràng]

## ✅ ĐÁNH GIÁ TỔNG QUAN

- Tình trạng chung của văn bản
- Mức độ rủi ro (Cao/Trung bình/Thấp)
- Khuyến nghị tổng thể

---
*Lưu ý: Đây là phân tích sơ bộ. Nên có chuyên gia pháp lý xem xét kỹ trước khi ký kết.*
""")
        
        return "\n".join(prompt_parts)
    
    def _generate_fallback_answer(self, checklist_results: List[Dict[str, Any]]) -> str:
        """Generate simple fallback answer when Gemini is not available."""
        
        if not checklist_results:
            return "Không thể phân tích văn bản vì không tìm thấy nội dung phù hợp."
        
        findings_with_content = [r for r in checklist_results if r.get('user_chunks_found')]
        findings_without_content = [r for r in checklist_results if not r.get('user_chunks_found')]
        
        answer_parts = []
        answer_parts.append("📋 **KẾT QUẢ RÀ SOÁT HỢP ĐỒNG**\n")
        
        if findings_with_content:
            answer_parts.append("## ⚠️ CÁC ĐIỂM CẦN LƯU Ý\n")
            for i, result in enumerate(findings_with_content[:10], 1):
                answer_parts.append(f"### {i}. {result.get('title')}")
                answer_parts.append(f"**Câu hỏi:** {result.get('risk_question')}\n")
                
                chunks = result.get('user_chunks_found', [])
                if chunks:
                    answer_parts.append("**Nội dung tìm thấy:**")
                    for chunk in chunks[:2]:
                        text = chunk.get('text', '')[:200]
                        answer_parts.append(f"- \"{text}...\"\n")
        
        if findings_without_content:
            answer_parts.append(f"## 📝 CẦN KIỂM TRA THÊM ({len(findings_without_content)} điểm)\n")
            for result in findings_without_content[:8]:
                answer_parts.append(f"- **{result.get('title')}**")
        
        answer_parts.append("\n---")
        answer_parts.append(f"**Tổng kết:** Đã rà soát {len(checklist_results)} tiêu chí")
        answer_parts.append("\n⚠️ *Khuyến nghị có chuyên gia pháp lý xem xét kỹ hơn.*")
        
        return "\n".join(answer_parts)


# Singleton instance
gemini_service = GeminiService()
