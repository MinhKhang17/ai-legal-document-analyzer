import { ArrowLeft, Download, RefreshCw, Send, UserCheck, XCircle } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import {
  assignLawyerToLegalTicket,
  getAdminLegalTicket,
  getAdminTicketChatHistory,
  getAdminTicketFiles,
  getAdminTicketSummary,
  reassignLawyerToLegalTicket,
  rejectLegalTicket,
  approveLegalTicketInternal,
  closeLegalTicketInternal,
  downloadStaffDocument,
} from "../../services/legalTicket.service";
import {
  getTicketAiAssessment,
  getTicketAiCitations,
  getTicketAiSummary,
} from "../../services/aiFeature.service";
import { getAdminExperts, type BackendUser } from "../../services/user.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type {
  AdminTicketFile,
  LegalTicket,
  LegalTicketMessage,
  TicketSummary,
} from "../../types/legalTicket";
import type {
  AiCitation,
  AiFeatureSummary,
  AiRiskAssessment,
} from "../../types/aiFeature";
import { getLegalTicketStatusLabel } from "../../types/legalTicketStatus";
import { formatDisplayDate } from "../../utils/format";

const getRiskTone = (risk?: string | null) => {
  if (risk === "HIGH") return "red";
  if (risk === "MEDIUM") return "amber";
  if (risk === "LOW") return "green";
  return "slate";
};

export function AdminTicketDetailPage() {
  const { ticketId = "" } = useParams();
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [ticket, setTicket] = useState<LegalTicket | null>(null);
  const [ticketSummary, setTicketSummary] = useState<TicketSummary | null>(null);
  const [chatMessages, setChatMessages] = useState<LegalTicketMessage[]>([]);
  const [files, setFiles] = useState<AdminTicketFile[]>([]);
  const [assessment, setAssessment] = useState<AiRiskAssessment | null>(null);
  const [aiSummary, setAiSummary] = useState<AiFeatureSummary | null>(null);
  const [citations, setCitations] = useState<AiCitation[]>([]);
  const [experts, setExperts] = useState<BackendUser[]>([]);
  const [selectedLawyerId, setSelectedLawyerId] = useState("");
  const [adminNote, setAdminNote] = useState("");
  const [rejectReason, setRejectReason] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [auxiliaryError, setAuxiliaryError] = useState("");

  const loadTicket = useCallback(async () => {
    if (!ticketId) return;

    setLoading(true);
    setError("");
    setAuxiliaryError("");

    try {
      const loadedTicket = await getAdminLegalTicket(ticketId);
      setTicket(loadedTicket);
      setSelectedLawyerId(
        loadedTicket.assigned_lawyer_id
          ? String(loadedTicket.assigned_lawyer_id)
          : "",
      );

      const [
        summaryResult,
        chatHistoryResult,
        filesResult,
        assessmentResult,
        aiSummaryResult,
        citationsResult,
        usersResult,
      ] = await Promise.allSettled([
        getAdminTicketSummary(ticketId),
        getAdminTicketChatHistory(ticketId),
        getAdminTicketFiles(ticketId),
        getTicketAiAssessment(ticketId),
        getTicketAiSummary(ticketId),
        getTicketAiCitations(ticketId),
        getAdminExperts(),
      ]);

      setTicketSummary(summaryResult.status === "fulfilled" ? summaryResult.value : null);
      setChatMessages(
        chatHistoryResult.status === "fulfilled" ? chatHistoryResult.value.messages ?? [] : [],
      );
      setFiles(filesResult.status === "fulfilled" ? filesResult.value : []);
      setAssessment(assessmentResult.status === "fulfilled" ? assessmentResult.value : null);
      setAiSummary(aiSummaryResult.status === "fulfilled" ? aiSummaryResult.value : null);
      setCitations(citationsResult.status === "fulfilled" ? citationsResult.value : []);
      setExperts(
        usersResult.status === "fulfilled"
          ? usersResult.value
          : [],
      );
      if ([summaryResult, chatHistoryResult, filesResult, assessmentResult, aiSummaryResult, citationsResult, usersResult].some((result) => result.status === "rejected")) {
        setAuxiliaryError(t("common.partialDataError"));
      }
    } catch (ticketError) {
      setTicket(null);
      setTicketSummary(null);
      setChatMessages([]);
      setFiles([]);
      setAssessment(null);
      setAiSummary(null);
      setCitations([]);
      setExperts([]);
      setSelectedLawyerId("");
      setError(ticketError instanceof Error ? ticketError.message : t("adminTickets.loadError"));
    } finally {
      setLoading(false);
    }
  }, [ticketId, t]);

  useEffect(() => {
    void loadTicket();
  }, [loadTicket]);

  const hasAssignedLawyer = Boolean(ticket?.assigned_lawyer_id);

  const selectedExpertLabel = useMemo(() => {
    const expert = experts.find((user) => String(user.id) === selectedLawyerId);
    return expert ? `${expert.firstName} ${expert.lastName}`.trim() || expert.email : "";
  }, [experts, selectedLawyerId]);

  const handleAssign = async (forceReassign: boolean) => {
    if (!ticketId || !selectedLawyerId) {
      toast.warning(t("adminTickets.selectExpertFirst"));
      return;
    }

    setSaving(true);
    setError("");

    try {
      const payload = {
        lawyer_id: Number(selectedLawyerId),
        admin_note: adminNote.trim() || null,
        force_reassign: forceReassign,
      };
      const updatedTicket = forceReassign
        ? await reassignLawyerToLegalTicket(ticketId, payload)
        : await assignLawyerToLegalTicket(ticketId, payload);
      setTicket(updatedTicket);
      toast.success(
        t("adminTickets.assignSuccess")
          .replace("{action}", forceReassign ? t("adminTickets.reassignAction") : t("adminTickets.assignAction"))
          .replace("{expert}", selectedExpertLabel || t("adminTickets.expert")),
      );
    } catch (actionError) {
      const message = actionError instanceof Error ? actionError.message : t("adminTickets.assignError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const handleReject = async () => {
    const reason = rejectReason.trim();

    if (!ticketId || !reason) {
      toast.warning(t("adminTickets.rejectReasonRequired"));
      return;
    }

    setSaving(true);
    setError("");

    try {
      const updatedTicket = await rejectLegalTicket(ticketId, { reason });
      setTicket(updatedTicket);
      toast.success(t("adminTickets.rejectSuccess"));
    } catch (actionError) {
      const message = actionError instanceof Error ? actionError.message : t("adminTickets.rejectError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const handleInternalAction = async (action: "approve" | "close") => {
    if (!ticketId) return;
    setSaving(true);
    setError("");
    try {
      const updated = action === "approve"
        ? await approveLegalTicketInternal(ticketId)
        : await closeLegalTicketInternal(ticketId, adminNote.trim() || undefined);
      setTicket(updated);
      toast.success(action === "approve" ? "Đã tiếp nhận xử lý nội bộ." : "Đã đóng ticket.");
    } catch (actionError) {
      const message = actionError instanceof Error ? actionError.message : "Không thể cập nhật ticket";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const messageColumns: DataTableColumn<LegalTicketMessage>[] = [
    { header: t("table.user"), cell: (message) => message.sender_name || message.sender_role },
    { header: t("chat.role"), cell: (message) => message.sender_role },
    { header: t("legalTickets.detail.messages"), cell: (message) => <span className="line-clamp-3">{message.content}</span> },
    { header: t("legalTickets.table.created"), cell: (message) => formatDisplayDate(message.created_at, "-", locale) },
  ];

  const fileColumns: DataTableColumn<AdminTicketFile>[] = [
    { header: t("legalTickets.table.document"), cell: (file) => <span className="font-semibold">{file.originalFileName}</span> },
    { header: t("documents.type"), cell: (file) => file.fileType },
    { header: t("knowledge.scope"), cell: (file) => file.visibilityScope },
    { header: t("documents.uploadedAt"), cell: (file) => formatDisplayDate(file.uploadedAt, "-", locale) },
    { header: t("table.actions"), cell: (file) => <Button size="sm" variant="secondary" leftIcon={<Download className="h-4 w-4" />} onClick={async () => { try { const url = await downloadStaffDocument(file.documentId); const anchor = document.createElement('a'); anchor.href = url; anchor.download = file.originalFileName; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000); } catch (downloadError) { toast.error(downloadError instanceof Error ? downloadError.message : 'Unable to download document.'); } }}>{language === 'vi' ? 'Tải xuống' : 'Download'}</Button> },
  ];

  return (
    <div>
      <PageHeader
        title={t("adminTickets.detailTitle")}
        subtitle={ticketId}
        actions={
          <>
            <Link to="/admin/tickets">
              <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
                {t("adminTickets.ticketList")}
              </Button>
            </Link>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadTicket()}
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
      {ticket && auxiliaryError && (
        <div className="mb-lg rounded-xl border border-amber-300 bg-amber-50 p-md text-sm text-amber-900" role="alert">
          {auxiliaryError} <Button variant="secondary" onClick={() => void loadTicket()}>{t("common.retry")}</Button>
        </div>
      )}

      {loading ? (
        <Card>{t("adminTickets.loading")}</Card>
      ) : !ticket ? (
        <div className="space-y-md">
          <EmptyState
            title={t("adminTickets.emptyTitle")}
            description={t("adminTickets.emptyDescription")}
          />
          <Link to="/admin/tickets">
            <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
              {t("adminTickets.backToList")}
            </Button>
          </Link>
        </div>
      ) : (
        <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_380px]">
          <main className="space-y-gutter">
            <Card
              title={ticket.issue_title || ticket.question || ticket.id}
              subtitle={ticket.issue_summary || ticket.customer_note || t("adminTickets.summaryFallback")}
              actions={
                <div className="flex flex-wrap gap-xs">
                  <Badge>{getLegalTicketStatusLabel(ticket.status, t)}</Badge>
                  <Badge tone={getRiskTone(ticket.risk_level)}>{ticket.risk_level || t("common.none")}</Badge>
                </div>
              }
            >
              <dl className="grid gap-md text-sm md:grid-cols-2">
                <div><dt className="label-uppercase">{t("legalTickets.table.customer")}</dt><dd className="mt-xs">{ticket.created_by_name || "-"}</dd></div>
                <div><dt className="label-uppercase">{language === "vi" ? "Loại ticket" : "Ticket type"}</dt><dd className="mt-xs"><Badge tone="blue">{ticket.ticket_type ?? "CONTACT_EXPERT"}</Badge></dd></div>
                <div><dt className="label-uppercase">{t("contracts.workspace")}</dt><dd className="mt-xs">{ticket.workspace_name || ticket.workspace_id || "-"}</dd></div>
                <div><dt className="label-uppercase">{t("legalTickets.table.document")}</dt><dd className="mt-xs">{ticket.document_name || ticket.document_id || "-"}</dd></div>
                <div><dt className="label-uppercase">{t("adminTickets.assignedExpert")}</dt><dd className="mt-xs">{ticket.assigned_lawyer_name || "-"}</dd></div>
                <div><dt className="label-uppercase">{t("legalTickets.table.created")}</dt><dd className="mt-xs">{formatDisplayDate(ticket.created_at, "-", locale)}</dd></div>
                <div><dt className="label-uppercase">{t("table.updated")}</dt><dd className="mt-xs">{formatDisplayDate(ticket.updated_at, "-", locale)}</dd></div>
              </dl>
            </Card>

            <section className="grid gap-gutter lg:grid-cols-2">
              <Card title={t("adminTickets.aiSummary")}>
                <p className="whitespace-pre-line text-sm leading-6 text-on-surface-variant dark:text-slate-400">
                  {aiSummary?.summary || ticketSummary?.summary || ticket.answer || t("adminTickets.noSummary")}
                </p>
              </Card>
              <Card title={t("adminTickets.aiAssessment")}>
                <div className="space-y-sm text-sm">
                  <p><strong>{t("adminTickets.risk")}:</strong> {assessment?.riskLevel || ticketSummary?.riskLevel || ticket.risk_level || "-"}</p>
                  <p><strong>{t("adminTickets.confidence")}:</strong> {typeof assessment?.confidenceScore === "number" ? `${Math.round(assessment.confidenceScore * 100)}%` : "-"}</p>
                  <p><strong>{t("adminTickets.suggestion")}:</strong> {assessment?.suggestionReason || ticket.suggestion_reason || "-"}</p>
                </div>
              </Card>
            </section>

            <Card title={t("adminTickets.chatHistory")}>
              <DataTable
                columns={messageColumns}
                data={chatMessages}
                getRowKey={(message) => message.id}
                emptyMessage={t("adminTickets.noChatHistory")}
              />
            </Card>

            <Card title={t("adminTickets.files")}>
              <DataTable
                columns={fileColumns}
                data={files}
                getRowKey={(file) => file.documentId}
                emptyMessage={t("adminTickets.noFiles")}
              />
            </Card>

            <Card title={t("chat.aiCitations")}>
              {citations.length === 0 ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("adminTickets.noCitations")}</p>
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
          </main>

          <aside className="space-y-gutter">
            <Card title={t("adminTickets.assignmentControls")}>
              <div className="space-y-md">
                <label className="block text-sm font-semibold">
                  {t("adminTickets.expert")}
                  <select
                    className="form-field mt-xs"
                    value={selectedLawyerId}
                    onChange={(event) => setSelectedLawyerId(event.target.value)}
                  >
                    <option value="">{t("admin.selectExpert")}</option>
                    {experts.map((expert) => (
                      <option key={expert.id} value={expert.id}>
                        {`${expert.firstName} ${expert.lastName}`.trim() || expert.email} — {expert.specialty || '-'} / {expert.legalDomain || '-'}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block text-sm font-semibold">
                  {t("adminTickets.adminNote")}
                  <textarea
                    className="form-field mt-xs min-h-24"
                    value={adminNote}
                    onChange={(event) => setAdminNote(event.target.value)}
                  />
                </label>
                <div className="flex flex-wrap gap-sm">
                  {ticket.ticket_type !== "CONTACT_EXPERT" && <Button variant="secondary" onClick={() => void handleInternalAction("approve")} disabled={saving || ticket.status !== "PENDING_ADMIN_REVIEW"}>{language === "vi" ? "Tiếp nhận nội bộ" : "Review internally"}</Button>}
                  <Button variant="secondary" onClick={() => void handleInternalAction("close")} disabled={saving || ticket.status === "CLOSED" || ticket.status === "CANCELLED"}>{language === "vi" ? "Đóng ticket" : "Close ticket"}</Button>
                  <Button
                    leftIcon={<UserCheck className="h-4 w-4" />}
                    onClick={() => void handleAssign(false)}
                    disabled={saving || hasAssignedLawyer}
                  >
                    {t("adminTickets.assign")}
                  </Button>
                  <Button
                    variant="secondary"
                    leftIcon={<Send className="h-4 w-4" />}
                    onClick={() => void handleAssign(true)}
                    disabled={saving || !hasAssignedLawyer}
                  >
                    {t("adminTickets.reassign")}
                  </Button>
                </div>
              </div>
            </Card>

            <Card title={t("adminTickets.rejectTicket")}>
              <div className="space-y-md">
                <textarea
                  className="form-field min-h-28"
                  value={rejectReason}
                  onChange={(event) => setRejectReason(event.target.value)}
                  placeholder={t("adminTickets.rejectReason")}
                />
                <Button
                  variant="danger"
                  leftIcon={<XCircle className="h-4 w-4" />}
                  onClick={() => void handleReject()}
                  disabled={saving || !rejectReason.trim()}
                >
                  {t("adminTickets.reject")}
                </Button>
              </div>
            </Card>
          </aside>
        </div>
      )}
    </div>
  );
}
