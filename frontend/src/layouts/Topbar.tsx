import { Bell, LogOut, Menu, Search, Shield, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button } from '../components/common/Button';
import { Dropdown } from '../components/common/Dropdown';
import { LanguageToggle } from '../components/common/LanguageToggle';
import { ThemeToggle } from '../components/common/ThemeToggle';
import { useI18n } from '../hooks/useI18n';
import { useAppStore } from '../store/AppStore';
import { getMyCustomerPlan } from '../services/subscription.service';

const routeLabels: Array<{ prefix: string; key: string }> = [
  { prefix: '/dashboard', key: 'nav.dashboard' },
  { prefix: '/projects', key: 'nav.projects' },
  { prefix: '/documents', key: 'nav.documents' },
  { prefix: '/upload', key: 'nav.upload' },
  { prefix: '/editor/risk-review', key: 'nav.riskReview' },
  { prefix: '/chat/history', key: 'nav.chatHistory' },
  { prefix: '/chat', key: 'nav.legalChat' },
  { prefix: '/knowledge-base', key: 'nav.knowledgeBase' },
  { prefix: '/billing/subscribe', key: 'nav.billingSubscribe' },
  { prefix: '/billing', key: 'nav.billing' },
  { prefix: '/jobs', key: 'nav.jobs' },
  { prefix: '/admin/system-health', key: 'nav.systemHealth' },
  { prefix: '/admin/audit-logs', key: 'nav.auditLogs' },
  { prefix: '/admin', key: 'nav.admin' },
];

export function Topbar() {
  const { t } = useI18n();
  const { setMobileSidebarOpen, user, signOut } = useAppStore();
  const location = useLocation();
  const navigate = useNavigate();
  const current = routeLabels.find((route) => location.pathname.startsWith(route.prefix));
  const isAdmin = user?.role === 'ADMIN';
  const isCustomer = user?.role === 'CUSTOMER';
  const [planName, setPlanName] = useState('FREE');
  useEffect(() => {
    if (!isCustomer) return;
    void getMyCustomerPlan().then((response) => setPlanName(response.data?.subscriptionPlan.displayName ?? response.data?.subscriptionPlan.planName ?? 'FREE')).catch(() => setPlanName('FREE'));
  }, [isCustomer]);
  const profileWorkspacePath = isAdmin ? '/admin' : '/dashboard';
  const displayName = user
    ? `${user.firstName} ${user.lastName}`.trim() || user.email
    : "Guest";
  const displayEmail = user?.email ?? "";
  const initialsSource = user ? `${user.firstName} ${user.lastName}`.trim() : "";
  const avatarInitials = initialsSource.length > 1
    ? initialsSource
      .split(" ")
      .filter(Boolean)
      .map((segment) => segment[0]?.toUpperCase())
      .join("")
      .slice(0, 2)
    : user && user.email
      ? user.email.slice(0, 2).toUpperCase()
      : "--";

  return (
    <header className="sticky top-0 z-30 border-b border-outline-variant bg-white/90 px-md py-sm backdrop-blur dark:border-slate-800 dark:bg-slate-950/90 sm:px-lg">
      <div className="flex items-center justify-between gap-md">
        <div className="flex min-w-0 flex-1 items-center gap-md">
          <Button variant="ghost" size="icon" className="lg:hidden" aria-label={t('nav.openNavigation')} onClick={() => setMobileSidebarOpen(true)}>
            <Menu className="h-5 w-5" />
          </Button>
          <div className="hidden min-w-0 flex-1 items-center gap-md md:flex">
            <div className="relative max-w-lg flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline" aria-hidden="true" />
              <input className="w-full rounded-xl border border-outline-variant bg-surface-container-low py-sm pl-10 pr-md text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10 dark:border-slate-700 dark:bg-slate-900" placeholder={t('topbar.search')} type="search" />
            </div>
            {isAdmin && (
              <div className="hidden items-center gap-md xl:flex">
                <Link className="border-b-2 border-primary pb-xs text-sm font-semibold text-primary dark:border-inverse-primary dark:text-inverse-primary" to="/admin">
                  {t('topbar.privateWorkspace')}
                </Link>
                <Link className="flex items-center gap-xs text-sm font-semibold text-on-surface-variant hover:text-primary dark:text-slate-400 dark:hover:text-inverse-primary" to="/admin/system-health">
                  <Shield className="h-4 w-4" aria-hidden="true" />
                  {location.pathname.includes('system-health') ? t('topbar.systemHealth') : t('topbar.localAiOnline')}
                </Link>
              </div>
            )}
          </div>
          <div className="md:hidden">
            <p className="text-sm font-bold text-primary dark:text-inverse-primary">{current ? t(current.key) : t('app.name')}</p>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-sm">
          <div className="hidden sm:block">
            <LanguageToggle />
          </div>
          <div className="hidden md:block">
            <ThemeToggle />
          </div>
          <Button variant="ghost" size="icon" className="relative" aria-label={t('topbar.notifications')}>
            <Bell className="h-5 w-5" />
            <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-error" />
          </Button>
          <Dropdown
            label={
              <span className="flex items-center gap-sm">
                {isCustomer && <span className="hidden rounded-full bg-primary/10 px-sm py-xs text-[10px] font-bold uppercase text-primary md:inline">{planName}</span>}
                <span className="hidden text-sm font-semibold md:inline">{displayName}</span>
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">{avatarInitials}</span>
              </span>
            }
          >
            <div className="p-sm">
              <p className="font-semibold text-on-surface dark:text-slate-100">{displayName}</p>
              <p className="text-xs text-on-surface-variant dark:text-slate-400">{displayEmail}</p>
            </div>
            <button className="flex w-full items-center gap-sm rounded-lg px-sm py-sm text-left text-sm hover:bg-surface-container-low dark:hover:bg-slate-800" type="button" onClick={() => navigate(profileWorkspacePath)}>
              <UserRound className="h-4 w-4" aria-hidden="true" />
              {t('topbar.profileWorkspace')}
            </button>
            {isCustomer && (
              <button className="flex w-full items-center gap-sm rounded-lg px-sm py-sm text-left text-sm hover:bg-surface-container-low dark:hover:bg-slate-800" type="button" onClick={() => navigate('/billing')}>
                <Shield className="h-4 w-4" aria-hidden="true" />
                Billing · {planName}
              </button>
            )}
            <button
              className="flex w-full items-center gap-sm rounded-lg px-sm py-sm text-left text-sm text-error hover:bg-error-container dark:hover:bg-red-950/40"
              type="button"
              onClick={() => {
                void signOut().finally(() => navigate('/login'));
              }}
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              {t('actions.signOut')}
            </button>
          </Dropdown>
        </div>
      </div>
    </header>
  );
}
