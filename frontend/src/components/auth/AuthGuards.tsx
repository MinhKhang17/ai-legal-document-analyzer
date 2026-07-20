import { type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useI18n } from "../../hooks/useI18n";
import { useAppStore } from "../../store/AppStore";

interface RouteGuardProps {
  children: ReactNode;
}

function AuthLoadingView() {
  const { t } = useI18n();

  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory text-on-surface dark:bg-slate-950 dark:text-slate-100">
      <div className="text-center">
        <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-outline-variant border-t-primary dark:border-slate-700" />
        <p className="mt-3 text-sm text-on-surface-variant dark:text-slate-400">{t("auth.verifyingSession")}</p>
      </div>
    </div>
  );
}

function AuthRecoveryUnavailableView({ onRetry }: { onRetry: () => Promise<void> }) {
  const { t } = useI18n();

  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory px-6 text-on-surface dark:bg-slate-950 dark:text-slate-100">
      <div className="max-w-md text-center">
        <h1 className="font-domine text-2xl font-bold">{t("auth.recoveryUnavailableTitle")}</h1>
        <p className="mt-3 text-sm text-on-surface-variant dark:text-slate-400">
          {t("auth.recoveryUnavailableMessage")}
        </p>
        <button
          className="mt-5 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary-container focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          type="button"
          onClick={() => void onRetry()}
        >
          {t("auth.retrySessionRecovery")}
        </button>
      </div>
    </div>
  );
}

function SignInRedirect() {
  const location = useLocation();
  const { t } = useI18n();
  const returnTo = `${location.pathname}${location.search}${location.hash}`;

  return (
    <Navigate
      to="/login"
      replace
      state={{
        errorMessage: t("auth.mustSignIn"),
        from: returnTo,
      }}
    />
  );
}

type CurrentUserState = ReturnType<typeof useAppStore>["user"];

function resolveRedirectToDashboardOrAdmin(user: CurrentUserState) {
  if (user?.role === "ADMIN") {
    return "/admin";
  }

  if (user?.role === "EXPERT") {
    return "/lawyer/tickets";
  }

  return "/dashboard";
}

function RoleAccessDeniedView() {
  const { t } = useI18n();

  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory px-6 text-on-surface dark:bg-slate-950 dark:text-slate-100">
      <div className="max-w-md text-center">
        <p className="text-sm font-semibold uppercase tracking-[0.16em] text-primary dark:text-inverse-primary">{t("auth.accessRestricted")}</p>
        <h1 className="mt-3 font-domine text-2xl font-bold">{t("auth.unauthorizedTitle")}</h1>
        <p className="mt-3 text-sm text-on-surface-variant dark:text-slate-400">
          {t("auth.accessRestrictedMessage")}
        </p>
      </div>
    </div>
  );
}

export function PublicRoute({ children }: RouteGuardProps) {
  const {
    isAuthLoading,
    isAuthReady,
    isAuthenticated,
    authRecoveryError,
    retryAuthRecovery,
    user,
  } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (authRecoveryError) {
    return <AuthRecoveryUnavailableView onRetry={retryAuthRecovery} />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <>{children}</>;
  }

  return <Navigate to={resolveRedirectToDashboardOrAdmin(user)} replace />;
}

export function AuthenticatedRoute({ children }: RouteGuardProps) {
  const {
    isAuthLoading,
    isAuthReady,
    isAuthenticated,
    authRecoveryError,
    retryAuthRecovery,
    user,
  } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (authRecoveryError) {
    return <AuthRecoveryUnavailableView onRetry={retryAuthRecovery} />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <SignInRedirect />;
  }

  return <>{children}</>;
}

export function CustomerRoute({ children }: RouteGuardProps) {
  const {
    isAuthLoading,
    isAuthReady,
    isAuthenticated,
    authRecoveryError,
    retryAuthRecovery,
    user,
  } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (authRecoveryError) {
    return <AuthRecoveryUnavailableView onRetry={retryAuthRecovery} />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <SignInRedirect />;
  }

  if (user.role === "CUSTOMER") {
    return <>{children}</>;
  }

  if (user.role === "ADMIN") {
    return <Navigate to="/admin" replace />;
  }

  return <RoleAccessDeniedView />;
}

export function AdminRoute({ children }: RouteGuardProps) {
  const {
    isAuthLoading,
    isAuthReady,
    isAuthenticated,
    authRecoveryError,
    retryAuthRecovery,
    user,
  } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (authRecoveryError) {
    return <AuthRecoveryUnavailableView onRetry={retryAuthRecovery} />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <SignInRedirect />;
  }

  if (user.role !== "ADMIN") {
    return <RoleAccessDeniedView />;
  }

  return <>{children}</>;
}

export function ExpertRoute({ children }: RouteGuardProps) {
  const {
    isAuthLoading,
    isAuthReady,
    isAuthenticated,
    authRecoveryError,
    retryAuthRecovery,
    user,
  } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (authRecoveryError) {
    return <AuthRecoveryUnavailableView onRetry={retryAuthRecovery} />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <SignInRedirect />;
  }

  if (user.role !== "EXPERT") {
    return <RoleAccessDeniedView />;
  }

  return <>{children}</>;
}

export function AdminOrExpertRoute({ children }: RouteGuardProps) {
  const {
    isAuthLoading,
    isAuthReady,
    isAuthenticated,
    authRecoveryError,
    retryAuthRecovery,
    user,
  } = useAppStore();
  if (isAuthLoading || !isAuthReady) return <AuthLoadingView />;
  if (authRecoveryError) return <AuthRecoveryUnavailableView onRetry={retryAuthRecovery} />;
  if (!isAuthenticated || user === null || user.active === false) return <SignInRedirect />;
  if (user.role !== "ADMIN" && user.role !== "EXPERT") return <RoleAccessDeniedView />;
  return <>{children}</>;
}
