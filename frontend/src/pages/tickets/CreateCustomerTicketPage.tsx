import { ArrowLeft, RefreshCw, Send } from "lucide-react";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createLegalTicket } from "../../api/legalTicketApi";
import {
  getWorkspaceDocuments,
  getWorkspaces,
  type Document,
  type Workspace,
} from "../../api/workspaceApi";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { LegalTicketType } from "../../types/legalTicket";
import { ApiRequestError, isPlanEntitlementError } from "../../services/http";

import { getAccessToken as getSessionAccessToken } from "../../services/authSession";
const getAccessToken = () => getSessionAccessToken() ?? "";

export function CreateCustomerTicketPage() {
  const { t } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [documents, setDocuments] = useState<Document[]>([]);

  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState("");
  const [selectedDocumentId, setSelectedDocumentId] = useState("");
  const [issueFingerprint, setIssueFingerprint] = useState("");
  const [customerNote, setCustomerNote] = useState("");
  const [question, setQuestion] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [legalIssueCategory, setLegalIssueCategory] = useState("");
  const [contractType, setContractType] = useState("");
  const [expectedOutcome, setExpectedOutcome] = useState("");
  const [shareDisplayName, setShareDisplayName] = useState(true);
  const [shareEmail, setShareEmail] = useState(false);
  const [consentGranted, setConsentGranted] = useState(false);
  const [ticketType, setTicketType] = useState<LegalTicketType>("CONTACT_EXPERT");

  const [loadingWorkspaces, setLoadingWorkspaces] = useState(true);
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const loadWorkspaces = useCallback(async () => {
    setLoadingWorkspaces(true);

    try {
      const accessToken = getAccessToken();

      if (!accessToken) {
        toast.error(t("auth.errors.sessionExpired"), t("toast.errorTitle"));
        setWorkspaces([]);
        return;
      }

      const data = await getWorkspaces(accessToken);
      setWorkspaces(data);
    } catch (error) {
      console.error("Failed to load workspaces", error);
      toast.error(
        t("legalTickets.create.loadWorkspacesError"),
        t("toast.errorTitle"),
      );
      setWorkspaces([]);
    } finally {
      setLoadingWorkspaces(false);
    }
  }, [t, toast]);

  const loadDocuments = useCallback(
    async (workspaceId: string) => {
      if (!workspaceId) {
        setDocuments([]);
        setSelectedDocumentId("");
        return;
      }

      setLoadingDocuments(true);

      try {
        const accessToken = getAccessToken();

        if (!accessToken) {
          toast.error(t("auth.errors.sessionExpired"), t("toast.errorTitle"));
          setDocuments([]);
          setSelectedDocumentId("");
          return;
        }

        const data = await getWorkspaceDocuments(accessToken, workspaceId);
        setDocuments(data);
        setSelectedDocumentId("");
      } catch (error) {
        console.error("Failed to load workspace documents", error);
        toast.error(
          t("legalTickets.create.loadDocumentsError"),
          t("toast.errorTitle"),
        );
        setDocuments([]);
      } finally {
        setLoadingDocuments(false);
      }
    },
    [t, toast],
  );

  useEffect(() => {
    void loadWorkspaces();
  }, [loadWorkspaces]);

  useEffect(() => {
    void loadDocuments(selectedWorkspaceId);
  }, [selectedWorkspaceId, loadDocuments]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (
      !selectedWorkspaceId ||
      !question.trim() ||
      !issueFingerprint.trim() ||
      (ticketType === "CONTACT_EXPERT" && (!title.trim() || !description.trim() || !legalIssueCategory.trim() || !expectedOutcome.trim() || !consentGranted))
    ) {
      toast.error(
        t("legalTickets.create.validationError"),
        t("toast.errorTitle"),
      );
      return;
    }

    setSubmitting(true);

    try {
      const ticket = await createLegalTicket({
        creationSource: "MANUAL_FORM",
        ticket_type: ticketType,
        request_id: null,
        workspace_id: selectedWorkspaceId,
        document_id: selectedDocumentId || null,
        documentIds: selectedDocumentId ? [selectedDocumentId] : [],
        question: question.trim(),
        issue_fingerprint: issueFingerprint.trim(),
        customer_note: customerNote.trim() || null,
        title: title.trim(),
        description: description.trim(),
        legalIssueCategory: legalIssueCategory.trim(),
        contractType: contractType.trim() || null,
        userExpectedOutcome: expectedOutcome.trim(),
        sharedProfileFields: [shareDisplayName ? "DISPLAY_NAME" : null, shareEmail ? "EMAIL" : null].filter((value): value is "DISPLAY_NAME" | "EMAIL" => value !== null),
        consentGranted,
      });

      toast.success(t("legalTickets.create.success"), t("toast.successTitle"));
      navigate(`/tickets/${ticket.id}`);
    } catch (error) {
      console.error("Failed to create legal ticket", error);
      if (isPlanEntitlementError(error)) {
        toast.warning(t("legalTickets.planRequired"), t("toast.warningTitle"));
        navigate("/billing/subscribe?reason=plan-required");
        return;
      }
      if (error instanceof ApiRequestError && error.details?.data && typeof error.details.data === "object") {
        toast.error(Object.values(error.details.data as Record<string, string>).join(" · "), t("toast.errorTitle"));
      } else {
        toast.error(error instanceof Error ? error.message : t("legalTickets.create.submitError"), t("toast.errorTitle"));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title={t("legalTickets.create.title")}
        subtitle={t("legalTickets.create.subtitle")}
        actions={
          <Link to="/tickets">
            <Button
              variant="secondary"
              leftIcon={<ArrowLeft className="h-4 w-4" />}
            >
              {t("actions.back")}
            </Button>
          </Link>
        }
      />

      <Card title={t("legalTickets.create.formTitle")}>
        <form className="space-y-lg" onSubmit={handleSubmit}>
          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-type">{t("legalTickets.typeLabel")}</label>
            <select id="create-ticket-type" className="form-field" value={ticketType} onChange={(event) => setTicketType(event.target.value as LegalTicketType)} disabled={submitting}>
              <option value="CONTACT_EXPERT">{t("legalTickets.type.CONTACT_EXPERT")}</option>
              <option value="QUERY_ERROR">{t("legalTickets.type.QUERY_ERROR")}</option>
              <option value="SYSTEM_ERROR">{t("legalTickets.type.SYSTEM_ERROR")}</option>
            </select>
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-title">Tiêu đề *</label>
            <input id="create-ticket-title" className="form-field" maxLength={255} value={title} onChange={(event) => setTitle(event.target.value)} disabled={submitting} required={ticketType === "CONTACT_EXPERT"} />
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-description">Mô tả chi tiết *</label>
            <textarea id="create-ticket-description" className="form-field min-h-24" maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} disabled={submitting} required={ticketType === "CONTACT_EXPERT"} />
          </div>
          <div className="grid gap-md sm:grid-cols-2">
            <div><label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-category">Nhóm vấn đề pháp lý *</label><input id="create-ticket-category" className="form-field" maxLength={255} value={legalIssueCategory} onChange={(event) => setLegalIssueCategory(event.target.value)} disabled={submitting} required={ticketType === "CONTACT_EXPERT"} /></div>
            <div><label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-contract">Loại hợp đồng</label><input id="create-ticket-contract" className="form-field" maxLength={255} value={contractType} onChange={(event) => setContractType(event.target.value)} disabled={submitting} /></div>
          </div>
          <div><label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-outcome">Kết quả mong muốn *</label><textarea id="create-ticket-outcome" className="form-field min-h-20" maxLength={5000} value={expectedOutcome} onChange={(event) => setExpectedOutcome(event.target.value)} disabled={submitting} required={ticketType === "CONTACT_EXPERT"} /></div>
          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-workspace">
              {t("legalTickets.create.workspace")}
            </label>
            <select
              id="create-ticket-workspace"
              className="form-field"
              value={selectedWorkspaceId}
              onChange={(event) => setSelectedWorkspaceId(event.target.value)}
              disabled={loadingWorkspaces || submitting}
            >
              <option value="">
                {loadingWorkspaces
                  ? t("legalTickets.create.loadingWorkspaces")
                  : t("legalTickets.create.selectWorkspace")}
              </option>

              {workspaces.map((workspace) => (
                <option
                  key={workspace.workspaceId}
                  value={workspace.workspaceId}
                >
                  {workspace.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-document">
              {t("legalTickets.create.document")}
            </label>
            <select
              id="create-ticket-document"
              className="form-field"
              value={selectedDocumentId}
              onChange={(event) => setSelectedDocumentId(event.target.value)}
              disabled={!selectedWorkspaceId || loadingDocuments || submitting}
              required
            >
              <option value="">
                {loadingDocuments
                  ? t("legalTickets.create.loadingDocuments")
                  : t("legalTickets.create.selectDocument")}
              </option>

              {documents.map((document) => (
                <option key={document.documentId} value={document.documentId}>
                  {document.originalFileName}
                </option>
              ))}
            </select>
          </div>

          {ticketType === "CONTACT_EXPERT" && <div className="rounded-lg border border-legal-border p-md">
            <p className="font-semibold">Dữ liệu chia sẻ với chuyên gia</p>
            <p className="mt-xs text-sm text-on-surface-variant">Tài liệu: {selectedDocumentId ? documents.find((item) => item.documentId === selectedDocumentId)?.originalFileName ?? selectedDocumentId : "Không chia sẻ tài liệu"}</p>
            <div className="mt-sm flex gap-md text-sm"><label><input type="checkbox" checked={shareDisplayName} onChange={(event) => setShareDisplayName(event.target.checked)} /> Tên hiển thị</label><label><input type="checkbox" checked={shareEmail} onChange={(event) => setShareEmail(event.target.checked)} /> Email</label></div>
            <label className="mt-sm flex items-start gap-sm text-sm"><input className="mt-1" type="checkbox" checked={consentGranted} onChange={(event) => setConsentGranted(event.target.checked)} /><span>Tôi đồng ý chia sẻ đúng các dữ liệu được liệt kê phía trên.</span></label>
          </div>}
          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-question">
              {t("legalTickets.create.question")}
            </label>

            <textarea
              id="create-ticket-question"
              className="form-field min-h-24"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder={t("legalTickets.create.questionPlaceholder")}
              disabled={submitting}
              required
            />
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-fingerprint">
              {t("legalTickets.create.issueFingerprint")}
            </label>
            <input
              id="create-ticket-fingerprint"
              className="form-field"
              value={issueFingerprint}
              onChange={(event) => setIssueFingerprint(event.target.value)}
              placeholder={t("legalTickets.create.issueFingerprintPlaceholder")}
              disabled={submitting}
              required
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium" htmlFor="create-ticket-note">
              {t("legalTickets.create.customerNote")}
            </label>
            <textarea
              id="create-ticket-note"
              className="form-field min-h-32"
              value={customerNote}
              onChange={(event) => setCustomerNote(event.target.value)}
              placeholder={t("legalTickets.create.customerNotePlaceholder")}
              disabled={submitting}
            />
          </div>

          <div className="flex justify-end gap-md">
            <Button
              type="button"
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadWorkspaces()}
              disabled={loadingWorkspaces || submitting}
            >
              {t("common.refresh")}
            </Button>

            <Button
              type="submit"
              leftIcon={<Send className="h-4 w-4" />}
              disabled={submitting}
            >
              {submitting
                ? t("legalTickets.create.submitting")
                : t("legalTickets.create.submit")}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
