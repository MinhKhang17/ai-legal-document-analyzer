import { MailCheck, RotateCcw } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { useI18n } from "../../hooks/useI18n";
import { resendVerificationEmail } from "../../services/auth.service";

export function CheckEmailPage() {
  const { t } = useI18n();
  const location = useLocation();
  const navigate = useNavigate();
  const initial = (location.state ?? {}) as Record<string, unknown>;
  const [email, setEmail] = useState(typeof initial.email === "string" ? initial.email : "");
  const [maskedEmail, setMaskedEmail] = useState(typeof initial.maskedEmail === "string" ? initial.maskedEmail : "");
  const [delivery, setDelivery] = useState(typeof initial.emailDeliveryStatus === "string" ? initial.emailDeliveryStatus : "SENT");
  const [seconds, setSeconds] = useState(typeof initial.resendAvailableInSeconds === "number" ? initial.resendAvailableInSeconds : 0);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = window.setInterval(() => setSeconds((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [seconds]);

  const resend = async () => {
    if (!email.trim() || seconds > 0) return;
    setSending(true); setMessage("");
    try {
      const response = await resendVerificationEmail(email.trim());
      setMaskedEmail(response.data.maskedEmail); setDelivery(response.data.emailDeliveryStatus);
      setSeconds(response.data.resendAvailableInSeconds); setMessage(t("auth.checkEmail.resendProcessed"));
    } catch { setMessage(t("auth.checkEmail.resendError")); }
    finally { setSending(false); }
  };

  return <Card className="mx-auto w-full max-w-md text-center">
    <MailCheck className="mx-auto h-12 w-12 text-primary" aria-hidden="true" />
    <h1 className="mt-md text-2xl font-bold">{t("auth.checkEmail.title")}</h1>
    <p className="mt-sm text-sm text-on-surface-variant">{t("auth.checkEmail.description", { email: maskedEmail || t("auth.checkEmail.defaultRecipient") })}</p>
    {delivery === "FAILED" && <p className="mt-md rounded-lg bg-amber-50 p-sm text-sm text-amber-800 dark:bg-amber-950/30 dark:text-amber-200">{t("auth.checkEmail.initialDeliveryFailed")}</p>}
    {!email && <input className="form-field mt-md" type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder={t("auth.checkEmail.emailPlaceholder")} aria-label={t("auth.checkEmail.emailPlaceholder")} />}
    {message && <p role="status" className="mt-md text-sm text-on-surface-variant">{message}</p>}
    <div className="mt-lg space-y-sm">
      <Button className="w-full" variant="secondary" leftIcon={<RotateCcw className="h-4 w-4" />} disabled={sending || seconds > 0 || !email.trim()} onClick={() => void resend()}>{sending ? t("auth.checkEmail.sending") : seconds > 0 ? t("auth.checkEmail.resendCountdown", { seconds }) : t("auth.checkEmail.resend")}</Button>
      <Button className="w-full" onClick={() => navigate("/login", { replace: true, state: { message: t("auth.checkEmail.loginAfterVerification") } })}>{t("auth.checkEmail.confirmed")}</Button>
      <Link className="block text-sm font-semibold text-primary" to="/login">{t("auth.checkEmail.backToLogin")}</Link>
    </div>
  </Card>;
}
