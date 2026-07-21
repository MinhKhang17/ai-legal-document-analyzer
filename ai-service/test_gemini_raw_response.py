import json
from app.core.config import settings
from app.services.gemini_client import GeminiClient

def test_raw():
    client = GeminiClient(
        api_key=settings.gemini_api_key,
        model=settings.gemini_model,
        base_url=settings.gemini_base_url,
        timeout_seconds=settings.gemini_timeout_seconds,
        max_output_tokens=settings.gemini_max_output_tokens,
        max_retries=settings.gemini_max_retries,
        retry_backoff_seconds=settings.gemini_retry_backoff_seconds,
    )

    # Let's mock a prompt similar to the one in the log
    system_prompt = (
        "Bạn là LexAI, trợ lý pháp lý chuyên nghiệp."
    )
    
    ws_id = "ws_c8a2733ff4e840d395354f3806e669f8"
    docs_summary = (
        "- Tài liệu/Biểu mẫu chung có sẵn trong hệ thống (Neo4j): "
        "01.2019.QĐ.UBND, 01_2025_NQ_HĐND, 02.2025, 03_2021_QD_UBNDs"
    )
    
    download_instructions = (
        "HƯỚNG DẪN CUNG CẤP ĐƯỜNG DẪN TẢI XUỐNG CHO TÀI LIỆU HỆ THỐNG:\n"
        "Nếu người dùng muốn tải xuống các tệp ví dụ hoặc tài liệu đối chiếu thuộc danh sách tài liệu hệ thống (Neo4j) nêu trên, bạn có thể tạo đường dẫn tải xuống trực tiếp cho họ bằng định dạng Markdown sau:\n"
        f"- [Tải xuống Tên_Tài_Liệu](http://localhost:8080/api/v1/workspaces/{ws_id}/documents/system/download?filename=Tên_File_Gốc)\n"
    )
    
    reference_question_instructions = (
        "\n\n═══════════════════════════════════════════════════\n"
        "⚠️ HƯỚNG DẪN BẮT BUỘC: TRẢ LỜI CÂU HỎI VỀ TÀI LIỆU/FILE THAM KHẢO\n"
        "═══════════════════════════════════════════════════\n"
        "Người dùng đang hỏi về các file, tài liệu, hợp đồng bạn đã tham khảo hoặc nguồn gốc thông tin.\n"
        "Bạn PHẢI thực hiện nghiêm ngặt các quy định sau:\n"
        "1. KHÔNG trả lời lan man, KHÔNG giải thích dông dài về cách xây dựng kiến thức pháp lý chung, KHÔNG có phần 'Lưu ý quan trọng'.\n"
        "2. LIỆT KÊ TRỰC TIẾP danh sách các file/tài liệu tham khảo có sẵn trong hệ thống (Neo4j) dưới dạng bullet points.\n"
        "3. CUNG CẤP đường dẫn tải xuống dạng link Markdown trực tiếp cho từng file tài liệu hệ thống đó, ví dụ:\n"
        f"   - [Tên_Tài_Liệu](http://localhost:8080/api/v1/workspaces/{ws_id}/documents/system/download?filename=Tên_File_Gốc)\n"
        "4. Định dạng câu trả lời cực kỳ ngắn gọn và đi thẳng vào danh sách các file/tài liệu tham khảo.\n\n"
    )
    
    suffix = "BẮT BUỘC: Bạn chỉ được liệt kê trực tiếp danh sách tài liệu tham khảo kèm link tải xuống như hướng dẫn, tuyệt đối không trả lời thêm gì khác ngoài danh sách này."

    user_prompt = (
        "DANH SÁCH TÀI LIỆU ĐANG CÓ TRONG HỆ THỐNG:\n"
        f"{docs_summary}\n\n"
        f"{download_instructions}"
        f"{reference_question_instructions}"
        "CÂU HỎI HIỆN TẠI:\n"
        "phần mẫu này bạn đã tham khảo ở đâu\n\n"
        f"{suffix}"
    )

    payload = {
        "systemInstruction": {
            "parts": [{"text": system_prompt}],
        },
        "contents": [
            {
                "role": "user",
                "parts": [{"text": user_prompt}],
            }
        ],
        "generationConfig": {
            "temperature": 0.0,
            "maxOutputTokens": settings.gemini_max_output_tokens,
        },
    }

    response = client.generate_text(system_prompt=system_prompt, user_prompt=user_prompt)
    if response.error:
        print("Error:", response.error)
    else:
        print("Text generated:")
        print(response.text)

if __name__ == "__main__":
    test_raw()
