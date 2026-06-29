export type AppRole = 'ADMIN' | 'CUSTOMER' | 'EXPERT' | string;

export const normalizeRole = (role?: string | null): AppRole | undefined => {
  if (typeof role !== 'string') {
    return undefined;
  }

  const normalized = role.trim().toUpperCase();

  if (normalized.length === 0) {
    return undefined;
  }

  return normalized.startsWith('ROLE_') ? normalized.slice(5) : normalized;
};

