import { Link, Outlet } from 'react-router-dom';
import { DashboardLayout } from '../../layouts/DashboardLayout';
import { useAppStore } from '../../store/AppStore';

export function AppShell() {
  const { user, language } = useAppStore();
  return (
    <DashboardLayout>
      {user?.mustChangePassword && (
        <div className="mb-lg rounded-xl border border-warning/40 bg-warning/10 p-md text-sm">
          <p className="font-semibold">{language === 'vi' ? 'Bạn đang dùng mật khẩu tạm.' : 'You are using a temporary password.'}</p>
          <p className="mt-xs text-on-surface-variant dark:text-slate-300">{language === 'vi' ? `Hãy đổi mật khẩu trước ${user.passwordResetDeadline ? new Date(user.passwordResetDeadline).toLocaleString('vi-VN') : 'thời hạn 7 ngày'}.` : `Change it before ${user.passwordResetDeadline ? new Date(user.passwordResetDeadline).toLocaleString('en-US') : 'the 7-day deadline'}.`} <Link className="font-semibold text-primary underline" to="/settings">{language === 'vi' ? 'Đổi ngay' : 'Change now'}</Link></p>
        </div>
      )}
      <Outlet />
    </DashboardLayout>
  );
}
