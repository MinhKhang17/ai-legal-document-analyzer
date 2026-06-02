import { ArrowRight, Check, Eye, EyeOff, Lock, Mail, UserRound } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '../../api/authApi';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { useI18n } from '../../hooks/useI18n';
import { useAppStore } from '../../store/AppStore';

type RegisterFormData = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
};

export function RegisterPage() {
  const { t } = useI18n();
  const { signIn } = useAppStore();
  const navigate = useNavigate();

  const [formData, setFormData] = useState<RegisterFormData>({
    firstName: 'Legal',
    lastName: 'Team',
    email: 'legal.team@lexiguard.ai',
    password: 'lexiguard',
    confirmPassword: '',
  });
  const [confirmPasswordTouched, setConfirmPasswordTouched] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const isPasswordMismatch =
    confirmPasswordTouched &&
    formData.confirmPassword.length > 0 &&
    formData.password !== formData.confirmPassword;

  const canSubmit =
    !isSubmitting &&
    acceptedTerms &&
    formData.password === formData.confirmPassword &&
    formData.confirmPassword.length > 0;

  const updateFormField = <K extends keyof RegisterFormData>(
    field: K,
    value: RegisterFormData[K],
  ) => {
    setFormData((previous) => ({ ...previous, [field]: value }));
    setSubmitError(null);
  };

  return (
    <Card className="mx-auto w-full max-w-[380px] px-5 py-4">
      <header className="mb-2.5">
        <p className="label-uppercase text-[8px] text-secondary dark:text-accent-gold">
          {t('auth.secureWorkspaceSetup')}
        </p>

        <h1 className="mt-1 font-sans text-[30px] font-semibold leading-[1.1] text-on-surface dark:text-slate-100">
          {t('auth.createAccount')}
        </h1>

        <p className="mt-1 text-[11px] leading-4 text-on-surface-variant dark:text-slate-400">
          {t('auth.registerSubtitle')}
        </p>
      </header>

      <form
        className="space-y-2.5"
        onSubmit={async (event) => {
          event.preventDefault();
          const form = event.currentTarget;

          setConfirmPasswordTouched(true);
          setSubmitError(null);

          if (!form.checkValidity()) {
            form.reportValidity();
            return;
          }

          if (formData.password.length === 0) {
            return;
          }

          if (formData.confirmPassword.length === 0) {
            return;
          }

          if (formData.password !== formData.confirmPassword) {
            return;
          }

          if (!acceptedTerms) {
            setSubmitError(t('auth.acceptTermsRequired'));
            return;
          }

          setIsSubmitting(true);

          try {
            await register({
              firstName: formData.firstName.trim(),
              lastName: formData.lastName.trim(),
              email: formData.email.trim(),
              password: formData.password,
              confirmPassword: formData.confirmPassword,
              acceptedTerms,
            });

            signIn();
            navigate('/dashboard');
          } catch (error) {
            setSubmitError(
              error instanceof Error && error.message.length > 0
                ? error.message
                : t('auth.registrationFailed'),
            );
          } finally {
            setIsSubmitting(false);
          }
        }}
      >
        <div className="grid gap-2.5 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="label-uppercase text-[8px]" htmlFor="firstName">
              {t('auth.firstName')}
            </label>
            <div className="relative">
              <UserRound className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-outline" />
              <input
                id="firstName"
                className="form-field h-9 py-1.5 pl-8 text-[13px]"
                value={formData.firstName}
                onChange={(event) => updateFormField('firstName', event.target.value)}
                required
              />
            </div>
          </div>

          <div className="space-y-1">
            <label className="label-uppercase text-[8px]" htmlFor="lastName">
              {t('auth.lastName')}
            </label>
            <input
              id="lastName"
              className="form-field h-9 py-1.5 text-[13px]"
              value={formData.lastName}
              onChange={(event) => updateFormField('lastName', event.target.value)}
              required
            />
          </div>
        </div>

        <div className="space-y-1">
          <label className="label-uppercase text-[8px]" htmlFor="registerEmail">
            {t('auth.corporateEmail')}
          </label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-outline" />
            <input
              id="registerEmail"
              className="form-field h-9 py-1.5 pl-8 text-[13px]"
              type="email"
              value={formData.email}
              onChange={(event) => updateFormField('email', event.target.value)}
              required
            />
          </div>
        </div>

        <div className="grid gap-2.5 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="label-uppercase text-[8px]" htmlFor="registerPassword">
              {t('auth.password')}
            </label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-outline" />
              <input
                id="registerPassword"
                className="form-field h-9 py-1.5 pl-8 pr-10 text-[13px]"
                type={showPassword ? 'text' : 'password'}
                value={formData.password}
                onChange={(event) => updateFormField('password', event.target.value)}
                required
              />
              <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-primary"
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? t('auth.hidePassword') : t('auth.showPassword')}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <div className="space-y-1">
            <label className="label-uppercase text-[8px]" htmlFor="confirmPassword">
              {t('auth.confirmPassword')}
            </label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-outline" />
              <input
                id="confirmPassword"
                className="form-field h-9 py-1.5 pl-8 pr-10 text-[13px]"
                type={showConfirmPassword ? 'text' : 'password'}
                value={formData.confirmPassword}
                onChange={(event) => updateFormField('confirmPassword', event.target.value)}
                onBlur={() => setConfirmPasswordTouched(true)}
                placeholder={t('auth.confirmPasswordPlaceholder')}
                required
                aria-invalid={isPasswordMismatch}
                aria-describedby={isPasswordMismatch ? 'confirm-password-error' : undefined}
              />
              <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-primary"
                onClick={() => setShowConfirmPassword((value) => !value)}
                aria-label={
                  showConfirmPassword
                    ? t('auth.hideConfirmPassword')
                    : t('auth.showConfirmPassword')
                }
              >
                {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>
        </div>

        {isPasswordMismatch && (
          <p
            id="confirm-password-error"
            className="text-[10px] font-medium text-error dark:text-red-300"
          >
            {t('auth.passwordMismatch')}
          </p>
        )}

        <label className="flex cursor-pointer items-start gap-2 rounded-lg bg-surface-container-low p-2.5 text-[10px] leading-4 text-on-surface-variant dark:bg-slate-800 dark:text-slate-400">
          <span className="relative mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center">
            <input
              className="peer h-4 w-4 cursor-pointer appearance-none rounded border border-outline bg-white checked:border-primary checked:bg-primary dark:border-slate-600 dark:bg-slate-900"
              type="checkbox"
              checked={acceptedTerms}
              onChange={(event) => {
                setAcceptedTerms(event.target.checked);
                setSubmitError(null);
              }}
              aria-label={t('auth.acceptWorkspaceTerms')}
            />
            <Check className="pointer-events-none absolute h-3 w-3 text-white opacity-0 peer-checked:opacity-100" />
          </span>

          <span>
            {t('auth.acceptWorkspaceTerms')}
          </span>
        </label>

        {submitError && (
          <p className="text-[10px] font-medium text-error dark:text-red-300" role="alert">
            {submitError}
          </p>
        )}

        <Button
          type="submit"
          className="h-10 w-full disabled:cursor-not-allowed disabled:opacity-50"
          size="md"
          disabled={!canSubmit}
          rightIcon={isSubmitting ? undefined : <ArrowRight className="h-4 w-4" />}
        >
          {isSubmitting ? t('auth.creatingWorkspace') : t('actions.register')}
        </Button>
      </form>

      <p className="mt-3 text-center text-[10px] text-on-surface-variant dark:text-slate-400">
        {t('auth.alreadyHaveAccount')}{' '}
        <Link className="font-semibold text-primary hover:underline dark:text-inverse-primary" to="/login">
          {t('auth.logIn')}
        </Link>
      </p>
    </Card>
  );
}
