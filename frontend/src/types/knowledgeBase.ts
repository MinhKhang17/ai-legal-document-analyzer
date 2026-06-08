import type { Status } from './status';

export interface KnowledgeArticle {
  id: string;
  title: string;
  category: string;
  jurisdiction: string;
  status: Status;
  updatedAt: string;
  chunks: number;
  impactedContracts: number;
  summary: string;
}
