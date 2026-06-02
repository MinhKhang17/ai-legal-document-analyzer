"""Verified Vietnamese legal corpus seeded into the knowledge graph.

Each entry references a real statute and article. Content is paraphrased/
summarized (not full verbatim text) to ground the RAG/analysis without
reproducing copyrighted text. Articles are linked to the relevant ClauseType
and RiskType nodes so retrieval can surface a concrete legal basis.

IMPORTANT: keep this list conservative and verifiable. Do not invent article
numbers. When in doubt, omit rather than guess.
"""
from typing import Any, Dict, List

# Source documents (LegalDocument nodes).
LEGAL_DOCUMENTS: List[Dict[str, str]] = [
    {
        "code": "BLDS_2015",
        "title": "Bộ luật Dân sự 2015",
        "reference": "Luật số 91/2015/QH13",
    },
    {
        "code": "LUAT_DAT_DAI_2024",
        "title": "Luật Đất đai 2024",
        "reference": "Luật số 31/2024/QH15",
    },
    {
        "code": "LUAT_NHA_O_2023",
        "title": "Luật Nhà ở 2023",
        "reference": "Luật số 27/2023/QH15",
    },
    {
        "code": "LUAT_KDBDS_2023",
        "title": "Luật Kinh doanh bất động sản 2023",
        "reference": "Luật số 29/2023/QH15",
    },
    {
        "code": "LTM_2005",
        "title": "Luật Thương mại 2005",
        "reference": "Luật số 36/2005/QH11",
    },
]

# LegalArticle nodes. Each links to a document (CONTAINS) and may apply to a
# clause type (APPLIES_TO) and/or reference a risk type (REFERENCES).
# `summary` is a short paraphrase, NOT the verbatim legal text.
LEGAL_ARTICLES: List[Dict[str, Any]] = [
    {
        "code": "BLDS_2015_D328",
        "document": "BLDS_2015",
        "title": "Điều 328 - Đặt cọc",
        "summary": (
            "Đặt cọc là việc một bên giao cho bên kia một khoản tiền hoặc tài sản "
            "trong một thời hạn để bảo đảm giao kết hoặc thực hiện hợp đồng. Nếu bên "
            "đặt cọc từ chối thì mất cọc; nếu bên nhận cọc từ chối thì phải trả lại "
            "cọc và một khoản tiền tương đương (phạt cọc), trừ khi có thỏa thuận khác."
        ),
        "clauseType": "DEPOSIT",
        "riskType": "DEPOSIT_RISK",
    },
    {
        "code": "BLDS_2015_D385",
        "document": "BLDS_2015",
        "title": "Điều 385 - Khái niệm hợp đồng",
        "summary": (
            "Hợp đồng là sự thỏa thuận giữa các bên về việc xác lập, thay đổi hoặc "
            "chấm dứt quyền, nghĩa vụ dân sự."
        ),
        "clauseType": "RIGHTS_OBLIGATIONS",
        "riskType": "UNBALANCED_OBLIGATION_RISK",
    },
    {
        "code": "BLDS_2015_D398",
        "document": "BLDS_2015",
        "title": "Điều 398 - Nội dung của hợp đồng",
        "summary": (
            "Các bên có quyền thỏa thuận về nội dung hợp đồng, thường gồm: đối tượng, "
            "số lượng/chất lượng, giá và phương thức thanh toán, thời hạn/địa điểm/"
            "phương thức thực hiện, quyền và nghĩa vụ, trách nhiệm do vi phạm, và "
            "phương thức giải quyết tranh chấp."
        ),
        "clauseType": "OBJECT",
        "riskType": "MISSING_CLAUSE_RISK",
    },
    {
        "code": "BLDS_2015_D418",
        "document": "BLDS_2015",
        "title": "Điều 418 - Thỏa thuận phạt vi phạm",
        "summary": (
            "Phạt vi phạm là thỏa thuận để bên vi phạm nộp một khoản tiền cho bên bị "
            "vi phạm. Mức phạt do các bên thỏa thuận, trừ trường hợp luật liên quan có "
            "quy định khác."
        ),
        "clauseType": "PENALTY",
        "riskType": "PENALTY_RISK",
    },
    {
        "code": "BLDS_2015_D419",
        "document": "BLDS_2015",
        "title": "Điều 419 - Thiệt hại được bồi thường do vi phạm hợp đồng",
        "summary": (
            "Bên có quyền có thể yêu cầu bồi thường thiệt hại do vi phạm nghĩa vụ hợp "
            "đồng, bao gồm thiệt hại vật chất và tinh thần theo quy định."
        ),
        "clauseType": "COMPENSATION",
        "riskType": "COMPENSATION_RISK",
    },
    {
        "code": "BLDS_2015_D156",
        "document": "BLDS_2015",
        "title": "Điều 156 - Sự kiện bất khả kháng",
        "summary": (
            "Sự kiện bất khả kháng là sự kiện xảy ra khách quan, không thể lường trước "
            "và không thể khắc phục dù đã áp dụng mọi biện pháp cần thiết trong khả năng."
        ),
        "clauseType": "FORCE_MAJEURE",
        "riskType": "AMBIGUOUS_LANGUAGE_RISK",
    },
    {
        "code": "LUAT_DAT_DAI_2024_D45",
        "document": "LUAT_DAT_DAI_2024",
        "title": "Điều 45 - Điều kiện chuyển nhượng quyền sử dụng đất",
        "summary": (
            "Việc chuyển nhượng quyền sử dụng đất thường yêu cầu: có Giấy chứng nhận, "
            "đất không có tranh chấp, quyền sử dụng đất không bị kê biên/áp dụng biện "
            "pháp bảo đảm thi hành án, và còn trong thời hạn sử dụng đất."
        ),
        "clauseType": "OBJECT",
        "riskType": "LAND_LEGAL_STATUS_RISK",
    },
    {
        "code": "LUAT_NHA_O_2023_HDTN",
        "document": "LUAT_NHA_O_2023",
        "title": "Hợp đồng thuê nhà ở - nội dung cơ bản",
        "summary": (
            "Hợp đồng thuê nhà ở cần có các nội dung cơ bản như thông tin các bên, mô "
            "tả nhà ở, giá thuê và phương thức thanh toán, thời hạn thuê, quyền và "
            "nghĩa vụ của các bên, và các thỏa thuận khác."
        ),
        "clauseType": "PARTY_INFO",
        "riskType": "PARTY_INFORMATION_RISK",
    },
    {
        "code": "LTM_2005_D301",
        "document": "LTM_2005",
        "title": "Điều 301 - Mức phạt vi phạm trong thương mại",
        "summary": (
            "Trong hoạt động thương mại, mức phạt vi phạm do các bên thỏa thuận nhưng "
            "không vượt quá 8% giá trị phần nghĩa vụ hợp đồng bị vi phạm, trừ trường "
            "hợp đặc thù theo luật."
        ),
        "clauseType": "PENALTY",
        "riskType": "PENALTY_RISK",
    },
    {
        "code": "LTM_2005_D292",
        "document": "LTM_2005",
        "title": "Điều 292 - Các loại chế tài trong thương mại",
        "summary": (
            "Các chế tài thương mại gồm: buộc thực hiện đúng hợp đồng, phạt vi phạm, "
            "buộc bồi thường thiệt hại, tạm ngừng/đình chỉ/hủy bỏ hợp đồng và các biện "
            "pháp khác do các bên thỏa thuận."
        ),
        "clauseType": "PENALTY",
        "riskType": "DISPUTE_RESOLUTION_RISK",
    },
    {
        "code": "BLDS_2015_D117",
        "document": "BLDS_2015",
        "title": "Điều 117 - Điều kiện có hiệu lực của giao dịch dân sự",
        "summary": (
            "Giao dịch dân sự có hiệu lực khi: chủ thể có năng lực pháp luật và năng lực "
            "hành vi phù hợp; tham gia hoàn toàn tự nguyện; mục đích và nội dung không "
            "vi phạm điều cấm của luật, không trái đạo đức xã hội. Hình thức là điều kiện "
            "có hiệu lực nếu luật có quy định."
        ),
        "clauseType": "PARTY_INFO",
        "riskType": "PARTY_INFORMATION_RISK",
    },
    {
        "code": "BLDS_2015_D119",
        "document": "BLDS_2015",
        "title": "Điều 119 - Hình thức giao dịch dân sự",
        "summary": (
            "Giao dịch dân sự được thể hiện bằng lời nói, văn bản hoặc hành vi cụ thể. "
            "Trường hợp luật quy định phải bằng văn bản có công chứng, chứng thực, đăng ký "
            "thì phải tuân theo quy định đó."
        ),
        "clauseType": "PARTY_INFO",
        "riskType": "MISSING_CLAUSE_RISK",
    },
    {
        "code": "BLDS_2015_D401",
        "document": "BLDS_2015",
        "title": "Điều 401 - Hiệu lực của hợp đồng",
        "summary": (
            "Hợp đồng được giao kết hợp pháp có hiệu lực từ thời điểm giao kết, trừ khi "
            "có thỏa thuận khác hoặc luật liên quan có quy định khác. Từ thời điểm có "
            "hiệu lực, các bên phải thực hiện quyền và nghĩa vụ theo cam kết."
        ),
        "clauseType": "RIGHTS_OBLIGATIONS",
        "riskType": "UNBALANCED_OBLIGATION_RISK",
    },
    {
        "code": "BLDS_2015_D423",
        "document": "BLDS_2015",
        "title": "Điều 423 - Hủy bỏ hợp đồng",
        "summary": (
            "Một bên có quyền hủy bỏ hợp đồng và không phải bồi thường khi: bên kia vi "
            "phạm điều kiện hủy bỏ mà các bên đã thỏa thuận; bên kia vi phạm nghiêm trọng "
            "nghĩa vụ; hoặc trường hợp khác do luật quy định."
        ),
        "clauseType": "TERMINATION",
        "riskType": "TERMINATION_RISK",
    },
    {
        "code": "BLDS_2015_D428",
        "document": "BLDS_2015",
        "title": "Điều 428 - Đơn phương chấm dứt thực hiện hợp đồng",
        "summary": (
            "Một bên có quyền đơn phương chấm dứt thực hiện hợp đồng và không phải bồi "
            "thường khi bên kia vi phạm nghiêm trọng nghĩa vụ hoặc theo thỏa thuận/luật "
            "định. Bên đơn phương chấm dứt phải thông báo cho bên kia."
        ),
        "clauseType": "TERMINATION",
        "riskType": "TERMINATION_RISK",
    },
    {
        "code": "BLDS_2015_D440",
        "document": "BLDS_2015",
        "title": "Điều 440 - Nghĩa vụ trả tiền",
        "summary": (
            "Bên mua có nghĩa vụ thanh toán tiền theo thời hạn, địa điểm và mức tiền đã "
            "thỏa thuận. Nếu không có thỏa thuận thì áp dụng theo quy định pháp luật và "
            "tập quán."
        ),
        "clauseType": "PAYMENT",
        "riskType": "PAYMENT_RISK",
    },
    {
        "code": "BLDS_2015_D472",
        "document": "BLDS_2015",
        "title": "Điều 472 - Hợp đồng thuê tài sản",
        "summary": (
            "Hợp đồng thuê tài sản là sự thỏa thuận, theo đó bên cho thuê giao tài sản "
            "cho bên thuê sử dụng trong một thời hạn, bên thuê phải trả tiền thuê."
        ),
        "clauseType": "OBJECT",
        "riskType": "OBJECT_DESCRIPTION_RISK",
    },
    {
        "code": "BLDS_2015_D513",
        "document": "BLDS_2015",
        "title": "Điều 513 - Hợp đồng dịch vụ",
        "summary": (
            "Hợp đồng dịch vụ là sự thỏa thuận, theo đó bên cung ứng dịch vụ thực hiện "
            "công việc cho bên sử dụng dịch vụ, bên sử dụng dịch vụ phải trả tiền dịch vụ."
        ),
        "clauseType": "OBJECT",
        "riskType": "SERVICE_SCOPE_RISK",
    },
    {
        "code": "BLDS_2015_D14",
        "document": "BLDS_2015",
        "title": "Điều 14 - Bảo vệ quyền dân sự thông qua cơ quan có thẩm quyền",
        "summary": (
            "Tòa án, cơ quan có thẩm quyền có trách nhiệm bảo vệ quyền dân sự của cá nhân, "
            "pháp nhân. Việc giải quyết tranh chấp được thực hiện theo thủ tục luật định."
        ),
        "clauseType": "DISPUTE",
        "riskType": "DISPUTE_RESOLUTION_RISK",
    },
    {
        "code": "LUAT_DAT_DAI_2024_D27",
        "document": "LUAT_DAT_DAI_2024",
        "title": "Điều 27 - Quyền chuyển đổi, chuyển nhượng, cho thuê quyền sử dụng đất",
        "summary": (
            "Người sử dụng đất được thực hiện các quyền chuyển đổi, chuyển nhượng, cho "
            "thuê, thừa kế, tặng cho, thế chấp, góp vốn bằng quyền sử dụng đất theo quy "
            "định của pháp luật khi đáp ứng đủ điều kiện."
        ),
        "clauseType": "OBJECT",
        "riskType": "OWNERSHIP_RIGHT_RISK",
    },
    {
        "code": "LUAT_KDBDS_2023_DATCOC",
        "document": "LUAT_KDBDS_2023",
        "title": "Quy định về đặt cọc trong kinh doanh bất động sản hình thành trong tương lai",
        "summary": (
            "Chủ đầu tư dự án bất động sản chỉ được thu tiền đặt cọc theo thỏa thuận với "
            "khách hàng khi nhà ở, công trình đã đủ điều kiện đưa vào kinh doanh; số tiền "
            "đặt cọc và việc xử lý đặt cọc phải minh bạch theo hợp đồng."
        ),
        "clauseType": "DEPOSIT",
        "riskType": "DEPOSIT_RISK",
    },
]
