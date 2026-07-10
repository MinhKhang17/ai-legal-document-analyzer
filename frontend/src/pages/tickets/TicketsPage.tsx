import { RefreshCw, TicketCheck } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { createLegalTicket, getMyLegalTickets } from "../../api/legalTicketApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { LegalTicketComposerCard } from "../../components/tickets/LegalTicketComposerCard";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { LegalTicket } from "../../types/legalTicket";
import { formatDisplayDate } from "../../utils/format";

const getRiskTone = (risk?: string | null) => {
  if (risk === "HIGH") return "red";
  if (risk === "MEDIUM") return "amber";
  if (risk === "LOW") return "green";
  return "slate";
};

export function TicketsPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tickets, setTickets] = useState<LegalTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadTickets = useCallback(async () => {
    try {
      setLoading(true);
      const response = await getMyLegalTickets(0, 20);
      setTickets(response.items ?? []);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not load tickets.";
      toast.error(message);
      setTickets([]);
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    void loadTickets();
  }, [loadTickets]);

  const activeCount = useMemo(
    () => tickets.filter((ticket) => ticket.status !== "CLOSED" && ticket.status !== "CANCELLED").length,
    [tickets],
  );

  return (
    <div className="space-y-xl">
      <PageHeader
        eyebrow={t("nav.tickets")}
        title={t("tickets.title")}
        subtitle={t("tickets.subtitle")}
        actions={
          <Button variant="secondary" onClick={() => void loadTickets()} disabled={loading}>
            <RefreshCw className="mr-2 h-4 w-4" />
            {loading ? t("common.loading") : t("common.refresh")}
          </Button>
        }
      />

      <section className="grid gap-gutter xl:grid-cols-[1.05fr_0.95fr]">
        <LegalTicketComposerCard
          title={t("tickets.createTitle")}
          subtitle={t("tickets.createSubtitle")}
          submitLabel={t("tickets.createAction")}
          busy={saving}
          onSubmit={async (payload) => {
            setSaving(true);
            try {
              await createLegalTicket(payload);
              toast.success(t("tickets.createSuccess"), t("toast.successTitle"));
              await loadTickets();
            } catch (error) {
              const message = error instanceof Error ? error.message : t("tickets.createError");
              toast.error(message, t("toast.errorTitle"));
            } finally {
              setSaving(false);
            }
          }}
        />

        <div className="space-y-gutter">
          <section className="grid gap-gutter sm:grid-cols-2">
            <div className="paper-card p-lg">
              <p className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
                {t("tickets.total")}
              </p>
              <p className="mt-2 text-3xl font-bold">{tickets.length}</p>
            </div>
            <div className="paper-card p-lg">
              <p className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
                {t("tickets.open")}
              </p>
              <p className="mt-2 text-3xl font-bold">{activeCount}</p>
            </div>
          </section>

          <section className="space-y-md">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-title-lg font-semibold">{t("tickets.historyTitle")}</h2>
                <p className="text-sm text-on-surface-variant">{t("tickets.historySubtitle")}</p>
              </div>
              <Badge tone="blue">{tickets.length}</Badge>
            </div>

            {loading ? (
              <p className="text-sm text-on-surface-variant">{t("tickets.loading")}</p>
            ) : tickets.length === 0 ? (
              <EmptyState
                icon={<TicketCheck className="h-10 w-10" />}
                title={t("tickets.emptyTitle")}
                description={t("tickets.emptyDescription")}
              />
            ) : (
              <div className="grid gap-md">
                {tickets.map((ticket) => (
                  <Link
                    key={ticket.id}
                    to={`/tickets/${ticket.id}`}
                    className="rounded-2xl border border-legal-border bg-white p-lg transition hover:border-primary hover:shadow-sm dark:border-slate-700 dark:bg-slate-900"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-xs font-semibold uppercase tracking-wide text-primary dark:text-inverse-primary">
                          {ticket.workspace_id || t("tickets.workspace")}
                          {ticket.document_id ? ` · ${ticket.document_id}` : ""}
                        </p>
                        <h3 className="mt-2 text-lg font-semibold">
                          {ticket.issue_title || ticket.question || ticket.id}
                        </h3>
                        <p className="mt-2 line-clamp-2 text-sm text-on-surface-variant">
                          {ticket.issue_summary || ticket.customer_note || ticket.suggestion_reason || ticket.answer || t("common.noData")}
                        </p>
                        <p className="mt-3 text-xs text-on-surface-variant">
                          {formatDisplayDate(ticket.created_at, "-", "vi-VN")}
                        </p>
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-2">
                        <Badge>{ticket.status || "UNKNOWN"}</Badge>
                        <Badge tone={getRiskTone(ticket.risk_level)}>{ticket.risk_level || "NONE"}</Badge>
                      </div>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </div>
      </section>
    </div>
  );
}
