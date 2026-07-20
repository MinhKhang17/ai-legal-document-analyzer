import { useEffect, useState } from 'react';
import { CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { confirmSubscriptionRefundEmail } from '../../services/subscription.service';

const confirmationErrorMessage = (error: unknown) => {
  const message = error instanceof Error ? error.message : '';
  if (message.includes('TOKEN_EXPIRED')) return 'Liên kết đã hết hạn.';
  if (message.includes('TOKEN_ALREADY_USED')) return 'Liên kết đã được sử dụng.';
  if (message.includes('TOKEN_INVALID')) return 'Liên kết không hợp lệ.';
  return message || 'Liên kết đã hết hạn hoặc không hợp lệ.';
};

export function RefundEmailConfirmationPage() {
  const [params] = useSearchParams();
  const [state, setState] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('Đang xác nhận yêu cầu hoàn tiền…');

  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setState('error');
      setMessage('Liên kết xác nhận không hợp lệ.');
      return;
    }
    void confirmSubscriptionRefundEmail(token)
      .then(() => {
        setState('success');
        setMessage('Đã xác nhận email. Admin có thể tiếp tục tạo lệnh hoàn tiền.');
      })
      .catch((error: unknown) => {
        setState('error');
        setMessage(confirmationErrorMessage(error));
      });
  }, [params]);

  return (
    <main className="grid min-h-screen place-items-center bg-surface p-lg dark:bg-slate-950">
      <section className="w-full max-w-lg rounded-2xl border border-legal-border bg-surface-container p-xl text-center shadow-xl dark:border-slate-700 dark:bg-slate-900">
        {state === 'loading' ? <Loader2 className="mx-auto h-12 w-12 animate-spin text-primary" /> : state === 'success' ? <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-500" /> : <XCircle className="mx-auto h-12 w-12 text-error" />}
        <h1 className="mt-md text-xl font-bold">Xác nhận hoàn tiền</h1>
        <p className="mt-sm text-on-surface-variant dark:text-slate-300">{message}</p>
        {state !== 'loading' && <Link className="mt-lg inline-block" to="/billing/refunds"><Button>Xem yêu cầu hoàn tiền</Button></Link>}
      </section>
    </main>
  );
}
