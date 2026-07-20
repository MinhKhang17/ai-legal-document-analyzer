import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  getCurrentUser,
  isAuthUnauthorizedError,
  logout as requestLogout,
  refreshAccessToken,
} from "../services/auth.service";
import type { CurrentUser } from "../types/auth";
import { clearAccessToken, getAccessToken, migrateLegacyAccessToken, setAccessToken } from "../services/authSession";

export type ThemeMode = "light" | "dark" | "system";
export type Language = "en" | "vi";

interface AppStoreValue {
  theme: ThemeMode;
  resolvedTheme: "light" | "dark";
  language: Language;
  sidebarCollapsed: boolean;
  mobileSidebarOpen: boolean;
  isAuthenticated: boolean;
  isAuthLoading: boolean;
  isAuthReady: boolean;
  user: CurrentUser | null;
  setTheme: (theme: ThemeMode) => void;
  setLanguage: (language: Language) => void;
  toggleSidebar: () => void;
  setMobileSidebarOpen: (open: boolean) => void;
  signIn: (accessToken?: string, user?: CurrentUser) => void;
  signOut: (options?: SignOutOptions) => Promise<void>;
}

interface SignOutOptions {
  remote?: boolean;
}

const STORAGE_KEYS = {
  theme: "lexiguard.theme",
  language: "lexiguard.language",
  sidebar: "lexiguard.sidebarCollapsed",
  user: "lexiguard.authUser",
} as const;

const getSystemTheme = (): "light" | "dark" => {
  if (typeof window === "undefined") return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
};

const applyResolvedTheme = (theme: "light" | "dark") => {
  if (typeof document === "undefined") return;
  document.documentElement.classList.toggle("dark", theme === "dark");
  document.documentElement.dataset.theme = theme;
};

const parseStoredUser = (): CurrentUser | null => {
  if (typeof window === "undefined") return null;

  const raw = window.localStorage.getItem(STORAGE_KEYS.user);
  if (raw === null) return null;

  try {
    const parsed = JSON.parse(raw) as Partial<CurrentUser>;

    if (
      parsed &&
      typeof parsed.id === "number" &&
      typeof parsed.firstName === "string" &&
      typeof parsed.lastName === "string" &&
      typeof parsed.email === "string" &&
      typeof parsed.role === "string" &&
      typeof parsed.active === "boolean"
    ) {
      return {
        id: parsed.id,
        firstName: parsed.firstName,
        lastName: parsed.lastName,
        email: parsed.email,
        role: parsed.role,
        active: parsed.active,
        emailVerified: parsed.emailVerified,
        mustChangePassword: parsed.mustChangePassword,
        passwordResetDeadline: parsed.passwordResetDeadline,
      };
    }
  } catch {
    window.localStorage.removeItem(STORAGE_KEYS.user);
  }

  return null;
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

const AppStoreContext = createContext<AppStoreValue | undefined>(undefined);

export function AppStoreProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>(() => {
    if (typeof window === "undefined") {
      return "light";
    }

    const storedTheme = window.localStorage.getItem(STORAGE_KEYS.theme);

    return storedTheme === "light" || storedTheme === "dark" || storedTheme === "system"
      ? storedTheme
      : "system";
  });

  const [resolvedTheme, setResolvedTheme] = useState<"light" | "dark">(() =>
    theme === "system" ? getSystemTheme() : theme,
  );
  const [language, setLanguageState] = useState<Language>(() => {
    if (typeof window === "undefined") return "vi";

    const storedLanguage = window.localStorage.getItem(STORAGE_KEYS.language);
    return storedLanguage === "en" || storedLanguage === "vi"
      ? storedLanguage
      : "vi";
  });
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    return window.localStorage.getItem(STORAGE_KEYS.sidebar) === "true";
  });
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isAuthLoading, setIsAuthLoading] = useState<boolean>(true);
  const [isAuthReady, setIsAuthReady] = useState<boolean>(false);
  const [user, setUser] = useState<CurrentUser | null>(() => parseStoredUser());

  useEffect(() => {
    const nextTheme = theme === "system" ? getSystemTheme() : theme;
    setResolvedTheme(nextTheme);
    applyResolvedTheme(nextTheme);
    window.localStorage.setItem(STORAGE_KEYS.theme, theme);

    if (theme !== "system") return;

    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const handleChange = () => {
      const current = getSystemTheme();
      setResolvedTheme(current);
      applyResolvedTheme(current);
    };

    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, [theme]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(STORAGE_KEYS.language, language);
    document.documentElement.lang = language;
  }, [language]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(STORAGE_KEYS.sidebar, String(sidebarCollapsed));
  }, [sidebarCollapsed]);

  const setTheme = (nextTheme: ThemeMode) => {
    setThemeState(nextTheme);
  };

  const setLanguage = (nextLanguage: Language) => {
    setLanguageState(nextLanguage);
  };

  const clearAuthState = () => {
    if (typeof window === "undefined") {
      setIsAuthenticated(false);
      setUser(null);
      setIsAuthLoading(false);
      setIsAuthReady(true);
      return;
    }

    clearAccessToken();
    window.localStorage.removeItem(STORAGE_KEYS.user);

    setIsAuthenticated(false);
    setUser(null);
    setIsAuthLoading(false);
    setIsAuthReady(true);
  };

  const hydrateAuthState = (accessToken: string, nextUser: CurrentUser) => {
    if (!nextUser.active) {
      clearAuthState();
      return;
    }

    setAccessToken(accessToken);
    window.localStorage.setItem(STORAGE_KEYS.user, JSON.stringify(nextUser));
    setUser(nextUser);
    setIsAuthenticated(true);
    setIsAuthLoading(false);
    setIsAuthReady(true);
  };

  const refreshSession = useCallback(async (): Promise<void> => {
    if (typeof window === "undefined") {
      setIsAuthLoading(false);
      setIsAuthReady(true);
      return;
    }

    setIsAuthLoading(true);

    try {
      const storedToken = migrateLegacyAccessToken();

      if (storedToken) try {
        const response = await getCurrentUser(storedToken);
        if (!isCurrentUser(response.data)) {
          clearAuthState();
          return;
        }
        hydrateAuthState(storedToken, response.data);
        return;
      } catch (error) {
        if (!isAuthUnauthorizedError(error)) {
          clearAuthState();
          return;
        }
      }

      const refreshResponse = await refreshAccessToken();
      const refreshedToken = refreshResponse.data?.accessToken;

      if (typeof refreshedToken !== "string" || refreshedToken.trim().length === 0) {
        clearAuthState();
        return;
      }

      const response = await getCurrentUser(refreshedToken);
      if (!isCurrentUser(response.data)) {
        clearAuthState();
        return;
      }
      hydrateAuthState(refreshedToken, response.data);
    } catch {
      clearAuthState();
    } finally {
      setIsAuthLoading(false);
      setIsAuthReady(true);
    }
  }, []);

  useEffect(() => {
    void refreshSession();
  }, [refreshSession]);

  const signIn = (accessToken?: string, nextUser?: CurrentUser) => {
    if (typeof accessToken !== "string" || accessToken.trim().length === 0) {
      clearAuthState();
      return;
    }

    if (!nextUser || nextUser.active === false) {
      clearAuthState();
      return;
    }

    hydrateAuthState(accessToken, nextUser);
  };

  const signOut = async (options: SignOutOptions = {}) => {
    const shouldCallRemote =
      options.remote !== false &&
      typeof window !== "undefined" &&
      Boolean(getAccessToken());

    if (shouldCallRemote) {
      try {
        await requestLogout();
      } catch (error) {
        console.warn("Logout API failed; clearing local session anyway.", error);
      }
    }

    clearAuthState();
  };

  const value = useMemo<AppStoreValue>(
    () => ({
      theme,
      resolvedTheme,
      language,
      sidebarCollapsed,
      mobileSidebarOpen,
      isAuthenticated,
      isAuthLoading,
      isAuthReady,
      user,
      setTheme,
      setLanguage,
      toggleSidebar: () => setSidebarCollapsed((previous) => !previous),
      setMobileSidebarOpen,
      signIn,
      signOut,
    }),
    [
      theme,
      resolvedTheme,
      language,
      sidebarCollapsed,
      mobileSidebarOpen,
      isAuthenticated,
      isAuthLoading,
      isAuthReady,
      user,
    ],
  );

  return <AppStoreContext.Provider value={value}>{children}</AppStoreContext.Provider>;
}

export const useAppStore = () => {
  const context = useContext(AppStoreContext);
  if (!context) {
    throw new Error("useAppStore must be used within AppStoreProvider");
  }
  return context;
};
