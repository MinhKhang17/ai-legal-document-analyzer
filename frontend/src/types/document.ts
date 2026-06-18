import type { RiskLevel } from './risk';
import type { Status } from './status';

export interface LegalDocument {
  id: string;
  name: string;
  type: string;
  projectId: string;
  projectName: string;
  status: Status;
  riskLevel: RiskLevel;
  owner: string;
  pages: number;
  size: string;
  jurisdiction: string;
  updatedAt: string;
  summary: string;
  clauses: Array<{
    ref: string;
    title: string;
    text: string;
    riskLevel: RiskLevel;
  }>;
}
