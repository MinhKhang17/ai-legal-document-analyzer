export interface AiRiskAssessment {
  requestId: string | null;
  confidenceScore: number | null;
  riskLevel: string | null;
  shouldSuggestLawyer: boolean | null;
  suggestionType: string | null;
  suggestionReason: string | null;
  missingInformation: string | null;
  userActionHint: string | null;
}

export interface AiFeatureSummary {
  requestId: string | null;
  confidenceScore: number | null;
  riskLevel: string | null;
  summary: string | null;
}

export interface AiCitation {
  id: string;
  sourceType: string | null;
  sourceReferenceId: string | null;
  label: string | null;
  excerpt: string | null;
  pageNumber: number | null;
  chunkIndex: number | null;
  score: number | null;
  uri: string | null;
}
