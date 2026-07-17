import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { RefundStatusBadge } from '../../components/billing/RefundStatusBadge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { Modal } from '../../components/common/Modal';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import {
  getSubscriptionRefund,
  isSubscriptionRequestError,
  updateSubscriptionRefundStatus,
} from '../../services/subscription.service';
import type { RefundRequestRecord, RefundStatus } from '../../types/subscription';
import { formatDisplayDate, formatVndCurrency } from '../../utils/format';
import { getAllowedRefundStatusTransitions } from '../../utils/refund';

export function AdminRefundDetailPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { id = '' } = useParams();
  const [refund, setRefund] = useState<RefundRequestRecord | null>(null);
  const [selectedAction, setSelectedAction] = useState<RefundStatus | null>(null);
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  const load = useCallback(async () => {
    if (!id) {
      setRefund(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      setRefund((await getSubscriptionRefund(id)).data ?? null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('refund.errors.loadDetail'));
    } finally {
      setLoading(false);
    }
  }, [id, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const allowedStatuses = useMemo(
    () => refund ? getAllowedRefundStatusTransitions(refund.status) : [],
    [refund],
  );

  const closeActionDialog = () => {
    if (saving) return;
    setSelectedAction(null);
    setNote('');
    setActionError('');
  };

  const openActionDialog = (status: RefundStatus) => {
    setSelectedAction(status);
    setNote('');
    setActionError('');
  };

  const refreshAfterConflict = async () => {
    try {
      setRefund((await getSubscriptionRefund(id)).data ?? null);
    } catch {
      // Preserve the mutation error; the main retry remains available.
    }
  };

  const update = async () => {
    if (!selectedAction) return;

    const normalizedNote = note.trim();
    if (selectedAction === 'REJECTED' && !normalizedNote) {
      setActionError(t('validation.refund.rejectNoteRequired'));
      return;
    }
    if (normalizedNote.length > 2000) {
      setActionError(t('validation.refund.adminNoteMaximum'));
      return;
    }

    setSaving(true);
    setActionError('');
    try {
      const response = await updateSubscriptionRefundStatus(id, {
        status: selectedAction,
        adminNote: normalizedNote || null,
      });
      setRefund(response.data ?? null);
      setSelectedAction(null);
      setNote('');
      toast.success(t('refund.updateSuccess'));
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : t('refund.errors.update');
      if (isSubscriptionRequestError(cause) && cause.status === 409) {
        await refreshAfterConflict();
      }
      setActionError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <PageHeader
        title={t('refund.admin.detailTitle')}
        subtitle={`#${id}`}
        actions={<Link to="/admin/refunds"><Button variant="secondary">{t('actions.back')}</Button></Link>}
      />

      {error ? (
        <Card>
          <div role="alert" className="text-error">
            {error} <Button onClick={() => void load()}>{t('common.retry')}</Button>
          </div>
        </Card>
      ) : loading ? (
        <Card><p aria-live="polite">{t('common.loading')}</p></Card>
      ) : refund ? (
        <div className="grid gap-lg xl:grid-cols-[minmax(0,1fr)_360px]">
          <Card title={t('refund.detail.title')}>
            <dl className="grid gap-md md:grid-cols-2">
              <div><dt className="label-uppercase">{t('refund.id')}</dt><dd className="mt-xs font-semibold">#{refund.id}</dd></div>
              <div><dt className="label-uppercase">{t('table.status')}</dt><dd className="mt-xs"><RefundStatusBadge status={refund.status} /></dd></div>
              <div><dt className="label-uppercase">{t('refund.customer')}</dt><dd className="mt-xs">#{refund.requestedById}</dd></div>
              <div><dt className="label-uppercase">{t('refund.transaction')}</dt><dd className="mt-xs">#{refund.paymentTransactionId}</dd></div>
              <div><dt className="label-uppercase">{t('refund.customerPlan')}</dt><dd className="mt-xs">{refund.customerPlanId ? `#${refund.customerPlanId}` : t('common.unknown')}</dd></div>
              <div><dt className="label-uppercase">{t('refund.amount')}</dt><dd className="mt-xs font-semibold">{formatVndCurrency(refund.amount)}</dd></div>
              <div><dt className="label-uppercase">{t('table.created')}</dt><dd className="mt-xs">{formatDisplayDate(refund.createdAt, '-', language === 'vi' ? 'vi-VN' : 'en-US')}</dd></div>
              <div><dt className="label-uppercase">{t('table.updated')}</dt><dd className="mt-xs">{formatDisplayDate(refund.updatedAt, '-', language === 'vi' ? 'vi-VN' : 'en-US')}</dd></div>
              <div className="md:col-span-2"><dt className="label-uppercase">{t('refund.reason')}</dt><dd className="mt-xs whitespace-pre-wrap">{refund.reason}</dd></div>
              <div className="md:col-span-2"><dt className="label-uppercase">{t('refund.adminNote')}</dt><dd className="mt-xs whitespace-pre-wrap">{refund.adminNote || t('refund.noAdminNote')}</dd></div>
            </dl>
          </Card>

          <Card title={t('refund.actions.title')} subtitle={t('refund.actions.description')}>
            {allowedStatuses.length > 0 ? (
              <div className="flex flex-col gap-sm">
                {allowedStatuses.map((status) => (
                  <Button
                    key={status}
                    variant={status === 'REJECTED' ? 'danger' : 'primary'}
                    disabled={saving}
                    onClick={() => openActionDialog(status)}
                  >
                    {t(`refund.actions.${status}`)}
                  </Button>
                ))}
              </div>
            ) : (
              <p className="text-sm text-on-surface-variant">{t('refund.actions.none')}</p>
            )}
          </Card>
        </div>
      ) : (
        <Card><p>{t('refund.detail.notFound')}</p></Card>
      )}

      <Modal
        open={selectedAction !== null}
        title={selectedAction ? t('refund.actionDialogTitle', { status: t(`refund.status.${selectedAction}`) }) : t('refund.admin.detailTitle')}
        onClose={closeActionDialog}
        size="sm"
        footer={(
          <div className="flex justify-end gap-sm">
            <Button variant="secondary" disabled={saving} onClick={closeActionDialog}>{t('actions.cancel')}</Button>
            <Button
              variant={selectedAction === 'REJECTED' ? 'danger' : 'primary'}
              disabled={saving}
              onClick={() => void update()}
            >
              {saving ? t('refund.updating') : t('refund.confirmUpdate')}
            </Button>
          </div>
        )}
      >
        <div className="space-y-md">
          <p className="text-sm text-on-surface-variant">
            {selectedAction ? t('refund.confirmStatusChange', { status: t(`refund.status.${selectedAction}`) }) : ''}
          </p>
          <label className="block text-sm font-semibold">
            {t('refund.adminNote')}
            <textarea
              className="form-field mt-xs min-h-28"
              maxLength={2000}
              value={note}
              disabled={saving}
              onChange={(event) => setNote(event.target.value)}
            />
          </label>
          <p className="text-xs text-on-surface-variant">
            {selectedAction === 'REJECTED' ? t('refund.rejectNoteRequired') : t('refund.adminNoteOptional')}
          </p>
          {actionError && <p role="alert" className="text-sm font-semibold text-error">{actionError}</p>}
        </div>
      </Modal>
    </div>
  );
}
