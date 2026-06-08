import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { useI18n } from '../../hooks/useI18n';

export function UnauthorizedPage() {
  const { t } = useI18n();

  return (
    <div className="mx-auto flex min-h-[60vh] max-w-xl flex-col items-center justify-center gap-sm px-md text-center">
      <p className="label-uppercase text-[10px] font-bold tracking-[0.14em] text-secondary dark:text-accent-gold">
        403
      </p>
      <h1 className="text-2xl font-bold text-on-surface dark:text-slate-100">{t('auth.unauthorizedTitle')}</h1>
      <p className="text-sm leading-7 text-on-surface-variant dark:text-slate-400">
        {t('auth.unauthorizedMessage')}
      </p>
      <div className="flex flex-wrap justify-center gap-sm">
        <Link to="/dashboard">
          <Button variant="secondary">{t('actions.goToDashboard')}</Button>
        </Link>
        <Link to="/login">
          <Button>{t('actions.signIn')}</Button>
        </Link>
      </div>
    </div>
  );
}
