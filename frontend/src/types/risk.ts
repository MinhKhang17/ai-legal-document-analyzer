export type RiskLevel = 'critical' | 'high' | 'medium' | 'low' | 'none';

export interface RiskFinding {
  id: string;
  title: string;
  level: RiskLevel;
  clauseRef: string;
  summary: string;
  plainLanguage: string;
  legalBasis: string;
  citation: string;
  suggestedAction: string;
}

export interface RiskDistribution {
  critical: number;
  high: number;
  medium: number;
  low: number;
}
