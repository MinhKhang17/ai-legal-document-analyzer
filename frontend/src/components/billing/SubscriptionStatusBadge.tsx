import { Badge, type BadgeTone } from '../common/Badge';
import { useI18n } from '../../hooks/useI18n';
import type { CustomerPlanStatus } from '../../types/subscription';

interface SubscriptionStatusBadgeProps {
  status: CustomerPlanStatus;
  className?: string;
}

const statusConfig: Record<string, { labelKey: string; tone: BadgeTone }> = {
  PENDING: { labelKey: 'billing.status.pending', tone: 'amber' },
  ACTIVE: { labelKey: 'billing.status.active', tone: 'green' },
  EXPIRED: { labelKey: 'billing.status.expired', tone: 'red' },
  CANCELLED: { labelKey: 'billing.status.cancelled', tone: 'slate' },
};

export function SubscriptionStatusBadge({ status, className }: SubscriptionStatusBadgeProps) {
  const { t } = useI18n();
  const normalizedStatus = status.toUpperCase();
  const config = statusConfig[normalizedStatus] ?? {
    labelKey: 'billing.status.unknown',
    tone: 'slate' as const,
  };

  return (
    <Badge tone={config.tone} className={className}>
      {t(config.labelKey)}
    </Badge>
  );
}
