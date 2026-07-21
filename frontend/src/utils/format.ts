export const localeForLanguage = (language: 'en' | 'vi') =>
  language === 'vi' ? 'vi-VN' : 'en-US';

export const formatPercent = (value: number, locale = 'en-US') =>
  `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(value)}%`;

export const formatNumber = (value: number, locale = 'en-US') =>
  new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(value);

export const formatCurrency = (value: number, currency = 'USD', locale = 'en-US') =>
  new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(value);

export const formatVndCurrency = (value: number, freeLabel = '-', locale = 'vi-VN') => {
  if (value <= 0) {
    return freeLabel;
  }

  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
};

export const formatFileSize = (value: number, locale = 'en-US') => {
  const units = ['B', 'KB', 'MB', 'GB'] as const;
  const safeValue = Math.max(0, value);
  const unitIndex = safeValue === 0
    ? 0
    : Math.min(Math.floor(Math.log(safeValue) / Math.log(1024)), units.length - 1);
  const scaledValue = safeValue / 1024 ** unitIndex;

  return `${new Intl.NumberFormat(locale, {
    maximumFractionDigits: unitIndex === 0 ? 0 : 2,
  }).format(scaledValue)} ${units[unitIndex]}`;
};

export const formatDisplayDate = (
  value: string | null | undefined,
  fallback: string,
  locale = 'vi-VN',
) => {
  if (!value) {
    return fallback;
  }

  const normalizedValue = value.replace(/(\.\d{3})\d+/, '$1');
  const date = new Date(normalizedValue);

  if (Number.isNaN(date.getTime())) {
    return fallback;
  }

  return new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
};

export const formatDisplayDateTime = (
  value: string | null | undefined,
  fallback: string,
  locale = "vi-VN",
) => {
  if (!value) {
    return fallback;
  }

  const normalizedValue = value.replace(/(\.\d{3})\d+/, "$1");
  const date = new Date(normalizedValue);

  if (Number.isNaN(date.getTime())) {
    return fallback;
  }

  return new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
};
