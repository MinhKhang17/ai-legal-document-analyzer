import type { RiskLevel } from './risk';
import type { Status } from './status';

export interface ProjectMember {
  id: string;
  name: string;
  role: string;
  initials: string;
}

export interface Project {
  id: string;
  name: string;
  client: string;
  description: string;
  status: Status;
  riskLevel: RiskLevel;
  owner: string;
  jurisdiction: string;
  documentCount: number;
  highRiskCount: number;
  updatedAt: string;
  progress: number;
  members: ProjectMember[];
  tags: string[];
}
