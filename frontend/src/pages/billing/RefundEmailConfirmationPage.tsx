import { useEffect, useState } from 'react';
import { CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { useI18n } from '../../hooks/useI18n';
import { confirmSubscriptionRefundEmail } from '../../services/subscription.service';

const confirmationErrorKey = (error: unknown) => {
  const message = error instanceof Error ? error.message : '';
  if (message.includes('TOKEN_EXPIRED')) return 'refund.emailConfirmation.expired';
  if (message.includes('TOKEN_ALREADY_USED')) return 'refund.emailConfirmation.used';
  if (message.includes('TOKEN_INVALID')) return 'refund.emailConfirmation.invalidLink';
  return 'refund.emailConfirmation.expired';
};

export function RefundEmailConfirmationPage() {
  const { t } = useI18n();
  const [params] = useSearchParams();
  const [state, setState] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState(() => t('refund.emailConfirmation.loading'));

  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setState('error');
      setMessage(t('refund.emailConfirmation.invalidLink'));
      return;
    }
    void confirmSubscriptionRefundEmail(token)
      .then(() => {
        setState('success');
        setMessage(t('refund.emailConfirmation.success'));
      })
      .catch((error) => {
        setState('error');
        setMessage(t(confirmationErrorKey(error)));
      });
  }, [params, t]);

  return (
    <main className="grid min-h-screen place-items-center bg-surface p-lg dark:bg-slate-950">
      <section className="w-full max-w-lg rounded-2xl border border-legal-border bg-surface-container p-xl text-center shadow-xl dark:border-slate-700 dark:bg-slate-900">
        {state === 'loading' ? <Loader2 className="mx-auto h-12 w-12 animate-spin text-primary" /> : state === 'success' ? <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-500" /> : <XCircle className="mx-auto h-12 w-12 text-error" />}
        <h1 className="mt-md text-xl font-bold">{t('refund.emailConfirmation.title')}</h1>
        <p className="mt-sm text-on-surface-variant dark:text-slate-300">{message}</p>
        {state !== 'loading' && <Link className="mt-lg inline-block" to="/billing/refunds"><Button>{t('refund.emailConfirmation.viewRequests')}</Button></Link>}
      </section>
    </main>
  );
}
