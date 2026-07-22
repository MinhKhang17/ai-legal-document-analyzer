from app.models.intent_enums import ContractType, LegalQueryIntent
from app.services.completeness_checker import check_completeness


def test_general_legal_question_does_not_require_an_attached_document():
    result = check_completeness(
        LegalQueryIntent.GENERAL_LEGAL_QUESTION,
        contract_type=ContractType.UNKNOWN,
        has_user_chunks=False,
        question="What is a force majeure clause?",
    )

    assert result.is_complete
    assert result.missing_items == []
    assert result.questions_to_ask == []


def test_contract_type_guidance_does_not_require_an_attached_document():
    result = check_completeness(
        LegalQueryIntent.CONTRACT_TYPE_ANALYSIS,
        contract_type=ContractType.UNKNOWN,
        has_user_chunks=False,
        question="Explain common types of contracts.",
    )

    assert result.is_complete
    assert result.missing_items == []
