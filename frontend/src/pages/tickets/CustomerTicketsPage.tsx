import { RefreshCw, TicketCheck } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { getMyLegalTickets } from "../../services/legalTicket.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
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

export function CustomerTicketsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const [tickets, setTickets] = useState<LegalTicket[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<LegalTicketFilter>("ALL");
  const filterOptions = useMemo(() => getLegalTicketFilterOptions(t), [t]);

  const loadTickets = useCallback(async () => {
    setLoading(true);

    try {
      const response = await getMyLegalTickets(
        0,
        20,
        statusFilter === "ALL" ? undefined : statusFilter,
      );
      setTickets(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
    } catch (error) {
      console.error("Failed to load customer legal tickets", error);
      toast.error(t("legalTickets.errors.load"), t("toast.errorTitle"));
      setTickets([]);
      setTotalItems(0);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, t, toast]);

  useEffect(() => {
    void loadTickets();
  }, [loadTickets]);

  const openCount = useMemo(
    () => tickets.filter((ticket) => !isTerminalLegalTicketStatus(ticket.status)).length,
    [tickets],
  );

  const columns: DataTableColumn<LegalTicket>[] = [
    {
      header: t("legalTickets.table.ticket"),
      cell: (ticket) => (
        <Link className="font-semibold text-primary hover:underline dark:text-inverse-primary" to={`/tickets/${ticket.id}`}>
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
    { header: t("legalTickets.table.expert"), cell: (ticket) => ticket.assigned_lawyer_name || "-" },
    {
      header: t("legalTickets.table.created"),
      cell: (ticket) => formatDisplayDate(ticket.created_at, "-", language === "vi" ? "vi-VN" : "en-US"),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t("legalTickets.title")}
        subtitle={t("legalTickets.subtitle")}
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

      <section className="mb-xl grid gap-gutter md:grid-cols-3">
        <Card title={t("legalTickets.totalTickets")}><p className="text-3xl font-bold">{totalItems}</p></Card>
        <Card title={t("legalTickets.openTickets")}><p className="text-3xl font-bold">{openCount}</p></Card>
        <Card title={t("legalTickets.currentFilter")}>
          <p className="break-words text-2xl font-bold">
            {getLegalTicketFilterLabel(statusFilter, t)}
          </p>
        </Card>
      </section>

      <Card title={t("legalTickets.myLegalTickets")} actions={<Badge tone="blue">{tickets.length}</Badge>}>
        {loading ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("legalTickets.loading")}</p>
        ) : tickets.length === 0 ? (
          <EmptyState
            icon={<TicketCheck className="h-6 w-6" />}
            title={t("legalTickets.emptyTitle")}
            description={t("legalTickets.emptyDescription")}
          />
        ) : (
          <DataTable columns={columns} data={tickets} getRowKey={(ticket) => ticket.id} />
        )}
      </Card>
    </div>
  );
}
