import { Badge, type BadgeTone } from './Badge';
import { useI18n } from '../../hooks/useI18n';
import type { Status } from '../../types/status';

interface StatusBadgeProps {
  status: Status;
  className?: string;
}

const statusTone: Record<Status, BadgeTone> = {
  ready: 'green',
  processing: 'gold',
  pending: 'amber',
  success: 'green',
  failed: 'red',
  active: 'green',
  offline: 'slate',
  online: 'green',
  draft: 'slate',
  finalized: 'blue',
  archived: 'slate',
  rateLimited: 'amber',
};

const statusLabelKey: Record<Status, string> = {
  ready: 'status.ready',
  processing: 'status.processing',
  pending: 'status.pending',
  success: 'status.success',
  failed: 'status.failed',
  active: 'status.active',
  offline: 'status.offline',
  online: 'status.online',
  draft: 'status.draft',
  finalized: 'status.finalized',
  archived: 'status.archived',
  rateLimited: 'status.rateLimited',
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const { t } = useI18n();
  const label = t(statusLabelKey[status]);
  return <Badge tone={statusTone[status]} className={className}>{label}</Badge>;
}
