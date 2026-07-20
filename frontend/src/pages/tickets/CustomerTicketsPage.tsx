import { RefreshCw, TicketCheck, Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/common/Pagination";
import { parsePageParam, toPageParam } from "../../utils/pagination";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import {
  DataTable,
  type DataTableColumn,
} from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { createLegalTicket, getMyLegalTickets } from "../../services/legalTicket.service";
import { getWorkspaceDocuments, getWorkspaces } from "../../services/workspace.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { LegalTicket, LegalTicketType } from "../../types/legalTicket";
import type { LegalTicketFilter } from "../../types/legalTicketStatus";
import type { Document, Workspace } from "../../types/workspace";
import {
  getLegalTicketFilterLabel,
  getLegalTicketFilterOptions,
  getLegalTicketStatusLabel,
  isTerminalLegalTicketStatus,
  normalizeLegalTicketStatus,
  toLegalTicketFilter,
} from "../../types/legalTicketStatus";
import { formatDisplayDate } from "../../utils/format";

const getRiskTone = (risk?: string | null) => {
  if (risk === "HIGH") return "red";
  if (risk === "MEDIUM") return "amber";
  if (risk === "LOW") return "green";
  return "slate";
};

import { getAccessToken as getSessionAccessToken } from "../../services/authSession";
const getAccessToken = () => getSessionAccessToken() ?? "";

const getStatusTone = (status?: string | null) => {
  switch (normalizeLegalTicketStatus(status)) {
    case "RESOLVED":
      return "green";
    case "CLOSED":
    case "CANCELLED":
    case "REJECTED_BY_ADMIN":
      return "slate";
    case "IN_REVIEW":
    case "ASSIGNED_TO_LAWYER":
    case "CUSTOMER_RESPONDED":
      return "blue";
    case "NEED_MORE_INFO":
      return "amber";
    default:
      return "slate";
  }
};

export function CustomerTicketsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [tickets, setTickets] = useState<LegalTicket[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(() => parsePageParam(searchParams.get("page")));
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<LegalTicketFilter>(() => toLegalTicketFilter(searchParams.get("status") ?? "ALL"));
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [workspaceLoading, setWorkspaceLoading] = useState(false);
  const [documentLoading, setDocumentLoading] = useState(false);
  const [ticketWorkspaceId, setTicketWorkspaceId] = useState("");
  const [ticketDocumentId, setTicketDocumentId] = useState("");
  const [ticketQuestion, setTicketQuestion] = useState("");
  const [ticketType, setTicketType] = useState<LegalTicketType>("CONTACT_EXPERT");
  const [ticketSubmitting, setTicketSubmitting] = useState(false);
  const filterOptions = useMemo(() => getLegalTicketFilterOptions(t), [t]);

  const loadTickets = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const response = await getMyLegalTickets(
        page,
        20,
        statusFilter === "ALL" ? undefined : statusFilter,
      );
      setTickets(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch (error) {
      console.error("Failed to load customer legal tickets", error);
      toast.error(t("legalTickets.errors.load"), t("toast.errorTitle"));
      setError(t("legalTickets.errors.load"));
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, t, toast]);

  useEffect(() => {
    void loadTickets();
  }, [loadTickets]);

  useEffect(() => {
    let active = true;

    const loadWorkspaces = async () => {
      setWorkspaceLoading(true);

      try {
        const data = await getWorkspaces(getAccessToken());
        if (!active) return;

        setWorkspaces(data);
        const firstWorkspaceId = data[0]?.workspaceId ?? "";
        setTicketWorkspaceId((previous) => previous || firstWorkspaceId);
      } catch (error) {
        console.error("Failed to load workspaces for ticket creation", error);
      } finally {
        if (active) {
          setWorkspaceLoading(false);
        }
      }
    };

    void loadWorkspaces();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!ticketWorkspaceId) {
      setDocuments([]);
      setTicketDocumentId("");
      return;
    }

    let active = true;

    const loadDocuments = async () => {
      setDocumentLoading(true);

      try {
        const data = await getWorkspaceDocuments(getAccessToken(), ticketWorkspaceId);
        if (!active) return;

        setDocuments(data);
        setTicketDocumentId((previous) => {
          if (previous && data.some((document) => document.documentId === previous)) {
            return previous;
          }
          return data[0]?.documentId ?? "";
        });
      } catch (error) {
        console.error("Failed to load workspace documents for ticket creation", error);
        if (active) {
          setDocuments([]);
          setTicketDocumentId("");
        }
      } finally {
        if (active) {
          setDocumentLoading(false);
        }
      }
    };

    void loadDocuments();

    return () => {
      active = false;
    };
  }, [ticketWorkspaceId]);

  const openCount = useMemo(
    () =>
      tickets.filter((ticket) => !isTerminalLegalTicketStatus(ticket.status))
        .length,
    [tickets],
  );

  const handleCreateTicket = async () => {
    const question = ticketQuestion.trim();

    if (!ticketWorkspaceId) {
      toast.warning(t("legalTickets.createWorkspaceRequired"), t("toast.warningTitle"));
      return;
    }

    if (!question) {
      toast.warning(t("legalTickets.createQuestionRequired"), t("toast.warningTitle"));
      return;
    }

    setTicketSubmitting(true);

    try {
      const ticket = await createLegalTicket({
        ticket_type: ticketType,
        workspace_id: ticketWorkspaceId,
        document_id: ticketDocumentId || null,
        question,
        customer_note: question,
      });

      setTicketQuestion("");
      setTicketDocumentId("");
      await loadTickets();
      toast.success(t("legalTickets.createSuccess"), t("toast.successTitle"));
      navigate(`/tickets/${ticket.id}`);
    } catch (error) {
      console.error("Failed to create legal ticket", error);
      toast.error(t("legalTickets.createError"), t("toast.errorTitle"));
    } finally {
      setTicketSubmitting(false);
    }
  };

  const columns: DataTableColumn<LegalTicket>[] = [
    {
      header: t("legalTickets.table.ticket"),
      cell: (ticket) => (
        <Link
          className="font-semibold text-primary hover:underline dark:text-inverse-primary"
          to={`/tickets/${ticket.id}`}
        >
          {ticket.issue_title || ticket.question || ticket.id}
        </Link>
      ),
    },
    {
      header: t("table.status"),
      cell: (ticket) => (
        <Badge tone={getStatusTone(ticket.status)}>
          {getLegalTicketStatusLabel(ticket.status, t)}
        </Badge>
      ),
    },
    {
      header: t("table.risk"),
      cell: (ticket) => (
        <Badge tone={getRiskTone(ticket.risk_level)}>
          {ticket.risk_level
            ? t(`risk.${ticket.risk_level.toLowerCase()}`)
            : t("risk.none")}
        </Badge>
      ),
    },
    {
      header: t("legalTickets.table.expert"),
      cell: (ticket) => ticket.assigned_lawyer_name || "-",
    },
    {
      header: t("legalTickets.table.created"),
      cell: (ticket) =>
        formatDisplayDate(
          ticket.created_at,
          "-",
          language === "vi" ? "vi-VN" : "en-US",
        ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t("legalTickets.title")}
        subtitle={t("legalTickets.subtitle")}
        actions={
          <>
            <Link to="/tickets/create">
              <Button leftIcon={<Plus className="h-4 w-4" />}>
                {t("legalTickets.createButton")}
              </Button>
            </Link>

            <select
              className="form-field max-w-48"
              value={statusFilter}
              onChange={(event) => {
                const nextStatus = toLegalTicketFilter(event.target.value);
                setStatusFilter(nextStatus); setPage(0);
                const next = new URLSearchParams(searchParams); next.set("page", "1");
                nextStatus === "ALL" ? next.delete("status") : next.set("status", nextStatus);
                setSearchParams(next);
              }}
            >
              {filterOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadTickets()}
              disabled={loading}
            >
              {t("common.refresh")}
            </Button>
          </>
        }
      />

      <Card title={t("legalTickets.createTitle")} subtitle={t("legalTickets.createSubtitle")} className="mb-xl">
        <div className="grid gap-md lg:grid-cols-3">
          <div className="space-y-sm">
            <label className="label-uppercase" htmlFor="ticket-type">{language === "vi" ? "Lý do" : "Reason"}</label>
            <select id="ticket-type" className="form-field" value={ticketType} onChange={(event) => setTicketType(event.target.value as LegalTicketType)}>
              <option value="CONTACT_EXPERT">{language === "vi" ? "Liên hệ chuyên gia" : "Contact expert"}</option>
              <option value="QUERY_ERROR">{language === "vi" ? "Câu trả lời AI sai/thiếu" : "AI answer issue"}</option>
              <option value="SYSTEM_ERROR">{language === "vi" ? "Lỗi hệ thống" : "System error"}</option>
            </select>
          </div>
          <div className="space-y-sm">
            <label className="label-uppercase" htmlFor="ticket-workspace">
              {t("legalTickets.createWorkspace")}
            </label>
            <select
              id="ticket-workspace"
              className="form-field"
              value={ticketWorkspaceId}
              onChange={(event) => setTicketWorkspaceId(event.target.value)}
              disabled={workspaceLoading || workspaces.length === 0}
            >
              <option value="">{workspaceLoading ? t("common.loading") : t("legalTickets.createSelectWorkspace")}</option>
              {workspaces.map((workspace) => (
                <option key={workspace.workspaceId} value={workspace.workspaceId}>
                  {workspace.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-sm">
            <label className="label-uppercase" htmlFor="ticket-document">
              {t("legalTickets.createDocument")}
            </label>
            <select
              id="ticket-document"
              className="form-field"
              value={ticketDocumentId}
              onChange={(event) => setTicketDocumentId(event.target.value)}
              disabled={!ticketWorkspaceId || documentLoading}
            >
              <option value="">{documentLoading ? t("common.loading") : t("legalTickets.createSelectDocument")}</option>
              {documents.map((document) => (
                <option key={document.documentId} value={document.documentId}>
                  {document.originalFileName}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="mt-md space-y-sm">
          <label className="label-uppercase" htmlFor="ticket-question">
            {t("legalTickets.createQuestion")}
          </label>
          <textarea
            id="ticket-question"
            className="form-field min-h-32"
            value={ticketQuestion}
            onChange={(event) => setTicketQuestion(event.target.value)}
            placeholder={t("legalTickets.createQuestionPlaceholder")}
          />
        </div>

        <div className="mt-md flex flex-wrap items-center gap-sm">
          <Button
            onClick={() => void handleCreateTicket()}
            disabled={ticketSubmitting || workspaceLoading || workspaces.length === 0}
          >
            {ticketSubmitting ? t("legalTickets.createSubmitting") : t("legalTickets.createSubmit")}
          </Button>
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("legalTickets.createHint")}
          </p>
        </div>
      </Card>

      <section className="mb-xl grid gap-gutter md:grid-cols-3">
        <Card title={t("legalTickets.totalTickets")}>
          <p className="text-3xl font-bold">{totalItems}</p>
        </Card>
        <Card title={t("legalTickets.openTicketsCurrentPage")}>
          <p className="text-3xl font-bold">{openCount}</p>
        </Card>
        <Card title={t("legalTickets.currentFilter")}>
          <p className="break-words text-2xl font-bold">
            {getLegalTicketFilterLabel(statusFilter, t)}
          </p>
        </Card>
      </section>

      <Card
        title={t("legalTickets.myLegalTickets")}
        actions={<Badge tone="blue">{tickets.length}</Badge>}
      >
        {error ? (
          <div role="alert" className="text-sm text-error">{error} <Button variant="secondary" onClick={() => void loadTickets()}>{t("common.retry")}</Button></div>
        ) : loading && tickets.length === 0 ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("legalTickets.loading")}
          </p>
        ) : tickets.length === 0 ? (
          <EmptyState
            icon={<TicketCheck className="h-6 w-6" />}
            title={t("legalTickets.emptyTitle")}
            description={t("legalTickets.emptyDescription")}
          />
        ) : (
          <DataTable
            columns={columns}
            data={tickets}
            getRowKey={(ticket) => ticket.id}
          />
        )}
        <Pagination page={page} totalPages={totalPages} totalItems={totalItems} disabled={loading} onPageChange={(nextPage) => { setPage(nextPage); const next = new URLSearchParams(searchParams); next.set("page", toPageParam(nextPage)); setSearchParams(next); }} />
      </Card>
    </div>
  );
}
