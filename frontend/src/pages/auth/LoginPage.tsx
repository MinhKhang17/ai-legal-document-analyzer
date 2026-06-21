import { Eye, EyeOff, Lock, Mail } from "lucide-react";
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Button } from "../../components/common/Button";
import { useI18n } from "../../hooks/useI18n";
import { useAppStore } from "../../store/AppStore";
import { getCurrentUser, login } from "../../api/authApi";
import type { CurrentUser } from "../../types/auth";

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
      <path
        fill="#EA4335"
        d="M12 10.2v3.9h5.5c-.2 1.2-.9 2.2-1.9 2.9l3.1 2.4c1.8-1.7 2.9-4.1 2.9-7 0-.7-.1-1.4-.2-2.1H12z"
      />
      <path
        fill="#34A853"
        d="M12 22c2.6 0 4.9-.9 6.5-2.5l-3.1-2.4c-.9.6-2 1-3.4 1-2.6 0-4.7-1.7-5.4-4l-3.2 2.5C4.9 19.8 8.2 22 12 22z"
      />
      <path
        fill="#FBBC05"
        d="M6.6 14.1c-.2-.6-.4-1.3-.4-2.1s.1-1.4.4-2.1L3.4 7.4C2.8 8.6 2.5 10.2 2.5 12s.3 3.4.9 4.6l3.2-2.5z"
      />
      <path
        fill="#4285F4"
        d="M12 5.9c1.4 0 2.6.5 3.6 1.4l2.7-2.7C16.8 3.1 14.6 2 12 2 8.2 2 4.9 4.2 3.4 7.4l3.2 2.5c.7-2.3 2.8-4 5.4-4z"
      />
    </svg>
  );
}

const getStringStateValue = (state: unknown, key: string): string => {
  if (typeof state === "object" && state !== null) {
    const value = (state as Record<string, unknown>)[key];
    return typeof value === "string" ? value : "";
  }

  return "";
};

const isCurrentUser = (value: unknown): value is CurrentUser => {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const user = value as Partial<CurrentUser>;

  return (
    typeof user.id === "number" &&
    typeof user.firstName === "string" &&
    typeof user.lastName === "string" &&
    typeof user.email === "string" &&
    typeof user.role === "string" &&
    typeof user.active === "boolean"
  );
};

export function LoginPage() {
  const { t } = useI18n();
  const location = useLocation();
  const { signIn, signOut } = useAppStore();
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const initialError = getStringStateValue(location.state, "errorMessage");
  const initialNotice =
    getStringStateValue(location.state, "noticeMessage") ||
    getStringStateValue(location.state, "message");
  const [error, setError] = useState(initialError);
  const [notice, setNotice] = useState(initialNotice);

  return (
    <div>
      <header className="mb-7">
        <h1 className="mt-1 font-sans text-[30px] font-semibold leading-[1.1] text-on-surface dark:text-slate-100">
          {t("auth.loginTitle")}
        </h1>
        <p className="mt-1.5 text-[11px] leading-5 text-on-surface-variant dark:text-slate-400">
          {t("auth.loginSubtitle")}
        </p>
      </header>

      <form
        className="space-y-4"
        onSubmit={async (event) => {
          event.preventDefault();

          setError("");
          setNotice("");
          setLoading(true);

          try {
            const response = await login({
              email: email.trim(),
              password,
            });

            const accessToken = response.data.accessToken?.trim();

            if (!accessToken) {
              throw new Error("Login response did not include an access token.");
            }

            localStorage.setItem("accessToken", accessToken);

            const currentUserResponse = await getCurrentUser(accessToken);
            const currentUser = currentUserResponse.data;

            if (!isCurrentUser(currentUser)) {
              throw new Error("Unable to load current user session.");
            }

            if (!currentUser.active) {
              signOut();
              localStorage.removeItem("accessToken");
              setError("Your account is inactive.");
              return;
            }

            signIn(accessToken, currentUser);

            if (currentUser.role === "ADMIN") {
              navigate("/admin", { replace: true });
            } else {
              navigate("/dashboard", { replace: true });
            }
          } catch (error) {
            localStorage.removeItem("accessToken");
            signOut();
            setError(error instanceof Error ? error.message : "Login failed");
          } finally {
            setLoading(false);
          }
        }}
      >
        <div className="space-y-xs">
          <label className="label-uppercase text-[9px]" htmlFor="email">
            {t("auth.corporateEmailAddress")}
          </label>
          <div className="relative">
            <Mail
              className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline"
              aria-hidden="true"
            />
            <input
              id="email"
              className="form-field h-10 py-2 pl-9 text-sm"
              type="email"
              placeholder="name@company.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>
        </div>

        <div className="space-y-xs">
          <div className="flex items-center justify-between">
            <label className="label-uppercase text-[9px]" htmlFor="password">
              {t("auth.password")}
            </label>
            <a
              className="text-[10px] font-semibold text-primary hover:underline dark:text-inverse-primary"
              href="#forgot"
            >
              {t("auth.forgotPassword")}
            </a>
          </div>
          <div className="relative">
            <Lock
              className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-outline"
              aria-hidden="true"
            />
            <input
              id="password"
              className="form-field h-10 py-2 pl-9 pr-10 text-sm"
              type={showPassword ? "text" : "password"}
              placeholder="••••••••"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            <button
              className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-primary"
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              aria-label={
                showPassword ? t("auth.hidePassword") : t("auth.showPassword")
              }
            >
              {showPassword ? (
                <EyeOff className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Eye className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <input
            id="remember"
            type="checkbox"
            className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
            defaultChecked
          />
          <label
            className="text-[11px] text-on-surface-variant dark:text-slate-400"
            htmlFor="remember"
          >
            {t("auth.rememberMe")}
          </label>
        </div>
        {error && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-xs font-medium text-red-600">
            {error}
          </p>
        )}
        {notice && !error && (
          <p className="rounded-md bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700">
            {notice}
          </p>
        )}
        <Button
          type="submit"
          className="h-11 w-full"
          size="md"
          disabled={loading}
        >
          {loading ? "Signing in..." : t("auth.signIn")}
        </Button>
      </form>

      {/* <div className="my-5 flex items-center gap-3">
        <div className="h-px flex-1 bg-outline-variant" />
        <span className="text-[10px] font-bold uppercase tracking-[0.12em] text-on-surface-variant dark:text-slate-400">
          {t("auth.orContinueWith")}
        </span>
        <div className="h-px flex-1 bg-outline-variant" />
      </div> */}

      {/* <Button
        variant="secondary"
        className="h-10 w-full text-sm"
        size="md"
        leftIcon={<GoogleIcon />}
      >
        {t("auth.continueWithGoogle")}
      </Button> */}

      <footer className="mt-6 text-center text-[11px] text-on-surface-variant dark:text-slate-400">
        {t("auth.newToLexiGuard")}{" "}
        <Link
          className="font-semibold text-primary hover:underline dark:text-inverse-primary"
          to="/register"
        >
          {t("auth.requestEarlyAccess")}
        </Link>
        <div className="mt-4 flex justify-center gap-3 text-[11px]">
          <a className="hover:text-primary" href="#privacy">
            {t("auth.privacyPolicy")}
          </a>
          <span>•</span>
          <a className="hover:text-primary" href="#terms">
            {t("auth.termsOfService")}
          </a>
        </div>
      </footer>
    </div>
  );
}
