import { ArrowLeft, RefreshCw, Reply, ShieldAlert, XCircle } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  cancelLegalTicket,
  closeLegalTicket,
  getLegalTicket,
  getLegalTicketMessages,
  reopenLegalTicket,
  replyToLegalTicket,
} from "../../api/legalTicketApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { LegalTicket, LegalTicketMessage } from "../../types/legalTicket";
import { formatDisplayDate } from "../../utils/format";

export function TicketDetailPage() {
  const { t } = useI18n();
  const toast = useToast();
  const { ticketId } = useParams();

  const [ticket, setTicket] = useState<LegalTicket | null>(null);
  const [messages, setMessages] = useState<LegalTicketMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [messageValue, setMessageValue] = useState("");
  const [busyAction, setBusyAction] = useState<"reply" | "cancel" | "close" | "reopen" | null>(null);

  const loadTicket = useCallback(async () => {
    if (!ticketId) return;

    try {
      setLoading(true);
      const [detail, history] = await Promise.all([
        getLegalTicket(ticketId),
        getLegalTicketMessages(ticketId),
      ]);
      setTicket(detail);
      setMessages(history);
    } catch (error) {
      const message = error instanceof Error ? error.message : t("tickets.detail.loadError");
      toast.error(message);
      setTicket(null);
      setMessages([]);
    } finally {
      setLoading(false);
    }
  }, [ticketId, toast, t]);

  useEffect(() => {
    void loadTicket();
  }, [loadTicket]);

  const isClosed = ticket?.status === "CLOSED";
  const isCancelled = ticket?.status === "CANCELLED";

  const messageCount = useMemo(() => messages.length, [messages]);

  return (
    <div className="space-y-xl">
      <PageHeader
        eyebrow={t("nav.tickets")}
        title={t("tickets.detail.title")}
        subtitle={t("tickets.detail.subtitle")}
        actions={
          <div className="flex flex-wrap gap-sm">
            <Link to="/tickets">
              <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
                {t("tickets.detail.back")}
              </Button>
            </Link>
            <Button variant="secondary" onClick={() => void loadTicket()} disabled={loading}>
              <RefreshCw className="h-4 w-4" />
              {t("tickets.detail.refresh")}
            </Button>
          </div>
        }
      />

      {loading ? (
        <Card>
          <p className="text-sm text-on-surface-variant">{t("tickets.detail.loading")}</p>
        </Card>
      ) : !ticket ? (
        <Card>
          <p className="text-sm text-on-surface-variant">{t("tickets.detail.empty")}</p>
        </Card>
      ) : (
        <div className="grid gap-gutter xl:grid-cols-[1.08fr_0.92fr]">
          <div className="space-y-gutter">
            <Card
              title={ticket.issue_title || ticket.question || ticket.id}
              subtitle={ticket.issue_summary || ticket.customer_note || ticket.answer || t("common.noData")}
              actions={
                <div className="flex flex-wrap gap-xs">
                  <Badge>{ticket.status || "UNKNOWN"}</Badge>
                  <Badge tone={ticket.risk_level === "HIGH" ? "red" : ticket.risk_level === "MEDIUM" ? "amber" : "green"}>
                    {ticket.risk_level || "NONE"}
                  </Badge>
                </div>
              }
            >
              <div className="grid gap-md sm:grid-cols-2">
                <Meta label={t("tickets.detail.workspace")} value={ticket.workspace_id || "-"} />
                <Meta label={t("tickets.detail.document")} value={ticket.document_id || "-"} />
                <Meta
                  label={t("tickets.detail.assignedLawyer")}
                  value={ticket.assigned_lawyer_name || ticket.assigned_lawyer_id || "-"}
                />
                <Meta label={t("tickets.detail.domain")} value={ticket.legal_domain || "-"} />
                <Meta label={t("tickets.detail.createdAt")} value={formatDisplayDate(ticket.created_at, "-", "vi-VN")} />
                <Meta label={t("tickets.detail.updatedAt")} value={formatDisplayDate(ticket.updated_at, "-", "vi-VN")} />
              </div>

              <div className="mt-md space-y-sm">
                <p className="label-uppercase">{t("tickets.detail.question")}</p>
                <p className="whitespace-pre-line text-sm leading-6 text-on-surface-variant">{ticket.question || t("common.noData")}</p>
              </div>

              <div className="mt-md space-y-sm">
                <p className="label-uppercase">{t("tickets.detail.answer")}</p>
                <p className="whitespace-pre-line text-sm leading-6 text-on-surface-variant">{ticket.answer || t("common.noData")}</p>
              </div>

              {ticket.suggestion_reason && (
                <div className="mt-md rounded-xl bg-surface-container-low p-md dark:bg-slate-800">
                  <p className="label-uppercase mb-xs">{t("tickets.detail.suggestionReason")}</p>
                  <p className="text-sm leading-6 text-on-surface-variant">{ticket.suggestion_reason}</p>
                </div>
              )}
            </Card>

            <Card title={t("tickets.detail.messages")} actions={<Badge tone="blue">{messageCount}</Badge>}>
              <div className="space-y-md">
                {messages.length === 0 ? (
                  <p className="text-sm text-on-surface-variant">{t("tickets.detail.noMessages")}</p>
                ) : (
                  messages.map((message) => (
                    <article key={message.id} className="rounded-xl border border-legal-border p-md dark:border-slate-700">
                      <div className="flex items-start justify-between gap-md">
                        <div>
                          <p className="font-semibold">{message.sender_name}</p>
                          <p className="text-xs text-on-surface-variant">
                            {message.sender_role} · {message.message_type}
                          </p>
                        </div>
                        <p className="text-xs text-on-surface-variant">{formatDisplayDate(message.created_at, "-", "vi-VN")}</p>
                      </div>
                      <p className="mt-sm whitespace-pre-line text-sm leading-6">{message.content}</p>
                    </article>
                  ))
                )}
              </div>
            </Card>
          </div>

          <aside className="space-y-gutter">
            <Card title={t("tickets.detail.actions")}>
              <div className="space-y-md">
                <label className="block text-sm font-semibold">
                  {t("tickets.detail.reply")}
                  <textarea
                    className="form-field mt-xs min-h-28"
                    value={messageValue}
                    onChange={(event) => setMessageValue(event.target.value)}
                    placeholder={t("tickets.detail.replyPlaceholder")}
                    disabled={isClosed || isCancelled}
                  />
                </label>
                <div className="flex flex-wrap gap-sm">
                  <Button
                    leftIcon={<Reply className="h-4 w-4" />}
                    disabled={busyAction !== null || isClosed || isCancelled || messageValue.trim().length === 0}
                    onClick={async () => {
                      if (!ticketId || !messageValue.trim()) return;
                      setBusyAction("reply");
                      try {
                        const updated = await replyToLegalTicket(ticketId, { message: messageValue.trim() });
                        setTicket(updated);
                        setMessageValue("");
                        await loadTicket();
                        toast.success(t("tickets.detail.replySuccess"), t("toast.successTitle"));
                      } catch (error) {
                        const message = error instanceof Error ? error.message : t("tickets.detail.replyError");
                        toast.error(message, t("toast.errorTitle"));
                      } finally {
                        setBusyAction(null);
                      }
                    }}
                  >
                    {busyAction === "reply" ? t("common.loading") : t("tickets.detail.sendReply")}
                  </Button>
                </div>
              </div>
            </Card>

            <Card title={t("tickets.detail.workflow")}>
              <div className="space-y-sm">
                <Button
                  variant="secondary"
                  leftIcon={<XCircle className="h-4 w-4" />}
                  disabled={busyAction !== null || isCancelled}
                  onClick={async () => {
                    if (!ticketId) return;
                    setBusyAction("cancel");
                    try {
                      const updated = await cancelLegalTicket(ticketId, { reason: t("tickets.detail.cancelReason") });
                      setTicket(updated);
                      await loadTicket();
                      toast.success(t("tickets.detail.cancelSuccess"), t("toast.successTitle"));
                    } catch (error) {
                      const message = error instanceof Error ? error.message : t("tickets.detail.cancelError");
                      toast.error(message, t("toast.errorTitle"));
                    } finally {
                      setBusyAction(null);
                    }
                  }}
                >
                  {busyAction === "cancel" ? t("common.loading") : t("tickets.detail.cancel")}
                </Button>

                <Button
                  variant="secondary"
                  leftIcon={<ShieldAlert className="h-4 w-4" />}
                  disabled={busyAction !== null || isClosed || isCancelled}
                  onClick={async () => {
                    if (!ticketId) return;
                    setBusyAction("close");
                    try {
                      const updated = await closeLegalTicket(ticketId, { feedback: t("tickets.detail.closeFeedback") });
                      setTicket(updated);
                      await loadTicket();
                      toast.success(t("tickets.detail.closeSuccess"), t("toast.successTitle"));
                    } catch (error) {
                      const message = error instanceof Error ? error.message : t("tickets.detail.closeError");
                      toast.error(message, t("toast.errorTitle"));
                    } finally {
                      setBusyAction(null);
                    }
                  }}
                >
                  {busyAction === "close" ? t("common.loading") : t("tickets.detail.close")}
                </Button>

                <Button
                  variant="secondary"
                  disabled={busyAction !== null || (!isClosed && !isCancelled)}
                  onClick={async () => {
                    if (!ticketId) return;
                    setBusyAction("reopen");
                    try {
                      const updated = await reopenLegalTicket(ticketId, { reason: t("tickets.detail.reopenReason") });
                      setTicket(updated);
                      await loadTicket();
                      toast.success(t("tickets.detail.reopenSuccess"), t("toast.successTitle"));
                    } catch (error) {
                      const message = error instanceof Error ? error.message : t("tickets.detail.reopenError");
                      toast.error(message, t("toast.errorTitle"));
                    } finally {
                      setBusyAction(null);
                    }
                  }}
                >
                  {busyAction === "reopen" ? t("common.loading") : t("tickets.detail.reopen")}
                </Button>
              </div>
            </Card>
          </aside>
        </div>
      )}
    </div>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-surface-container-low p-md dark:bg-slate-800">
      <p className="label-uppercase mb-xs">{label}</p>
      <p className="text-sm font-semibold break-words">{value}</p>
    </div>
  );
}
