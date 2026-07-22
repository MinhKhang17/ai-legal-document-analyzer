export const ticketText = (value: unknown, fallback = "Not provided"): string =>
  typeof value === "string" && value.trim() ? value.trim() : fallback;

export const ticketMoney = (
  value: number | null | undefined,
  fallback = "Pending pricing",
  locale = "vi-VN",
): string => Number.isFinite(value) ? new Intl.NumberFormat(locale, {
  style: "currency", currency: "VND", maximumFractionDigits: 0,
}).format(value as number) : fallback;

export const ticketDate = (
  value: string | null | undefined,
  fallback = "Not scheduled",
  locale = "vi-VN",
): string => {
  if (!value) return fallback;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? fallback : date.toLocaleString(locale);
};

export const ticketList = <T>(value: T[] | null | undefined): T[] => Array.isArray(value) ? value : [];
