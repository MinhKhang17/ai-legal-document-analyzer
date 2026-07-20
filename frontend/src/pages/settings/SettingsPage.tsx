import { Check, KeyRound, Languages, Monitor, Moon, Palette, Sun } from 'lucide-react';
import { useState } from 'react';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import { useAppStore, type Language, type ThemeMode } from '../../store/AppStore';
import { cn } from '../../utils/cn';
import { translate } from '../../utils/i18n';
import { changePassword } from '../../services/user.service';

const themeOptions: Array<{ mode: ThemeMode; labelKey: string; icon: typeof Sun }> = [
  { mode: 'light', labelKey: 'theme.light', icon: Sun },
  { mode: 'dark', labelKey: 'theme.dark', icon: Moon },
  { mode: 'system', labelKey: 'theme.system', icon: Monitor },
];

const languageOptions: Array<{ language: Language; labelKey: string; shortLabel: string }> = [
  { language: 'vi', labelKey: 'language.vietnamese', shortLabel: 'VI' },
  { language: 'en', labelKey: 'language.english', shortLabel: 'EN' },
];

export function SettingsPage() {
  const { t } = useI18n();
  const toast = useToast();
  const { language, setLanguage, theme, setTheme } = useAppStore();
  const [passwordForm, setPasswordForm] = useState({ oldPassword: '', newPassword: '', confirmNewPassword: '' });
  const [changingPassword, setChangingPassword] = useState(false);

  const handleChangePassword = async () => {
    if (!passwordForm.oldPassword.trim() || passwordForm.newPassword.length < 8 || passwordForm.newPassword !== passwordForm.confirmNewPassword) {
      toast.warning(t('settings.password.validation'));
      return;
    }
    setChangingPassword(true);
    try {
      await changePassword(passwordForm);
      setPasswordForm({ oldPassword: '', newPassword: '', confirmNewPassword: '' });
      toast.success(t('settings.password.success'));
      window.setTimeout(() => window.location.reload(), 600);
    } catch {
      toast.error(t('settings.password.error'), t('toast.errorTitle'));
    } finally { setChangingPassword(false); }
  };

  return (
    <div>
      <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />

      <div className="grid gap-gutter xl:grid-cols-2">
        <Card
          title={t('settings.appearance.title')}
          subtitle={t('settings.appearance.subtitle')}
          actions={<Palette className="h-5 w-5 text-primary dark:text-inverse-primary" />}
        >
          <div className="grid gap-sm sm:grid-cols-3">
            {themeOptions.map((option) => {
              const Icon = option.icon;
              const active = theme === option.mode;

              return (
                <button
                  key={option.mode}
                  type="button"
                  aria-pressed={active}
                  className={cn(
                    'flex min-h-28 flex-col items-start justify-between rounded-lg border p-md text-left transition',
                    active
                      ? 'border-primary bg-primary text-white shadow-sm dark:border-inverse-primary dark:bg-inverse-primary dark:text-slate-950'
                      : 'border-legal-border bg-white text-on-surface hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800',
                  )}
                  onClick={() => {
                    setTheme(option.mode);
                    toast.success(t('settings.themeChanged'), t('toast.successTitle'));
                  }}
                >
                  <span className="flex w-full items-center justify-between gap-sm">
                    <Icon className="h-5 w-5" aria-hidden="true" />
                    {active && <Check className="h-4 w-4" aria-hidden="true" />}
                  </span>
                  <span>
                    <span className="block text-sm font-semibold">{t(option.labelKey)}</span>
                    <span className={cn('mt-xs block text-xs', active ? 'text-white/80 dark:text-slate-800' : 'text-on-surface-variant dark:text-slate-400')}>
                      {t(`settings.appearance.${option.mode}`)}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        </Card>

        <Card
          title={t('settings.language.title')}
          subtitle={t('settings.language.subtitle')}
          actions={<Languages className="h-5 w-5 text-primary dark:text-inverse-primary" />}
        >
          <div className="grid gap-sm sm:grid-cols-2">
            {languageOptions.map((option) => {
              const active = language === option.language;

              return (
                <button
                  key={option.language}
                  type="button"
                  aria-pressed={active}
                  className={cn(
                    'flex min-h-24 items-center gap-md rounded-lg border p-md text-left transition',
                    active
                      ? 'border-primary bg-primary text-white shadow-sm dark:border-inverse-primary dark:bg-inverse-primary dark:text-slate-950'
                      : 'border-legal-border bg-white text-on-surface hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800',
                  )}
                  onClick={() => {
                    setLanguage(option.language);
                    toast.success(
                      translate(option.language, 'settings.languageChanged'),
                      translate(option.language, 'toast.successTitle'),
                    );
                  }}
                >
                  <span className={cn(
                    'flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-bold',
                    active ? 'bg-white/15 text-white dark:bg-slate-950/10 dark:text-slate-950' : 'bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary',
                  )}>
                    {option.shortLabel}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold">{t(option.labelKey)}</span>
                    <span className={cn('mt-xs block text-xs', active ? 'text-white/80 dark:text-slate-800' : 'text-on-surface-variant dark:text-slate-400')}>
                      {t(`settings.language.${option.language}`)}
                    </span>
                  </span>
                  {active && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
                </button>
              );
            })}
          </div>
        </Card>
      </div>

      <Card className="mt-gutter" title={t('settings.password.title')} subtitle={t('settings.password.subtitle')} actions={<KeyRound className="h-5 w-5 text-primary" />}>
        <div className="grid gap-md md:grid-cols-3">
          <label className="text-sm font-semibold">{t('settings.password.current')}<input className="form-field mt-xs" type="password" autoComplete="current-password" value={passwordForm.oldPassword} onChange={(e) => setPasswordForm((v) => ({ ...v, oldPassword: e.target.value }))} /></label>
          <label className="text-sm font-semibold">{t('settings.password.new')}<input className="form-field mt-xs" type="password" autoComplete="new-password" value={passwordForm.newPassword} onChange={(e) => setPasswordForm((v) => ({ ...v, newPassword: e.target.value }))} /></label>
          <label className="text-sm font-semibold">{t('settings.password.confirm')}<input className="form-field mt-xs" type="password" autoComplete="new-password" value={passwordForm.confirmNewPassword} onChange={(e) => setPasswordForm((v) => ({ ...v, confirmNewPassword: e.target.value }))} /></label>
        </div>
        <Button className="mt-md" onClick={() => void handleChangePassword()} disabled={changingPassword}>{changingPassword ? t('settings.password.changing') : t('settings.password.submit')}</Button>
      </Card>
    </div>
  );
}
