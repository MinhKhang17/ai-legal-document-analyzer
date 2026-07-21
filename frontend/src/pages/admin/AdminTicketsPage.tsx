import { RefreshCw, TicketCheck } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Pagination } from "../../components/common/Pagination";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { getAdminLegalTickets } from "../../services/legalTicket.service";
import {
  LEGAL_TICKET_RISK_LEVELS,
  LEGAL_TICKET_TYPES,
  isLegalTicketRiskLevel,
  isLegalTicketType,
  type LegalTicket,
  type LegalTicketRiskLevel,
  type LegalTicketType,
} from "../../types/legalTicket";
import {
  getLegalTicketFilterOptions,
  getLegalTicketStatusLabel,
  isLegalTicketStatus,
  isTerminalLegalTicketStatus,
  normalizeLegalTicketStatus,
  type LegalTicketFilter,
} from "../../types/legalTicketStatus";
import {
  getAdminTicketLoadErrorMessage,
  getSafeApiErrorStatus,
} from "../../utils/adminTicketError";
import { formatDisplayDate } from "../../utils/format";
import {
  clampPage,
  isValidPageParam,
  parsePageParam,
  toPageParam,
} from "../../utils/pagination";

const PAGE_SIZE = 20;

const riskLabelKeys: Record<LegalTicketRiskLevel, string> = {
  NONE: "risk.none",
  LOW: "risk.low",
  MEDIUM: "risk.medium",
  HIGH: "risk.high",
  CRITICAL: "risk.critical",
  UNKNOWN: "risk.unknown",
};

const ticketTypeKeys: Record<LegalTicketType, string> = {
  SYSTEM_ERROR: "legalTickets.type.SYSTEM_ERROR",
  QUERY_ERROR: "legalTickets.type.QUERY_ERROR",
  CONTACT_EXPERT: "legalTickets.type.CONTACT_EXPERT",
  REFUND_REQUEST: "legalTickets.type.REFUND_REQUEST",
};

const getRiskTone = (risk?: LegalTicketRiskLevel) => {
  if (risk === "CRITICAL" || risk === "HIGH") return "red";
  if (risk === "MEDIUM" || risk === "UNKNOWN") return "amber";
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

export function AdminTicketsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [tickets, setTickets] = useState<LegalTicket[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const requestSequenceRef = useRef(0);

  const rawPage = searchParams.get("page");
  const rawStatus = searchParams.get("status");
  const rawRisk = searchParams.get("risk");
  const rawType = searchParams.get("type");

  const page = isValidPageParam(rawPage) ? parsePageParam(rawPage) : 0;
  const statusFilter: LegalTicketFilter =
    rawStatus !== null && isLegalTicketStatus(rawStatus) ? rawStatus : "ALL";
  const riskLevel: LegalTicketRiskLevel | "" =
    rawRisk !== null && isLegalTicketRiskLevel(rawRisk) ? rawRisk : "";
  const ticketType: LegalTicketType | "" =
    rawType !== null && isLegalTicketType(rawType) ? rawType : "";

  const filterOptions = useMemo(() => getLegalTicketFilterOptions(t), [t]);
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const numberFormatter = new Intl.NumberFormat(locale);

  const getTicketTypeLabel = (value?: LegalTicketType) =>
    value ? t(ticketTypeKeys[value]) : t("legalTickets.type.CONTACT_EXPERT");

  useEffect(() => {
    const next = new URLSearchParams(searchParams);
    let changed = false;

    if (rawPage !== null && !isValidPageParam(rawPage)) {
      next.delete("page");
      changed = true;
    }

    if (rawStatus !== null && !isLegalTicketStatus(rawStatus)) {
      next.delete("status");
      changed = true;
    }

    if (rawRisk !== null && !isLegalTicketRiskLevel(rawRisk)) {
      next.delete("risk");
      changed = true;
    }

    if (rawType !== null && !isLegalTicketType(rawType)) {
      next.delete("type");
      changed = true;
    }

    if (changed) {
      setSearchParams(next, { replace: true });
    }
  }, [
    rawPage,
    rawRisk,
    rawStatus,
    rawType,
    searchParams,
    setSearchParams,
  ]);

  const updateFilterQuery = useCallback((key: "status" | "risk" | "type", value?: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (value) next.set(key, value);
      else next.delete(key);
      next.set("page", "1");
      return next;
    });
  }, [setSearchParams]);

  const updatePageQuery = useCallback((nextPage: number, replace = false) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("page", toPageParam(nextPage));
      return next;
    }, { replace });
  }, [setSearchParams]);

  const resetFilters = useCallback(() => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete("status");
      next.delete("risk");
      next.delete("type");
      next.set("page", "1");
      return next;
    });
  }, [setSearchParams]);

  const loadTickets = useCallback(async () => {
    const requestSequence = ++requestSequenceRef.current;
    setLoading(true);
    setError("");

    try {
      const response = await getAdminLegalTickets(
        page,
        PAGE_SIZE,
        statusFilter === "ALL" ? undefined : statusFilter,
        riskLevel || undefined,
        ticketType || undefined,
      );

      if (requestSequence !== requestSequenceRef.current) return;

      const safePage = clampPage(response.page, response.totalPages);
      if (safePage !== page) {
        setTotalItems(response.totalItems ?? 0);
        setTotalPages(response.totalPages ?? 0);
        updatePageQuery(safePage, true);
        return;
      }

      setTickets(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch (loadError) {
      if (requestSequence !== requestSequenceRef.current) return;

      const message = getAdminTicketLoadErrorMessage(loadError, t);
      console.error("Failed to load admin legal tickets", {
        status: getSafeApiErrorStatus(loadError),
      });
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  }, [page, riskLevel, statusFilter, t, ticketType, toast, updatePageQuery]);

  useEffect(() => {
    void loadTickets();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadTickets]);

  const activeFilters = useMemo(() => {
    const filters: Array<{ key: string; label: string }> = [];

    if (statusFilter !== "ALL") {
      filters.push({
        key: "status",
        label: `${t("table.status")}: ${getLegalTicketStatusLabel(statusFilter, t)}`,
      });
    }

    if (riskLevel) {
      filters.push({
        key: "risk",
        label: `${t("table.risk")}: ${t(riskLabelKeys[riskLevel])}`,
      });
    }

    if (ticketType) {
      filters.push({
        key: "type",
        label: `${t("legalTickets.ticketType")}: ${t(ticketTypeKeys[ticketType])}`,
      });
    }

    return filters;
  }, [riskLevel, statusFilter, t, ticketType]);

  const openCount = useMemo(
    () => tickets.filter((ticket) => !isTerminalLegalTicketStatus(ticket.status)).length,
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
          {ticket.risk_level ? t(riskLabelKeys[ticket.risk_level]) : t("risk.none")}
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
            <select
              aria-label={t("legalTickets.filters.ticketType")}
              className="form-field max-w-52"
              value={ticketType}
              onChange={(event) => {
                const value = event.currentTarget.value;
                updateFilterQuery("type", isLegalTicketType(value) ? value : undefined);
              }}
            >
              <option value="">{t("legalTickets.type.all")}</option>
              {LEGAL_TICKET_TYPES.map((value) => (
                <option key={value} value={value}>{t(ticketTypeKeys[value])}</option>
              ))}
            </select>
            <select
              aria-label={t("legalTickets.filters.status")}
              className="form-field max-w-48"
              value={statusFilter}
              onChange={(event) => {
                const value = event.currentTarget.value;
                updateFilterQuery("status", isLegalTicketStatus(value) ? value : undefined);
              }}
            >
              {filterOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            <select
              aria-label={t("legalTickets.filters.risk")}
              className="form-field max-w-44"
              value={riskLevel}
              onChange={(event) => {
                const value = event.currentTarget.value;
                updateFilterQuery("risk", isLegalTicketRiskLevel(value) ? value : undefined);
              }}
            >
              <option value="">{t("legalTickets.filters.allRisks")}</option>
              {LEGAL_TICKET_RISK_LEVELS.map((value) => (
                <option key={value} value={value}>{t(riskLabelKeys[value])}</option>
              ))}
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

      {activeFilters.length > 0 && (
        <section
          aria-label={t("legalTickets.filters.active")}
          className="mb-lg flex flex-wrap items-center gap-sm"
        >
          <span className="text-sm font-semibold text-on-surface-variant dark:text-slate-300">
            {t("legalTickets.filters.active")}:
          </span>
          {activeFilters.map((filter) => (
            <Badge key={filter.key} tone="blue">{filter.label}</Badge>
          ))}
          <Button variant="ghost" size="sm" onClick={resetFilters}>
            {t("legalTickets.filters.reset")}
          </Button>
        </section>
      )}

      <section className="mb-xl grid gap-gutter md:grid-cols-3">
        <Card title={t("legalTickets.totalTickets")}>
          <p className="text-3xl font-bold">{numberFormatter.format(totalItems)}</p>
        </Card>
        <Card title={t("legalTickets.openTicketsCurrentPage")}>
          <p className="text-3xl font-bold">{numberFormatter.format(openCount)}</p>
        </Card>
        <Card title={t("legalTickets.currentFilter")}>
          <p className="text-3xl font-bold">
            {activeFilters.length > 0
              ? numberFormatter.format(activeFilters.length)
              : t("legalTickets.filters.ALL")}
          </p>
        </Card>
      </section>

      <Card
        aria-busy={loading}
        title={t("legalTickets.title")}
        actions={<Badge tone="blue">{numberFormatter.format(tickets.length)}</Badge>}
      >
        {error ? (
          <div role="alert" className="text-sm text-error">
            {error}{" "}
            <Button variant="secondary" onClick={() => void loadTickets()}>
              {t("common.retry")}
            </Button>
          </div>
        ) : loading && tickets.length === 0 ? (
          <p
            className="text-sm text-on-surface-variant dark:text-slate-400"
            aria-live="polite"
            role="status"
          >
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
        <Pagination
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          disabled={loading}
          onPageChange={(nextPage) => updatePageQuery(nextPage)}
        />
      </Card>
    </div>
  );
}
