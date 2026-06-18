import { Navigate } from 'react-router-dom';
import { type PropsWithChildren } from 'react';
import { AppShell } from '../components/common/AppShell';
import { useAppStore } from '../store/AppStore';
import type { CurrentUser } from '../types/auth';

const AUTH_GUARD_FALLBACK_TEXT = 'Checking authentication...';

function AuthLoadingFallback() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-surface text-on-surface dark:bg-slate-950 dark:text-slate-100">
      <p className="text-sm text-on-surface-variant">{AUTH_GUARD_FALLBACK_TEXT}</p>
    </div>
  );
}

type GuardResult = {
  isReady: boolean;
  shouldRedirect?: { to: string };
};

const isAdminRole = (role: CurrentUser['role']) => role === 'ADMIN';

const getHomeRouteForRole = (role: CurrentUser['role']) =>
  isAdminRole(role) ? '/admin' : '/dashboard';

const useAuthGuardState = (requiresAdmin = false): GuardResult => {
  const { isAuthLoading, isAuthReady, isAuthenticated, user } = useAppStore();

  if (isAuthLoading || !isAuthReady) {
    return { isReady: false };
  }

  if (!isAuthenticated || user === null || user.active === false) {
    return { isReady: true, shouldRedirect: { to: '/login' } };
  }

  if (requiresAdmin && !isAdminRole(user.role)) {
    return { isReady: true, shouldRedirect: { to: '/dashboard' } };
  }

  return { isReady: true };
};

export function PublicRoute({ children }: PropsWithChildren) {
  const { isAuthLoading, isAuthReady, isAuthenticated, user } = useAppStore();

  if (isAuthLoading || !isAuthReady) return <AuthLoadingFallback />;

  if (isAuthenticated && user !== null && user.active) {
    return <Navigate to={getHomeRouteForRole(user.role)} replace />;
  }

  return <>{children}</>;
}

export function ProtectedAppRoute() {
  const guard = useAuthGuardState();

  if (!guard.isReady) return <AuthLoadingFallback />;
  if (guard.shouldRedirect) return <Navigate to={guard.shouldRedirect.to} replace />;

  return <AppShell />;
}

export function AdminRoute({ children }: PropsWithChildren) {
  const guard = useAuthGuardState(true);

  if (!guard.isReady) return <AuthLoadingFallback />;
  if (guard.shouldRedirect) return <Navigate to={guard.shouldRedirect.to} replace />;

  return <>{children}</>;
}
