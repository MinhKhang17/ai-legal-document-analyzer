import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type ThemeMode = 'light' | 'dark' | 'system';
export type Language = 'en' | 'vi';

export interface MockUser {
  id: string;
  name: string;
  email: string;
  role: string;
  avatarInitials: string;
}

interface AppStoreValue {
  theme: ThemeMode;
  resolvedTheme: 'light' | 'dark';
  language: Language;
  sidebarCollapsed: boolean;
  mobileSidebarOpen: boolean;
  isAuthenticated: boolean;
  user: MockUser;
  setTheme: (theme: ThemeMode) => void;
  setLanguage: (language: Language) => void;
  toggleSidebar: () => void;
  setMobileSidebarOpen: (open: boolean) => void;
  signIn: () => void;
  signOut: () => void;
}

const STORAGE_KEYS = {
  theme: 'lexiguard.theme',
  language: 'lexiguard.language',
  sidebar: 'lexiguard.sidebarCollapsed',
  auth: 'lexiguard.authenticated',
} as const;

const defaultUser: MockUser = {
  id: 'usr-001',
  name: 'Legal Team',
  email: 'legal.team@lexiguard.ai',
  role: 'Senior Counsel',
  avatarInitials: 'LT',
};

const AppStoreContext = createContext<AppStoreValue | undefined>(undefined);

const getStoredValue = <T extends string>(key: string, fallback: T, allowed: readonly T[]): T => {
  if (typeof window === 'undefined') return fallback;
  const value = window.localStorage.getItem(key) as T | null;
  return value && allowed.includes(value) ? value : fallback;
};

const getSystemTheme = (): 'light' | 'dark' => {
  if (typeof window === 'undefined') return 'light';
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
};

const applyResolvedTheme = (theme: 'light' | 'dark') => {
  if (typeof document === 'undefined') return;
  document.documentElement.classList.toggle('dark', theme === 'dark');
  document.documentElement.dataset.theme = theme;
};

export function AppStoreProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>(() =>
    getStoredValue<ThemeMode>(STORAGE_KEYS.theme, 'system', ['light', 'dark', 'system']),
  );
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>(() =>
    theme === 'system' ? getSystemTheme() : theme,
  );
  const [language, setLanguageState] = useState<Language>(() =>
    getStoredValue<Language>(STORAGE_KEYS.language, 'vi', ['en', 'vi']),
  );
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return window.localStorage.getItem(STORAGE_KEYS.sidebar) === 'true';
  });
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => {
    if (typeof window === 'undefined') return true;
    return window.localStorage.getItem(STORAGE_KEYS.auth) !== 'false';
  });

  useEffect(() => {
    const nextTheme = theme === 'system' ? getSystemTheme() : theme;
    setResolvedTheme(nextTheme);
    applyResolvedTheme(nextTheme);
    window.localStorage.setItem(STORAGE_KEYS.theme, theme);

    if (theme !== 'system') return undefined;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = () => {
      const current = getSystemTheme();
      setResolvedTheme(current);
      applyResolvedTheme(current);
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEYS.language, language);
    document.documentElement.lang = language;
  }, [language]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEYS.sidebar, String(sidebarCollapsed));
  }, [sidebarCollapsed]);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEYS.auth, String(isAuthenticated));
  }, [isAuthenticated]);

  const value = useMemo<AppStoreValue>(
    () => ({
      theme,
      resolvedTheme,
      language,
      sidebarCollapsed,
      mobileSidebarOpen,
      isAuthenticated,
      user: defaultUser,
      setTheme: setThemeState,
      setLanguage: setLanguageState,
      toggleSidebar: () => setSidebarCollapsed((previous) => !previous),
      setMobileSidebarOpen,
      signIn: () => setIsAuthenticated(true),
      signOut: () => setIsAuthenticated(false),
    }),
    [theme, resolvedTheme, language, sidebarCollapsed, mobileSidebarOpen, isAuthenticated],
  );

  return <AppStoreContext.Provider value={value}>{children}</AppStoreContext.Provider>;
}

export const useAppStore = () => {
  const context = useContext(AppStoreContext);
  if (!context) {
    throw new Error('useAppStore must be used within AppStoreProvider');
  }
  return context;
};
