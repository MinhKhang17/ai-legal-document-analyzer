import { useEffect, useState } from 'react';
import type { PaymentTransaction } from '../../types/paymentTransaction';
import { formatVndCurrency } from '../../utils/format';
import { validateRefundForm } from '../../utils/refund';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';
import { useI18n } from '../../hooks/useI18n';

interface Props {
  transaction: PaymentTransaction | null;
  submitting: boolean;
  onClose: () => void;
  onSubmit: (amount: number, reason: string) => Promise<void>;
}

export function RefundRequestModal({ transaction, submitting, onClose, onSubmit }: Props) {
  const { t } = useI18n();
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  useEffect(() => { setAmount(transaction ? String(transaction.amount) : ''); setReason(''); setError(''); }, [transaction]);
  if (!transaction) return null;

  const submit = async () => {
    const validationError = validateRefundForm({ amount, reason }, transaction.amount);
    if (validationError) { setError(t(validationError)); return; }
    setError('');
    await onSubmit(Number(amount), reason.trim());
  };

  return (
    <Modal open title={t('refund.request.title')} onClose={submitting ? () => undefined : onClose} footer={
      <div className="flex justify-end gap-sm"><Button variant="secondary" disabled={submitting} onClick={onClose}>{t('actions.cancel')}</Button><Button disabled={submitting} onClick={() => void submit()}>{submitting ? t('refund.request.submitting') : t('refund.request.submit')}</Button></div>
    }>
      <div className="space-y-md">
        <p className="text-sm text-on-surface-variant">{t('refund.transaction')} <strong>{transaction.transactionCode || `#${transaction.id}`}</strong> · {formatVndCurrency(transaction.amount)}</p>
        <label className="block text-sm font-semibold">{t('refund.amount')}
          <input className="form-field mt-xs" type="number" min="1" max={transaction.amount} step="1" value={amount} disabled={submitting} onChange={(event) => setAmount(event.target.value)} />
        </label>
        <label className="block text-sm font-semibold">{t('refund.reason')}
          <textarea className="form-field mt-xs min-h-28" maxLength={2000} value={reason} disabled={submitting} onChange={(event) => setReason(event.target.value)} />
        </label>
        {error && <p className="text-sm text-error" role="alert">{error}</p>}
      </div>
    </Modal>
  );
}
