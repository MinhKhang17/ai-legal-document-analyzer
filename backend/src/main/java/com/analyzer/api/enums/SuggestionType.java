package com.analyzer.api.enums;

/**
 * AI guidance on what the user should do next when the answer is uncertain
 * or the legal risk is high.
 */
public enum SuggestionType {
    NONE,
    ASK_MORE_INFO,
    SUGGEST_LAWYER,
    REQUIRE_LAWYER
}
