import { type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAppStore } from "../../store/AppStore";

interface RouteGuardProps {
  children: ReactNode;
}

function AuthLoadingView() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory text-on-surface dark:bg-slate-950 dark:text-slate-100">
      <div className="text-center">
        <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-outline-variant border-t-primary dark:border-slate-700" />
        <p className="mt-3 text-sm text-on-surface-variant dark:text-slate-400">Verifying session...</p>
      </div>
    </div>
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

function isExpert(user: CurrentUserState) {
  return user?.role === "EXPERT";
}

function RoleAccessDeniedView() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-ivory px-6 text-on-surface dark:bg-slate-950 dark:text-slate-100">
      <div className="max-w-md text-center">
        <p className="text-sm font-semibold uppercase tracking-[0.16em] text-primary dark:text-inverse-primary">Access restricted</p>
        <h1 className="mt-3 font-domine text-2xl font-bold">This account cannot access this page.</h1>
        <p className="mt-3 text-sm text-on-surface-variant dark:text-slate-400">
          Please switch to an account with the matching role or return to the correct workspace area.
        </p>
      </div>
    </div>
  );
}

export function PublicRoute({ children }: RouteGuardProps) {
  const { isAuthLoading, isAuthReady, isAuthenticated, user } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <>{children}</>;
  }

  return <Navigate to={resolveRedirectToDashboardOrAdmin(user)} replace />;
}

export function AuthenticatedRoute({ children }: RouteGuardProps) {
  const { isAuthLoading, isAuthReady, isAuthenticated, user } = useAppStore();
  const location = useLocation();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          errorMessage: "You must be signed in to access this page.",
          from: location.pathname,
        }}
      />
    );
  }

  return <>{children}</>;
}

export function CustomerRoute({ children }: RouteGuardProps) {
  const { isAuthLoading, isAuthReady, isAuthenticated, user } = useAppStore();
  const location = useLocation();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          errorMessage: "You must be signed in to access this page.",
          from: location.pathname,
        }}
      />
    );
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
  const { isAuthLoading, isAuthReady, isAuthenticated, user } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <Navigate to="/login" replace />;
  }

  if (user.role !== "ADMIN") {
  return <Navigate to={resolveRedirectToDashboardOrAdmin(user)} replace />;
}

  return <>{children}</>;
}

export function ExpertRoute({ children }: RouteGuardProps) {
  const { isAuthLoading, isAuthReady, isAuthenticated, user } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return <AuthLoadingView />;
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return <Navigate to="/login" replace />;
  }

  if (user.role !== "EXPERT") {
    return <Navigate to={resolveRedirectToDashboardOrAdmin(user)} replace />;
  }

  return <>{children}</>;
}
