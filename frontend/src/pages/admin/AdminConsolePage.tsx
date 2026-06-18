import { CheckCircle2, Cpu, Database, LockKeyhole, Monitor, Moon, MoreHorizontal, Palette, ShieldCheck, Sun, UserPlus, UsersRound } from 'lucide-react';
import { useState } from 'react';
import { AdminMetricCard } from '../../components/admin/AdminMetricCard';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Tabs } from '../../components/common/Tabs';
import { LanguageToggle } from '../../components/common/LanguageToggle';
import { workspaceUsers } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import { useAppStore, type ThemeMode } from '../../store/AppStore';
import type { WorkspaceUser } from '../../types/user';

type AdminTabId = 'users' | 'workspace' | 'language' | 'theme' | 'security' | 'aiConfig';

const adminTabs: Array<{ id: AdminTabId; labelKey: string }> = [
  { id: 'users', labelKey: 'admin.tabs.users' },
  { id: 'workspace', labelKey: 'admin.tabs.workspace' },
  { id: 'language', labelKey: 'admin.tabs.language' },
  { id: 'theme', labelKey: 'admin.tabs.theme' },
  { id: 'security', labelKey: 'admin.tabs.security' },
  { id: 'aiConfig', labelKey: 'admin.tabs.aiConfig' },
];

const themeOptions: Array<{ mode: ThemeMode; labelKey: string; icon: typeof Sun }> = [
  { mode: 'light', labelKey: 'theme.light', icon: Sun },
  { mode: 'dark', labelKey: 'theme.dark', icon: Moon },
  { mode: 'system', labelKey: 'theme.system', icon: Monitor },
];

const legalTextSizes = ['16px', '18px', '20px'] as const;

const getTabId = (tab: AdminTabId) => `admin-tab-${tab}`;
const getPanelId = (tab: AdminTabId) => `admin-panel-${tab}`;

export function AdminConsolePage() {
  const { t } = useI18n();
  const { theme, setTheme } = useAppStore();
  const [activeTab, setActiveTab] = useState<AdminTabId>('users');
  const [legalTextSize, setLegalTextSize] = useState<(typeof legalTextSizes)[number]>('18px');

  const columns: DataTableColumn<WorkspaceUser>[] = [
    {
      header: t('table.user'),
      cell: (user) => (
        <div className="flex items-center gap-md">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">{user.initials}</div>
          <div>
            <p className="font-semibold">{user.name}</p>
            <p className="text-xs text-on-surface-variant dark:text-slate-400">{user.email}</p>
          </div>
        </div>
      ),
    },
    { header: t('table.role'), cell: (user) => user.role },
    {
      header: t('table.status'),
      headerClassName: 'text-center align-middle',
      className: 'text-center align-middle',
      cell: (user) => (
        <div className="flex justify-center">
          <StatusBadge
            status={user.status}
            className="min-w-[88px] justify-center text-center whitespace-nowrap"
          />
        </div>
      ),
    },
    { header: t('admin.users.lastAccess'), cell: (user) => <span className="whitespace-nowrap">{user.lastAccess}</span> },
    {
      header: t('table.actions'),
      cell: () => (
        <Button variant="ghost" size="icon" aria-label={t('admin.users.userActions')}>
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      ),
    },
  ];

  const renderUsersTab = () => (
    <>
      <section className="grid gap-gutter md:grid-cols-3">
        <AdminMetricCard label={t('admin.users.activeUsersLabel')} value="24" detail={t('admin.users.activeUsersDetail')} icon={<UsersRound className="h-5 w-5" />} />
        <AdminMetricCard label={t('admin.users.twoFaAdoptionLabel')} value="85%" detail={t('admin.users.twoFaAdoptionDetail')} icon={<ShieldCheck className="h-5 w-5" />} tone="green" />
        <AdminMetricCard label={t('admin.users.passwordExpiryLabel')} value="12%" detail={t('admin.users.passwordExpiryDetail')} icon={<LockKeyhole className="h-5 w-5" />} tone="red" />
      </section>

      <div className="mt-xl grid gap-gutter xl:grid-cols-[1.2fr_0.8fr]">
        <Card title={t('admin.users')} actions={<Badge tone="blue">{t('admin.users.badgeCount')}</Badge>}>
          <DataTable columns={columns} data={workspaceUsers} getRowKey={(user) => user.id} />
        </Card>

        <aside className="space-y-gutter">
          <Card tone="ai">
            <h2 className="text-title-lg font-semibold">{t('admin.users.aiRecommendationTitle')}</h2>
            <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
              {t('admin.users.aiRecommendationText')}
            </p>
            <div className="mt-md grid grid-cols-2 gap-sm">
              <div className="rounded-lg bg-white p-md dark:bg-slate-950">
                <p className="label-uppercase">{t('table.risk')}</p>
                <p className="mt-xs font-bold text-secondary dark:text-accent-gold">{t('risk.medium')}</p>
              </div>
              <div className="rounded-lg bg-white p-md dark:bg-slate-950">
                <p className="label-uppercase">{t('table.updated')}</p>
                <p className="mt-xs font-bold">{t('admin.users.justNow')}</p>
              </div>
            </div>
          </Card>

          <Card title={t('admin.security')}>
            <div className="space-y-md">
              <div>
                <div className="mb-xs flex items-center justify-between text-sm">
                  <span>{t('admin.users.twoFactorAuthentication')}</span>
                  <span>85%</span>
                </div>
                <ProgressBar value={85} />
              </div>
              <div>
                <div className="mb-xs flex items-center justify-between text-sm">
                  <span>{t('admin.users.passwordRotation')}</span>
                  <span>12%</span>
                </div>
                <ProgressBar value={12} />
              </div>
              <Button variant="secondary" className="w-full" leftIcon={<CheckCircle2 className="h-4 w-4" />}>
                {t('admin.users.manageSecurityPolicy')}
              </Button>
            </div>
          </Card>
        </aside>
      </div>
    </>
  );

  const renderWorkspaceTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.15fr_0.85fr]">
      <Card title={t('admin.workspace')} subtitle={t('admin.workspace.subtitle')}>
        <div className="grid gap-md sm:grid-cols-2">
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.workspaceName')}</p>
            <p className="mt-xs text-sm font-semibold">LexiGuard Legal Team</p>
          </div>
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.dataRegion')}</p>
            <p className="mt-xs text-sm font-semibold">Singapore / Vietnam</p>
          </div>
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.defaultJurisdiction')}</p>
            <p className="mt-xs text-sm font-semibold">Vietnam Commercial Law</p>
          </div>
          <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
            <p className="label-uppercase">{t('admin.workspace.retentionPolicy')}</p>
            <p className="mt-xs text-sm font-semibold">7 years encrypted archive</p>
          </div>
        </div>
        <div className="mt-md flex flex-wrap gap-sm">
          <Button variant="secondary">{t('actions.save')}</Button>
          <Button variant="ghost">{t('actions.cancel')}</Button>
        </div>
      </Card>

      <aside className="space-y-gutter">
        <Card title={t('admin.workspace.connectedSystems')}>
          <div className="space-y-sm text-sm">
            <p className="rounded-lg border border-legal-border p-sm dark:border-slate-700">{t('admin.workspace.connectedSystemsDms')}</p>
            <p className="rounded-lg border border-legal-border p-sm dark:border-slate-700">{t('admin.workspace.connectedSystemsSso')}</p>
            <p className="rounded-lg border border-legal-border p-sm dark:border-slate-700">{t('admin.workspace.connectedSystemsAudit')}</p>
          </div>
        </Card>
        <Card title={t('admin.workspace.policyBaseline')}>
          <p className="text-sm leading-6 text-on-surface-variant dark:text-slate-400">
            {t('admin.workspace.policyBaselineText')}
          </p>
        </Card>
      </aside>
    </div>
  );

  const renderLanguageTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.05fr_0.95fr]">
      <Card title={t('admin.language')} subtitle={t('admin.language.subtitle')}>
        <div className="space-y-md">
          <div>
            <p className="label-uppercase mb-sm">{t('admin.language.interfaceLanguage')}</p>
            <LanguageToggle />
          </div>
          <div className="rounded-lg bg-surface-container-low p-md text-sm leading-6 dark:bg-slate-800">
            {t('admin.language.helperText')}
          </div>
        </div>
      </Card>

      <Card title={t('admin.language.translationQuality')}>
        <div className="space-y-md text-sm">
          <div>
            <div className="mb-xs flex items-center justify-between">
              <span>{t('admin.language.uiLabelsCoverage')}</span>
              <span className="font-semibold">98%</span>
            </div>
            <ProgressBar value={98} />
          </div>
          <div>
            <div className="mb-xs flex items-center justify-between">
              <span>{t('admin.language.legalGlossaryConsistency')}</span>
              <span className="font-semibold">95%</span>
            </div>
            <ProgressBar value={95} />
          </div>
          <Button variant="secondary" className="w-full">
            {t('admin.language.exportLanguageReport')}
          </Button>
        </div>
      </Card>
    </div>
  );

  const renderThemeTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.05fr_0.95fr]">
      <Card title={t('admin.appearance')} actions={<Palette className="h-5 w-5 text-secondary dark:text-accent-gold" />}>
        <div className="space-y-md">
          <div>
            <p className="label-uppercase mb-sm">{t('admin.theme.themeMode')}</p>
            <div className="grid grid-cols-3 gap-2">
              {themeOptions.map((option) => {
                const Icon = option.icon;
                const active = theme === option.mode;
                return (
                  <button
                    key={option.mode}
                    type="button"
                    aria-pressed={active}
                    className={`inline-flex h-11 items-center justify-center gap-2 rounded-lg border text-sm font-semibold transition ${
                      active
                        ? 'border-primary bg-primary text-white shadow-sm dark:border-inverse-primary dark:bg-inverse-primary dark:text-slate-950'
                        : 'border-legal-border bg-white text-on-surface-variant hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'
                    }`}
                    onClick={() => setTheme(option.mode)}
                  >
                    <Icon className="h-4 w-4" aria-hidden="true" />
                    <span>{t(option.labelKey)}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <p className="label-uppercase mb-sm">{t('admin.theme.legalTextSize')}</p>
            <div className="grid grid-cols-3 gap-sm">
              {legalTextSizes.map((size) => {
                const active = legalTextSize === size;
                return (
                  <button
                    key={size}
                    type="button"
                    className={`h-10 rounded-lg border text-sm font-semibold transition ${
                      active
                        ? 'border-primary bg-surface-container text-primary dark:border-inverse-primary dark:bg-slate-800 dark:text-inverse-primary'
                        : 'border-legal-border bg-white hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800'
                    }`}
                    onClick={() => setLegalTextSize(size)}
                  >
                    {size}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </Card>

      <Card title={t('admin.theme.preview')}>
        <p style={{ fontSize: legalTextSize }} className="leading-7 text-on-surface-variant dark:text-slate-300">
          {t('admin.theme.previewText')}
        </p>
      </Card>
    </div>
  );

  const renderSecurityTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.15fr_0.85fr]">
      <Card title={t('admin.security')} subtitle={t('admin.security.subtitle')}>
        <div className="space-y-md">
          <div>
            <div className="mb-xs flex items-center justify-between text-sm">
              <span>{t('admin.security.twoFactorAuthentication')}</span>
              <span className="font-semibold">85%</span>
            </div>
            <ProgressBar value={85} />
          </div>
          <div>
            <div className="mb-xs flex items-center justify-between text-sm">
              <span>{t('admin.security.passwordRotation')}</span>
              <span className="font-semibold">88%</span>
            </div>
            <ProgressBar value={88} />
          </div>
          <div>
            <div className="mb-xs flex items-center justify-between text-sm">
              <span>{t('admin.security.sessionTimeout')}</span>
              <span className="font-semibold">92%</span>
            </div>
            <ProgressBar value={92} />
          </div>
          <Button leftIcon={<CheckCircle2 className="h-4 w-4" />}>{t('admin.security.applyPolicyUpdates')}</Button>
        </div>
      </Card>

      <Card tone="ai">
        <h2 className="text-title-lg font-semibold">{t('admin.security.guidance')}</h2>
        <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
          {t('admin.security.guidanceText')}
        </p>
      </Card>
    </div>
  );

  const renderAiConfigTab = () => (
    <div className="grid gap-gutter xl:grid-cols-[1.15fr_0.85fr]">
      <Card title={t('admin.aiConfig')} subtitle={t('admin.aiConfig.subtitle')}>
        <div className="grid gap-md md:grid-cols-2">
          <div className="rounded-lg border border-legal-border p-md dark:border-slate-700">
            <div className="mb-sm flex items-center gap-sm">
              <Cpu className="h-4 w-4 text-primary dark:text-inverse-primary" />
              <p className="font-semibold">{t('admin.aiConfig.primaryModel')}</p>
            </div>
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.aiConfig.primaryModelText')}</p>
          </div>
          <div className="rounded-lg border border-legal-border p-md dark:border-slate-700">
            <div className="mb-sm flex items-center gap-sm">
              <Database className="h-4 w-4 text-primary dark:text-inverse-primary" />
              <p className="font-semibold">{t('admin.aiConfig.retrievalStrategy')}</p>
            </div>
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('admin.aiConfig.retrievalStrategyText')}</p>
          </div>
        </div>
        <div className="mt-md flex flex-wrap gap-sm">
          <Button variant="secondary">{t('actions.save')}</Button>
          <Button variant="ghost">{t('admin.aiConfig.runValidation')}</Button>
        </div>
      </Card>

      <Card title={t('admin.aiConfig.guardrails')} actions={<ShieldCheck className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
        <ul className="space-y-sm text-sm text-on-surface-variant dark:text-slate-400">
          <li className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">{t('admin.aiConfig.guardrailsCitations')}</li>
          <li className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">{t('admin.aiConfig.guardrailsPii')}</li>
          <li className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">{t('admin.aiConfig.guardrailsConfidence')}</li>
        </ul>
      </Card>
    </div>
  );

  const renderActiveTab = () => {
    switch (activeTab) {
      case 'users':
        return renderUsersTab();
      case 'workspace':
        return renderWorkspaceTab();
      case 'language':
        return renderLanguageTab();
      case 'theme':
        return renderThemeTab();
      case 'security':
        return renderSecurityTab();
      case 'aiConfig':
        return renderAiConfigTab();
      default:
        return renderUsersTab();
    }
  };

  return (
    <div>
      <PageHeader
        title={t('admin.title')}
        subtitle={t('admin.subtitle')}
        actions={<Button leftIcon={<UserPlus className="h-4 w-4" />}>{t('admin.addUser')}</Button>}
      />

      <Tabs
        variant="pill"
        activeId={activeTab}
        tabListLabel={t('admin.title')}
        getTabId={(id) => getTabId(id as AdminTabId)}
        getPanelId={(id) => getPanelId(id as AdminTabId)}
        onChange={(id) => setActiveTab(id as AdminTabId)}
        items={adminTabs.map((tab) => ({ id: tab.id, label: t(tab.labelKey) }))}
      />

      <section
        className="mt-xl"
        role="tabpanel"
        id={getPanelId(activeTab)}
        aria-labelledby={getTabId(activeTab)}
      >
        {renderActiveTab()}
      </section>
    </div>
  );
}
