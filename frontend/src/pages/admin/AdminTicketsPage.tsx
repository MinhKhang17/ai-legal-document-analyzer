import { RefreshCw, TicketCheck } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/common/Pagination";
import { parsePageParam, toPageParam } from "../../utils/pagination";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { getAdminLegalTickets } from "../../services/legalTicket.service";
import type { LegalTicket, LegalTicketType } from "../../types/legalTicket";
import type { LegalTicketFilter } from "../../types/legalTicketStatus";
import {
  getLegalTicketFilterLabel,
  getLegalTicketFilterOptions,
  getLegalTicketStatusLabel,
  isTerminalLegalTicketStatus,
  normalizeLegalTicketStatus,
  toLegalTicketFilter,
} from "../../types/legalTicketStatus";
import { formatDisplayDate } from "../../utils/format";

const getRiskTone = (risk?: string | null) => {
  if (risk === "HIGH") return "red";
  if (risk === "MEDIUM") return "amber";
  if (risk === "LOW") return "green";
  return "slate";
};

const getStatusTone = (status?: string | null) => {
  switch (normalizeLegalTicketStatus(status)) {
    case "RESOLVED":
      return "green";
    case "CLOSED":
    case "CANCELLED":
    case "REJECTED_BY_ADMIN":
      return "slate";
    case "IN_REVIEW":
    case "ASSIGNED_TO_LAWYER":
    case "CUSTOMER_RESPONDED":
      return "blue";
    case "NEED_MORE_INFO":
      return "amber";
    default:
      return "slate";
  }
};

const ticketTypeKeys: Record<string, string> = {
  SYSTEM_ERROR: "legalTickets.type.SYSTEM_ERROR",
  QUERY_ERROR: "legalTickets.type.QUERY_ERROR",
  CONTACT_EXPERT: "legalTickets.type.CONTACT_EXPERT",
  REFUND_REQUEST: "legalTickets.type.REFUND_REQUEST",
};

export function AdminTicketsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [tickets, setTickets] = useState<LegalTicket[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(() => parsePageParam(searchParams.get("page")));
  const [statusFilter, setStatusFilter] = useState<LegalTicketFilter>(() => toLegalTicketFilter(searchParams.get("status") ?? "ALL"));
  const [riskLevel, setRiskLevel] = useState(searchParams.get("risk") ?? "");
  const [ticketType, setTicketType] = useState<LegalTicketType | "">((searchParams.get("type") as LegalTicketType) ?? "");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const filterOptions = useMemo(() => getLegalTicketFilterOptions(t), [t]);
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const numberFormatter = new Intl.NumberFormat(locale);
  const getTicketTypeLabel = (ticketTypeValue?: string | null) => {
    const value = ticketTypeValue || "CONTACT_EXPERT";
    const key = ticketTypeKeys[value];
    return key ? t(key) : value;
  };

  const loadTickets = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const response = await getAdminLegalTickets(
        page,
        20,
        statusFilter === "ALL" ? undefined : statusFilter,
        riskLevel || undefined,
        ticketType || undefined,
      );
      setTickets(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch (loadError) {
      console.error("Failed to load admin legal tickets", loadError);
      setError(t("legalTickets.errors.load"));
      toast.error(t("legalTickets.errors.load"), t("toast.errorTitle"));
    } finally {
      setLoading(false);
    }
  }, [page, riskLevel, statusFilter, ticketType, t, toast]);

  useEffect(() => {
    void loadTickets();
  }, [loadTickets]);

  const openCount = useMemo(
    () =>
      tickets.filter(
        (ticket) => !isTerminalLegalTicketStatus(ticket.status),
      ).length,
    [tickets],
  );

  const columns: DataTableColumn<LegalTicket>[] = [
    {
      header: t("legalTickets.ticketType"),
      cell: (ticket) => <Badge tone="blue">{getTicketTypeLabel(ticket.ticket_type)}</Badge>,
    },
    {
      header: t("legalTickets.table.ticket"),
      cell: (ticket) => (
        <Link
          className="break-all font-semibold text-primary hover:underline dark:text-inverse-primary"
          to={`/admin/tickets/${ticket.id}`}
        >
          {ticket.issue_title || ticket.question || ticket.id}
        </Link>
      ),
    },
    {
      header: t("table.status"),
      cell: (ticket) => (
        <Badge tone={getStatusTone(ticket.status)}>
          {getLegalTicketStatusLabel(ticket.status, t)}
        </Badge>
      ),
    },
    {
      header: t("table.risk"),
      cell: (ticket) => (
        <Badge tone={getRiskTone(ticket.risk_level)}>
          {ticket.risk_level ? t(`risk.${ticket.risk_level.toLowerCase()}`) : t("risk.none")}
        </Badge>
      ),
    },
    {
      header: t("legalTickets.table.customer"),
      cell: (ticket) => ticket.created_by_name || ticket.created_by_id || "-",
    },
    {
      header: t("legalTickets.table.expert"),
      cell: (ticket) => ticket.assigned_lawyer_name || ticket.assigned_lawyer_id || "-",
    },
    {
      header: t("legalTickets.table.created"),
      cell: (ticket) => formatDisplayDate(ticket.created_at, "-", locale),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t("legalTickets.adminTitle")}
        subtitle={t("legalTickets.adminSubtitle")}
        actions={
          <>
            <select aria-label={t("legalTickets.filters.ticketType")} className="form-field max-w-52" value={ticketType} onChange={(event) => { const value = event.target.value as LegalTicketType | ""; setTicketType(value); setPage(0); const next = new URLSearchParams(searchParams); next.set("page", "1"); value ? next.set("type", value) : next.delete("type"); setSearchParams(next); }}>
              <option value="">{t("legalTickets.type.all")}</option><option value="SYSTEM_ERROR">{t("legalTickets.type.SYSTEM_ERROR")}</option><option value="QUERY_ERROR">{t("legalTickets.type.QUERY_ERROR")}</option><option value="CONTACT_EXPERT">{t("legalTickets.type.CONTACT_EXPERT")}</option><option value="REFUND_REQUEST">{t("legalTickets.type.REFUND_REQUEST")}</option>
            </select>
            <select
              aria-label={t("legalTickets.filters.status")}
              className="form-field max-w-48"
              value={statusFilter}
              onChange={(event) => { const value = toLegalTicketFilter(event.target.value); setStatusFilter(value); setPage(0); const next = new URLSearchParams(searchParams); next.set("page", "1"); value === "ALL" ? next.delete("status") : next.set("status", value); setSearchParams(next); }}
            >
              {filterOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <select
              aria-label={t("legalTickets.filters.risk")}
              className="form-field max-w-44"
              value={riskLevel}
              onChange={(event) => { const value = event.target.value; setRiskLevel(value); setPage(0); const next = new URLSearchParams(searchParams); next.set("page", "1"); value ? next.set("risk", value) : next.delete("risk"); setSearchParams(next); }}
            >
              <option value="">{t("legalTickets.filters.allRisks")}</option>
              <option value="HIGH">{t("risk.high")}</option>
              <option value="MEDIUM">{t("risk.medium")}</option>
              <option value="LOW">{t("risk.low")}</option>
            </select>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadTickets()}
              disabled={loading}
            >
              {t("common.refresh")}
            </Button>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <section className="mb-xl grid gap-gutter md:grid-cols-3">
        <Card title={t("legalTickets.totalTickets")}>
          <p className="text-3xl font-bold">{numberFormatter.format(totalItems)}</p>
        </Card>
        <Card title={t("legalTickets.openTicketsCurrentPage")}>
          <p className="text-3xl font-bold">{numberFormatter.format(openCount)}</p>
        </Card>
        <Card title={t("legalTickets.currentFilter")}>
          <p className="break-words text-2xl font-bold">
            {getLegalTicketFilterLabel(statusFilter, t)}
          </p>
        </Card>
      </section>

      <Card title={t("legalTickets.title")} actions={<Badge tone="blue">{numberFormatter.format(tickets.length)}</Badge>}>
        {error ? (
          <div role="alert" className="text-sm text-error">{error} <Button variant="secondary" onClick={() => void loadTickets()}>{t("common.retry")}</Button></div>
        ) : loading && tickets.length === 0 ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("legalTickets.loading")}
          </p>
        ) : tickets.length === 0 ? (
          <EmptyState
            icon={<TicketCheck className="h-6 w-6" />}
            title={t("legalTickets.emptyTitle")}
            description={t("legalTickets.adminEmptyDescription")}
          />
        ) : (
          <DataTable columns={columns} data={tickets} getRowKey={(ticket) => ticket.id} />
        )}
        <Pagination page={page} totalPages={totalPages} totalItems={totalItems} disabled={loading} onPageChange={(nextPage) => { setPage(nextPage); const next = new URLSearchParams(searchParams); next.set("page", toPageParam(nextPage)); setSearchParams(next); }} />
      </Card>
    </div>
  );
}
