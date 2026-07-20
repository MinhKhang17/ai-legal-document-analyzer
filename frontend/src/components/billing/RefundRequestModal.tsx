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
  onSubmit: (payload: { amount: number; reason: string; bankName: string; accountNumber: string; accountHolderName: string }) => Promise<void>;
}

export function RefundRequestModal({ transaction, submitting, onClose, onSubmit }: Props) {
  const { t } = useI18n();
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [bankName, setBankName] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [accountHolderName, setAccountHolderName] = useState('');
  const [error, setError] = useState('');

  useEffect(() => { setAmount(transaction ? String(transaction.amount) : ''); setReason(''); setBankName(''); setAccountNumber(''); setAccountHolderName(''); setError(''); }, [transaction]);
  if (!transaction) return null;

  const submit = async () => {
    const validationError = validateRefundForm({ amount, reason }, transaction.amount);
    if (validationError) { setError(t(validationError)); return; }
    if (!bankName.trim() || !accountNumber.trim() || !accountHolderName.trim()) {
      setError('Vui lòng nhập đầy đủ thông tin tài khoản nhận hoàn tiền.');
      return;
    }
    setError('');
    await onSubmit({ amount: Number(amount), reason: reason.trim(), bankName: bankName.trim(), accountNumber: accountNumber.trim(), accountHolderName: accountHolderName.trim() });
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
        <div className="grid gap-md md:grid-cols-2">
          <label className="block text-sm font-semibold">Ngân hàng
            <input className="form-field mt-xs" value={bankName} disabled={submitting} onChange={(event) => setBankName(event.target.value)} />
          </label>
          <label className="block text-sm font-semibold">Số tài khoản
            <input className="form-field mt-xs" value={accountNumber} disabled={submitting} onChange={(event) => setAccountNumber(event.target.value)} />
          </label>
        </div>
        <label className="block text-sm font-semibold">Tên chủ tài khoản
          <input className="form-field mt-xs uppercase" value={accountHolderName} disabled={submitting} onChange={(event) => setAccountHolderName(event.target.value)} />
        </label>
        <p className="rounded-lg bg-amber-500/10 p-sm text-xs text-amber-700 dark:text-amber-300">Sau khi gửi, bạn cần xác nhận qua email trước khi admin có thể tạo lệnh hoàn tiền.</p>
        {error && <p className="text-sm text-error" role="alert">{error}</p>}
      </div>
    </Modal>
  );
}
