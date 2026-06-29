import { RefreshCw, TicketCheck } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { getAdminLegalTickets } from "../../services/legalTicket.service";
import type { LegalTicket } from "../../types/legalTicket";
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

export function AdminTicketsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const [tickets, setTickets] = useState<LegalTicket[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [statusFilter, setStatusFilter] = useState<LegalTicketFilter>("ALL");
  const [riskLevel, setRiskLevel] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const filterOptions = useMemo(() => getLegalTicketFilterOptions(t), [t]);
  const locale = language === "vi" ? "vi-VN" : "en-US";

  const loadTickets = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const response = await getAdminLegalTickets(
        0,
        50,
        statusFilter === "ALL" ? undefined : statusFilter,
        riskLevel || undefined,
      );
      setTickets(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
    } catch (loadError) {
      console.error("Failed to load admin legal tickets", loadError);
      setError(t("legalTickets.errors.load"));
      setTickets([]);
      setTotalItems(0);
      toast.error(t("legalTickets.errors.load"), t("toast.errorTitle"));
    } finally {
      setLoading(false);
    }
  }, [riskLevel, statusFilter, t, toast]);

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
            <select
              className="form-field max-w-48"
              value={statusFilter}
              onChange={(event) => setStatusFilter(toLegalTicketFilter(event.target.value))}
            >
              {filterOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <select
              className="form-field max-w-44"
              value={riskLevel}
              onChange={(event) => setRiskLevel(event.target.value)}
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
          <p className="text-3xl font-bold">{totalItems}</p>
        </Card>
        <Card title={t("legalTickets.openTickets")}>
          <p className="text-3xl font-bold">{openCount}</p>
        </Card>
        <Card title={t("legalTickets.currentFilter")}>
          <p className="break-words text-2xl font-bold">
            {getLegalTicketFilterLabel(statusFilter, t)}
          </p>
        </Card>
      </section>

      <Card title={t("legalTickets.title")} actions={<Badge tone="blue">{tickets.length}</Badge>}>
        {loading ? (
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
      </Card>
    </div>
  );
}
