import { Badge } from '../common/Badge';
import { useI18n } from '../../hooks/useI18n';

export function RefundStatusBadge({ status }: { status: string }) {
  const { t } = useI18n();
  return <Badge>{t(`refund.status.${status}`)}</Badge>;
}
