import type { KnowledgeAction, KnowledgeStatus } from '../types/knowledgeBase';

const ACTIONS: Record<KnowledgeStatus, readonly KnowledgeAction[]> = {
  UPLOADED: ['INGEST', 'ARCHIVE'], PROCESSING: [], INGESTED: ['REVIEW', 'ARCHIVE'],
  REVIEWING: ['PUBLISH', 'ARCHIVE'], PUBLIC: ['ARCHIVE'], ARCHIVED: [], FAILED: ['INGEST', 'ARCHIVE'],
};
export const getKnowledgeActions = (status?: string | null): readonly KnowledgeAction[] =>
  status && status in ACTIONS ? ACTIONS[status as KnowledgeStatus] : [];
export const canKnowledgeAction = (status: string | null | undefined, action: KnowledgeAction): boolean => getKnowledgeActions(status).includes(action);
