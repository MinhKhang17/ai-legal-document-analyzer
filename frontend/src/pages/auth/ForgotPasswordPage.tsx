import { ArrowLeft, CheckCircle2, Mail } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { requestPasswordReset } from "../../api/authApi";
import { Button } from "../../components/common/Button";
import { useI18n } from "../../hooks/useI18n";
import { isAuthRequestError } from "../../services/auth.service";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;

export function ForgotPasswordPage() {
  const { t } = useI18n();
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const normalizedEmail = email.trim();
    if (!EMAIL_PATTERN.test(normalizedEmail)) {
      setError(t("auth.forgotPasswordInvalidEmail"));
      return;
    }

    setError("");
    setLoading(true);

    try {
      await requestPasswordReset({ email: normalizedEmail });
      setSuccess(true);
    } catch (requestError) {
      if (isAuthRequestError(requestError) && requestError.status === 400) {
        setError(t("auth.forgotPasswordInvalidEmail"));
      } else if (isAuthRequestError(requestError) && requestError.status === 429) {
        setError(t("auth.passwordResetRateLimited"));
      } else {
        setError(t("auth.forgotPasswordRequestError"));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <header className="mb-7">
        <h1 className="mt-1 font-sans text-[30px] font-semibold leading-[1.1] text-on-surface dark:text-slate-100">
          {t("auth.forgotPasswordTitle")}
        </h1>
        <p className="mt-2 text-[11px] leading-5 text-on-surface-variant dark:text-slate-400">
          {t("auth.forgotPasswordDescription")}
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
            <p className="text-sm leading-6">{t("auth.forgotPasswordSuccess")}</p>
          </div>
        </div>
      ) : (
        <form className="space-y-4" onSubmit={handleSubmit} aria-busy={loading} noValidate>
          <div className="space-y-xs">
            <label className="label-uppercase text-[9px]" htmlFor="forgot-password-email">
              {t("auth.emailAddress")}
            </label>
            <div className="relative">
              <Mail
                className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline"
                aria-hidden="true"
              />
              <input
                id="forgot-password-email"
                className="form-field h-10 py-2 pl-9 text-sm"
                type="email"
                autoComplete="email"
                inputMode="email"
                placeholder={t("auth.emailPlaceholder")}
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  if (error) setError("");
                }}
                required
                aria-invalid={Boolean(error)}
                aria-describedby={error ? "forgot-password-error" : undefined}
                disabled={loading}
              />
            </div>
          </div>

          {error && (
            <p
              id="forgot-password-error"
              className="rounded-md bg-red-50 px-3 py-2 text-xs font-medium text-red-700 dark:bg-red-950/40 dark:text-red-200"
              role="alert"
            >
              {error}
            </p>
          )}

          <Button type="submit" className="h-11 w-full" size="md" disabled={loading}>
            {loading ? t("auth.forgotPasswordSubmitting") : t("auth.forgotPasswordSubmit")}
          </Button>
        </form>
      )}

      <div className="mt-6 text-center">
        <Link
          className="inline-flex items-center gap-2 text-[11px] font-semibold text-primary hover:underline focus-visible:rounded focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:text-inverse-primary"
          to="/login"
        >
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
          {t("auth.backToSignIn")}
        </Link>
      </div>
    </div>
  );
}
