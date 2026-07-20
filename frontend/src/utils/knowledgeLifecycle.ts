import type { KnowledgeAction, KnowledgeStatus } from '../types/knowledgeBase';

const ACTIONS: Record<KnowledgeStatus, readonly KnowledgeAction[]> = {
  PENDING: ['INGEST', 'ARCHIVE'],
  UPLOADED: ['INGEST', 'ARCHIVE'], PROCESSING: [], INGESTED: ['REVIEW', 'ARCHIVE'],
  REVIEWING: ['REVIEW', 'ARCHIVE'], PUBLIC: ['UNPUBLISH', 'ARCHIVE'], ARCHIVED: [], FAILED: ['INGEST', 'ARCHIVE'],
};
export const getKnowledgeActions = (status?: string | null): readonly KnowledgeAction[] =>
  status && status in ACTIONS ? ACTIONS[status as KnowledgeStatus] : [];
export const canKnowledgeAction = (status: string | null | undefined, action: KnowledgeAction): boolean => getKnowledgeActions(status).includes(action);
