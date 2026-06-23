import {
  Activity,
  BarChart3,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  FileClock,
  FileText,
  FolderOpen,
  Gavel,
  History,
  LayoutDashboard,
  MessageSquareText,
  Receipt,
  Scale,
  Settings,
  ShieldCheck,
  UploadCloud,
  UsersRound,
  Wrench,
  X,
  type LucideIcon,
} from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { Button } from '../components/common/Button';
import { useI18n } from '../hooks/useI18n';
import { useAppStore } from '../store/AppStore';
import { cn } from '../utils/cn';

interface NavItem {
  to: string;
  labelKey: string;
  icon: LucideIcon;
  end?: boolean;
  section?: NavSection;
}

type NavSection = 'main' | 'intelligence' | 'system' | 'admin';

const navItems: NavItem[] = [
  { to: '/dashboard', labelKey: 'nav.overview', icon: LayoutDashboard, section: 'main' },
  { to: '/projects', labelKey: 'nav.projects', icon: FolderOpen, section: 'main' },
  { to: '/documents', labelKey: 'nav.documents', icon: FileText, section: 'main' },
  { to: '/upload', labelKey: 'nav.upload', icon: UploadCloud, section: 'main' },
  { to: '/editor/risk-review', labelKey: 'nav.riskReview', icon: ShieldCheck, section: 'intelligence' },
  { to: '/chat', labelKey: 'nav.legalChat', icon: MessageSquareText, section: 'intelligence' },
  { to: '/chat/history', labelKey: 'nav.chatHistory', icon: FileClock, section: 'intelligence' },
  { to: '/editor/version-comparison', labelKey: 'nav.versionComparison', icon: Scale, section: 'intelligence' },
  { to: '/editor/comparison-history', labelKey: 'nav.comparisonHistory', icon: History, section: 'intelligence' },
  { to: '/reports', labelKey: 'nav.reports', icon: BarChart3, section: 'intelligence' },
  { to: '/knowledge-base', labelKey: 'nav.knowledgeBase', icon: BookOpen, section: 'intelligence' },
  { to: '/billing', labelKey: 'nav.billing', icon: Receipt, section: 'system' },
  { to: '/jobs', labelKey: 'nav.jobs', icon: Activity, section: 'system' },
  { to: '/templates', labelKey: 'nav.templates', icon: Wrench, section: 'system' },
  { to: '/settings', labelKey: 'nav.settings', icon: Settings, section: 'system' },
  { to: '/admin', labelKey: 'nav.admin', icon: UsersRound, section: 'admin' },
  { to: '/admin/audit-logs', labelKey: 'nav.auditLogs', icon: FileClock, section: 'admin' },
  { to: '/admin/system-health', labelKey: 'nav.systemHealth', icon: Activity, section: 'admin' },
];

const sectionLabelKeys: Record<NavSection, string> = {
  main: 'nav.section.main',
  intelligence: 'nav.section.intelligence',
  system: 'nav.section.system',
  admin: 'nav.section.admin',
};

const sectionOrder: NavSection[] = ['main', 'intelligence', 'system', 'admin'];

function SidebarContent() {
  const { t } = useI18n();
  const { sidebarCollapsed, toggleSidebar, setMobileSidebarOpen, user } = useAppStore();
  const isAdmin = user?.role === 'ADMIN';

  const groupedItems = navItems.reduce<Partial<Record<NavSection, NavItem[]>>>((accumulator, item) => {
    const section = item.section ?? 'main';
    if (section === 'admin' && !isAdmin) {
      return accumulator;
    }

    accumulator[section] = [...(accumulator[section] ?? []), item];
    return accumulator;
  }, {});

  return (
    <div className="flex h-full flex-col bg-surface dark:bg-slate-950">
      <div className="flex items-center justify-between gap-sm border-b border-outline-variant px-md py-lg dark:border-slate-800">
        <NavLink to="/dashboard" className="flex min-w-0 items-center gap-sm" onClick={() => setMobileSidebarOpen(false)}>
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary text-white shadow-navy">
            <Gavel className="h-5 w-5" aria-hidden="true" />
          </div>
          {!sidebarCollapsed && (
            <div className="min-w-0">
              <p className="font-domine text-lg font-bold leading-tight text-primary dark:text-inverse-primary">{t('app.name')}</p>
              <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-on-surface-variant dark:text-slate-400">{t('app.suite')}</p>
            </div>
          )}
        </NavLink>
        <button className="lg:hidden" type="button" aria-label={t('nav.closeNavigation')} onClick={() => setMobileSidebarOpen(false)}>
          <X className="h-5 w-5" />
        </button>
      </div>

      <div className="p-md">
        <NavLink to="/upload" onClick={() => setMobileSidebarOpen(false)}>
          <Button className={cn('w-full', sidebarCollapsed && 'px-0')} leftIcon={<UploadCloud className="h-4 w-4" />}>
            {!sidebarCollapsed && t('actions.newAnalysis')}
          </Button>
        </NavLink>
      </div>

      <nav className="flex-1 overflow-y-auto px-xs pb-lg" aria-label={t('nav.primaryNavigation')}>
        {sectionOrder.map((section) => {
          const sectionItems = groupedItems[section] ?? [];

          if (sectionItems.length === 0) {
            return null;
          }

          return (
            <div key={section} className="mb-md">
              {!sidebarCollapsed && <p className="px-md py-sm text-[11px] font-bold uppercase tracking-wider text-outline">{t(sectionLabelKeys[section])}</p>}
              <div className="space-y-xs">
                {sectionItems.map((item) => {
                  const Icon = item.icon;
                  return (
                    <NavLink
                      key={`${item.to}-${item.labelKey}`}
                      to={item.to}
                      end={item.end}
                      onClick={() => setMobileSidebarOpen(false)}
                      className={({ isActive }) =>
                        cn(
                          'group flex items-center gap-md rounded-r-full px-md py-sm text-sm font-semibold transition',
                          sidebarCollapsed && 'justify-center rounded-lg px-sm',
                          isActive
                            ? 'border-r-4 border-primary bg-surface-container-high text-primary dark:border-inverse-primary dark:bg-slate-800 dark:text-inverse-primary'
                            : 'text-on-surface-variant hover:bg-surface-container-low hover:text-primary dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-inverse-primary',
                        )
                      }
                    >
                      <Icon className="h-5 w-5 shrink-0" aria-hidden="true" />
                      {!sidebarCollapsed && <span>{t(item.labelKey)}</span>}
                    </NavLink>
                  );
                })}
              </div>
            </div>
          );
        })}
      </nav>

      <div className="border-t border-outline-variant p-md dark:border-slate-800">
        <Button variant="secondary" className="hidden w-full lg:inline-flex" onClick={toggleSidebar} leftIcon={sidebarCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}>
          {!sidebarCollapsed && t('actions.collapse')}
        </Button>
      </div>
    </div>
  );
}

export function Sidebar() {
  const { sidebarCollapsed, mobileSidebarOpen } = useAppStore();

  return (
    <>
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 hidden border-r border-outline-variant shadow-sm transition-all duration-300 dark:border-slate-800 lg:block',
          sidebarCollapsed ? 'w-20' : 'w-72',
        )}
      >
        <SidebarContent />
      </aside>
      {mobileSidebarOpen && <div className="fixed inset-0 z-40 bg-slate-950/50 lg:hidden" aria-hidden="true" />}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 w-72 transform border-r border-outline-variant shadow-raised transition-transform duration-300 dark:border-slate-800 lg:hidden',
          mobileSidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <SidebarContent />
      </aside>
    </>
  );
}
