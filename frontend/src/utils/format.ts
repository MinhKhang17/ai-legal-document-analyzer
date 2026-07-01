export const formatPercent = (value: number) => `${value.toFixed(value % 1 === 0 ? 0 : 1)}%`;

export const formatNumber = (value: number) =>
  new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value);

export const formatCurrency = (value: number, currency = 'USD') =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(value);

export const formatVndCurrency = (value: number, freeLabel = 'Miễn phí') => {
  if (value <= 0) {
    return freeLabel;
  }

  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
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
