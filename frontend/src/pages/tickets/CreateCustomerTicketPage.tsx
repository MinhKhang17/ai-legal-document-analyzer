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
      !selectedDocumentId ||
      !question.trim() ||
      !issueFingerprint.trim()
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
        ticket_type: ticketType,
        request_id: null,
        workspace_id: selectedWorkspaceId,
        document_id: selectedDocumentId,
        question: question.trim(),
        issue_fingerprint: issueFingerprint.trim(),
        customer_note: customerNote.trim() || null,
      });

      toast.success(t("legalTickets.create.success"), t("toast.successTitle"));
      navigate(`/tickets/${ticket.id}`);
    } catch (error) {
      console.error("Failed to create legal ticket", error);
      toast.error(t("legalTickets.create.submitError"), t("toast.errorTitle"));
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
              {t("common.back")}
            </Button>
          </Link>
        }
      />

      <Card title={t("legalTickets.create.formTitle")}>
        <form className="space-y-lg" onSubmit={handleSubmit}>
          <div>
            <label className="mb-2 block text-sm font-medium">Lý do tạo ticket</label>
            <select className="form-field" value={ticketType} onChange={(event) => setTicketType(event.target.value as LegalTicketType)} disabled={submitting}>
              <option value="CONTACT_EXPERT">Liên hệ chuyên gia / luật sư</option>
              <option value="QUERY_ERROR">Câu trả lời AI sai hoặc thiếu</option>
              <option value="SYSTEM_ERROR">Lỗi hệ thống / kỹ thuật</option>
            </select>
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium">
              {t("legalTickets.create.workspace")}
            </label>
            <select
              className="form-field"
              value={selectedWorkspaceId}
              onChange={(event) => setSelectedWorkspaceId(event.target.value)}
              disabled={loadingWorkspaces || submitting}
              required
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
            <label className="mb-2 block text-sm font-medium">
              {t("legalTickets.create.document")}
            </label>
            <select
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
          <div>
            <label className="mb-2 block text-sm font-medium">
              {t("legalTickets.create.question")}
            </label>

            <textarea
              className="form-field min-h-24"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder={t("legalTickets.create.questionPlaceholder")}
              disabled={submitting}
              required
            />
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium">
              {t("legalTickets.create.issueFingerprint")}
            </label>
            <input
              className="form-field"
              value={issueFingerprint}
              onChange={(event) => setIssueFingerprint(event.target.value)}
              placeholder={t("legalTickets.create.issueFingerprintPlaceholder")}
              disabled={submitting}
              required
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium">
              {t("legalTickets.create.customerNote")}
            </label>
            <textarea
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
