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
  PlayCircle,
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
import { LAWYER_UPLOAD_ACCEPT, validateLawyerUpload } from "../../config/lawyerUpload";
import {
  downloadLawyerTicketFile,
  getLawyerTicketDetail,
  getLawyerTicketFiles,
  requestMoreInfoLawyerTicket,
  resolveLawyerTicket,
  startReviewLawyerTicket,
  type LawyerTicketDetail,
  type LawyerTicketFile,
} from "../../api/lawyerTicketApi";
import { getAttachmentPolicy, getTicketConversation, sendTicketConversationMessage, uploadTicketAttachment, downloadTicketAttachment } from "../../services/legalTicket.service";
import type { AttachmentPolicy, LegalTicketMessage } from "../../types/legalTicket";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { EmptyState } from "../../components/common/EmptyState";
import { Modal } from "../../components/common/Modal";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { formatDisplayDate, formatFileSize, localeForLanguage } from "../../utils/format";
import { ApiRequestError } from "../../services/http";
import { getLegalTicketStatusLabel } from "../../types/legalTicketStatus";

const getValue = (value?: string | number | null) =>
  value === undefined || value === null || value === "" ? "—" : String(value);

const senderRoleKeys: Record<string, string> = {
  ADMIN: "role.admin",
  EXPERT: "role.expert",
  LAWYER: "role.lawyer",
  CUSTOMER: "role.customer",
  ASSISTANT: "chat.role.assistant",
  AI: "role.ai",
  SYSTEM: "role.system",
  USER: "role.user",
};

const messageTypeKeys: Record<string, string> = {
  MESSAGE: "legalTickets.messageType.MESSAGE",
  COMMENT: "legalTickets.messageType.COMMENT",
  REPLY: "legalTickets.messageType.REPLY",
  STATUS_CHANGE: "legalTickets.messageType.STATUS_CHANGE",
  INTERNAL_NOTE: "legalTickets.messageType.INTERNAL_NOTE",
  ATTACHMENT: "legalTickets.messageType.ATTACHMENT",
};

const visibilityScopeKeys: Record<string, string> = {
  CUSTOMER: "adminTickets.visibilityScope.CUSTOMER",
  LAWYER: "adminTickets.visibilityScope.LAWYER",
  ADMIN: "adminTickets.visibilityScope.ADMIN",
  ALL_INTERNAL: "adminTickets.visibilityScope.ALL_INTERNAL",
  GLOBAL: "knowledge.scopeValue.GLOBAL",
  WORKSPACE: "knowledge.scopeValue.WORKSPACE",
  PRIVATE: "knowledge.visibility.PRIVATE",
  PUBLIC: "knowledge.visibility.PUBLIC",
};

const terminalStatuses = new Set(["REJECTED_BY_ADMIN", "RESOLVED", "CLOSED", "CANCELLED"]);

const canStartReview = (status?: string | null) =>
  status === "ASSIGNED_TO_LAWYER" || status === "CUSTOMER_RESPONDED" || status === "REOPENED";

const canChatOnTicket = (status?: string | null) =>
  Boolean(status && ['ASSIGNED_TO_LAWYER', 'IN_REVIEW', 'NEED_MORE_INFO', 'CUSTOMER_RESPONDED', 'REOPENED'].includes(status));
const canRequestMoreInfo = (status?: string | null) =>
  Boolean(status && ['ASSIGNED_TO_LAWYER', 'IN_REVIEW', 'CUSTOMER_RESPONDED'].includes(status));
const canResolveTicket = (status?: string | null) =>
  Boolean(status && ['ASSIGNED_TO_LAWYER', 'IN_REVIEW', 'CUSTOMER_RESPONDED', 'REOPENED'].includes(status));

export function LawyerTicketDetailPage() {
  const { t, language } = useI18n();
  const locale = localeForLanguage(language);
  const toast = useToast();
  const { ticketId } = useParams();

  const [ticket, setTicket] = useState<LawyerTicketDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailError, setDetailError] = useState("");

  const [messages, setMessages] = useState<LegalTicketMessage[]>([]);
  const [attachmentPolicy, setAttachmentPolicy] = useState<AttachmentPolicy | null>(null);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageValue, setMessageValue] = useState("");
  const [sendingMessage, setSendingMessage] = useState(false);

  const [files, setFiles] = useState<LawyerTicketFile[]>([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [uploadingFile] = useState(false);
  const [selectedUploadFile, setSelectedUploadFile] = useState<File | null>(null);
  const [uploadValidationError, setUploadValidationError] = useState("");
  const [openingDocumentId, setOpeningDocumentId] = useState("");

  const [startingReview, setStartingReview] = useState(false);
  const [requestInfoOpen, setRequestInfoOpen] = useState(false);
  const [requestInfoMessage, setRequestInfoMessage] = useState("");
  const [requestInfoSubmitting, setRequestInfoSubmitting] = useState(false);
  const [resolveOpen, setResolveOpen] = useState(false);
  const [expertAnswer, setExpertAnswer] = useState("");
  const [expertInternalNote, setExpertInternalNote] = useState("");
  const [resolveSubmitting, setResolveSubmitting] = useState(false);
  const [actionError, setActionError] = useState("");

  const isTerminal = terminalStatuses.has(String(ticket?.status ?? ""));
  const translateEnum = (value: string | null | undefined, keys: Record<string, string>) => {
    if (!value) return "—";
    const key = keys[value.toUpperCase()];
    return key ? t(key) : t("common.unknown");
  };

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
      const data = await getTicketConversation(ticketId);
      setMessages(data.items ?? []);
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
    if (!ticketId || (!messageValue.trim() && !selectedUploadFile)) return;

    try {
      setSendingMessage(true);
      const attachmentIds: string[] = [];
      if (selectedUploadFile) {
        const uploaded = await uploadTicketAttachment(selectedUploadFile, `draft_${crypto.randomUUID()}`, () => undefined);
        attachmentIds.push(uploaded.id);
      }
      await sendTicketConversationMessage(ticketId, messageValue.trim(), attachmentIds);

      setMessageValue("");
      setSelectedUploadFile(null);
      toast.success(t("lawyerTickets.messages.sendSuccess"));
      await loadMessages();
    } catch (error) {
      toast.error(t("lawyerTickets.messages.sendError"), t("toast.errorTitle"));
      if (error instanceof ApiRequestError && error.status === 409) await refreshAll();
    } finally {
      setSendingMessage(false);
    }
  }, [ticketId, messageValue, selectedUploadFile, toast, t, loadMessages, refreshAll]);

  const handleUploadFile = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      if (!ticketId || !ticket) return;

      const file = event.target.files?.[0];
      event.target.value = "";

      if (!file) return;
      const validation = validateLawyerUpload(file);
      if (!validation.valid) {
        setSelectedUploadFile(null);
        setUploadValidationError(t(validation.messageKey));
        return;
      }
      setSelectedUploadFile(file);
      setUploadValidationError("");

      if (attachmentPolicy && file.size > attachmentPolicy.maxAttachmentSizeKb * 1024) {
        setSelectedUploadFile(null);
        setUploadValidationError(t("ticketComposer.fileTooLarge", {
          file: file.name,
          size: Math.ceil(file.size / 1024),
          max: attachmentPolicy.maxAttachmentSizeKb,
        }));
      }
    },
    [ticketId, ticket, attachmentPolicy, t],
  );

  const handleStartReview = useCallback(async () => {
    if (!ticketId || !ticket || !canStartReview(ticket.status)) return;

    try {
      setStartingReview(true);
      setActionError("");
      const data = await startReviewLawyerTicket(ticketId);
      setTicket(data);
      toast.success(t("lawyerTickets.actions.startSuccess"), t("toast.successTitle"));
      await refreshAll();
    } catch (error) {
      const message = t("lawyerTickets.actions.startError");
      setActionError(message);
      toast.error(message);
      if (error instanceof ApiRequestError && error.status === 409) await refreshAll();
    } finally {
      setStartingReview(false);
    }
  }, [ticketId, ticket, refreshAll, toast, t]);

  const handleRequestMoreInfo = useCallback(async () => {
    const message = requestInfoMessage.trim();
    if (!ticketId || !message) return;

    try {
      setRequestInfoSubmitting(true);
      setActionError("");
      const data = await requestMoreInfoLawyerTicket(ticketId, { message });
      setTicket(data);
      setRequestInfoMessage("");
      setRequestInfoOpen(false);
      toast.success(t("lawyerTickets.actions.requestInfoSuccess"), t("toast.successTitle"));
      await refreshAll();
    } catch (error) {
      const messageText = t("lawyerTickets.actions.requestInfoError");
      setActionError(messageText);
      toast.error(messageText);
      if (error instanceof ApiRequestError && error.status === 409) await refreshAll();
    } finally {
      setRequestInfoSubmitting(false);
    }
  }, [ticketId, requestInfoMessage, refreshAll, toast, t]);

  const handleResolveTicket = useCallback(async () => {
    const answer = expertAnswer.trim();
    if (!ticketId || !answer) return;

    try {
      setResolveSubmitting(true);
      setActionError("");
      const data = await resolveLawyerTicket(ticketId, {
        expert_answer: answer,
        expert_internal_note: expertInternalNote.trim() || null,
      });
      setTicket(data);
      setExpertAnswer("");
      setExpertInternalNote("");
      setResolveOpen(false);
      toast.success(t("lawyerTickets.actions.resolveSuccess"), t("toast.successTitle"));
      await refreshAll();
    } catch (error) {
      const message = t("lawyerTickets.actions.resolveError");
      setActionError(message);
      toast.error(message);
      if (error instanceof ApiRequestError && error.status === 409) await refreshAll();
    } finally {
      setResolveSubmitting(false);
    }
  }, [ticketId, expertAnswer, expertInternalNote, refreshAll, toast, t]);

  const handleOpenFile = useCallback(
    async (file: LawyerTicketFile) => {
      if (!ticketId) return;

      let objectUrl = "";

      try {
        setOpeningDocumentId(file.documentId);
        objectUrl = await downloadLawyerTicketFile(ticketId, file.documentId);
        window.open(objectUrl, "_blank", "noopener,noreferrer");
      } catch {
        toast.error(t("lawyerTickets.files.openError"));
      } finally {
        if (objectUrl) {
          window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        }
        setOpeningDocumentId("");
      }
    },
    [ticketId, toast, t],
  );

  useEffect(() => {
    void refreshAll();
    void getAttachmentPolicy().then(setAttachmentPolicy).catch(() => undefined);
    const intervalId = window.setInterval(() => {
      if (!ticketId) return;
      void Promise.allSettled([
        getLawyerTicketDetail(ticketId).then(setTicket),
        getTicketConversation(ticketId).then((page) => setMessages(page.items ?? [])),
        getLawyerTicketFiles(ticketId).then((items) => setFiles(items ?? [])),
      ]);
    }, 4000);
    return () => window.clearInterval(intervalId);
  }, [refreshAll, ticketId]);

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

          </div>
        }
      />

      {loading && (
        <Card className="p-6 text-sm text-muted-foreground" aria-live="polite">
          {t("lawyerTickets.detail.loading")}
        </Card>
      )}

      {!loading && !ticket && (
        <div className="space-y-md">
          {detailError && (
            <div role="alert" className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
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
                <Badge>{getLegalTicketStatusLabel(ticket.status, t)}</Badge>
                <Badge>{ticket.risk_level ? t(`risk.${ticket.risk_level.toLowerCase()}`) : t("risk.none")}</Badge>
              </div>
            </div>

            <div className="mt-6 grid gap-4 md:grid-cols-2">
              <InfoItem
                icon={<CalendarDays className="h-4 w-4" />}
                label={t("lawyerTickets.detail.createdAt")}
                value={formatDisplayDate(ticket.created_at, "—", locale)}
              />
              <InfoItem
                icon={<CalendarDays className="h-4 w-4" />}
                label={t("lawyerTickets.detail.updatedAt")}
                value={formatDisplayDate(ticket.updated_at, "—", locale)}
              />
            </div>
          </Card>

          {ticket.contextSnapshot && <Card className="p-6"><h3 className="font-semibold">{t("sharedTicket.immutableContext")}</h3><div className="mt-md grid gap-md lg:grid-cols-2"><div><p className="text-xs font-semibold uppercase">{t("sharedTicket.userQuestion")}</p><p className="mt-xs whitespace-pre-line text-sm">{ticket.contextSnapshot.userQuestion}</p></div><div><p className="text-xs font-semibold uppercase">{t("adminTickets.contextAnswer")}</p><p className="mt-xs whitespace-pre-line text-sm">{ticket.contextSnapshot.assistantAnswer || "—"}</p></div><pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-container-low p-sm text-xs">{ticket.contextSnapshot.documentSnapshotJson || "[]"}</pre><pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-container-low p-sm text-xs">{ticket.contextSnapshot.citationSnapshotJson || "[]"}</pre></div></Card>}

          <Card className="p-6">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <PlayCircle className="h-5 w-5" />
                  <h3 className="font-semibold">{t('lawyerTickets.actions.title')}</h3>
                </div>
                <p className="mt-1 text-sm text-muted-foreground">
                  {t('lawyerTickets.actions.currentStatus', { status: getLegalTicketStatusLabel(ticket.status, t) })}
                </p>
                {actionError && (
                  <p className="mt-3 rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
                    {actionError}
                  </p>
                )}
              </div>

              <div className="flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  onClick={handleStartReview}
                  disabled={startingReview || !canStartReview(ticket.status)}
                >
                  {startingReview ? t("common.loading") : t("lawyerTickets.actions.startReview")}
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => setRequestInfoOpen(true)}
                  disabled={!canRequestMoreInfo(ticket.status)}
                >
                  {t("lawyerTickets.actions.requestMoreInfo")}
                </Button>
                <Button
                  onClick={() => setResolveOpen(true)}
                  disabled={!canResolveTicket(ticket.status)}
                >
                  {t("lawyerTickets.actions.submitConclusion")}
                </Button>
              </div>
            </div>

            {isTerminal && (
              <p className="mt-3 rounded-lg bg-surface-container-low p-sm text-sm text-muted-foreground dark:bg-slate-800">
                {t("lawyerTickets.actions.terminalNotice")}
              </p>
            )}
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
              <Badge>{ticket.risk_level ? t(`risk.${ticket.risk_level.toLowerCase()}`) : t("risk.none")}</Badge>
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
                        {formatDisplayDate(message.created_at, "—", locale)}
                      </p>
                    </div>

                    <p className="mt-1 text-xs text-muted-foreground">
                      {translateEnum(message.sender_role, senderRoleKeys)} ·{" "}
                      {translateEnum(message.message_type, messageTypeKeys)}
                    </p>

                    <p className="mt-3 whitespace-pre-line text-sm">
                      {getValue(message.content)}
                    </p>
                    {message.attachments?.map((attachment) => <button type="button" key={attachment.id} className="mt-2 mr-2 rounded-lg border px-3 py-2 text-xs font-semibold text-primary" onClick={async () => { try { const url = await downloadTicketAttachment(attachment.id); const anchor = document.createElement("a"); anchor.href = url; anchor.download = attachment.originalFileName; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000); } catch { toast.error(t("legalTickets.detail.downloadError"), t("toast.errorTitle")); } }}><Paperclip className="mr-1 inline h-3 w-3" aria-hidden="true" />{attachment.originalFileName}</button>)}
                  </div>
                ))}
              </div>
            )}

            {canChatOnTicket(ticket.status) && <div className="mt-4 flex gap-2">
              <textarea
                value={messageValue}
                onChange={(event) => setMessageValue(event.target.value)}
                placeholder={t("lawyerTickets.messages.placeholder")}
                className="min-h-24 flex-1 rounded-lg border bg-background px-3 py-2 text-sm outline-none focus:border-primary"
              />
              <Button
                onClick={handleSendMessage}
                disabled={sendingMessage || (!messageValue.trim() && !selectedUploadFile)}
              >
                <Send className="mr-2 h-4 w-4" />
                {t("lawyerTickets.messages.send")}
              </Button>
            </div>}
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
                {canChatOnTicket(ticket.status) && <label className="inline-flex cursor-pointer items-center rounded-lg border px-4 py-2 text-sm font-medium">
                  <input
                    type="file"
                    accept={LAWYER_UPLOAD_ACCEPT}
                    className="hidden"
                    onChange={handleUploadFile}
                    disabled={uploadingFile}
                  />
                  {uploadingFile
                    ? t("common.loading")
                    : t("lawyerTickets.files.upload")}
                </label>}

                <Button onClick={loadFiles} disabled={filesLoading}>
                  <RefreshCw className="mr-2 h-4 w-4" />
                  {t("lawyerTickets.files.refresh")}
                </Button>
              </div>
            </div>
            {selectedUploadFile && <p className="mb-3 text-sm" aria-live="polite">{selectedUploadFile.name} · {formatFileSize(selectedUploadFile.size, locale)} · {selectedUploadFile.type || t('files.mimeUnknown')}</p>}
            {uploadValidationError && <p className="mb-3 text-sm text-error" role="alert">{uploadValidationError}</p>}

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
                        <p className="truncate font-medium" title={file.originalFileName || undefined}>
                          {getValue(file.originalFileName)}
                        </p>
                      </div>

                      <p className="mt-1 text-xs text-muted-foreground">
                        {getValue(file.fileType)} ·{" "}
                        {formatFileSize(file.fileSize ?? 0, locale)} ·{" "}
                        {formatDisplayDate(file.uploadedAt, "—", locale)}
                      </p>

                      <p className="mt-1 text-xs text-muted-foreground">
                        {getValue(file.documentPurpose)} ·{" "}
                        {translateEnum(file.visibilityScope, visibilityScopeKeys)}
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

      <Modal
        open={requestInfoOpen}
        title={t("lawyerTickets.requestInformation")}
        onClose={() => {
          if (!requestInfoSubmitting) setRequestInfoOpen(false);
        }}
        footer={
          <div className="flex justify-end gap-sm">
            <Button variant="secondary" onClick={() => setRequestInfoOpen(false)} disabled={requestInfoSubmitting}>
              {t("actions.cancel")}
            </Button>
            <Button onClick={() => void handleRequestMoreInfo()} disabled={requestInfoSubmitting || !requestInfoMessage.trim()}>
              {requestInfoSubmitting ? t("common.loading") : t("lawyerTickets.actions.sendRequest")}
            </Button>
          </div>
        }
      >
        <label className="block text-sm font-semibold">
          {t("lawyerTickets.actions.requestInfoContent")}
          <textarea
            className="form-field mt-xs min-h-32"
            value={requestInfoMessage}
            onChange={(event) => setRequestInfoMessage(event.target.value)}
          />
        </label>
      </Modal>

      <Modal
        open={resolveOpen}
        title={t("lawyerTickets.submitConclusion")}
        onClose={() => {
          if (!resolveSubmitting) setResolveOpen(false);
        }}
        footer={
          <div className="flex justify-end gap-sm">
            <Button variant="secondary" onClick={() => setResolveOpen(false)} disabled={resolveSubmitting}>
              {t("actions.cancel")}
            </Button>
            <Button onClick={() => void handleResolveTicket()} disabled={resolveSubmitting || !expertAnswer.trim()}>
              {resolveSubmitting ? t("common.loading") : t("lawyerTickets.actions.submitConclusion")}
            </Button>
          </div>
        }
      >
        <div className="space-y-md">
          <label className="block text-sm font-semibold">
            {t("lawyerTickets.actions.customerConclusion")}
            <textarea
              className="form-field mt-xs min-h-36"
              value={expertAnswer}
              onChange={(event) => setExpertAnswer(event.target.value)}
            />
          </label>
          <label className="block text-sm font-semibold">
            {t("lawyerTickets.actions.internalNote")}
            <textarea
              className="form-field mt-xs min-h-24"
              value={expertInternalNote}
              onChange={(event) => setExpertInternalNote(event.target.value)}
            />
          </label>
        </div>
      </Modal>
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
