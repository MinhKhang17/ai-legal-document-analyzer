import { CheckCircle2, XCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { resendVerificationEmail, verifyEmail } from "../../services/auth.service";

const verificationErrorMessage = (error: unknown) => {
  const message = error instanceof Error ? error.message : "";
  if (message.includes("TOKEN_EXPIRED")) return "Liên kết đã hết hạn.";
  if (message.includes("TOKEN_ALREADY_USED")) return "Liên kết đã được sử dụng.";
  if (message.includes("TOKEN_INVALID")) return "Liên kết không hợp lệ.";
  return message || "Không thể xác thực email.";
};

export function VerifyEmailPage() {
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
      setMessage("Liên kết xác thực không hợp lệ vì thiếu token.");
      return;
    }
    let active = true;
    void verifyEmail(token)
      .then(() => { if (active) setState("success"); })
      .catch((error: unknown) => {
        if (active) {
          setState("error");
          setMessage(verificationErrorMessage(error));
        }
      });
    return () => { active = false; };
  }, [token]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory p-lg dark:bg-slate-950">
      <Card className="w-full max-w-lg text-center">
        {state === "loading" ? (
          <><div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-outline-variant border-t-primary" /><h1 className="mt-md text-xl font-bold">Đang xác thực email…</h1></>
        ) : state === "success" ? (
          <><CheckCircle2 className="mx-auto h-12 w-12 text-success" /><h1 className="mt-md text-xl font-bold">Xác thực email thành công</h1><p className="mt-sm text-sm text-on-surface-variant">Tài khoản của bạn đã sẵn sàng sử dụng.</p><Link to="/login"><Button className="mt-lg">Đăng nhập</Button></Link></>
        ) : (
          <><XCircle className="mx-auto h-12 w-12 text-error" /><h1 className="mt-md text-xl font-bold">Không thể xác thực email</h1><p className="mt-sm text-sm text-error">{message}</p><div className="mt-lg text-left"><label className="text-sm font-semibold">Gửi lại email xác thực<input className="form-field mt-xs" type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="email@example.com" /></label><Button className="mt-sm" disabled={resending || !email.trim()} onClick={async () => { setResending(true); try { await resendVerificationEmail(email.trim()); setResendMessage('Nếu email hợp lệ, liên kết mới đã được gửi.'); } catch (error) { setResendMessage(error instanceof Error ? error.message : 'Không thể gửi lại email.'); } finally { setResending(false); } }}>{resending ? 'Đang gửi…' : 'Gửi lại liên kết'}</Button>{resendMessage && <p className="mt-sm text-xs text-on-surface-variant">{resendMessage}</p>}</div><Link to="/login"><Button className="mt-lg" variant="secondary">Về trang đăng nhập</Button></Link></>
        )}
      </Card>
    </div>
  );
}
