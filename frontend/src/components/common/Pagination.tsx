import { Button } from "./Button";
import { useI18n } from "../../hooks/useI18n";

interface PaginationProps {
  page: number;
  totalPages: number;
  totalItems?: number;
  disabled?: boolean;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalItems, disabled = false, onPageChange }: PaginationProps) {
  const { t } = useI18n();
  const safeTotalPages = Math.max(0, totalPages);
  if (safeTotalPages <= 1) return null;

  return (
    <nav className="mt-md flex flex-wrap items-center justify-between gap-sm" aria-label={t('pagination.label')}>
      <Button variant="secondary" disabled={disabled || page <= 0} onClick={() => onPageChange(page - 1)}>
        {t("pagination.previous")}
      </Button>
      <p className="text-sm text-on-surface-variant" aria-live="polite">
        {t("pagination.page")} {page + 1} / {safeTotalPages}{typeof totalItems === "number" ? ` · ${totalItems} ${t("pagination.items")}` : ""}
      </p>
      <Button variant="secondary" disabled={disabled || page >= safeTotalPages - 1} onClick={() => onPageChange(page + 1)}>
        {t("pagination.next")}
      </Button>
    </nav>
  );
}
