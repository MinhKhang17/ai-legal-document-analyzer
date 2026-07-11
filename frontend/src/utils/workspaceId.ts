export const normalizeWorkspaceId = (value: string): string | null => {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
};
