import {
  ArrowLeft,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  ShieldAlert,
  ShieldCheck,
} from 'lucide-react';
import { useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import { changePassword } from '../../services/user.service';
import { useAppStore } from '../../store/AppStore';
import { cn } from '../../utils/cn';

type PasswordFieldName = 'oldPassword' | 'newPassword' | 'confirmNewPassword';

type PasswordForm = Record<PasswordFieldName, string>;
type PasswordErrors = Partial<Record<PasswordFieldName, string>>;

const EMPTY_FORM: PasswordForm = {
  oldPassword: '',
  newPassword: '',
  confirmNewPassword: '',
};

interface PasswordFieldProps {
  id: string;
  label: string;
  value: string;
  visible: boolean;
  autoComplete: 'current-password' | 'new-password';
  disabled: boolean;
  error?: string;
  helperText?: string;
  showLabel: string;
  hideLabel: string;
  onChange: (value: string) => void;
  onToggleVisibility: () => void;
}

function PasswordField({
  id,
  label,
  value,
  visible,
  autoComplete,
  disabled,
  error,
  helperText,
  showLabel,
  hideLabel,
  onChange,
  onToggleVisibility,
}: PasswordFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const helperId = helperText ? `${id}-helper` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [helperId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className="space-y-xs">
      <label className="block text-sm font-semibold text-on-surface dark:text-slate-100" htmlFor={id}>
        {label}
      </label>
      <div className="relative">
        <KeyRound
          className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline dark:text-slate-500"
          aria-hidden="true"
        />
        <input
          ref={inputRef}
          id={id}
          className={cn(
            'form-field h-11 pl-10 pr-11 text-sm',
            error && 'border-error focus:border-error focus:ring-error/15 dark:border-red-500',
          )}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          disabled={disabled}
          required
          aria-invalid={Boolean(error)}
          aria-describedby={describedBy}
        />
        <button
          className="absolute right-2.5 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-outline transition hover:bg-surface-container-high hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-inverse-primary"
          type="button"
          onClick={() => {
            const selectionStart = inputRef.current?.selectionStart ?? null;
            const selectionEnd = inputRef.current?.selectionEnd ?? null;
            onToggleVisibility();
            window.requestAnimationFrame(() => {
              if (selectionStart !== null && selectionEnd !== null) {
                inputRef.current?.setSelectionRange(selectionStart, selectionEnd);
              }
            });
          }}
          disabled={disabled}
          aria-label={`${visible ? hideLabel : showLabel}: ${label}`}
          aria-pressed={visible}
        >
          {visible ? (
            <EyeOff className="h-4 w-4" aria-hidden="true" />
          ) : (
            <Eye className="h-4 w-4" aria-hidden="true" />
          )}
        </button>
      </div>
      {helperText && (
        <p id={helperId} className="text-xs leading-5 text-on-surface-variant dark:text-slate-400">
          {helperText}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs font-medium leading-5 text-error dark:text-red-300">
          {error}
        </p>
      )}
    </div>
  );
}

export function AccountSecurityPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { user } = useAppStore();
  const [passwordForm, setPasswordForm] = useState<PasswordForm>(EMPTY_FORM);
  const [errors, setErrors] = useState<PasswordErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [showOldPassword, setShowOldPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const passwordDeadline = user?.passwordResetDeadline
    ? new Date(user.passwordResetDeadline).toLocaleString(language === 'vi' ? 'vi-VN' : 'en-US')
    : t('auth.temporaryPasswordDefaultDeadline');

  const updateField = (field: PasswordFieldName, value: string) => {
    setPasswordForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setSubmitError('');
    setSuccessMessage('');
  };

  const validate = (): boolean => {
    const nextErrors: PasswordErrors = {};

    if (!passwordForm.oldPassword.trim()) {
      nextErrors.oldPassword = t('settings.password.currentRequired');
    }

    if (!passwordForm.newPassword) {
      nextErrors.newPassword = t('settings.password.newRequired');
    } else if (passwordForm.newPassword.length < 8) {
      nextErrors.newPassword = t('settings.password.minimumLength');
    } else if (passwordForm.newPassword === passwordForm.oldPassword) {
      nextErrors.newPassword = t('settings.password.mustDiffer');
    }

    if (!passwordForm.confirmNewPassword) {
      nextErrors.confirmNewPassword = t('settings.password.confirmRequired');
    } else if (passwordForm.newPassword !== passwordForm.confirmNewPassword) {
      nextErrors.confirmNewPassword = t('settings.password.mismatch');
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitError('');
    setSuccessMessage('');

    if (!validate()) return;

    setSubmitting(true);
    try {
      await changePassword(passwordForm);
      setPasswordForm(EMPTY_FORM);
      setErrors({});
      setShowOldPassword(false);
      setShowNewPassword(false);
      setShowConfirmPassword(false);
      setSuccessMessage(t('settings.password.success'));
      toast.success(t('settings.password.success'));
      window.setTimeout(() => window.location.reload(), 600);
    } catch {
      setSubmitError(t('settings.password.error'));
      toast.error(t('settings.password.error'), t('toast.errorTitle'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-4xl">
      <nav className="mb-md" aria-label={t('settings.security.backToSettings')}>
        <Link
          to="/settings"
          className="inline-flex items-center justify-center gap-sm rounded-lg border border-legal-border bg-white px-md py-sm text-sm font-semibold text-primary transition hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:border-slate-700 dark:bg-slate-900 dark:text-inverse-primary dark:hover:bg-slate-800"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {t('settings.security.backToSettings')}
        </Link>
      </nav>

      <PageHeader
        className="mb-lg"
        eyebrow={t('settings.title')}
        title={t('settings.security.title')}
        subtitle={t('settings.security.subtitle')}
      />

      <Card
        className="w-full"
        title={t('settings.password.title')}
        subtitle={t('settings.security.passwordDescription')}
        actions={<ShieldCheck className="h-5 w-5 text-primary dark:text-inverse-primary" aria-hidden="true" />}
      >
        {user?.mustChangePassword && (
          <div className="mb-lg flex items-start gap-sm rounded-lg border border-warning/40 bg-warning/10 p-md text-sm" role="status">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-warning" aria-hidden="true" />
            <div>
              <p className="font-semibold text-on-surface dark:text-slate-100">
                {t('auth.temporaryPasswordNotice')}
              </p>
              <p className="mt-xs leading-5 text-on-surface-variant dark:text-slate-300">
                {t('auth.temporaryPasswordDeadline', { deadline: passwordDeadline })}
              </p>
            </div>
          </div>
        )}

        <form className="space-y-lg" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
          <div className="max-w-xl">
            <PasswordField
              id="security-current-password"
              label={t('settings.password.current')}
              value={passwordForm.oldPassword}
              visible={showOldPassword}
              autoComplete="current-password"
              disabled={submitting}
              error={errors.oldPassword}
              showLabel={t('auth.showPassword')}
              hideLabel={t('auth.hidePassword')}
              onChange={(value) => updateField('oldPassword', value)}
              onToggleVisibility={() => setShowOldPassword((current) => !current)}
            />
          </div>

          <div className="grid gap-lg md:grid-cols-2">
            <PasswordField
              id="security-new-password"
              label={t('settings.password.new')}
              value={passwordForm.newPassword}
              visible={showNewPassword}
              autoComplete="new-password"
              disabled={submitting}
              error={errors.newPassword}
              helperText={t('settings.password.requirements')}
              showLabel={t('auth.showPassword')}
              hideLabel={t('auth.hidePassword')}
              onChange={(value) => updateField('newPassword', value)}
              onToggleVisibility={() => setShowNewPassword((current) => !current)}
            />
            <PasswordField
              id="security-confirm-password"
              label={t('settings.password.confirm')}
              value={passwordForm.confirmNewPassword}
              visible={showConfirmPassword}
              autoComplete="new-password"
              disabled={submitting}
              error={errors.confirmNewPassword}
              showLabel={t('auth.showPassword')}
              hideLabel={t('auth.hidePassword')}
              onChange={(value) => updateField('confirmNewPassword', value)}
              onToggleVisibility={() => setShowConfirmPassword((current) => !current)}
            />
          </div>

          {submitError && (
            <div className="rounded-lg border border-error/30 bg-error/10 px-md py-sm text-sm font-medium text-error dark:border-red-900 dark:bg-red-950/40 dark:text-red-200" role="alert">
              {submitError}
            </div>
          )}

          {successMessage && (
            <div className="flex items-start gap-sm rounded-lg border border-emerald-200 bg-emerald-50 px-md py-sm text-sm font-medium text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200" role="status" aria-live="polite">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              {successMessage}
            </div>
          )}

          <div className="flex flex-col-reverse gap-sm border-t border-legal-border pt-lg sm:flex-row sm:items-center sm:justify-end dark:border-slate-700">
            <p className="flex-1 text-xs leading-5 text-on-surface-variant dark:text-slate-400">
              {t('settings.security.passwordPrivacy')}
            </p>
            <Button
              type="submit"
              className="h-11 w-full sm:w-auto sm:min-w-44"
              disabled={submitting}
              leftIcon={submitting ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" /> : undefined}
            >
              {submitting ? t('settings.password.changing') : t('settings.password.submit')}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
