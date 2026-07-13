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
import { formatDisplayDate } from "../../utils/format";

const getRiskTone = (risk?: string | null) => {
  if (risk === "HIGH") return "red";
  if (risk === "MEDIUM") return "amber";
  if (risk === "LOW") return "green";
  return "slate";
};

export function LawyerTicketsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(() => parsePageParam(searchParams.get('page')));
  const [totalPages, setTotalPages] = useState(0);

  const [tickets, setTickets] = useState<LawyerTicket[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  const loadTickets = useCallback(async () => {
    setIsLoading(true);

    try {
      const response = await getMyLawyerTickets(page, 10);
      setTickets(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : t("lawyerTickets.loadError");

      toast.error(message);
      setTickets([]);
      setTotalItems(0);
    } finally {
      setIsLoading(false);
    }
  }, [page, t, toast]);

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
          <Briefcase className="mb-3 h-8 w-8 text-primary" />
          <p className="text-xs font-semibold uppercase tracking-wide">
            {t("lawyerTickets.totalTickets")}
          </p>
          <p className="mt-2 text-3xl font-bold">{totalItems}</p>
        </Card>

        <Card className="p-lg">
          <Clock className="mb-3 h-8 w-8 text-primary" />
          <p className="text-xs font-semibold uppercase tracking-wide">
            {t("lawyerTickets.openTicketsCurrentPage")}
          </p>
          <p className="mt-2 text-3xl font-bold">{openCount}</p>
        </Card>

        <Card className="p-lg">
          <AlertTriangle className="mb-3 h-8 w-8 text-primary" />
          <p className="text-xs font-semibold uppercase tracking-wide">
            {t("lawyerTickets.highRiskTicketsCurrentPage")}
          </p>
          <p className="mt-2 text-3xl font-bold">{highRiskCount}</p>
        </Card>
      </div>

      <Card className="p-lg">
        <div className="mb-5 flex items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold">
              {t("lawyerTickets.ticketQueue")}
            </h2>
            <p className="mt-1 text-sm text-on-surface-variant">
              {t("lawyerTickets.ticketQueueDescription")}
            </p>
          </div>

          <Badge tone="blue">
            {isLoading
              ? t("common.loading")
              : `${tickets.length}/${totalItems}`}
          </Badge>
        </div>

        {isLoading ? (
          <p className="text-sm text-on-surface-variant">
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
                className="rounded-2xl border bg-surface p-lg transition hover:border-primary hover:shadow-sm"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-xs font-semibold uppercase tracking-wide text-primary">
                      {ticket.workspace_name || t("lawyerTickets.workspace")} ·{" "}
                      {ticket.document_name || t("lawyerTickets.document")}
                    </p>

                    <h3 className="mt-2 text-lg font-semibold">
                      {ticket.issue_title ||
                        ticket.question ||
                        `${t("legalTickets.table.ticket")} #${ticket.id}`}
                    </h3>

                    <p className="mt-2 line-clamp-2 text-sm text-on-surface-variant">
                      {ticket.issue_summary ||
                        ticket.customer_note ||
                        ticket.suggestion_reason ||
                        t("common.noData")}
                    </p>

                    <p className="mt-3 text-xs text-on-surface-variant">
                      {t("lawyerTickets.createdBy")}{" "}
                      {ticket.created_by_name || "-"} ·{" "}
                      {formatDisplayDate(ticket.created_at, "-", locale)}
                    </p>
                  </div>

                  <div className="flex shrink-0 flex-col items-end gap-2">
                    <Badge>{ticket.status || "UNKNOWN"}</Badge>
                    <Badge tone={getRiskTone(ticket.risk_level)}>
                      {ticket.risk_level || "NONE"}
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
