import { CheckCircle2, XCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { useI18n } from "../../hooks/useI18n";
import { resendVerificationEmail, verifyEmail } from "../../services/auth.service";

export function VerifyEmailPage() {
  const { t } = useI18n();
  const [params] = useSearchParams();
  const token = params.get("token")?.trim() ?? "";
  const [state, setState] = useState<"loading" | "success" | "error">("loading");
  const [message, setMessage] = useState("");
  const [email, setEmail] = useState("");
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState("");

  useEffect(() => {
    if (!token) {
      setState("error");
      setMessage(t("auth.verifyEmail.missingToken"));
      return;
    }
    let active = true;
    void verifyEmail(token)
      .then(() => { if (active) setState("success"); })
      .catch(() => {
        if (active) {
          setState("error");
          setMessage(t("auth.verifyEmail.error"));
        }
      });
    return () => { active = false; };
  }, [t, token]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory p-lg dark:bg-slate-950">
      <Card className="w-full max-w-lg text-center">
        {state === "loading" ? (
          <><div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-outline-variant border-t-primary" aria-hidden="true" /><h1 aria-live="polite" className="mt-md text-xl font-bold">{t("auth.verifyEmail.loading")}</h1></>
        ) : state === "success" ? (
          <><CheckCircle2 className="mx-auto h-12 w-12 text-success" aria-hidden="true" /><h1 className="mt-md text-xl font-bold">{t("auth.verifyEmail.successTitle")}</h1><p className="mt-sm text-sm text-on-surface-variant">{t("auth.verifyEmail.successDescription")}</p><Link to="/login"><Button className="mt-lg">{t("actions.signIn")}</Button></Link></>
        ) : (
          <><XCircle className="mx-auto h-12 w-12 text-error" aria-hidden="true" /><h1 className="mt-md text-xl font-bold">{t("auth.verifyEmail.errorTitle")}</h1><p role="alert" className="mt-sm text-sm text-error">{message}</p><div className="mt-lg text-left"><label className="text-sm font-semibold">{t("auth.verifyEmail.resendLabel")}<input className="form-field mt-xs" type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder={t("auth.checkEmail.emailPlaceholder")} /></label><Button className="mt-sm" disabled={resending || !email.trim()} onClick={async () => { setResending(true); try { await resendVerificationEmail(email.trim()); setResendMessage(t("auth.verifyEmail.resendSuccess")); } catch { setResendMessage(t("auth.verifyEmail.resendError")); } finally { setResending(false); } }}>{resending ? t("auth.verifyEmail.resending") : t("auth.verifyEmail.resendAction")}</Button>{resendMessage && <p role="status" className="mt-sm text-xs text-on-surface-variant">{resendMessage}</p>}</div><Link to="/login"><Button className="mt-lg" variant="secondary">{t("auth.checkEmail.backToLogin")}</Button></Link></>
        )}
      </Card>
    </div>
  );
}
