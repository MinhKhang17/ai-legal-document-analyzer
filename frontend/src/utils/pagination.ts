export const parsePageParam = (value: string | null): number => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed - 1 : 0;
};

export const toPageParam = (page: number): string => String(Math.max(0, page) + 1);

export const clampPage = (page: number, totalPages: number): number =>
  totalPages > 0 ? Math.min(Math.max(0, page), totalPages - 1) : 0;
