package com.analyzer.api.dto.chatmessage;

import com.analyzer.api.dto.ai.RagQueryResponse;
import com.analyzer.api.dto.chatsession.ChatSessionResponse;
import com.analyzer.api.enums.ChatMode;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResponse {
    private ChatSessionResponse chatSession;
    private ChatMessageResponse userMessage;
    private ChatMessageResponse assistantMessage;
    private String answer;
    private ChatMode resolvedMode;
    private List<RagQueryResponse.Citation> sources;
    private RiskLevel riskLevel;
    private SuggestionType suggestionType;
    private List<UserActionHint> userActionHints;
    private String assistantMessageId;
    private String intent;
    private List<String> intents;
    private List<String> suggestedActions;
    private List<String> selectedDocumentIds;
    private String draftingPrompt;
    private Boolean redactionRequired;
    private String contractType;
    private String draftingStatus;
    private List<RagQueryResponse.DraftingQuestion> questions;
    private Map<String, String> providedInformation;
    private List<String> draftingMissingInformation;
    private String privacyWarning;
    private String draftingOriginalRequirement;
}
