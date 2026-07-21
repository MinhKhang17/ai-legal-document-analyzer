import { ArrowLeft, Download, Paperclip, RefreshCw, Send, Share2, X } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import {
  cancelLegalTicket,
  closeLegalTicket,
  getLegalTicket,
  getLegalTicketMessages,
  getCustomerTicketFiles,
  downloadCustomerTicketFile,
  reopenLegalTicket,
  getTicketConversation,
  sendTicketConversationMessage,
  uploadTicketAttachment,
  getAttachmentPolicy,
  createTicketConversationShare,
  downloadTicketAttachment,
} from "../../services/legalTicket.service";
import {
  getTicketAiAssessment,
  getTicketAiCitations,
  getTicketAiSummary,
} from "../../services/aiFeature.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { AiCitation, AiFeatureSummary, AiRiskAssessment } from "../../types/aiFeature";
import type { AdminTicketFile, AttachmentPolicy, LegalTicket, LegalTicketMessage } from "../../types/legalTicket";
import { getLegalTicketStatusLabel } from "../../types/legalTicketStatus";
import { formatDisplayDate, formatFileSize, localeForLanguage } from "../../utils/format";
import { ApiRequestError } from "../../services/http";

export function CustomerTicketDetailPage() {
  const { id = "" } = useParams();
  const { t, language } = useI18n();
  const toast = useToast();
  const [ticket, setTicket] = useState<LegalTicket | null>(null);
  const [messages, setMessages] = useState<LegalTicketMessage[]>([]);
  const [files, setFiles] = useState<AdminTicketFile[]>([]);
  const [assessment, setAssessment] = useState<AiRiskAssessment | null>(null);
  const [summary, setSummary] = useState<AiFeatureSummary | null>(null);
  const [citations, setCitations] = useState<AiCitation[]>([]);
  const [reply, setReply] = useState("");
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);
  const [uploadProgress, setUploadProgress] = useState<Record<string, number>>({});
  const [attachmentPolicy, setAttachmentPolicy] = useState<AttachmentPolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [auxiliaryError, setAuxiliaryError] = useState("");
  const locale = localeForLanguage(language);
  const canReply = Boolean(ticket?.status && ['ASSIGNED', 'ASSIGNED_TO_LAWYER', 'IN_REVIEW', 'NEED_MORE_INFO', 'WAITING_FOR_USER', 'WAITING_FOR_EXPERT', 'CUSTOMER_RESPONDED', 'REOPENED'].includes(ticket.status));
  const reopenReference = ticket?.resolved_at ?? ticket?.closed_at;
  const canReopen = Boolean(ticket && (ticket.status === 'RESOLVED' || ticket.status === 'CLOSED') && reopenReference && new Date(reopenReference).getTime() + 7 * 24 * 60 * 60 * 1000 >= Date.now());

  const loadTicket = useCallback(async (showLoading = true) => {
    if (!id) return;
    if (showLoading) setLoading(true);
    setError("");
    setAuxiliaryError("");

    try {
      const loadedTicket = await getLegalTicket(id);
      setTicket(loadedTicket);

      const [messagesResult, assessmentResult, summaryResult, citationsResult, filesResult] =
        await Promise.allSettled([
          getTicketConversation(id).then((page) => page.items).catch(() => getLegalTicketMessages(id)),
          getTicketAiAssessment(id),
          getTicketAiSummary(id),
          getTicketAiCitations(id),
          getCustomerTicketFiles(id),
        ]);

      setMessages(messagesResult.status === "fulfilled" ? messagesResult.value : []);
      setAssessment(assessmentResult.status === "fulfilled" ? assessmentResult.value : null);
      setSummary(summaryResult.status === "fulfilled" ? summaryResult.value : null);
      setCitations(citationsResult.status === "fulfilled" ? citationsResult.value : []);
      setFiles(filesResult.status === "fulfilled" ? filesResult.value : []);
      if ([messagesResult, assessmentResult, summaryResult, citationsResult, filesResult].some((result) => result.status === "rejected")) {
        setAuxiliaryError(t("common.partialDataError"));
      }
    } catch (ticketError) {
      setTicket(null);
      setMessages([]);
      setAssessment(null);
      setSummary(null);
      setCitations([]);
      setFiles([]);
      console.error("Failed to load customer legal ticket detail", ticketError);
      setError(t("legalTickets.detail.loadError"));
    } finally {
      if (showLoading) setLoading(false);
    }
  }, [id, t]);

  useEffect(() => {
    void loadTicket();
    void getAttachmentPolicy().then(setAttachmentPolicy).catch(() => undefined);
    const intervalId = window.setInterval(() => void loadTicket(false), 4000);
    return () => window.clearInterval(intervalId);
  }, [loadTicket]);

  const openFile = async (file: AdminTicketFile) => {
    try {
      const objectUrl = await downloadCustomerTicketFile(id, file.documentId);
      const anchor = document.createElement('a'); anchor.href = objectUrl; anchor.download = file.originalFileName; anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    } catch { toast.error(t("legalTickets.detail.downloadError"), t("toast.errorTitle")); }
  };

  const openAttachment = async (attachmentId: string, fileName: string) => {
    try {
      const objectUrl = await downloadTicketAttachment(attachmentId);
      const anchor = document.createElement("a");
      anchor.href = objectUrl; anchor.download = fileName; anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    } catch {
      toast.error(t("legalTickets.detail.downloadError"), t("toast.errorTitle"));
    }
  };

  const runAction = async (action: () => Promise<LegalTicket>, successMessage: string) => {
    setSaving(true);
    setError("");

    try {
      const updated = await action();
      setTicket(updated);
      toast.success(successMessage);
      await loadTicket();
    } catch (actionError) {
      console.error("Failed to update customer legal ticket", actionError);
      const message = t("legalTickets.detail.actionError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
      if (actionError instanceof ApiRequestError && actionError.status === 409) await loadTicket(false);
    } finally {
      setSaving(false);
    }
  };

  const handleReply = async () => {
    const message = reply.trim();
    if (!id || (!message && pendingFiles.length === 0)) return;
    setSaving(true); setError("");
    try {
      const attachmentIds: string[] = [];
      for (const file of pendingFiles) {
        const uploaded = await uploadTicketAttachment(file, id, (progress) =>
          setUploadProgress((current) => ({ ...current, [file.name]: progress })));
        attachmentIds.push(uploaded.id);
      }
      await sendTicketConversationMessage(id, message, attachmentIds);
      setReply(""); setPendingFiles([]); setUploadProgress({});
      toast.success(t("legalTickets.detail.replySuccess"));
      await loadTicket(false);
    } catch {
      const messageText = t("legalTickets.detail.actionError");
      setError(messageText); toast.error(messageText);
    } finally { setSaving(false); }
  };

  const selectAttachments = (list: FileList | null) => {
    if (!list || !attachmentPolicy) return;
    const selected = Array.from(list);
    if (pendingFiles.length + selected.length > attachmentPolicy.maxAttachmentsPerMessage) {
      setError(t("ticketComposer.tooManyFiles", { count: attachmentPolicy.maxAttachmentsPerMessage })); return;
    }
    for (const file of selected) {
      if (file.size > attachmentPolicy.maxAttachmentSizeKb * 1024) {
        setError(t("ticketComposer.fileTooLarge", {
          file: file.name,
          size: Math.ceil(file.size / 1024),
          max: attachmentPolicy.maxAttachmentSizeKb,
        })); return;
      }
      if (!attachmentPolicy.allowedMimeTypes.includes(file.type)) {
        setError(t("ticketComposer.invalidFileType", { file: file.name })); return;
      }
    }
    setPendingFiles((current) => [...current, ...selected]); setError("");
  };

  const createShare = async () => {
    if (!id) return;
    try {
      const expiresAt = new Date(Date.now() + 7 * 86400000).toISOString().replace("Z", "");
      const share = await createTicketConversationShare(id, ticket?.conversationScope ?? "SELECTED_RESPONSE", expiresAt);
      await navigator.clipboard.writeText(share.shareUrl);
      toast.success(t("legalTickets.detail.shareSuccess"), t("toast.successTitle"));
    } catch { toast.error(t("legalTickets.detail.shareError"), t("toast.errorTitle")); }
  };

  return (
    <div>
      <PageHeader
        title={t("legalTickets.detail.title")}
        subtitle={id}
        actions={
          <>
            <Link to="/tickets">
              <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
                {t("legalTickets.detail.back")}
              </Button>
            </Link>
            <Button variant="secondary" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void loadTicket()} disabled={loading}>
              {t("common.refresh")}
            </Button>
            {ticket?.chat_session_id && <Button variant="secondary" leftIcon={<Share2 className="h-4 w-4" />} onClick={() => void createShare()}>{t("actions.share")}</Button>}
          </>
        }
      />

      {error && (
        <div role="alert" className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}
      {ticket && auxiliaryError && (
        <div className="mb-lg rounded-xl border border-amber-300 bg-amber-50 p-md text-sm text-amber-900" role="alert">
          {auxiliaryError} <Button variant="secondary" onClick={() => void loadTicket()}>{t("common.retry")}</Button>
        </div>
      )}

      {loading ? (
        <Card><p aria-live="polite">{t("legalTickets.detail.loading")}</p></Card>
      ) : !ticket ? (
        <div className="space-y-md">
          <EmptyState
            title={t("legalTickets.detail.emptyTitle")}
            description={t("legalTickets.detail.emptyDescription")}
          />
          <Link to="/tickets">
            <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
              {t("legalTickets.detail.back")}
            </Button>
          </Link>
        </div>
      ) : (
        <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_360px]">
          <main className="space-y-gutter">
            <Card
              title={ticket.issue_title || ticket.question || ticket.id}
              subtitle={ticket.issue_summary || ticket.customer_note || t("legalTickets.detail.fallbackSubtitle")}
              actions={<Badge>{getLegalTicketStatusLabel(ticket.status, t)}</Badge>}
            >
              <dl className="grid gap-md text-sm md:grid-cols-2">
                <div><dt className="label-uppercase">{t("legalTickets.table.expert")}</dt><dd>{ticket.assigned_lawyer_name || "-"}</dd></div>
                <div><dt className="label-uppercase">{t("table.risk")}</dt><dd>{ticket.risk_level ? t(`risk.${ticket.risk_level.toLowerCase()}`) : "-"}</dd></div>
                <div><dt className="label-uppercase">{t("legalTickets.table.created")}</dt><dd>{formatDisplayDate(ticket.created_at, "-", locale)}</dd></div>
                <div><dt className="label-uppercase">{t("legalTickets.table.document")}</dt><dd>{ticket.document_name || ticket.document_id || "-"}</dd></div>
              </dl>
            </Card>

            <Card title={t("legalTickets.detail.aiSummary")}>
              <p className="whitespace-pre-line text-sm leading-6 text-on-surface-variant dark:text-slate-400">
                {summary?.summary || ticket.answer || t("legalTickets.detail.noSummary")}
              </p>
              {assessment && (
                <div className="mt-md flex flex-wrap gap-xs">
                  <Badge>{assessment.riskLevel ? t(`risk.${assessment.riskLevel.toLowerCase()}`) : t("risk.none")}</Badge>
                  {typeof assessment.confidenceScore === "number" && <Badge tone="blue">{Math.round(assessment.confidenceScore * 100)}%</Badge>}
                </div>
              )}
            </Card>

            <Card title={t("legalTickets.detail.messages")}>
              <div className="space-y-sm">
                {messages.length === 0 ? (
                  <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("legalTickets.detail.noMessages")}</p>
                ) : (
                  messages.map((message) => (
                    <article key={message.id} className="rounded-lg border border-legal-border p-md text-sm dark:border-slate-700">
                      <div className="flex items-center justify-between gap-sm">
                        <p className="font-semibold">{message.sender_name || t(`role.${message.sender_role.toLowerCase()}`)}</p>
                        <p className="text-xs text-on-surface-variant dark:text-slate-400">{formatDisplayDate(message.created_at, "-", locale)}</p>
                      </div>
                      <p className="mt-sm whitespace-pre-line">{message.content}</p>
                      {message.attachments && message.attachments.length > 0 && <div className="mt-sm flex flex-wrap gap-xs">{message.attachments.map((attachment) => <button type="button" key={attachment.id} onClick={() => void openAttachment(attachment.id, attachment.originalFileName)} className="rounded-lg border border-legal-border px-sm py-xs text-xs font-semibold text-primary"><Paperclip className="mr-xs inline h-3 w-3" />{attachment.originalFileName}</button>)}</div>}
                    </article>
                  ))
                )}
              </div>
              {canReply && <div className="mt-md space-y-sm">
                <textarea className="form-field min-h-24" value={reply} onChange={(event) => setReply(event.target.value)} placeholder={t("legalTickets.detail.replyPlaceholder")} />
                {attachmentPolicy && <p className="text-xs text-on-surface-variant">{t("ticketComposer.attachmentPolicy", { size: attachmentPolicy.maxAttachmentSizeKb, count: attachmentPolicy.maxAttachmentsPerMessage })}</p>}
                {pendingFiles.map((file) => <div key={`${file.name}-${file.lastModified}`} className="rounded-lg border border-legal-border p-sm text-sm"><div className="flex items-center gap-sm"><span className="min-w-0 flex-1 truncate" title={file.name}>{file.name} · {formatFileSize(file.size, locale)}</span><button type="button" aria-label={t("ticketComposer.removeFile", { file: file.name })} disabled={saving} onClick={() => setPendingFiles((current) => current.filter((item) => item !== file))}><X className="h-4 w-4" aria-hidden="true" /></button></div>{typeof uploadProgress[file.name] === "number" && <div className="mt-xs h-1.5 overflow-hidden rounded bg-slate-200"><div className="h-full bg-primary" style={{ width: `${uploadProgress[file.name]}%` }} /></div>}</div>)}
                <div className="flex gap-sm"><label className="inline-flex cursor-pointer items-center gap-xs rounded-lg border border-legal-border px-md py-sm text-sm font-semibold"><Paperclip className="h-4 w-4" aria-hidden="true" />{t("legalTickets.detail.addFiles")}<input type="file" multiple className="hidden" accept=".jpg,.jpeg,.png,.webp,.pdf,.docx,.txt" onChange={(event) => selectAttachments(event.target.files)} /></label><Button leftIcon={<Send className="h-4 w-4" />} onClick={() => void handleReply()} disabled={saving || (!reply.trim() && pendingFiles.length === 0)}>{t("legalTickets.detail.send")}</Button></div>
              </div>}
            </Card>

            <Card title={t("legalTickets.detail.aiCitations")}>
              {citations.length === 0 ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("legalTickets.detail.noCitations")}</p>
              ) : (
                <div className="space-y-sm">
                  {citations.map((citation) => (
                    <article key={citation.id} className="rounded-lg border border-legal-border p-md text-sm dark:border-slate-700">
                      <p className="font-semibold">{citation.label || citation.sourceReferenceId || citation.id}</p>
                      <p className="mt-xs text-on-surface-variant dark:text-slate-400">{citation.excerpt || citation.uri || "-"}</p>
                    </article>
                  ))}
                </div>
              )}
            </Card>

            <Card title={t("ticketComposer.sharedDocuments")}>
              {files.length === 0 ? <p className="text-sm text-on-surface-variant">{t("legalTickets.detail.noSharedFiles")}</p> : <div className="space-y-sm">{files.map((file) => <div key={file.documentId} className="flex items-center justify-between gap-md rounded-lg border border-legal-border p-md dark:border-slate-700"><div className="min-w-0"><p className="truncate font-semibold" title={file.originalFileName}>{file.originalFileName}</p><p className="text-xs text-on-surface-variant">{file.fileType} · {formatFileSize(file.fileSize, locale)}</p></div><Button size="sm" variant="secondary" leftIcon={<Download className="h-4 w-4" />} onClick={() => void openFile(file)}>{t("actions.download")}</Button></div>)}</div>}
            </Card>
          </main>

          <aside className="space-y-gutter">
            {ticket.contextSnapshot && <Card title={t("sharedTicket.immutableContext")}><div className="space-y-md text-sm"><div><p className="label-uppercase">{t("sharedTicket.userQuestion")}</p><p className="mt-xs whitespace-pre-line">{ticket.contextSnapshot.userQuestion}</p></div><div><p className="label-uppercase">{t("sharedTicket.aiAnswer")}</p><p className="mt-xs max-h-64 overflow-y-auto whitespace-pre-line text-on-surface-variant">{ticket.contextSnapshot.assistantAnswer || "—"}</p></div><div><p className="label-uppercase">{t("ticketComposer.sharedDocuments")}</p><pre className="mt-xs max-h-40 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-container-low p-sm text-xs">{ticket.contextSnapshot.documentSnapshotJson || "[]"}</pre></div><p className="break-all text-xs text-on-surface-variant">{t("legalTickets.detail.contentHash")}: {ticket.contextSnapshot.contentHash}</p></div></Card>}
            <Card title={t("legalTickets.detail.actions")}>
              <div className="flex flex-col gap-sm">
                {ticket.status === 'PENDING_ADMIN_REVIEW' && <Button variant="secondary" disabled={saving} onClick={() => void runAction(() => cancelLegalTicket(id, { reason: t("legalTickets.detail.cancelReason") }), t("legalTickets.detail.cancelSuccess"))}>
                  {t("legalTickets.detail.cancel")}
                </Button>}
                {ticket.status === 'RESOLVED' && <Button variant="secondary" disabled={saving} onClick={() => void runAction(() => closeLegalTicket(id, { feedback: t("legalTickets.detail.closeFeedback") }), t("legalTickets.detail.closeSuccess"))}>
                  {t("legalTickets.detail.close")}
                </Button>}
                {canReopen && <Button disabled={saving} onClick={() => void runAction(() => reopenLegalTicket(id, { reason: t("legalTickets.detail.reopenReason") }), t("legalTickets.detail.reopenSuccess"))}>
                  {t("legalTickets.detail.reopen")}
                </Button>}
              </div>
            </Card>
          </aside>
        </div>
      )}
    </div>
  );
}
