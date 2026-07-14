import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { getSubscriptionRefund } from '../../services/subscription.service';
import type { RefundRequestRecord } from '../../types/subscription';
import { formatDisplayDate, formatVndCurrency } from '../../utils/format';
import { useI18n } from '../../hooks/useI18n';
import { RefundStatusBadge } from '../../components/billing/RefundStatusBadge';

export function RefundDetailPage() {
  const { t } = useI18n();
  const { id = '' } = useParams(); const [refund, setRefund] = useState<RefundRequestRecord | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const load = useCallback(async () => { if (!id) return; setLoading(true); setError(''); try { setRefund((await getSubscriptionRefund(id)).data ?? null); } catch (cause) { setError(cause instanceof Error ? cause.message : t('refund.errors.loadDetail')); } finally { setLoading(false); } }, [id, t]);
  useEffect(() => { void load(); }, [load]);
  return <div><PageHeader title={t('refund.detail.title')} subtitle={`#${id}`} actions={<Link to="/billing/refunds"><Button variant="secondary">{t('actions.back')}</Button></Link>} /><Card>{error ? <div role="alert" className="text-error">{error} <Button onClick={() => void load()}>{t('common.retry')}</Button></div> : loading ? <p>{t('common.loading')}</p> : refund ? <dl className="grid gap-md md:grid-cols-2"><div><dt className="label-uppercase">{t('table.status')}</dt><dd><RefundStatusBadge status={refund.status} /></dd></div><div><dt className="label-uppercase">{t('refund.amount')}</dt><dd>{formatVndCurrency(refund.amount)}</dd></div><div><dt className="label-uppercase">{t('refund.transaction')}</dt><dd>#{refund.paymentTransactionId}</dd></div><div><dt className="label-uppercase">{t('table.created')}</dt><dd>{formatDisplayDate(refund.createdAt, '-')}</dd></div><div className="md:col-span-2"><dt className="label-uppercase">{t('refund.reason')}</dt><dd>{refund.reason}</dd></div>{refund.adminNote && <div className="md:col-span-2"><dt className="label-uppercase">{t('refund.adminNote')}</dt><dd>{refund.adminNote}</dd></div>}</dl> : <p>{t('refund.detail.notFound')}</p>}</Card></div>;
}
