import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { getAdminSubscriptionRefunds } from '../../services/subscription.service';
import type { RefundRequestRecord, RefundStatus } from '../../types/subscription';
import { formatDisplayDate, formatVndCurrency } from '../../utils/format';
import { useI18n } from '../../hooks/useI18n';
import { RefundStatusBadge } from '../../components/billing/RefundStatusBadge';
import { REFUND_STATUSES } from '../../utils/refund';

export function AdminRefundsPage() {
  const { t, language } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const requestedStatus = params.get('status');
  const status: RefundStatus | '' = requestedStatus && REFUND_STATUSES.includes(requestedStatus as RefundStatus)
    ? requestedStatus as RefundStatus
    : '';
  const [refunds, setRefunds] = useState<RefundRequestRecord[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const load = useCallback(async () => { setLoading(true); setError(''); try { setRefunds((await getAdminSubscriptionRefunds(status || undefined)).data ?? []); } catch (cause) { console.error('Failed to load refund requests:', cause); setError(t('refund.errors.loadAdmin')); } finally { setLoading(false); } }, [status, t]);
  useEffect(() => { void load(); }, [load]);
  const locale = language === 'vi' ? 'vi-VN' : 'en-US';
  const columns: DataTableColumn<RefundRequestRecord>[] = [
    { header: t('refund.id'), cell: (item) => <Link className="font-semibold text-primary" to={`/admin/refunds/${item.id}`}>#{item.id}</Link> },
    { header: t('refund.customer'), cell: (item) => `#${item.requestedById}` },
    { header: t('refund.transaction'), cell: (item) => `#${item.paymentTransactionId}` },
    { header: t('refund.amount'), cell: (item) => formatVndCurrency(item.amount, t('billing.free'), locale) },
    { header: t('table.status'), cell: (item) => <RefundStatusBadge status={item.status} /> },
    { header: t('table.created'), cell: (item) => formatDisplayDate(item.createdAt, '-', locale) },
    {
      header: t('table.actions'),
      cell: (item) => (
        <Button variant="secondary" size="sm" onClick={() => navigate(`/admin/refunds/${item.id}`)}>
          {t('actions.viewDetails')}
        </Button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('refund.admin.title')}
        subtitle={t('refund.admin.subtitle')}
        actions={(
          <select
            className="form-field"
            aria-label={t('refund.filterStatus')}
            value={status}
            onChange={(event) => {
              const next = new URLSearchParams(params);
              event.target.value ? next.set('status', event.target.value) : next.delete('status');
              setParams(next);
            }}
          >
            <option value="">{t('refund.status.all')}</option>
            {REFUND_STATUSES.map((item) => <option key={item} value={item}>{t(`refund.status.${item}`)}</option>)}
          </select>
        )}
      />
      <Card>
        {error ? (
          <div role="alert" className="text-error">{error} <Button onClick={() => void load()}>{t('common.retry')}</Button></div>
        ) : loading ? (
          <p aria-live="polite">{t('common.loading')}</p>
        ) : (
          <DataTable columns={columns} data={refunds} getRowKey={(item) => String(item.id)} emptyMessage={t('refund.admin.empty')} />
        )}
      </Card>
    </div>
  );
}
