import { MailCheck, RotateCcw } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { resendVerificationEmail } from "../../services/auth.service";

export function CheckEmailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const initial = (location.state ?? {}) as Record<string, unknown>;
  const [email, setEmail] = useState(typeof initial.email === "string" ? initial.email : "");
  const [maskedEmail, setMaskedEmail] = useState(typeof initial.maskedEmail === "string" ? initial.maskedEmail : "email của bạn");
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
      setSeconds(response.data.resendAvailableInSeconds); setMessage("Nếu email hợp lệ, liên kết mới đã được xử lý.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Không thể gửi lại email."); }
    finally { setSending(false); }
  };

  return <Card className="mx-auto w-full max-w-md text-center">
    <MailCheck className="mx-auto h-12 w-12 text-primary" />
    <h1 className="mt-md text-2xl font-bold">Kiểm tra email</h1>
    <p className="mt-sm text-sm text-on-surface-variant">Chúng tôi đã xử lý email xác thực cho <strong>{maskedEmail}</strong>.</p>
    {delivery === "FAILED" && <p className="mt-md rounded-lg bg-amber-50 p-sm text-sm text-amber-800">Không gửi được email lần đầu. Tài khoản vẫn được giữ; bạn có thể gửi lại.</p>}
    {!email && <input className="form-field mt-md" type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="Email đăng ký" />}
    {message && <p className="mt-md text-sm text-on-surface-variant">{message}</p>}
    <div className="mt-lg space-y-sm">
      <Button className="w-full" variant="secondary" leftIcon={<RotateCcw className="h-4 w-4" />} disabled={sending || seconds > 0 || !email.trim()} onClick={() => void resend()}>{sending ? "Đang gửi…" : seconds > 0 ? `Gửi lại sau ${seconds}s` : "Gửi lại email"}</Button>
      <Button className="w-full" onClick={() => navigate("/login", { replace: true, state: { message: "Hãy đăng nhập sau khi đã xác nhận email." } })}>Tôi đã xác nhận</Button>
      <Link className="block text-sm font-semibold text-primary" to="/login">Quay về đăng nhập</Link>
    </div>
  </Card>;
}
