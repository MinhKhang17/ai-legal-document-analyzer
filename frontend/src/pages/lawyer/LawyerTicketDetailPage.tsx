import {
  ArrowLeft,
  Bot,
  Briefcase,
  CalendarDays,
  FileText,
  Lightbulb,
  MessageSquare,
  RefreshCw,
  Send,
  ShieldAlert,
  UserRound,
} from "lucide-react";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import {
  getLawyerTicketDetail,
  type LawyerTicketDetail,
  getLawyerTicketMessages,
  sendLawyerTicketMessage,
  type LawyerTicketMessage,
} from "../../api/lawyerTicketApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { formatDisplayDate } from "../../utils/format";

const getValue = (value?: string | number | null) =>
  value === undefined || value === null || value === "" ? "—" : String(value);

export function LawyerTicketDetailPage() {
  const { t } = useI18n();
  const toast = useToast();
  const { ticketId } = useParams();

  const [ticket, setTicket] = useState<LawyerTicketDetail | null>(null);
  const [loading, setLoading] = useState(false);

  const [messages, setMessages] = useState<LawyerTicketMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageValue, setMessageValue] = useState("");
  const [sendingMessage, setSendingMessage] = useState(false);

  const loadTicket = useCallback(async () => {
    if (!ticketId) return;

    try {
      setLoading(true);
      const data = await getLawyerTicketDetail(ticketId);
      setTicket(data);
    } catch {
      toast.error(t("lawyerTickets.detail.loadError"));
    } finally {
      setLoading(false);
    }
  }, [ticketId, toast, t]);

  const loadMessages = useCallback(async () => {
    if (!ticketId) return;

    try {
      setMessagesLoading(true);
      const data = await getLawyerTicketMessages(ticketId);
      setMessages(data ?? []);
    } catch {
      toast.error(t("lawyerTickets.messages.loadError"));
    } finally {
      setMessagesLoading(false);
    }
  }, [ticketId, toast, t]);

  const handleSendMessage = useCallback(async () => {
    if (!ticketId || !messageValue.trim()) return;

    try {
      setSendingMessage(true);
      await sendLawyerTicketMessage(ticketId, {
        message: messageValue.trim(),
      });
      setMessageValue("");
      toast.success(t("lawyerTickets.messages.sendSuccess"));
      await loadMessages();
    } catch {
      toast.error(t("lawyerTickets.messages.sendError"));
    } finally {
      setSendingMessage(false);
    }
  }, [ticketId, messageValue, toast, t, loadMessages]);

  useEffect(() => {
    loadTicket();
    loadMessages();
  }, [loadTicket, loadMessages]);

  return (
    <div className="space-y-6">
      <PageHeader
        title={t("lawyerTickets.detail.title")}
        subtitle={t("lawyerTickets.detail.subtitle")}
        actions={
          <div className="flex gap-2">
            <Link to="/lawyer/tickets">
              <Button>
                <ArrowLeft className="mr-2 h-4 w-4" />
                {t("lawyerTickets.detail.back")}
              </Button>
            </Link>

            <Button onClick={loadTicket} disabled={loading}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {t("lawyerTickets.detail.refresh")}
            </Button>
          </div>
        }
      />

      {loading && (
        <Card className="p-6 text-sm text-muted-foreground">
          {t("lawyerTickets.detail.loading")}
        </Card>
      )}

      {!loading && !ticket && (
        <Card className="p-6">
          <h3 className="font-semibold">
            {t("lawyerTickets.detail.emptyTitle")}
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("lawyerTickets.detail.emptyDescription")}
          </p>
        </Card>
      )}

      {!loading && ticket && (
        <>
          <Card className="p-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
              <div>
                <p className="text-sm text-muted-foreground">
                  {getValue(ticket.request_id)}
                </p>
                <h2 className="mt-1 text-2xl font-semibold">
                  {getValue(ticket.issue_title)}
                </h2>
                <p className="mt-2 text-sm text-muted-foreground">
                  {getValue(ticket.issue_summary)}
                </p>
              </div>

              <div className="flex flex-wrap gap-2">
                <Badge>{getValue(ticket.status)}</Badge>
                <Badge>{getValue(ticket.risk_level)}</Badge>
              </div>
            </div>

            <div className="mt-6 grid gap-4 md:grid-cols-2">
              <InfoItem
                icon={<CalendarDays className="h-4 w-4" />}
                label={t("lawyerTickets.detail.createdAt")}
                value={formatDisplayDate(ticket.created_at, "—")}
              />
              <InfoItem
                icon={<CalendarDays className="h-4 w-4" />}
                label={t("lawyerTickets.detail.updatedAt")}
                value={formatDisplayDate(ticket.updated_at, "—")}
              />
            </div>
          </Card>

          <div className="grid gap-6 lg:grid-cols-2">
            <SectionCard
              icon={<Bot className="h-5 w-5" />}
              title={t("lawyerTickets.detail.aiAnalysis")}
              content={ticket.answer}
            />

            <SectionCard
              icon={<Lightbulb className="h-5 w-5" />}
              title={t("lawyerTickets.detail.recommendation")}
              content={ticket.recommended_action}
            />

            <SectionCard
              icon={<ShieldAlert className="h-5 w-5" />}
              title={t("lawyerTickets.detail.evidence")}
              content={ticket.ai_evidence}
            />

            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <ShieldAlert className="h-5 w-5" />
                <h3 className="font-semibold">
                  {t("lawyerTickets.detail.riskLevel")}
                </h3>
              </div>
              <Badge>{getValue(ticket.risk_level)}</Badge>
            </Card>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <UserRound className="h-5 w-5" />
                <h3 className="font-semibold">
                  {t("lawyerTickets.detail.customerInfo")}
                </h3>
              </div>
              <div className="space-y-3">
                <InfoLine
                  label={t("lawyerTickets.detail.customerName")}
                  value={ticket.created_by_name}
                />
                <InfoLine
                  label={t("lawyerTickets.detail.customerNote")}
                  value={ticket.customer_note}
                />
              </div>
            </Card>

            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <Briefcase className="h-5 w-5" />
                <h3 className="font-semibold">
                  {t("lawyerTickets.detail.workspace")}
                </h3>
              </div>
              <div className="space-y-3">
                <InfoLine
                  label={t("lawyerTickets.detail.workspaceName")}
                  value={ticket.workspace_name}
                />
                <InfoLine
                  label={t("lawyerTickets.detail.legalDomain")}
                  value={ticket.legal_domain}
                />
              </div>
            </Card>

            <Card className="p-6">
              <div className="mb-4 flex items-center gap-2">
                <FileText className="h-5 w-5" />
                <h3 className="font-semibold">
                  {t("lawyerTickets.detail.document")}
                </h3>
              </div>
              <div className="space-y-3">
                <InfoLine
                  label={t("lawyerTickets.detail.documentName")}
                  value={ticket.document_name}
                />
                <InfoLine
                  label={t("lawyerTickets.detail.clauseReference")}
                  value={ticket.clause_reference}
                />
                <InfoLine
                  label={t("lawyerTickets.detail.pageNumber")}
                  value={ticket.page_number}
                />
              </div>
            </Card>
          </div>

          <Card className="p-6">
            <div className="mb-4 flex items-center gap-2">
              <FileText className="h-5 w-5" />
              <h3 className="font-semibold">
                {t("lawyerTickets.detail.problematicClause")}
              </h3>
            </div>
            <p className="whitespace-pre-line text-sm text-muted-foreground">
              {getValue(ticket.problematic_clause)}
            </p>
          </Card>

          <Card className="p-6">
            <div className="mb-4 flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2">
                  <MessageSquare className="h-5 w-5" />
                  <h3 className="font-semibold">
                    {t("lawyerTickets.messages.title")}
                  </h3>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">
                  {t("lawyerTickets.messages.description")}
                </p>
              </div>

              <Button onClick={loadMessages} disabled={messagesLoading}>
                <RefreshCw className="mr-2 h-4 w-4" />
                {t("lawyerTickets.messages.refresh")}
              </Button>
            </div>

            {messagesLoading ? (
              <p className="text-sm text-muted-foreground">
                {t("lawyerTickets.messages.loading")}
              </p>
            ) : messages.length === 0 ? (
              <div className="rounded-lg border p-4">
                <p className="font-medium">
                  {t("lawyerTickets.messages.emptyTitle")}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {t("lawyerTickets.messages.emptyDescription")}
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {messages.map((message) => (
                  <div key={message.id} className="rounded-lg border p-4">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-semibold">
                        {getValue(message.sender_name)}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {formatDisplayDate(message.created_at, "—")}
                      </p>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {getValue(message.sender_role)}
                    </p>
                    <p className="mt-3 whitespace-pre-line text-sm">
                      {getValue(message.content)}
                    </p>
                  </div>
                ))}
              </div>
            )}

            <div className="mt-4 flex gap-2">
              <textarea
                value={messageValue}
                onChange={(event) => setMessageValue(event.target.value)}
                placeholder={t("lawyerTickets.messages.placeholder")}
                className="min-h-24 flex-1 rounded-lg border bg-background px-3 py-2 text-sm outline-none focus:border-primary"
              />
              <Button
                onClick={handleSendMessage}
                disabled={sendingMessage || !messageValue.trim()}
              >
                <Send className="mr-2 h-4 w-4" />
                {t("lawyerTickets.messages.send")}
              </Button>
            </div>
          </Card>
        </>
      )}
    </div>
  );
}

function SectionCard({
  icon,
  title,
  content,
}: {
  icon: ReactNode;
  title: string;
  content?: string | null;
}) {
  return (
    <Card className="p-6">
      <div className="mb-4 flex items-center gap-2">
        {icon}
        <h3 className="font-semibold">{title}</h3>
      </div>
      <p className="whitespace-pre-line text-sm text-muted-foreground">
        {getValue(content)}
      </p>
    </Card>
  );
}

function InfoItem({
  icon,
  label,
  value,
}: {
  icon: ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-lg border p-3">
      {icon}
      <div>
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="text-sm font-medium">{value}</p>
      </div>
    </div>
  );
}

function InfoLine({
  label,
  value,
}: {
  label: string;
  value?: string | number | null;
}) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm font-medium">{getValue(value)}</p>
    </div>
  );
}
