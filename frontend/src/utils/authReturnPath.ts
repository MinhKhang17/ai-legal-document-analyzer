import type { UserRole } from "../types/auth";

const INTERNAL_URL_BASE = "https://lexiguard.local";

const matchesRoute = (pathname: string, route: string): boolean =>
  pathname === route || pathname.startsWith(`${route}/`);

const isRoleAuthorizedPath = (pathname: string, role: UserRole): boolean => {
  if (pathname === "/settings" || matchesRoute(pathname, "/shared-conversation")) {
    return true;
  }

  if (role === "ADMIN") {
    return [
      "/admin",
      "/knowledge-base",
      "/jobs",
      "/editor/risk-review",
      "/shared/chat",
    ].some((route) => matchesRoute(pathname, route));
  }

  if (role === "EXPERT") {
    return ["/lawyer", "/shared/chat"].some((route) => matchesRoute(pathname, route));
  }

  if (role !== "CUSTOMER") return false;
  if (matchesRoute(pathname, "/editor/risk-review")) return false;

  return [
    "/dashboard",
    "/projects",
    "/documents",
    "/upload",
    "/editor",
    "/chat",
    "/tickets",
    "/billing",
    "/payment-result",
    "/contracts",
  ].some((route) => matchesRoute(pathname, route));
};

export const getSafeAuthReturnPath = (
  value: unknown,
  role: UserRole,
): string | undefined => {
  if (
    typeof value !== "string" ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    value.includes("\\") ||
    /[\u0000-\u001f\u007f]/.test(value)
  ) {
    return undefined;
  }

  try {
    const parsed = new URL(value, INTERNAL_URL_BASE);
    if (parsed.origin !== INTERNAL_URL_BASE) return undefined;

    return isRoleAuthorizedPath(parsed.pathname, role) ? value : undefined;
  } catch {
    return undefined;
  }
};
