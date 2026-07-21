import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { getMySubscriptionRefunds } from '../../services/subscription.service';
import type { RefundRequestRecord } from '../../types/subscription';
import { formatDisplayDate, formatVndCurrency } from '../../utils/format';
import { useI18n } from '../../hooks/useI18n';
import { RefundStatusBadge } from '../../components/billing/RefundStatusBadge';

export function RefundHistoryPage() {
  const { t, language } = useI18n();
  const [refunds, setRefunds] = useState<RefundRequestRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(async () => { setLoading(true); setError(''); try { setRefunds((await getMySubscriptionRefunds()).data ?? []); } catch (cause) { console.error('Failed to load refund history:', cause); setError(t('refund.errors.loadHistory')); } finally { setLoading(false); } }, [t]);
  useEffect(() => { void load(); }, [load]);
  const locale = language === 'vi' ? 'vi-VN' : 'en-US';
  const columns: DataTableColumn<RefundRequestRecord>[] = [
    { header: t('refund.id'), cell: (item) => <Link className="font-semibold text-primary" to={`/billing/refunds/${item.id}`}>#{item.id}</Link> },
    { header: t('refund.transaction'), cell: (item) => `#${item.paymentTransactionId}` },
    { header: t('refund.amount'), cell: (item) => formatVndCurrency(item.amount, t('billing.free'), locale) },
    { header: t('table.status'), cell: (item) => <RefundStatusBadge status={item.status} /> },
    { header: t('table.created'), cell: (item) => formatDisplayDate(item.createdAt, '-', locale) },
  ];
  return <div><PageHeader title={t('refund.history.title')} subtitle={t('refund.history.subtitle')} actions={<Link to="/billing"><Button variant="secondary">{t('refund.backToBilling')}</Button></Link>} /><Card>{error ? <div role="alert" className="text-error">{error} <Button onClick={() => void load()}>{t('common.retry')}</Button></div> : loading ? <p aria-live="polite">{t('common.loading')}</p> : <DataTable columns={columns} data={refunds} getRowKey={(item) => String(item.id)} emptyMessage={t('refund.history.empty')} />}</Card></div>;
}
