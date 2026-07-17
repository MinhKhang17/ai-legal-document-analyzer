package com.analyzer.api.enums;

/**
 * AI guidance on what the user should do next when the answer is uncertain
 * or the legal risk is high.
 */
public enum SuggestionType {
    DIRECT_ANSWER,
    ASK_UPLOAD_CONTRACT,
    ASK_CONTRACT_TYPE,
    ASK_USER_ROLE,
    ASK_TARGET_CLAUSE,
    ASK_MORE_FACTS,
    SUGGEST_REVISE_CLAUSE,
    SUGGEST_NEGOTIATION,
    REDIRECT_TO_SUPPORTED_SCOPE,
    REFUSE_AND_REDIRECT,
    NONE,
    ASK_MORE_INFO,
    SUGGEST_LAWYER,
    REQUIRE_LAWYER
}
