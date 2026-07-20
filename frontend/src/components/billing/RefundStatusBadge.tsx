import { Badge } from '../common/Badge';
import { useI18n } from '../../hooks/useI18n';

export function RefundStatusBadge({ status }: { status: string }) {
  const { t } = useI18n();
  const key = `refund.status.${status}`;
  const translated = t(key);
  return <Badge>{translated === key ? t('refund.status.unknown') : translated}</Badge>;
}
