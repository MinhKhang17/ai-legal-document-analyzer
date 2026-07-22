from __future__ import annotations

import re
from dataclasses import dataclass

from app.schemas import DraftingQuestion, RagQueryRequest, RagQueryResponse, RagUsage


PRIVACY_WARNING = (
    "Dữ liệu nhận diện trực tiếp sẽ được thay bằng placeholder. Hãy rà soát prompt trước khi "
    "tự sao chép sang nền tảng bên ngoài; hệ thống không tự gửi prompt hoặc tài liệu của bạn."
)

_REDACTION_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("EMAIL", re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE)),
    ("PHONE", re.compile(r"(?<!\d)(?:\+?84|0)(?:[ .-]?\d){8,10}(?!\d)")),
    ("IDENTITY_NUMBER", re.compile(r"(?<!\d)\d{9,12}(?!\d)")),
    ("BANK_ACCOUNT", re.compile(r"(?i)(?:tai khoan|tài khoản|stk|account)\s*[:#-]?\s*\d{6,20}")),
    ("TAX_CODE", re.compile(r"(?i)(?:ma so thue|mã số thuế|mst|tax code)\s*[:#-]?\s*[\d-]{8,16}")),
    ("ADDRESS", re.compile(r"(?i)(?:địa chỉ|ở tại)\s*[:#-]?\s*[^,;\n]+")),
    ("FULL_NAME", re.compile(r"(?i)(?:họ và tên|họ tên|tên là|cho ông|cho bà|bên a là|bên b là)\s*[:#-]?\s*[A-Za-zÀ-ỹ ]{3,}")),
)


def redact_sensitive_text(value: str) -> tuple[str, bool]:
    result = value
    changed = False
    counters: dict[str, int] = {}
    replacements: dict[tuple[str, str], str] = {}
    for label, pattern in _REDACTION_PATTERNS:
        def replace(match: re.Match[str], current_label: str = label) -> str:
            nonlocal changed
            changed = True
            key = (current_label, match.group(0).lower())
            if key not in replacements:
                counters[current_label] = counters.get(current_label, 0) + 1
                replacements[key] = f"[{current_label}_{counters[current_label]}]"
            return replacements[key]
        result = pattern.sub(replace, result)
    return result, changed


@dataclass(frozen=True)
class ContractDefinition:
    label: str
    aliases: tuple[str, ...]
    questions: tuple[tuple[str, str, str], ...]
    structures: tuple[str, ...]


def _q(key: str, label: str, placeholder: str) -> tuple[str, str, str]:
    return key, label, placeholder


CONTRACTS: dict[str, ContractDefinition] = {
    "EMPLOYMENT_CONTRACT": ContractDefinition(
        "Hợp đồng lao động",
        ("hợp đồng lao động", "lao động", "việc làm", "nhân viên"),
        (
            _q("employer", "Thông tin người sử dụng lao động", "[NGƯỜI_SỬ_DỤNG_LAO_ĐỘNG]"),
            _q("employee", "Thông tin người lao động", "[NGƯỜI_LAO_ĐỘNG]"),
            _q("job", "Chức danh và công việc", "[CÔNG_VIỆC]"),
            _q("workplace", "Địa điểm làm việc", "[ĐỊA_ĐIỂM_LÀM_VIỆC]"),
            _q("term", "Loại và thời hạn hợp đồng", "[THỜI_HẠN]"),
            _q("salary", "Lương, phụ cấp và ngày trả lương", "[MỨC_LƯƠNG]"),
            _q("working_time", "Thời giờ làm việc và nghỉ ngơi", "[THỜI_GIỜ_LÀM_VIỆC]"),
            _q("insurance", "Bảo hiểm và quyền lợi liên quan", "[CHẾ_ĐỘ_BẢO_HIỂM]"),
            _q("termination", "Điều kiện chấm dứt và thời hạn báo trước", "[ĐIỀU_KIỆN_CHẤM_DỨT]"),
            _q("special_requirements", "Yêu cầu đặc biệt khác", "[YÊU_CẦU_KHÁC]"),
        ),
        ("Tên hợp đồng", "Thông tin các bên", "Công việc và địa điểm làm việc", "Thời hạn và thử việc",
         "Lương, phụ cấp và phương thức thanh toán", "Thời giờ làm việc và nghỉ ngơi", "Bảo hiểm và quyền lợi",
         "Quyền và nghĩa vụ các bên", "Bảo mật và tài sản", "Chấm dứt hợp đồng", "Vi phạm và bồi thường",
         "Giải quyết tranh chấp", "Hiệu lực", "Thông tin còn thiếu", "Nội dung cần luật sư kiểm tra"),
    ),
    "HOUSE_RENTAL_CONTRACT": ContractDefinition(
        "Hợp đồng thuê nhà",
        ("thuê nhà", "thuê trọ", "thuê phòng", "cho thuê nhà"),
        (
            _q("user_role", "Bạn là bên cho thuê hay bên thuê?", "[VAI_TRÒ_NGƯỜI_DÙNG]"),
            _q("property_address", "Địa chỉ nhà", "[ĐỊA_CHỈ_NHÀ]"),
            _q("property_type", "Loại nhà hoặc phòng", "[LOẠI_NHÀ]"),
            _q("rental_purpose", "Mục đích thuê", "[MỤC_ĐÍCH_THUÊ]"),
            _q("rental_term", "Thời hạn thuê", "[THỜI_HẠN_THUÊ]"),
            _q("monthly_rent", "Giá thuê hàng tháng", "[GIÁ_THUÊ]"),
            _q("deposit", "Tiền đặt cọc", "[TIỀN_ĐẶT_CỌC]"),
            _q("payment", "Phương thức và ngày thanh toán", "[PHƯƠNG_THỨC_THANH_TOÁN]"),
            _q("utilities", "Trách nhiệm trả điện, nước và phí dịch vụ", "[CHI_PHÍ_DỊCH_VỤ]"),
            _q("handover", "Tình trạng và ngày bàn giao", "[NGÀY_BÀN_GIAO]"),
            _q("maintenance", "Trách nhiệm sửa chữa, bảo trì", "[TRÁCH_NHIỆM_BẢO_TRÌ]"),
            _q("early_termination", "Điều kiện chấm dứt trước hạn", "[ĐIỀU_KIỆN_CHẤM_DỨT]"),
            _q("subleasing", "Có cho phép thuê lại không?", "[QUYỀN_CHO_THUÊ_LẠI]"),
            _q("assets", "Nội thất hoặc tài sản kèm theo", "[TÀI_SẢN_KÈM_THEO]"),
            _q("special_requirements", "Yêu cầu đặc biệt khác", "[YÊU_CẦU_KHÁC]"),
        ),
        ("Tên hợp đồng", "Thông tin bên cho thuê", "Thông tin bên thuê", "Mô tả nhà và tài sản kèm theo",
         "Mục đích thuê", "Thời hạn thuê", "Giá thuê, đặt cọc và thanh toán", "Bàn giao nhà",
         "Điện, nước và phí dịch vụ", "Quyền và nghĩa vụ bên cho thuê", "Quyền và nghĩa vụ bên thuê",
         "Sửa chữa, bảo trì và bồi thường tài sản", "Cho thuê lại", "Chấm dứt và xử lý đặt cọc",
         "Vi phạm và bồi thường", "Giải quyết tranh chấp", "Hiệu lực", "Thông tin còn thiếu",
         "Nội dung cần luật sư kiểm tra"),
    ),
    "LOAN_CONTRACT": ContractDefinition(
        "Hợp đồng vay tiền",
        ("vay tiền", "cho vay", "hợp đồng vay", "giấy vay"),
        (
            _q("party_type", "Khoản vay giữa cá nhân hay tổ chức?", "[LOẠI_CHỦ_THỂ]"),
            _q("lender", "Thông tin bên cho vay", "[BÊN_CHO_VAY]"),
            _q("borrower", "Thông tin bên vay", "[BÊN_VAY]"),
            _q("principal", "Số tiền vay", "[SỐ_TIỀN_VAY]"),
            _q("purpose", "Mục đích vay", "[MỤC_ĐÍCH_VAY]"),
            _q("disbursement", "Phương thức giải ngân", "[PHƯƠNG_THỨC_GIẢI_NGÂN]"),
            _q("term", "Thời hạn vay", "[THỜI_HẠN_VAY]"),
            _q("interest", "Lãi suất và cách tính lãi", "[LÃI_SUẤT]"),
            _q("repayment", "Lịch và phương thức trả nợ", "[LỊCH_TRẢ_NỢ]"),
            _q("security", "Tài sản bảo đảm hoặc người bảo lãnh", "[TÀI_SẢN_BẢO_ĐẢM]"),
            _q("late_payment", "Cách xử lý chậm trả", "[XỬ_LÝ_CHẬM_TRẢ]"),
            _q("early_repayment", "Điều kiện trả nợ trước hạn", "[TRẢ_NỢ_TRƯỚC_HẠN]"),
            _q("special_requirements", "Yêu cầu khác", "[YÊU_CẦU_KHÁC]"),
        ),
        ("Tên hợp đồng", "Thông tin bên cho vay", "Thông tin bên vay", "Số tiền và mục đích vay",
         "Phương thức giải ngân", "Thời hạn vay", "Lãi suất và cách tính", "Lịch trả nợ", "Trả trước hạn",
         "Tài sản bảo đảm hoặc bảo lãnh", "Quyền và nghĩa vụ các bên", "Chậm trả, vi phạm và bồi thường",
         "Chấm dứt nghĩa vụ", "Giải quyết tranh chấp", "Hiệu lực", "Thông tin còn thiếu",
         "Nội dung cần luật sư kiểm tra"),
    ),
    "SALE_OF_GOODS_CONTRACT": ContractDefinition(
        "Hợp đồng mua bán hàng hóa",
        ("mua bán hàng hóa", "mua bán", "bên bán", "bên mua"),
        (
            _q("seller", "Thông tin bên bán", "[BÊN_BÁN]"),
            _q("buyer", "Thông tin bên mua", "[BÊN_MUA]"),
            _q("goods", "Tên và mô tả hàng hóa", "[TÊN_HÀNG_HÓA]"),
            _q("quantity", "Số lượng", "[SỐ_LƯỢNG]"),
            _q("quality", "Chất lượng hoặc tiêu chuẩn kỹ thuật", "[TIÊU_CHUẨN_CHẤT_LƯỢNG]"),
            _q("price", "Đơn giá và tổng giá trị", "[TỔNG_GIÁ_TRỊ]"),
            _q("tax", "Cách xử lý thuế", "[THUẾ]"),
            _q("payment", "Tiến độ và phương thức thanh toán", "[PHƯƠNG_THỨC_THANH_TOÁN]"),
            _q("delivery", "Địa điểm và thời hạn giao hàng", "[ĐỊA_ĐIỂM_GIAO_HÀNG]"),
            _q("acceptance", "Kiểm tra và nghiệm thu", "[ĐIỀU_KIỆN_NGHIỆM_THU]"),
            _q("warranty", "Bảo hành", "[ĐIỀU_KIỆN_BẢO_HÀNH]"),
            _q("risk_transfer", "Chuyển quyền sở hữu và rủi ro", "[THỜI_ĐIỂM_CHUYỂN_RỦI_RO]"),
            _q("returns", "Điều kiện đổi trả hoặc thay thế", "[ĐIỀU_KIỆN_ĐỔI_TRẢ]"),
            _q("penalties", "Phạt và bồi thường", "[PHẠT_VÀ_BỒI_THƯỜNG]"),
            _q("special_requirements", "Yêu cầu khác", "[YÊU_CẦU_KHÁC]"),
        ),
        ("Tên hợp đồng", "Thông tin bên bán", "Thông tin bên mua", "Hàng hóa", "Số lượng và chất lượng",
         "Giá và thuế", "Thanh toán", "Giao hàng", "Kiểm tra và nghiệm thu", "Chuyển quyền sở hữu và rủi ro",
         "Bảo hành", "Đổi trả hoặc thay thế", "Quyền và nghĩa vụ các bên", "Vi phạm, phạt và bồi thường",
         "Bất khả kháng", "Chấm dứt", "Giải quyết tranh chấp", "Hiệu lực", "Thông tin còn thiếu",
         "Nội dung cần luật sư kiểm tra"),
    ),
    "SERVICE_CONTRACT": ContractDefinition(
        "Hợp đồng dịch vụ",
        ("hợp đồng dịch vụ", "dịch vụ", "freelance"),
        (
            _q("provider", "Thông tin bên cung cấp dịch vụ", "[BÊN_CUNG_CẤP]"),
            _q("customer", "Thông tin bên sử dụng dịch vụ", "[BÊN_SỬ_DỤNG]"),
            _q("scope", "Phạm vi công việc và sản phẩm bàn giao", "[PHẠM_VI_DỊCH_VỤ]"),
            _q("term", "Thời hạn và tiến độ", "[THỜI_HẠN]"),
            _q("fee", "Phí dịch vụ và thanh toán", "[PHÍ_DỊCH_VỤ]"),
            _q("acceptance", "Tiêu chí nghiệm thu", "[TIÊU_CHÍ_NGHIỆM_THU]"),
            _q("confidentiality", "Bảo mật và quyền sở hữu kết quả", "[BẢO_MẬT_VÀ_SỞ_HỮU]"),
            _q("termination", "Điều kiện chấm dứt", "[ĐIỀU_KIỆN_CHẤM_DỨT]"),
            _q("special_requirements", "Yêu cầu khác", "[YÊU_CẦU_KHÁC]"),
        ),
        ("Tên hợp đồng", "Thông tin các bên", "Phạm vi dịch vụ", "Tiến độ và bàn giao", "Phí và thanh toán",
         "Nghiệm thu", "Quyền và nghĩa vụ", "Bảo mật và sở hữu kết quả", "Chấm dứt", "Vi phạm và bồi thường",
         "Giải quyết tranh chấp", "Hiệu lực", "Thông tin còn thiếu", "Nội dung cần luật sư kiểm tra"),
    ),
    "OTHER": ContractDefinition(
        "Loại hợp đồng khác",
        (),
        (
            _q("contract_name", "Tên hợp đồng", "[TÊN_HỢP_ĐỒNG]"),
            _q("purpose", "Mục đích hợp đồng", "[MỤC_ĐÍCH]"),
            _q("parties", "Các bên tham gia", "[BÊN_A] và [BÊN_B]"),
            _q("transaction", "Giao dịch hoặc công việc chính", "[NỘI_DUNG_CHÍNH]"),
            _q("payment", "Thanh toán hoặc đối giá", "[SỐ_TIỀN]"),
            _q("term", "Thời hạn", "[THỜI_HẠN]"),
            _q("risks", "Rủi ro chính cần xử lý", "[RỦI_RO_CHÍNH]"),
            _q("special_requirements", "Yêu cầu đặc biệt", "[YÊU_CẦU_KHÁC]"),
        ),
        ("Tên hợp đồng", "Thông tin các bên", "Mục đích và phạm vi", "Quyền và nghĩa vụ", "Thanh toán",
         "Thời hạn", "Bàn giao hoặc thực hiện", "Chấm dứt", "Vi phạm và bồi thường", "Giải quyết tranh chấp",
         "Hiệu lực", "Thông tin còn thiếu", "Nội dung cần luật sư kiểm tra"),
    ),
}


def detect_drafting_contract_type(text: str) -> str | None:
    normalized = text.lower()
    for contract_type, definition in CONTRACTS.items():
        if contract_type != "OTHER" and any(alias in normalized for alias in definition.aliases):
            return contract_type
    return None


def _clean_information(values: dict[str, str | None], allowed_keys: set[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for key, value in values.items():
        if key not in allowed_keys or value is None:
            continue
        normalized = str(value).strip()
        if normalized:
            result[key] = normalized
    return result


_FIELD_HINTS: dict[str, tuple[str, ...]] = {
    "property_address": ("địa chỉ nhà", "địa chỉ thuê"),
    "rental_term": ("thời hạn thuê",),
    "monthly_rent": ("giá thuê", "tiền thuê"),
    "deposit": ("tiền đặt cọc", "tiền cọc"),
    "principal": ("số tiền vay", "khoản vay"),
    "interest": ("lãi suất",),
    "term": ("thời hạn",),
    "repayment": ("lịch trả nợ", "phương thức trả nợ"),
    "goods": ("tên hàng hóa", "hàng hóa"),
    "quantity": ("số lượng",),
    "price": ("tổng giá trị", "đơn giá"),
    "delivery": ("giao hàng",),
    "job": ("công việc", "chức danh"),
    "salary": ("mức lương", "tiền lương", "lương"),
    "working_time": ("thời giờ làm việc", "giờ làm việc"),
    "scope": ("phạm vi công việc", "phạm vi dịch vụ"),
    "fee": ("phí dịch vụ",),
    "contract_name": ("tên hợp đồng",),
    "purpose": ("mục đích",),
}


def _extract_history_information(definition: ContractDefinition, text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    allowed = {key for key, _, _ in definition.questions}
    for key in allowed:
        for hint in _FIELD_HINTS.get(key, ()):
            match = re.search(
                rf"(?i)(?:^|[\n;,])\s*{re.escape(hint)}\s*(?::|=|-|là)?\s*([^\n;,]+)",
                text,
            )
            if match and match.group(1).strip():
                result[key] = match.group(1).strip().rstrip(".")
                break
    return result


def _questions(definition: ContractDefinition, provided: dict[str, str]) -> list[DraftingQuestion]:
    return [
        DraftingQuestion(key=key, label=label, placeholder=placeholder, required=False)
        for key, label, placeholder in definition.questions
        if key not in provided
    ]


def _response(
    request: RagQueryRequest,
    *,
    answer: str,
    status: str,
    contract_type: str | None,
    questions: list[DraftingQuestion] | None = None,
    provided: dict[str, str] | None = None,
    missing: list[str] | None = None,
    actions: list[str] | None = None,
    prompt: str | None = None,
    redacted: bool = False,
) -> RagQueryResponse:
    return RagQueryResponse(
        requestId=request.requestId,
        chatSessionId=request.chatSessionId,
        answer=answer,
        confidenceScore=1.0,
        shouldSuggestTicket=False,
        suggestionType="DIRECT_ANSWER",
        riskLevel="NONE",
        userActionHint="CONTINUE_CHAT",
        citations=[],
        usedKnowledgeCitationIds=[],
        usedUserCitationIds=[],
        retrievedUserChunks=0,
        retrievedKnowledgeChunks=0,
        intent="DRAFT_CONTRACT",
        intents=["DRAFT_CONTRACT"],
        contractType=contract_type,
        responseStatus=status,
        responseMode="DIRECT_ANSWER",
        inputComplete=status == "PROMPT_GENERATED",
        suggestedActions=actions or [],
        draftingPrompt=prompt,
        redactionRequired=redacted,
        draftingStatus=status,
        questions=questions or [],
        providedInformation=provided or {},
        draftingMissingInformation=missing or [],
        privacyWarning=PRIVACY_WARNING,
        draftingOriginalRequirement=(request.draftingOriginalRequirement or request.question).strip(),
        model="deterministic-drafting-workflow",
        usage=RagUsage(),
        llmExecuted=False,
    )


def _build_prompt(
    definition: ContractDefinition,
    original_requirement: str,
    provided: dict[str, str],
) -> tuple[str, bool, list[str]]:
    question_by_key = {key: (label, placeholder) for key, label, placeholder in definition.questions}
    missing_keys = [key for key, _, _ in definition.questions if key not in provided]
    directly_identifying_keys = {
        "employer", "employee", "lender", "borrower", "seller", "buyer", "provider", "customer",
        "parties", "property_address", "workplace",
    }
    external_values = dict(provided)
    structured_redaction = False
    for key in directly_identifying_keys.intersection(external_values):
        external_values[key] = question_by_key[key][1]
        structured_redaction = True

    serialized = "__REQUIREMENT__\t" + original_requirement.strip()
    for key, value in external_values.items():
        serialized += f"\n{key}\t{value}"
    redacted_serialized, pattern_redaction = redact_sensitive_text(serialized)
    changed = structured_redaction or pattern_redaction
    lines = redacted_serialized.splitlines()
    redacted_requirement = lines[0].split("\t", 1)[1] if "\t" in lines[0] else original_requirement
    redacted_values = dict(line.split("\t", 1) for line in lines[1:] if "\t" in line)

    provided_lines = [
        f"- {question_by_key[key][0]}: {redacted_values.get(key, value)}"
        for key, value in provided.items()
    ] or ["- Chưa có thông tin chi tiết."]
    missing_lines = [
        f"- {question_by_key[key][0]}: {question_by_key[key][1]}"
        for key in missing_keys
    ] or ["- Không còn trường thông tin nào trong biểu mẫu đang để trống."]
    placeholder_list = sorted({placeholder for _, _, placeholder in definition.questions})
    structure_lines = [f"{index}. {section}" for index, section in enumerate(definition.structures, 1)]

    prompt = (
        "Bạn là trợ lý hỗ trợ soạn thảo hợp đồng theo pháp luật Việt Nam.\n\n"
        f"Hãy tạo BẢN NHÁP {definition.label.upper()} để người dùng rà soát. "
        "Đây không phải tư vấn pháp lý cuối cùng; không khẳng định văn bản hoàn toàn hợp pháp, không có rủi ro hoặc đã sẵn sàng ký.\n\n"
        f"Yêu cầu của người dùng:\n{redacted_requirement or '[YÊU_CẦU_NGƯỜI_DÙNG]'}\n\n"
        "Thông tin đã cung cấp:\n" + "\n".join(provided_lines) + "\n\n"
        "Thông tin còn thiếu:\n" + "\n".join(missing_lines) + "\n\n"
        "Quy tắc:\n"
        "- Không suy đoán tên, địa chỉ, số định danh, mã số thuế, số tiền, ngày tháng hoặc sự kiện pháp lý còn thiếu.\n"
        "- Dùng đúng một placeholder nhất quán cho cùng một thông tin trong toàn bộ bản nháp.\n"
        f"- Ưu tiên các placeholder: {', '.join(placeholder_list)}.\n"
        "- Đánh dấu rõ nội dung người dùng cần bổ sung và các giả định cần xác minh.\n"
        "- Không tự xác định mức lãi, mức phạt, thuế suất hoặc bồi thường khi chưa có dữ kiện và căn cứ phù hợp.\n"
        "- Cuối bản nháp phải có mục 'Thông tin còn thiếu' và 'Nội dung cần luật sư kiểm tra'.\n\n"
        "Cấu trúc:\n" + "\n".join(structure_lines)
    )
    return prompt, changed, missing_keys


def build_drafting_response(request: RagQueryRequest) -> RagQueryResponse:
    history_text = " ".join(
        message.content for message in request.recentHistory if message.role.upper() == "USER"
    )
    contract_type = (request.draftingContractType or "").strip().upper() or None
    if contract_type not in CONTRACTS:
        contract_type = detect_drafting_contract_type(f"{history_text} {request.question}")

    if contract_type is None:
        return _response(
            request,
            answer="Bạn muốn soạn loại hợp đồng nào?",
            status="NEED_CONTRACT_TYPE",
            contract_type=None,
            actions=["SELECT_CONTRACT_TYPE"],
        )

    definition = CONTRACTS[contract_type]
    allowed_keys = {key for key, _, _ in definition.questions}
    provided = _extract_history_information(definition, history_text)
    provided.update(_clean_information(request.draftingInformation, allowed_keys))
    remaining_questions = _questions(definition, provided)
    missing_labels = [question.label for question in remaining_questions]
    action = (request.draftingAction or "").strip().upper()
    original_requirement = (request.draftingOriginalRequirement or request.question).strip()

    if action in {"GENERATE_PROMPT", "CONTINUE_WITH_PLACEHOLDERS"}:
        prompt, redacted, _ = _build_prompt(definition, original_requirement, provided)
        return _response(
            request,
            answer=(
                "Tôi đã tạo prompt soạn thảo để bạn rà soát và sử dụng trên ChatGPT. "
                "Dữ liệu nhận diện trực tiếp đã được thay bằng placeholder. "
                "Hãy kiểm tra lại nội dung trước khi sao chép sang nền tảng bên ngoài."
            ),
            status="PROMPT_GENERATED",
            contract_type=contract_type,
            questions=[
                DraftingQuestion(key=key, label=label, placeholder=placeholder, required=False)
                for key, label, placeholder in definition.questions
            ],
            provided=provided,
            missing=missing_labels,
            actions=["EDIT_INFORMATION", "COPY_PROMPT", "OPEN_CHATGPT"],
            prompt=prompt,
            redacted=redacted,
        )

    if action == "ANSWER_QUESTIONS":
        return _response(
            request,
            answer="Mình đã ghi nhận thông tin. Bạn có thể chỉnh sửa hoặc tiếp tục tạo prompt với placeholder cho các mục còn thiếu.",
            status="READY_TO_GENERATE_PROMPT",
            contract_type=contract_type,
            questions=remaining_questions,
            provided=provided,
            missing=missing_labels,
            actions=["EDIT_INFORMATION", "CONTINUE_WITH_PLACEHOLDERS", "GENERATE_PROMPT"],
        )

    return _response(
        request,
        answer=f"Bạn có thể cung cấp các thông tin cần thiết cho {definition.label}. Mục nào chưa biết có thể để trống.",
        status="NEED_MORE_INFORMATION",
        contract_type=contract_type,
        questions=remaining_questions,
        provided=provided,
        missing=missing_labels,
        actions=["ANSWER_QUESTIONS", "CONTINUE_WITH_PLACEHOLDERS"],
    )
