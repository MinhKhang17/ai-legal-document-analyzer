export type ExpertTicketRequiredField =
  | 'title'
  | 'description'
  | 'legalIssueCategory'
  | 'userExpectedOutcome';

export interface ExpertTicketRequiredValues {
  title: string;
  description: string;
  legalIssueCategory: string;
  userExpectedOutcome: string;
}

export const getMissingExpertTicketFields = (
  values: ExpertTicketRequiredValues,
): ExpertTicketRequiredField[] =>
  (Object.entries(values) as Array<[ExpertTicketRequiredField, string]>)
    .filter(([, value]) => !value.trim())
    .map(([field]) => field);
