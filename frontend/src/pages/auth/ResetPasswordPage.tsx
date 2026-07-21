import { ArrowLeft, CheckCircle2, Eye, EyeOff, LockKeyhole } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { resetPassword } from "../../api/authApi";
import { Button } from "../../components/common/Button";
import { useI18n } from "../../hooks/useI18n";
import { isAuthRequestError } from "../../services/auth.service";

const MIN_PASSWORD_LENGTH = 8;

export function ResetPasswordPage() {
  const { t } = useI18n();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token")?.trim() ?? "";
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setError(t("auth.resetPasswordTooShort"));
      return;
    }

    if (newPassword !== confirmNewPassword) {
      setError(t("auth.passwordMismatch"));
      return;
    }

    setError("");
    setLoading(true);

    try {
      await resetPassword({ token, newPassword, confirmNewPassword });
      setNewPassword("");
      setConfirmNewPassword("");
      setSuccess(true);
    } catch (requestError) {
      if (
        isAuthRequestError(requestError) &&
        (requestError.status === 400 || requestError.status === 404)
      ) {
        setError(t("auth.resetPasswordInvalidToken"));
      } else if (isAuthRequestError(requestError) && requestError.status === 409) {
        setError(t("auth.resetPasswordSameAsCurrent"));
      } else if (isAuthRequestError(requestError) && requestError.status === 429) {
        setError(t("auth.passwordResetRateLimited"));
      } else {
        setError(t("auth.resetPasswordRequestError"));
      }
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div>
        <header className="mb-7">
          <h1 className="mt-1 font-sans text-[30px] font-semibold leading-[1.1] text-on-surface dark:text-slate-100">
            {t("auth.resetPasswordTitle")}
          </h1>
        </header>
        <div
          className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-amber-900 dark:border-amber-900/70 dark:bg-amber-950/40 dark:text-amber-100"
          role="alert"
        >
          <p className="text-sm font-semibold">{t("auth.resetPasswordMissingTokenTitle")}</p>
          <p className="mt-1 text-xs leading-5">{t("auth.resetPasswordMissingToken")}</p>
        </div>
        <div className="mt-6 flex flex-wrap items-center justify-center gap-x-5 gap-y-3 text-[11px] font-semibold">
          <Link className="text-primary hover:underline dark:text-inverse-primary" to="/forgot-password">
            {t("auth.requestNewResetLink")}
          </Link>
          <Link className="inline-flex items-center gap-2 text-primary hover:underline dark:text-inverse-primary" to="/login">
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            {t("auth.backToSignIn")}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <header className="mb-6">
        <h1 className="mt-1 font-sans text-[30px] font-semibold leading-[1.1] text-on-surface dark:text-slate-100">
          {t("auth.resetPasswordTitle")}
        </h1>
        <p className="mt-2 text-[11px] leading-5 text-on-surface-variant dark:text-slate-400">
          {t("auth.resetPasswordDescription")}
        </p>
      </header>

      {success ? (
        <div
          className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-900 dark:border-emerald-900/70 dark:bg-emerald-950/40 dark:text-emerald-100"
          role="status"
          aria-live="polite"
        >
          <div className="flex items-start gap-3">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
            <div>
              <p className="text-sm font-semibold">{t("auth.resetPasswordSuccessTitle")}</p>
              <p className="mt-1 text-xs leading-5">{t("auth.resetPasswordSuccess")}</p>
            </div>
          </div>
        </div>
      ) : (
        <form className="space-y-4" onSubmit={handleSubmit} aria-busy={loading} noValidate>
          <div className="space-y-xs">
            <label className="label-uppercase text-[9px]" htmlFor="new-password">
              {t("auth.newPassword")}
            </label>
            <div className="relative">
              <LockKeyhole className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline" aria-hidden="true" />
              <input
                id="new-password"
                className="form-field h-10 py-2 pl-9 pr-10 text-sm"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                value={newPassword}
                onChange={(event) => {
                  setNewPassword(event.target.value);
                  if (error) setError("");
                }}
                minLength={MIN_PASSWORD_LENGTH}
                required
                aria-invalid={Boolean(error)}
                aria-describedby="new-password-guidance reset-password-error"
                disabled={loading}
              />
              <button
                className="absolute right-3 top-1/2 -translate-y-1/2 rounded text-outline hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                type="button"
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? t("auth.hidePassword") : t("auth.showPassword")}
              >
                {showPassword ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
              </button>
            </div>
            <p id="new-password-guidance" className="text-[10px] leading-4 text-on-surface-variant dark:text-slate-400">
              {t("auth.resetPasswordPolicy")}
            </p>
          </div>

          <div className="space-y-xs">
            <label className="label-uppercase text-[9px]" htmlFor="confirm-new-password">
              {t("auth.confirmNewPassword")}
            </label>
            <div className="relative">
              <LockKeyhole className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline" aria-hidden="true" />
              <input
                id="confirm-new-password"
                className="form-field h-10 py-2 pl-9 pr-10 text-sm"
                type={showConfirmPassword ? "text" : "password"}
                autoComplete="new-password"
                value={confirmNewPassword}
                onChange={(event) => {
                  setConfirmNewPassword(event.target.value);
                  if (error) setError("");
                }}
                minLength={MIN_PASSWORD_LENGTH}
                required
                aria-invalid={Boolean(error)}
                aria-describedby={error ? "reset-password-error" : undefined}
                disabled={loading}
              />
              <button
                className="absolute right-3 top-1/2 -translate-y-1/2 rounded text-outline hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                type="button"
                onClick={() => setShowConfirmPassword((value) => !value)}
                aria-label={showConfirmPassword ? t("auth.hideConfirmPassword") : t("auth.showConfirmPassword")}
              >
                {showConfirmPassword ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
              </button>
            </div>
          </div>

          {error && (
            <p
              id="reset-password-error"
              className="rounded-md bg-red-50 px-3 py-2 text-xs font-medium text-red-700 dark:bg-red-950/40 dark:text-red-200"
              role="alert"
            >
              {error}
            </p>
          )}

          <Button type="submit" className="h-11 w-full" size="md" disabled={loading}>
            {loading ? t("auth.resetPasswordSubmitting") : t("auth.resetPasswordSubmit")}
          </Button>
        </form>
      )}

      <div className="mt-6 flex flex-wrap items-center justify-center gap-x-5 gap-y-3 text-[11px] font-semibold">
        {!success && (
          <Link className="text-primary hover:underline dark:text-inverse-primary" to="/forgot-password">
            {t("auth.requestNewResetLink")}
          </Link>
        )}
        <Link className="inline-flex items-center gap-2 text-primary hover:underline dark:text-inverse-primary" to="/login">
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          {t("auth.backToSignIn")}
        </Link>
      </div>
    </div>
  );
}
