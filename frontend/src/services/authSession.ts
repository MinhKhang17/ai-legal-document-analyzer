const LEGACY_ACCESS_TOKEN_KEY = "accessToken";
const SESSION_ACCESS_TOKEN_KEY = "lexiguard.sessionAccessToken";

let accessToken: string | undefined;

const removeLegacyAccessToken = (): void => {
  if (typeof window === "undefined") return;

  try {
    window.localStorage.removeItem(LEGACY_ACCESS_TOKEN_KEY);
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
};

const removeSessionAccessToken = (): void => {
  if (typeof window === "undefined") return;

  try {
    window.sessionStorage.removeItem(SESSION_ACCESS_TOKEN_KEY);
  } catch {
    // Keep the in-memory session usable when storage is unavailable.
  }
};

export const setAccessToken = (value?: string): void => {
  const normalized = value?.trim();
  accessToken = normalized ? normalized : undefined;

  if (typeof window !== "undefined") {
    try {
      if (accessToken) {
        window.sessionStorage.setItem(SESSION_ACCESS_TOKEN_KEY, accessToken);
      } else {
        window.sessionStorage.removeItem(SESSION_ACCESS_TOKEN_KEY);
      }
    } catch {
      // The memory token remains the source for the current page lifetime.
    }
  }

  removeLegacyAccessToken();
};

export const getAccessToken = (): string | undefined => {
  if (accessToken || typeof window === "undefined") return accessToken;

  try {
    const storedToken = window.sessionStorage.getItem(SESSION_ACCESS_TOKEN_KEY)?.trim();
    if (storedToken) {
      accessToken = storedToken;
      return accessToken;
    }

    window.sessionStorage.removeItem(SESSION_ACCESS_TOKEN_KEY);
  } catch {
    // An unreadable storage entry is not proof of authentication.
  }

  return undefined;
};

export const migrateLegacyAccessToken = (): string | undefined => {
  const storedToken = getAccessToken();
  if (storedToken || typeof window === "undefined") {
    removeLegacyAccessToken();
    return storedToken;
  }

  try {
    const legacyToken = window.localStorage.getItem(LEGACY_ACCESS_TOKEN_KEY)?.trim();
    removeLegacyAccessToken();
    if (legacyToken) setAccessToken(legacyToken);
  } catch {
    removeLegacyAccessToken();
  }

  return getAccessToken();
};

export const clearAccessToken = (): void => {
  accessToken = undefined;
  removeSessionAccessToken();
  removeLegacyAccessToken();
};
