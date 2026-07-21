import {
  AlertTriangle,
  Briefcase,
  Clock,
  RefreshCw,
  TicketCheck,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { getMyLawyerTickets } from "../../api/lawyerTicketApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Pagination } from "../../components/common/Pagination";
import { parsePageParam, toPageParam } from "../../utils/pagination";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { LawyerTicket } from "../../types/lawyerTicket";
import {
  getLegalTicketFilterOptions,
  getLegalTicketStatusLabel,
  toLegalTicketFilter,
  type LegalTicketFilter,
} from "../../types/legalTicketStatus";
import { formatDisplayDate, localeForLanguage } from "../../utils/format";

const getRiskTone = (risk?: string | null) => {
  if (risk === "HIGH") return "red";
  if (risk === "MEDIUM") return "amber";
  if (risk === "LOW") return "green";
  return "slate";
};

export function LawyerTicketsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = localeForLanguage(language);
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(() => parsePageParam(searchParams.get('page')));
  const [statusFilter, setStatusFilter] = useState<LegalTicketFilter>(() =>
    toLegalTicketFilter(searchParams.get("status")),
  );
  const [totalPages, setTotalPages] = useState(0);

  const [tickets, setTickets] = useState<LawyerTicket[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  const loadTickets = useCallback(async () => {
    setIsLoading(true);

    try {
      const response = await getMyLawyerTickets(
        page,
        10,
        statusFilter === "ALL" ? undefined : statusFilter,
      );
      setTickets(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch {
      toast.error(t("lawyerTickets.loadError"), t("toast.errorTitle"));
      setTickets([]);
      setTotalItems(0);
    } finally {
      setIsLoading(false);
    }
  }, [page, statusFilter, t, toast]);

  useEffect(() => {
    void loadTickets();
  }, [loadTickets]);

  const openCount = useMemo(
    () => tickets.filter((ticket) => ticket.status !== "CLOSED").length,
    [tickets],
  );

  const highRiskCount = useMemo(
    () => tickets.filter((ticket) => ticket.risk_level === "HIGH").length,
    [tickets],
  );

  return (
    <div className="space-y-xl">
      <PageHeader
        eyebrow={t("nav.lawyerTickets")}
        title={t("lawyerTickets.title")}
        subtitle={t("lawyerTickets.subtitle")}
        actions={
          <Button onClick={() => void loadTickets()} disabled={isLoading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            {isLoading ? t("common.loading") : t("common.refresh")}
          </Button>
        }
      />

      <div className="grid gap-lg md:grid-cols-3">
        <Card className="p-lg">
          <Briefcase className="mb-3 h-8 w-8 text-primary" aria-hidden="true" />
          <p className="text-xs font-semibold uppercase tracking-wide">
            {t("lawyerTickets.totalTickets")}
          </p>
          <p className="mt-2 text-3xl font-bold">{totalItems}</p>
        </Card>

        <Card className="p-lg">
          <Clock className="mb-3 h-8 w-8 text-primary" aria-hidden="true" />
          <p className="text-xs font-semibold uppercase tracking-wide">
            {t("lawyerTickets.openTicketsCurrentPage")}
          </p>
          <p className="mt-2 text-3xl font-bold">{openCount}</p>
        </Card>

        <Card className="p-lg">
          <AlertTriangle className="mb-3 h-8 w-8 text-primary" aria-hidden="true" />
          <p className="text-xs font-semibold uppercase tracking-wide">
            {t("lawyerTickets.highRiskTicketsCurrentPage")}
          </p>
          <p className="mt-2 text-3xl font-bold">{highRiskCount}</p>
        </Card>
      </div>

      <Card className="p-lg">
        <div className="mb-5 flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <h2 className="text-xl font-semibold">
              {t("lawyerTickets.ticketQueue")}
            </h2>
            <p className="mt-1 text-sm text-on-surface-variant">
              {t("lawyerTickets.ticketQueueDescription")}
            </p>
          </div>
          <div className="flex flex-wrap items-end gap-3">
            <label className="min-w-56 text-sm font-semibold">
              <span className="mb-1 block text-xs uppercase tracking-wide text-on-surface-variant">
                {t("lawyerTickets.statusFilter")}
              </span>
              <select
                className="form-field"
                value={statusFilter}
                disabled={isLoading}
                onChange={(event) => {
                  const nextFilter = toLegalTicketFilter(event.target.value);
                  setStatusFilter(nextFilter);
                  setPage(0);
                  const next = new URLSearchParams(searchParams);
                  next.delete("page");
                  if (nextFilter === "ALL") next.delete("status");
                  else next.set("status", nextFilter);
                  setSearchParams(next);
                }}
              >
                {getLegalTicketFilterOptions(t).map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <Badge tone="blue">
              {isLoading
                ? t("common.loading")
                : `${tickets.length}/${totalItems}`}
            </Badge>
          </div>
        </div>

        {isLoading ? (
          <p aria-live="polite" className="text-sm text-on-surface-variant">
            {t("lawyerTickets.loading")}
          </p>
        ) : tickets.length === 0 ? (
          <EmptyState
            icon={<TicketCheck className="h-10 w-10" />}
            title={t("lawyerTickets.emptyTitle")}
            description={t("lawyerTickets.emptyDescription")}
          />
        ) : (
          <div className="grid gap-md">
            {tickets.map((ticket) => (
              <Link
                key={ticket.id}
                to={`/lawyer/tickets/${ticket.id}`}
                className="rounded-2xl border border-legal-border bg-surface p-lg transition hover:border-primary hover:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:hover:border-inverse-primary dark:hover:bg-slate-700"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-xs font-semibold uppercase tracking-wide text-primary dark:text-inverse-primary">
                      {ticket.workspace_name || t("lawyerTickets.workspace")} ·{" "}
                      {ticket.document_name || t("lawyerTickets.document")}
                    </p>

                    <h3 className="mt-2 line-clamp-2 text-lg font-semibold" title={ticket.issue_title || ticket.question || `${t("legalTickets.table.ticket")} #${ticket.id}`}>
                      {ticket.issue_title ||
                        ticket.question ||
                        `${t("legalTickets.table.ticket")} #${ticket.id}`}
                    </h3>

                    <p className="mt-2 line-clamp-2 text-sm text-on-surface-variant dark:text-slate-300">
                      {ticket.issue_summary ||
                        ticket.customer_note ||
                        ticket.suggestion_reason ||
                        t("common.noData")}
                    </p>

                    <p className="mt-3 text-xs text-on-surface-variant dark:text-slate-400">
                      {t("lawyerTickets.createdBy")}{" "}
                      {ticket.created_by_name || "-"} ·{" "}
                      {formatDisplayDate(ticket.created_at, "-", locale)}
                    </p>
                  </div>

                  <div className="flex shrink-0 flex-col items-end gap-2">
                    <Badge>{getLegalTicketStatusLabel(ticket.status, t)}</Badge>
                    <Badge tone={getRiskTone(ticket.risk_level)}>
                      {ticket.risk_level ? t(`risk.${ticket.risk_level.toLowerCase()}`) : t("risk.none")}
                    </Badge>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}
        <Pagination page={page} totalPages={totalPages} totalItems={totalItems} disabled={isLoading} onPageChange={(nextPage) => { setPage(nextPage); const next = new URLSearchParams(searchParams); next.set('page', toPageParam(nextPage)); setSearchParams(next); }} />
      </Card>
    </div>
  );
}
