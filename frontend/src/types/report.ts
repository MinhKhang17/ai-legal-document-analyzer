import type { RiskLevel } from './risk';
import type { Status } from './status';

export interface Report {
  id: string;
  title: string;
  template: string;
  documentName: string;
  author: string;
  status: Status;
  riskLevel: RiskLevel;
  createdAt: string;
  summary: string;
}
