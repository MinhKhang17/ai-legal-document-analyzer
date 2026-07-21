import { Link, Outlet, useLocation } from 'react-router-dom';
import { DashboardLayout } from '../../layouts/DashboardLayout';
import { useI18n } from '../../hooks/useI18n';
import { useAppStore } from '../../store/AppStore';

export function AppShell() {
  const location = useLocation();
  const { user } = useAppStore();
  const { t, language } = useI18n();
  const passwordDeadline = user?.passwordResetDeadline
    ? new Date(user.passwordResetDeadline).toLocaleString(language === 'vi' ? 'vi-VN' : 'en-US')
    : t('auth.temporaryPasswordDefaultDeadline');
  return (
    <DashboardLayout>
      {user?.mustChangePassword && location.pathname !== '/settings/security' && (
        <div className="mb-lg rounded-xl border border-warning/40 bg-warning/10 p-md text-sm">
          <p className="font-semibold">{t('auth.temporaryPasswordNotice')}</p>
          <p className="mt-xs text-on-surface-variant dark:text-slate-300">{t('auth.temporaryPasswordDeadline', { deadline: passwordDeadline })} <Link className="font-semibold text-primary underline" to="/settings/security">{t('auth.changePasswordNow')}</Link></p>
        </div>
      )}
      <Outlet />
    </DashboardLayout>
  );
}
