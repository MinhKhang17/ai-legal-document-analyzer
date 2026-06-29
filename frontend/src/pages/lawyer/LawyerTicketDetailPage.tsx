import {
  ArrowLeft,
  Bot,
  Briefcase,
  CalendarDays,
  Download,
  FileText,
  Lightbulb,
  MessageSquare,
  Paperclip,
  RefreshCw,
  Send,
  ShieldAlert,
  UserRound,
} from "lucide-react";
import {
  useCallback,
  useEffect,
  useState,
  type ChangeEvent,
  type ReactNode,
} from "react";
import { Link, useParams } from "react-router-dom";
import {
  closeLawyerTicket,
  downloadLawyerTicketFile,
  getLawyerTicketDetail,
  getLawyerTicketFiles,
  getLawyerTicketMessages,
  sendLawyerTicketMessage,
  type LawyerTicketDetail,
  type LawyerTicketFile,
  type LawyerTicketMessage,
  uploadLawyerTicketFile,
} from "../../api/lawyerTicketApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { formatDisplayDate } from "../../utils/format";

const getValue = (value?: string | number | null) =>
  value === undefined || value === null || value === "" ? "—" : String(value);

const formatFileSize = (size?: number | null) => {
  if (!size) return "—";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

const fileToBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();

    reader.onload = () => {
      const result = String(reader.result);
      resolve(result.split(",")[1] ?? "");
    };

    reader.onerror = reject;
    reader.readAsDataURL(file);
  });

export function LawyerTicketDetailPage() {
  const { t } = useI18n();
  const toast = useToast();
  const { ticketId } = useParams();

  const [ticket, setTicket] = useState<LawyerTicketDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailError, setDetailError] = useState("");

  const [messages, setMessages] = useState<LawyerTicketMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageValue, setMessageValue] = useState("");
  const [sendingMessage, setSendingMessage] = useState(false);

  const [files, setFiles] = useState<LawyerTicketFile[]>([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [uploadingFile, setUploadingFile] = useState(false);
  const [openingDocumentId, setOpeningDocumentId] = useState("");

  const [closingTicket, setClosingTicket] = useState(false);

  const isClosed = ticket?.status === "CLOSED";

  const loadTicket = useCallback(async () => {
    if (!ticketId) return false;

    try {
      setLoading(true);
      setDetailError("");
      const data = await getLawyerTicketDetail(ticketId);
      setTicket(data);
      return true;
    } catch {
      setTicket(null);
      setDetailError(t("lawyerTickets.detail.loadError"));
      return false;
    } finally {
      setLoading(false);
    }
  }, [ticketId, t]);

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

  const loadFiles = useCallback(async () => {
    if (!ticketId) return;

    try {
      setFilesLoading(true);
      const data = await getLawyerTicketFiles(ticketId);
      setFiles(data ?? []);
    } catch {
      toast.error(t("lawyerTickets.files.loadError"));
    } finally {
      setFilesLoading(false);
    }
  }, [ticketId, toast, t]);

  const refreshAll = useCallback(async () => {
    const loadedTicket = await loadTicket();

    if (!loadedTicket) {
      setMessages([]);
      setFiles([]);
      return;
    }

    await Promise.all([loadMessages(), loadFiles()]);
  }, [loadTicket, loadMessages, loadFiles]);

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

  const handleUploadFile = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      if (!ticketId || !ticket) return;

      const file = event.target.files?.[0];
      event.target.value = "";

      if (!file) return;

      if (!ticket.assigned_lawyer_id) {
        toast.error(t("lawyerTickets.files.uploadError"));
        return;
      }

      try {
        setUploadingFile(true);

        const contentBase64 = await fileToBase64(file);

        await uploadLawyerTicketFile(ticketId, {
          uploadedById: ticket.assigned_lawyer_id,
          originalFileName: file.name,
          fileType: file.type || "application/octet-stream",
          contentBase64,
          visibilityScope: "CUSTOMER",
        });

        toast.success(t("lawyerTickets.files.uploadSuccess"));
        await loadFiles();
      } catch {
        toast.error(t("lawyerTickets.files.uploadError"));
      } finally {
        setUploadingFile(false);
      }
    },
    [ticketId, ticket, loadFiles, toast, t],
  );

  const handleCloseTicket = useCallback(async () => {
    if (!ticketId || isClosed) return;

    const confirmed = window.confirm(
      t("lawyerTickets.close.confirmDescription"),
    );

    if (!confirmed) return;

    try {
      setClosingTicket(true);

      const data = await closeLawyerTicket(ticketId, {
        feedback: t("lawyerTickets.close.defaultFeedback"),
      });

      setTicket(data);
      toast.success(t("lawyerTickets.close.success"));
      await loadTicket();
    } catch {
      toast.error(t("lawyerTickets.close.error"));
    } finally {
      setClosingTicket(false);
    }
  }, [ticketId, isClosed, loadTicket, toast, t]);

  const handleOpenFile = useCallback(
    async (file: LawyerTicketFile) => {
      if (!ticketId) return;

      try {
        setOpeningDocumentId(file.documentId);
        const fileInfo = await downloadLawyerTicketFile(ticketId, file.documentId);

        if (fileInfo.filePath) {
          window.open(fileInfo.filePath, "_blank", "noopener,noreferrer");
          return;
        }

        toast.error(t("lawyerTickets.files.openError"));
      } catch {
        toast.error(t("lawyerTickets.files.openError"));
      } finally {
        setOpeningDocumentId("");
      }
    },
    [ticketId, toast, t],
  );

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  return (
    <div className="space-y-6">
      <PageHeader
        title={t("lawyerTickets.detail.title")}
        subtitle={t("lawyerTickets.detail.subtitle")}
        actions={
          <div className="flex flex-wrap gap-2">
            <Link to="/lawyer/tickets">
              <Button>
                <ArrowLeft className="mr-2 h-4 w-4" />
                {t("lawyerTickets.detail.back")}
              </Button>
            </Link>

            <Button onClick={() => void refreshAll()} disabled={loading}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {t("lawyerTickets.detail.refresh")}
            </Button>

            <Button
              onClick={handleCloseTicket}
              disabled={!ticket || loading || closingTicket || isClosed}
            >
              {closingTicket
                ? t("common.loading")
                : t("lawyerTickets.close.button")}
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
        <div className="space-y-md">
          {detailError && (
            <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
              {detailError}
            </div>
          )}
          <EmptyState
            title={t("lawyerTickets.detail.emptyTitle")}
            description={t("lawyerTickets.detail.emptyDescription")}
          />
          <Link to="/lawyer/tickets">
            <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
              {t("lawyerTickets.detail.back")}
            </Button>
          </Link>
        </div>
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
                      {getValue(message.sender_role)} ·{" "}
                      {getValue(message.message_type)}
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

          <Card className="p-6">
            <div className="mb-4 flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2">
                  <Paperclip className="h-5 w-5" />
                  <h3 className="font-semibold">
                    {t("lawyerTickets.files.title")}
                  </h3>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">
                  {t("lawyerTickets.files.description")}
                </p>
              </div>

              <div className="flex flex-wrap gap-2">
                <label className="inline-flex cursor-pointer items-center rounded-lg border px-4 py-2 text-sm font-medium">
                  <input
                    type="file"
                    className="hidden"
                    onChange={handleUploadFile}
                    disabled={uploadingFile}
                  />
                  {uploadingFile
                    ? t("common.loading")
                    : t("lawyerTickets.files.upload")}
                </label>

                <Button onClick={loadFiles} disabled={filesLoading}>
                  <RefreshCw className="mr-2 h-4 w-4" />
                  {t("lawyerTickets.files.refresh")}
                </Button>
              </div>
            </div>

            {filesLoading ? (
              <p className="text-sm text-muted-foreground">
                {t("lawyerTickets.files.loading")}
              </p>
            ) : files.length === 0 ? (
              <div className="rounded-lg border p-4">
                <p className="font-medium">
                  {t("lawyerTickets.files.emptyTitle")}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {t("lawyerTickets.files.emptyDescription")}
                </p>
              </div>
            ) : (
              <div className="grid gap-3">
                {files.map((file) => (
                  <div
                    key={file.documentId}
                    className="flex flex-col gap-3 rounded-lg border p-4 md:flex-row md:items-center md:justify-between"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <FileText className="h-4 w-4 shrink-0" />
                        <p className="truncate font-medium">
                          {getValue(file.originalFileName)}
                        </p>
                      </div>

                      <p className="mt-1 text-xs text-muted-foreground">
                        {getValue(file.fileType)} ·{" "}
                        {formatFileSize(file.fileSize)} ·{" "}
                        {formatDisplayDate(file.uploadedAt, "—")}
                      </p>

                      <p className="mt-1 text-xs text-muted-foreground">
                        {getValue(file.documentPurpose)} ·{" "}
                        {getValue(file.visibilityScope)}
                      </p>
                    </div>

                    <Button
                      onClick={() => void handleOpenFile(file)}
                      disabled={openingDocumentId === file.documentId}
                    >
                      <Download className="mr-2 h-4 w-4" />
                      {openingDocumentId === file.documentId
                        ? t("common.loading")
                        : t("lawyerTickets.files.open")}
                    </Button>
                  </div>
                ))}
              </div>
            )}
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
