import { Badge } from '../common/Badge';
import { useI18n } from '../../hooks/useI18n';

export function RefundStatusBadge({ status }: { status: string }) {
  const { t } = useI18n();
  const fallbacks: Record<string, string> = {
    NEW: 'Mới', ADMIN_REVIEWING: 'Admin đang xem xét', WAITING_USER_BANK_INFO: 'Chờ thông tin ngân hàng',
    WAITING_EMAIL_CONFIRMATION: 'Chờ xác nhận email', EMAIL_CONFIRMED: 'Đã xác nhận email',
    REFUND_REQUEST_CREATED: 'Đã tạo lệnh hoàn tiền', REFUNDED: 'Đã hoàn tiền', CLOSED: 'Đã đóng',
  };
  const key = `refund.status.${status}`;
  const translated = t(key);
  return <Badge>{translated === key ? fallbacks[status] ?? status : translated}</Badge>;
}
