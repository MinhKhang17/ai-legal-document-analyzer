export const isValidPageParam = (value: string | null): value is string => {
  if (value === null || !/^[1-9]\d*$/.test(value)) return false;
  return Number.isSafeInteger(Number(value));
};

export const parsePageParam = (value: string | null): number => {
  return isValidPageParam(value) ? Number(value) - 1 : 0;
};

export const toPageParam = (page: number): string => String(Math.max(0, page) + 1);

export const clampPage = (page: number, totalPages: number): number =>
  totalPages > 0 ? Math.min(Math.max(0, page), totalPages - 1) : 0;
