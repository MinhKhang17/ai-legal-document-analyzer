const LEGACY_ACCESS_TOKEN_KEY = "accessToken";

let accessToken: string | undefined;

export const setAccessToken = (value?: string): void => {
  const normalized = value?.trim();
  accessToken = normalized ? normalized : undefined;
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(LEGACY_ACCESS_TOKEN_KEY);
  }
};

export const getAccessToken = (): string | undefined => accessToken;

export const migrateLegacyAccessToken = (): string | undefined => {
  if (typeof window === "undefined") return accessToken;
  const legacyToken = window.localStorage.getItem(LEGACY_ACCESS_TOKEN_KEY)?.trim();
  window.localStorage.removeItem(LEGACY_ACCESS_TOKEN_KEY);
  if (legacyToken) accessToken = legacyToken;
  return accessToken;
};

export const clearAccessToken = (): void => {
  accessToken = undefined;
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(LEGACY_ACCESS_TOKEN_KEY);
  }
};
