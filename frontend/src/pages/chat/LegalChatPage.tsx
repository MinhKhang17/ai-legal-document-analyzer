import { ArrowDown, Bot, Check, ChevronDown, ChevronUp, ClipboardCheck, Download, FileText, Files, Info, MoreHorizontal, Paperclip, Pencil, Plus, RefreshCw, Send, Share2, Square, Trash2, UserRound, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { Modal } from "../../components/common/Modal";
import { PageHeader } from "../../components/common/PageHeader";
import { StatusBadge } from "../../components/common/StatusBadge";
import {
  createChatSession,
  deleteChatSession,
  getChatMessageDetail,
  getChatSessionDetail,
  getChatSessionMessages,
  getWorkspaceChatSessions,
  sendChatSessionMessage,
  updateChatSession,
  shareChatSession,
  getChatSessionDocuments,
  attachChatSessionDocument,
  detachChatSessionDocument,
  exportChatSessionMarkdown,
} from "../../api/chatApi";
import { getChatMessageAiCitations } from "../../api/aiFeatureApi";
import { createLegalTicket } from "../../api/legalTicketApi";
import { downloadWorkspaceDocument, getWorkspaceDocuments, getWorkspaces, uploadDocument } from "../../api/workspaceApi";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { ChatMessageContent } from "../../components/chat/ChatMessageContent";
import { ChatMessageFeedbackControls } from "../../components/chat/ChatMessageFeedbackControls";
import { CreateTicketModal } from "../../components/tickets/CreateTicketModal";
import type { CreateLegalTicketRequest } from "../../types/legalTicket";
import type { ChatMessage, ChatSessionDocument } from "../../types/chat";
import type { Document, Workspace } from "../../types/workspace";
import type { WorkspaceChatMessage, WorkspaceChatSession } from "../../types/chat";
import type { AiCitation } from "../../types/aiFeature";
import { formatDisplayDateTime, formatNumber, formatPercent } from "../../utils/format";
import { simulateStreaming } from "../../utils/simulateStreaming";
import { supportedContractScopeText } from "../../config/supportedContractTypes";
import { finishSubmission, tryStartSubmission } from "../../utils/submissionGuard";
import { getApiErrorCode, isPlanEntitlementError } from "../../services/http";
import { acceptCurrentPolicies } from "../../services/policy.service";

import { getAccessToken as getSessionAccessToken } from "../../services/authSession";
const getAccessToken = () => getSessionAccessToken() ?? "";

type Translate = (key: string, params?: Record<string, string | number>) => string;

const riskLevelLabelKeys: Record<string, string> = {
  NONE: "risk.none",
  LOW: "risk.low",
  MEDIUM: "risk.medium",
  HIGH: "risk.high",
  CRITICAL: "risk.critical",
  UNKNOWN: "risk.unknown",
};

const suggestionTypeLabelKeys: Record<string, string> = {
  DIRECT_ANSWER: "chat.suggestionType.directAnswer",
  ASK_UPLOAD_CONTRACT: "chat.suggestionType.askUploadContract",
  ASK_CONTRACT_TYPE: "chat.suggestionType.askContractType",
  ASK_USER_ROLE: "chat.suggestionType.askUserRole",
  ASK_TARGET_CLAUSE: "chat.suggestionType.askTargetClause",
  ASK_MORE_FACTS: "chat.suggestionType.askMoreFacts",
  SUGGEST_REVISE_CLAUSE: "chat.suggestionType.suggestReviseClause",
  SUGGEST_NEGOTIATION: "chat.suggestionType.suggestNegotiation",
  REDIRECT_TO_SUPPORTED_SCOPE: "chat.suggestionType.redirectToSupportedScope",
  REFUSE_AND_REDIRECT: "chat.suggestionType.refuseAndRedirect",
  NONE: "chat.suggestionType.none",
  ASK_MORE_INFO: "chat.suggestionType.askMoreInfo",
  SUGGEST_LAWYER: "chat.suggestionType.suggestLawyer",
  REQUIRE_LAWYER: "chat.suggestionType.requireLawyer",
};

const messageRoleLabelKeys: Record<string, string> = {
  USER: "chat.role.user",
  ASSISTANT: "chat.role.assistant",
  SYSTEM: "chat.role.system",
};

const messageStatusLabelKeys: Record<string, string> = {
  SENT: "status.sent",
  PROCESSING: "status.processing",
  COMPLETED: "status.completed",
  FAILED: "status.failed",
};

const documentStatusLabelKeys: Record<string, string> = {
  READY: "status.ready",
  PROCESSING: "status.processing",
  PENDING: "status.pending",
  FAILED: "status.failed",
};

const citationSourceTypeLabelKeys: Record<string, string> = {
  DOCUMENT: "chat.citationSource.document",
  KNOWLEDGE_BASE: "chat.citationSource.knowledgeBase",
  CONTRACT: "chat.citationSource.contract",
  CHAT_MESSAGE: "chat.citationSource.chatMessage",
  LEGAL_TICKET: "chat.citationSource.legalTicket",
};

const localizeEnumValue = (
  value: string | null | undefined,
  labelKeys: Record<string, string>,
  t: Translate,
) => {
  if (!value) return "-";
  const labelKey = labelKeys[value.trim().toUpperCase()];
  return labelKey ? t(labelKey) : value;
};

const formatMegabytes = (bytes: number, locale: string) =>
  `${new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(bytes / 1024 / 1024)} MB`;

const formatTimestamp = (
  value: string | null | undefined,
  language: "en" | "vi",
  justNowLabel: string,
) => {
  if (!value) {
    return justNowLabel;
  }

  return formatDisplayDateTime(value, justNowLabel, language === "vi" ? "vi-VN" : "en-US");
};

const toDisplayMessage = (
  message: WorkspaceChatMessage,
  language: "en" | "vi",
  justNowLabel: string,
): ChatMessage => ({
  id: message.messageId,
  role: message.role.toLowerCase() === "assistant" ? "assistant" : "user",
  content: message.content,
  timestamp: formatTimestamp(message.createdAt, language, justNowLabel),
  status: message.status.toLowerCase() === "failed"
    ? "error"
    : message.status.toLowerCase() === "processing"
      ? "completed"
      : "completed",
  errorMessage: message.errorMessage ?? undefined,
  requestId: message.requestId,
  confidenceScore: message.confidenceScore,
  shouldSuggestTicket: message.shouldSuggestTicket,
  suggestionType: message.suggestionType,
  suggestionReason: message.suggestionReason,
  missingInformation: message.missingInformation,
  riskLevel: message.riskLevel,
  legalDomain: message.legalDomain,
  userActionHint: message.userActionHint,
});

const createOptimisticUserMessage = (
  message: string,
  language: "en" | "vi",
  justNowLabel: string,
): ChatMessage => ({
  id: `local-user-${Date.now()}`,
  role: "user",
  content: message,
  timestamp: formatTimestamp(null, language, justNowLabel),
  status: "completed",
});

const normalizedDocumentStatus = (status?: string) => (status ?? "").trim().toUpperCase();
const isDocumentReady = (status?: string) => normalizedDocumentStatus(status) === "READY";
const isDocumentFailed = (status?: string) => normalizedDocumentStatus(status) === "FAILED";

function DocumentProcessingProgress({ status }: { status?: string }) {
  const { t } = useI18n();

  if (isDocumentReady(status)) {
    return (
      <div className="mt-xs" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={100}>
        <div className="h-1.5 overflow-hidden rounded-full bg-emerald-100 dark:bg-emerald-950/50">
          <div className="h-full w-full rounded-full bg-emerald-500" />
        </div>
        <p className="mt-1 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
          {t("chat.documentProcessing.complete")}
        </p>
      </div>
    );
  }

  if (isDocumentFailed(status)) {
    return (
      <p className="mt-xs text-[11px] font-medium text-error">
        {t("chat.documentProcessing.failed")}
      </p>
    );
  }

  return (
    <div className="mt-xs" role="progressbar" aria-label={t("chat.documentProcessing.ariaLabel")}>
      <div className="h-1.5 overflow-hidden rounded-full bg-amber-100 dark:bg-amber-950/50">
        <div className="h-full w-2/3 animate-pulse rounded-full bg-amber-500" />
      </div>
      <p className="mt-1 text-[11px] font-medium text-amber-700 dark:text-amber-300">
        {t("chat.documentProcessing.hint")}
      </p>
    </div>
  );
}

type DisplayMessage = ChatMessage;

export function LegalChatPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [workspaceDocuments, setWorkspaceDocuments] = useState<Document[]>([]);
  const [chatSessions, setChatSessions] = useState<WorkspaceChatSession[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(
    searchParams.get("workspaceId") ?? "",
  );
  const [selectedSessionId, setSelectedSessionId] = useState(
    searchParams.get("sessionId") ?? "",
  );
  const [selectedDocumentId, setSelectedDocumentId] = useState(
    searchParams.get("documentId") ?? "",
  );
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingContext, setLoadingContext] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [sessionActionError, setSessionActionError] = useState("");
  const [sessionActionMessage, setSessionActionMessage] = useState("");
  const [renamingSessionId, setRenamingSessionId] = useState("");
  const [sessionMenuOpenId, setSessionMenuOpenId] = useState("");
  const [activeDrawer, setActiveDrawer] = useState<"session" | "documents" | "settings" | null>(null);
  const [renameTitle, setRenameTitle] = useState("");
  const [sessionActionBusyId, setSessionActionBusyId] = useState("");
  const [creatingTicketMessageId, setCreatingTicketMessageId] = useState("");
  const [ticketNotices, setTicketNotices] = useState<Record<string, string>>({});
  const [ticketDraft, setTicketDraft] = useState<{ assistant: DisplayMessage; user?: DisplayMessage } | null>(null);
  const [messageDetailOpen, setMessageDetailOpen] = useState(false);
  const [messageDetail, setMessageDetail] = useState<WorkspaceChatMessage | null>(null);
  const [messageDetailLoading, setMessageDetailLoading] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [exportingMarkdown, setExportingMarkdown] = useState(false);
  const [shareUrl, setShareUrl] = useState("");
  const [attachedDocuments, setAttachedDocuments] = useState<ChatSessionDocument[]>([]);
  const [documentsExpanded, setDocumentsExpanded] = useState(false);
  const [documentModalOpen, setDocumentModalOpen] = useState(false);
  const [documentActionBusy, setDocumentActionBusy] = useState(false);
  const [openingDocumentModal, setOpeningDocumentModal] = useState(false);
  const [documentActionError, setDocumentActionError] = useState("");
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [policyModalOpen, setPolicyModalOpen] = useState(false);
  const [policyAccepted, setPolicyAccepted] = useState(false);
  const [policyAccepting, setPolicyAccepting] = useState(false);
  const [messageCitations, setMessageCitations] = useState<AiCitation[]>([]);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [sessionToDelete, setSessionToDelete] = useState<WorkspaceChatSession | null>(null);
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);
  const chatInputRef = useRef<HTMLTextAreaElement>(null);
  const activeRequestControllerRef = useRef<AbortController | null>(null);
  const submissionPendingRef = useRef(false);
  const shouldAutoScrollRef = useRef(true);
  const [showNewResponseButton, setShowNewResponseButton] = useState(false);
  const lastSubmissionRef = useRef<{
    requestId: string;
    workspaceId: string;
    sessionId: string;
    documentId?: string;
    message: string;
  } | null>(null);

  const scrollChatToBottom = (behavior: ScrollBehavior = "smooth") => {
    const container = chatScrollContainerRef.current;
    if (!container) return;
    container.scrollTo({ top: container.scrollHeight, behavior });
    shouldAutoScrollRef.current = true;
    setShowNewResponseButton(false);
  };

  const selectedWorkspace = useMemo(
    () => workspaces.find((workspace) => workspace.workspaceId === selectedWorkspaceId),
    [selectedWorkspaceId, workspaces],
  );

  const selectedSession = useMemo(
    () => chatSessions.find((session) => session.chatSessionId === selectedSessionId),
    [chatSessions, selectedSessionId],
  );
  const canExportMarkdown = Boolean(selectedSessionId) && messages.some(
    (message) => message.role === "assistant" && message.status === "completed" && message.content.trim(),
  );
  const unavailableAttachedDocuments = useMemo(
    () => attachedDocuments.filter((document) => !isDocumentReady(document.uploadStatus)),
    [attachedDocuments],
  );
  const failedAttachedDocuments = useMemo(
    () => unavailableAttachedDocuments.filter((document) => isDocumentFailed(document.uploadStatus)),
    [unavailableAttachedDocuments],
  );
  const chatBlockedByAttachedDocuments = unavailableAttachedDocuments.length > 0;
  const sourceStatusText = chatBlockedByAttachedDocuments
    ? failedAttachedDocuments.length > 0
      ? t("chat.documents.bannerFailed")
      : t("chat.documents.bannerProcessing", { count: formatNumber(unavailableAttachedDocuments.length, locale) })
    : attachedDocuments.length > 0
      ? t("chat.documents.bannerGrounded", { count: formatNumber(attachedDocuments.length, locale) })
      : t("chat.documents.bannerSystemKnowledge");

  useEffect(() => {
    let active = true;

    const loadWorkspaces = async () => {
      try {
        setLoading(true);
        const data = (await getWorkspaces(getAccessToken())).filter(
          (ws) => ws.description !== "System workspace for general contract assistant chat"
        );
        if (!active) return;
        setWorkspaces(data);

        const workspaceIdFromQuery = searchParams.get("workspaceId") ?? "";
        const matchedWorkspace =
          data.find((workspace) => workspace.workspaceId === workspaceIdFromQuery) ??
          data[0];

        if (matchedWorkspace) {
          setSelectedWorkspaceId(matchedWorkspace.workspaceId);
          if (workspaceIdFromQuery !== matchedWorkspace.workspaceId) {
            setSearchParams({ workspaceId: matchedWorkspace.workspaceId });
          }
        }
      } catch {
        if (active) {
          setError(t("chat.loadWorkspaceError"));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadWorkspaces();

    return () => {
      active = false;
    };
  }, [searchParams, setSearchParams, t]);

  useEffect(() => {
    if (!selectedWorkspaceId) {
      setWorkspaceDocuments([]);
      setChatSessions([]);
      setMessages([]);
      return;
    }

    let active = true;

    const loadWorkspaceContext = async () => {
      try {
        setLoadingContext(true);
        const [documents, sessions] = await Promise.all([
          getWorkspaceDocuments(getAccessToken(), selectedWorkspaceId),
          getWorkspaceChatSessions(getAccessToken(), selectedWorkspaceId),
        ]);

        if (!active) return;
        setWorkspaceDocuments(documents);
        setChatSessions(sessions.items);

        setSelectedDocumentId("");

        const sessionFromQuery = searchParams.get("sessionId") ?? "";
        const nextSessionId =
          sessions.items.find((session) => session.chatSessionId === sessionFromQuery)
            ?.chatSessionId ??
          sessions.items.find((session) => session.isDefault)?.chatSessionId ??
          sessions.items[0]?.chatSessionId ??
          "";

        setSelectedSessionId(nextSessionId);
        if (nextSessionId) {
          if (
            searchParams.get("workspaceId") !== selectedWorkspaceId ||
            searchParams.get("sessionId") !== nextSessionId ||
            searchParams.has("documentId")
          ) {
            const nextParams: Record<string, string> = {
              workspaceId: selectedWorkspaceId,
              sessionId: nextSessionId,
            };
            setSearchParams(nextParams);
          }
        } else if (searchParams.get("workspaceId") !== selectedWorkspaceId) {
          const nextParams: Record<string, string> = { workspaceId: selectedWorkspaceId };
          setSearchParams(nextParams);
        }
      } catch {
        if (active) {
          setError(t("chat.loadWorkspaceDataError"));
        }
      } finally {
        if (active) {
          setLoadingContext(false);
        }
      }
    };

    void loadWorkspaceContext();

    return () => {
      active = false;
    };
  }, [searchParams, selectedWorkspaceId, setSearchParams, t]);

  useEffect(() => {
    if (!selectedSessionId) {
      setMessages([]);
      return;
    }

    let active = true;

    const loadMessages = async () => {
      try {
        const data = await getChatSessionMessages(
          getAccessToken(),
          selectedSessionId,
          0,
          100,
        );

        if (!active) return;
        setMessages(data.items.map((message) => toDisplayMessage(message, language, t("common.justNow"))));
      } catch {
        if (active) {
          setError(t("chat.loadHistoryError"));
        }
      }
    };

    void loadMessages();

    return () => {
      active = false;
    };
  }, [language, selectedSessionId, t]);

  useEffect(() => {
    if (!selectedSessionId) {
      setAttachedDocuments([]);
      return;
    }
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const loadAttached = async () => {
      try {
        const items = await getChatSessionDocuments(getAccessToken(), selectedSessionId);
        if (!active) return;
        setAttachedDocuments(items);
        if (items.some((item) => !["READY", "FAILED"].includes(normalizedDocumentStatus(item.uploadStatus)))) {
          timer = setTimeout(loadAttached, 3000);
        }
      } catch {
        if (active) setDocumentActionError(t("chat.documents.loadAttachedError"));
      }
    };
    void loadAttached();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [selectedSessionId, t]);

  useEffect(() => {
    const container = chatScrollContainerRef.current;
    if (!container) return;

    const frame = window.requestAnimationFrame(() => {
      if (shouldAutoScrollRef.current) {
        container.scrollTo({ top: container.scrollHeight, behavior: sending ? "smooth" : "auto" });
        setShowNewResponseButton(false);
      } else if (sending) {
        setShowNewResponseButton(true);
      }
    });

    return () => window.cancelAnimationFrame(frame);
  }, [attachedDocuments.length, messages, selectedSessionId, sending]);

  useEffect(() => {
    shouldAutoScrollRef.current = true;
    setShowNewResponseButton(false);
  }, [selectedSessionId]);

  useEffect(() => {
    if (attachedDocuments.length === 0) setDocumentsExpanded(false);
  }, [attachedDocuments.length]);

  useEffect(() => {
    const textarea = chatInputRef.current;
    if (!textarea) return;

    const maximumHeight = 112;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(textarea.scrollHeight, maximumHeight)}px`;
    textarea.style.overflowY = textarea.scrollHeight > maximumHeight ? "auto" : "hidden";
  }, [input]);

  useEffect(() => () => activeRequestControllerRef.current?.abort(), []);

  const handleWorkspaceChange = (nextWorkspaceId: string) => {
    if (nextWorkspaceId === selectedWorkspaceId) return;

    activeRequestControllerRef.current?.abort();
    setSelectedWorkspaceId(nextWorkspaceId);
    setSelectedSessionId("");
    setSelectedDocumentId("");
    setWorkspaceDocuments([]);
    setAttachedDocuments([]);
    setMessages([]);
    setDocumentModalOpen(false);
    setDocumentsExpanded(false);
    setDocumentActionError("");
    setError("");
    setSearchParams(nextWorkspaceId ? { workspaceId: nextWorkspaceId } : {});
  };

  const handleCreateSession = async () => {
    if (!selectedWorkspaceId) {
      return;
    }

    try {
      const session = await createChatSession(
        getAccessToken(),
        selectedWorkspaceId,
        t("chat.sessionDefaultTitle").replace("{time}", new Date().toLocaleString(locale)),
      );
      setChatSessions((previous) => [session, ...previous.filter((item) => item.chatSessionId !== session.chatSessionId)]);
      setSelectedSessionId(session.chatSessionId);
      setSearchParams({
        workspaceId: selectedWorkspaceId,
        sessionId: session.chatSessionId,
      });
    } catch {
      const message = t("chat.sessionCreateError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
      return;
    }
    toast.success(t("chat.sessionCreated"), t("toast.successTitle"));
  };

  const handleSelectSession = async (session: WorkspaceChatSession) => {
    setSelectedSessionId(session.chatSessionId);
    setSessionActionError("");

    if (
      searchParams.get("workspaceId") !== selectedWorkspaceId ||
      searchParams.get("sessionId") !== session.chatSessionId
    ) {
      setSearchParams({
        workspaceId: selectedWorkspaceId,
        sessionId: session.chatSessionId,
      });
    }

    try {
      const detail = await getChatSessionDetail(getAccessToken(), session.chatSessionId);
      setChatSessions((previous) =>
        previous.map((item) => (item.chatSessionId === detail.chatSessionId ? detail : item)),
      );
    } catch {
      const message = t("chat.sessionCreateError");
      setSessionActionError(message);
      toast.error(message, t("toast.errorTitle"));
    }
  };

  const handleRenameSession = async (chatSessionId: string) => {
    const title = renameTitle.trim();

    if (!title) {
      setSessionActionError(t("chat.sessionNameRequired"));
      toast.warning(t("chat.sessionNameRequired"), t("toast.warningTitle"));
      return;
    }

    setSessionActionBusyId(chatSessionId);
    setSessionActionError("");
    setSessionActionMessage("");

    try {
      const updatedSession = await updateChatSession(getAccessToken(), chatSessionId, title);
      setChatSessions((previous) =>
        previous.map((session) =>
          session.chatSessionId === chatSessionId ? updatedSession : session,
        ),
      );
      setRenamingSessionId("");
      setRenameTitle("");
      setSessionActionMessage(t("chat.sessionRenamed"));
      toast.success(t("chat.sessionRenamed"), t("toast.successTitle"));
    } catch {
      const message = t("chat.sessionRenameError");
      setSessionActionError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setSessionActionBusyId("");
    }
  };

  const handleDeleteSessionClick = (chatSessionId: string) => {
    const session = chatSessions.find((s) => s.chatSessionId === chatSessionId);
    if (session) {
      setSessionToDelete(session);
      setIsDeleteModalOpen(true);
    }
  };

  const handleConfirmDeleteSession = async () => {
    if (!sessionToDelete) return;
    const chatSessionId = sessionToDelete.chatSessionId;

    setSessionActionBusyId(chatSessionId);
    setSessionActionError("");
    setSessionActionMessage("");

    try {
      await deleteChatSession(getAccessToken(), chatSessionId);
      const remainingSessions = chatSessions.filter((session) => session.chatSessionId !== chatSessionId);
      setChatSessions(remainingSessions);

      if (selectedSessionId === chatSessionId) {
        const nextSessionId =
          remainingSessions.find((session) => session.isDefault)?.chatSessionId ??
          remainingSessions[0]?.chatSessionId ??
          "";

        setSelectedSessionId(nextSessionId);
        setMessages([]);
        setSearchParams(
          nextSessionId
            ? { workspaceId: selectedWorkspaceId, sessionId: nextSessionId }
            : { workspaceId: selectedWorkspaceId },
        );
      }

      setSessionActionMessage(t("chat.sessionDeleted"));
      toast.success(t("chat.sessionDeleted"), t("toast.successTitle"));
    } catch {
      const message = t("chat.sessionDeleteError");
      setSessionActionError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setSessionActionBusyId("");
      setIsDeleteModalOpen(false);
      setSessionToDelete(null);
    }
  };

  const getCreateTicketErrorMessage = (err: unknown) => {
    if (!(err instanceof Error)) return t("chat.ticketCreateError");
    const normalizedMessage = err.message.toUpperCase();
    if (normalizedMessage.includes("AI_ANALYSIS_NOT_FOUND") || normalizedMessage.includes("ANALYSIS_NOT_FOUND")) {
      return t("chat.ticketMissingAnalysis");
    }
    return t("chat.ticketCreateError");
  };

  const handleCreateTicket = (assistantMessage: DisplayMessage, userMessage?: DisplayMessage) => {
    if (!selectedWorkspaceId) {
      setError(t("chat.ticketSelectWorkspace"));
      toast.warning(t("chat.ticketSelectWorkspace"), t("toast.warningTitle"));
      return;
    }
    if (assistantMessage.id.startsWith("local-")) {
      const message = t("chat.ticketMissingRequestId");
      setError(message);
      toast.warning(message, t("toast.warningTitle"));
      return;
    }
    setTicketDraft({ assistant: assistantMessage, user: userMessage });
  };

  const submitTicket = async (request: CreateLegalTicketRequest) => {
    if (!ticketDraft) return;
    const assistantMessage = ticketDraft.assistant;
    setCreatingTicketMessageId(assistantMessage.id);
    setError("");
    try {
      const ticket = await createLegalTicket(request);
      setTicketNotices((previous) => ({ ...previous, [assistantMessage.id]: `${t("chat.ticketCreated")} ${ticket.id}.` }));
      toast.success(`${t("chat.ticketCreated")} ${ticket.id}.`, t("toast.successTitle"));
      setTicketDraft(null);
    } catch (err) {
      if (isPlanEntitlementError(err)) {
        const message = t("legalTickets.planRequired");
        setError(message);
        toast.warning(message, t("toast.warningTitle"));
        setTicketDraft(null);
        navigate("/billing/subscribe?reason=plan-required");
        return;
      }
      if (isPlanEntitlementError(err)) {
        const message = "Dịch vụ này yêu cầu gói phù hợp. Vui lòng chọn hoặc nâng cấp gói để tiếp tục.";
        setError(message);
        setMessages((previous) => previous.map((item) => item.id === assistantMessageId
          ? { ...item, status: "error", errorMessage: message }
          : item));
        toast.warning(message);
        navigate("/billing/subscribe?reason=plan-required");
        return;
      }
      if (getApiErrorCode(err) === "TERMS_NOT_ACCEPTED") {
        const message = "Bạn cần chấp thuận chính sách hiện hành trước khi phân tích tài liệu.";
        setError(message);
        setMessages((previous) => previous.map((item) => item.id === assistantMessageId
          ? { ...item, status: "error", errorMessage: message }
          : item));
        setPolicyAccepted(false);
        setPolicyModalOpen(true);
        return;
      }
      const message = getCreateTicketErrorMessage(err);
      setError(message);
      toast.error(message, t("toast.errorTitle"));
      throw err;
    } finally {
      setCreatingTicketMessageId("");
    }
  };

  const handleOpenMessageDetail = async (messageId: string) => {
    if (messageId.startsWith("local-")) {
      return;
    }

    setMessageDetailOpen(true);
    setMessageDetailLoading(true);
    setMessageDetail(null);
    setMessageCitations([]);
    setError("");

    try {
      const [detailResult, citationsResult] = await Promise.allSettled([
        getChatMessageDetail(getAccessToken(), messageId),
        getChatMessageAiCitations(messageId),
      ]);
      if (detailResult.status === "rejected") throw detailResult.reason;
      setMessageDetail(detailResult.value);
      if (citationsResult.status === "fulfilled") {
        setMessageCitations(citationsResult.value);
      } else {
        setError(t("common.partialDataError"));
      }
    } catch {
      const message = t("chat.messageDetailError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setMessageDetailLoading(false);
    }
  };

  const refreshAttachedDocuments = async () => {
    if (!selectedSessionId) return;
    setAttachedDocuments(await getChatSessionDocuments(getAccessToken(), selectedSessionId));
  };

  const handleOpenDocumentModal = async () => {
    if (!selectedWorkspaceId) {
      toast.warning(t("chat.pickWorkspaceFirst"));
      return;
    }
    setOpeningDocumentModal(true);
    setDocumentActionError("");
    try {
      let sessionId = selectedSessionId || chatSessions[0]?.chatSessionId || "";
      if (!sessionId) {
        const session = await createChatSession(
          getAccessToken(),
          selectedWorkspaceId,
          t("chat.newConversation"),
        );
        sessionId = session.chatSessionId;
        setChatSessions((current) => [session, ...current]);
      }
      setSelectedSessionId(sessionId);
      setSearchParams({ workspaceId: selectedWorkspaceId, sessionId });
      setDocumentModalOpen(true);
    } catch {
      const message = t("chat.documents.openModalError");
      setError(message);
      toast.error(message);
    } finally {
      setOpeningDocumentModal(false);
    }
  };

  const handleAttachDocument = async (documentId: string) => {
    if (!selectedSessionId) return;
    setDocumentActionBusy(true);
    setDocumentActionError("");
    try {
      await attachChatSessionDocument(getAccessToken(), selectedSessionId, documentId);
      await refreshAttachedDocuments();
      toast.success(t("chat.documents.attachedSuccess"));
    } catch {
      const message = t("chat.documents.attachError");
      setDocumentActionError(message);
      toast.error(message);
    } finally {
      setDocumentActionBusy(false);
    }
  };

  const handleUploadAndAttach = async () => {
    if (!uploadFile || !selectedWorkspaceId || !selectedSessionId) return;
    setDocumentActionBusy(true);
    setDocumentActionError("");
    try {
      const uploaded = await uploadDocument(getAccessToken(), selectedWorkspaceId, uploadFile);
      setWorkspaceDocuments((current) => [uploaded, ...current.filter((item) => item.documentId !== uploaded.documentId)]);
      await attachChatSessionDocument(getAccessToken(), selectedSessionId, uploaded.documentId);
      await refreshAttachedDocuments();
      setUploadFile(null);
      toast.success(t("chat.documents.uploadedAttachedSuccess"));
    } catch (err) {
      if (getApiErrorCode(err) === "TERMS_NOT_ACCEPTED") {
        setPolicyAccepted(false);
        setPolicyModalOpen(true);
        setDocumentActionError("Bạn cần chấp thuận Điều khoản sử dụng và Chính sách xử lý dữ liệu hiện hành trước khi tải tài liệu.");
        return;
      }
      const raw = err instanceof Error ? err.message : t("chat.documents.uploadError");
      const message = /quota|storage|limit/i.test(raw) ? t("chat.documents.quotaExhausted") : t("chat.documents.uploadError");
      setDocumentActionError(message);
      toast.error(message);
    } finally {
      setDocumentActionBusy(false);
    }
  };

  const handleDetachDocument = async (documentId: string) => {
    if (!selectedSessionId) return;
    setDocumentActionBusy(true);
    try {
      await detachChatSessionDocument(getAccessToken(), selectedSessionId, documentId);
      setAttachedDocuments((current) => current.filter((item) => item.documentId !== documentId));
      toast.success(t("chat.documents.detachedSuccess"));
    } catch {
      toast.error(t("chat.documents.detachError"), t("toast.errorTitle"));
    } finally {
      setDocumentActionBusy(false);
    }
  };

  const handleDownloadWorkspaceDocument = async (document: Document) => {
    try {
      const url = await downloadWorkspaceDocument(getAccessToken(), selectedWorkspaceId, document.documentId);
      const anchor = window.document.createElement("a");
      anchor.href = url;
      anchor.download = document.originalFileName;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      toast.error(t("chat.documents.downloadError"), t("toast.errorTitle"));
    }
  };

  const handleSend = async (override?: {
    requestId?: string;
    message: string;
    workspaceId: string;
    sessionId?: string;
    documentId?: string;
  }) => {
    if (sending || submissionPendingRef.current || activeRequestControllerRef.current) return;

    const question = (override?.message ?? input).trim();
    const targetWorkspaceId = override?.workspaceId ?? selectedWorkspaceId;
    let targetSessionId = override?.sessionId ?? selectedSessionId;
    const targetDocumentId = override?.documentId ?? (selectedDocumentId || undefined);
    const requestId = override?.requestId ?? `req_${crypto.randomUUID().replaceAll("-", "")}`;

    if (!question || !targetWorkspaceId) {
      return;
    }

    if (chatBlockedByAttachedDocuments) {
      const message = failedAttachedDocuments.length > 0
        ? t("chat.documents.failedBlockMessage")
        : t("chat.documents.processingBlockMessage");
      setError(message);
      toast.error(message);
      return;
    }

    if (!tryStartSubmission(submissionPendingRef)) return;
    setSending(true);

    if (!targetSessionId) {
      try {
        const session = await createChatSession(
          getAccessToken(),
          targetWorkspaceId,
          t("chat.sessionDefaultTitle").replace("{time}", new Date().toLocaleString(locale)),
        );
        targetSessionId = session.chatSessionId;
        setChatSessions((previous) => [
          session,
          ...previous.filter((item) => item.chatSessionId !== session.chatSessionId),
        ]);
        setSelectedSessionId(session.chatSessionId);
        setSearchParams({ workspaceId: targetWorkspaceId, sessionId: session.chatSessionId });
      } catch {
        const message = t("chat.sessionCreateError");
        setError(message);
        toast.error(message, t("toast.errorTitle"));
        finishSubmission(submissionPendingRef);
        setSending(false);
        return;
      }
    }

    const optimisticUserMessage = createOptimisticUserMessage(question, language, t("common.justNow"));
    const assistantMessageId = `local-assistant-${Date.now()}`;
    const controller = new AbortController();
    activeRequestControllerRef.current = controller;

    setMessages((previous) => [
      ...previous,
      optimisticUserMessage,
      {
        id: assistantMessageId,
        role: "assistant",
        content: "",
        timestamp: formatTimestamp(null, language, t("common.justNow")),
        status: "thinking",
        statusMessage: t("chat.status.analyzing"),
      },
    ]);
    setInput("");
    setError("");
    shouldAutoScrollRef.current = true;
    window.requestAnimationFrame(() => chatInputRef.current?.focus());
    lastSubmissionRef.current = {
      requestId,
      workspaceId: targetWorkspaceId,
      sessionId: targetSessionId ?? "",
      documentId: targetDocumentId,
      message: question,
    };

    try {
      const conversation = await sendChatSessionMessage(
        getAccessToken(),
        targetSessionId,
        question,
        undefined,
        controller.signal,
        requestId,
      );

      if (conversation?.chatSession) {
        const nextSessionId = conversation.chatSession.chatSessionId;
        setSelectedSessionId(nextSessionId);
        setChatSessions((previous) => [
          conversation.chatSession,
          ...previous.filter((item) => item.chatSessionId !== nextSessionId),
        ]);
      }
      if (conversation?.assistantMessage) {
        const completedMessage = toDisplayMessage(conversation.assistantMessage, language, t("common.justNow"));
        setMessages((previous) => previous.map((item) => item.id === assistantMessageId
          ? { ...completedMessage, id: assistantMessageId, content: "", status: "streaming", statusMessage: undefined }
          : item));

        await simulateStreaming(
          completedMessage.content,
          (visibleText) => {
            setMessages((previous) => previous.map((item) => item.id === assistantMessageId
              ? { ...item, content: visibleText, status: "streaming" }
              : item));
          },
          controller.signal,
        );

        setMessages((previous) => previous.map((item) => item.id === assistantMessageId
          ? {
              ...completedMessage,
              id: conversation.assistantMessage.messageId ?? assistantMessageId,
              status: "completed",
              intent: conversation.intent,
              suggestedActions: conversation.suggestedActions,
              draftingPrompt: conversation.draftingPrompt,
              redactionRequired: conversation.redactionRequired,
            }
          : item));
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        setMessages((previous) => previous.map((item) => item.id === assistantMessageId
          ? {
              ...item,
              status: "cancelled",
              statusMessage: t("chat.status.stopped"),
            }
          : item));
        return;
      }
      const message = t("chat.messageSendError");
      setError(message);
      setMessages((previous) => previous.map((item) => item.id === assistantMessageId
        ? { ...item, status: "error", errorMessage: message }
        : item));
      toast.error(message, t("toast.errorTitle"));
    } finally {
      if (activeRequestControllerRef.current === controller) {
        activeRequestControllerRef.current = null;
      }
      finishSubmission(submissionPendingRef);
      setSending(false);
      window.requestAnimationFrame(() => chatInputRef.current?.focus());
    }
  };

  const handleStopResponse = () => {
    activeRequestControllerRef.current?.abort();
  };

  return (
    <div className="flex min-h-0 flex-col lg:h-full lg:flex-1">
      <PageHeader
        title={t("chat.pageTitle", {
          title: selectedSession?.title ?? t("chat.newConversation"),
        })}
        subtitle={t("chat.workspaceContext", {
          workspace: selectedWorkspace?.name ?? "—",
        })}
        className="shrink-0"
        compact
        actions={
          <>
            {selectedSessionId && (
              <Button
                variant="secondary"
                size="icon"
                className="h-10 w-10 sm:h-9 sm:w-9 2xl:w-auto 2xl:px-sm"
                disabled={!canExportMarkdown || exportingMarkdown}
                aria-label={exportingMarkdown ? t("chat.exporting") : t("chat.exportMarkdown")}
                title={!canExportMarkdown ? t("chat.exportUnavailable") : t("chat.exportMarkdown")}
                onClick={async () => {
                  setExportingMarkdown(true);
                  try {
                    await exportChatSessionMarkdown(getAccessToken(), selectedSessionId);
                    toast.success(t("chat.exportSuccess"));
                  } catch {
                    toast.error(t("chat.exportError"), t("toast.errorTitle"));
                  } finally {
                    setExportingMarkdown(false);
                  }
                }}
              >
                {exportingMarkdown ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
                <span className="hidden 2xl:inline">{exportingMarkdown ? t("chat.exporting") : t("chat.exportMarkdown")}</span>
              </Button>
            )}
            {selectedSessionId && (
              <Button
                variant="secondary"
                size="icon"
                className="h-10 w-10 sm:h-9 sm:w-9 2xl:w-auto 2xl:px-sm"
                disabled={sharing}
                aria-label={t("actions.share")}
                title={t("actions.share")}
                onClick={async () => {
                  setSharing(true);
                  try {
                    const shared = await shareChatSession(getAccessToken(), selectedSessionId);
                    setShareUrl(shared.shareUrl);
                    await navigator.clipboard.writeText(shared.shareUrl);
                    toast.success(t("chat.shareSuccess"));
                  } catch {
                    toast.error(t("chat.shareError"), t("toast.errorTitle"));
                  } finally {
                    setSharing(false);
                  }
                }}
              >
                <Share2 className="h-4 w-4" />
                <span className="hidden 2xl:inline">{t("actions.share")}</span>
              </Button>
            )}
            <Button
              variant="secondary"
              size="icon"
              className="h-10 w-10 sm:h-9 sm:w-9 2xl:w-auto 2xl:px-sm"
              aria-label={t("chat.refreshContext")}
              title={t("chat.refreshContext")}
              onClick={() => {
                if (!selectedWorkspaceId) return;
                setSearchParams({
                  workspaceId: selectedWorkspaceId,
                  ...(selectedSessionId ? { sessionId: selectedSessionId } : {}),
                });
              }}
            >
              <RefreshCw className="h-4 w-4" />
              <span className="hidden 2xl:inline">{t("chat.refreshContext")}</span>
            </Button>
            <Button
              size="sm"
              className="h-10 sm:h-9"
              leftIcon={<Plus className="h-4 w-4" />}
              onClick={handleCreateSession}
              disabled={!selectedWorkspaceId}
            >
              {t("chat.newSession")}
            </Button>
            <Button
              size="icon"
              className="h-10 w-10 sm:h-9 sm:w-9"
              variant={activeDrawer === "session" ? "primary" : "ghost"}
              aria-label={t("chat.drawer.sessionInformation")}
              title={t("chat.drawer.sessionInformation")}
              onClick={() => setActiveDrawer((current) => current === "session" ? null : "session")}
            >
              <Info className="h-4 w-4" />
            </Button>
          </>
        }
      />

      {error && (
        <div className="mb-sm shrink-0 rounded-xl border border-error/40 bg-error/10 p-sm text-sm text-error" role="alert">
          {error}
        </div>
      )}

      <div className="relative min-h-0 flex-1">
        {activeDrawer && <button type="button" aria-label={t("chat.drawer.close")} className="fixed inset-0 z-30 bg-slate-950/30" onClick={() => setActiveDrawer(null)} />}
        <aside className={`fixed inset-y-0 right-0 z-40 w-[min(400px,calc(100vw-24px))] overflow-y-auto border-l border-legal-border bg-white p-md shadow-2xl transition-transform dark:border-slate-700 dark:bg-slate-950 ${activeDrawer ? "translate-x-0" : "translate-x-full"}`}>
          <div className="mb-md flex items-center justify-between"><p className="font-semibold">{activeDrawer === "session" ? t("chat.drawer.session") : activeDrawer === "documents" ? t("chat.drawer.documents") : t("chat.drawer.options")}</p><Button size="icon" variant="ghost" aria-label={t("actions.close")} onClick={() => setActiveDrawer(null)}><X className="h-5 w-5" /></Button></div>
          <Card className={activeDrawer === "session" ? "" : "hidden"} title={t("chat.drawer.sessionInformation")}>
            <div className="space-y-md">
              <dl className="space-y-sm text-sm">
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">{t("chat.sessionIdLabel")}</dt><dd className="max-w-40 truncate font-medium" title={selectedSessionId}>{selectedSessionId || "—"}</dd></div>
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">{t("chatHistory.workspaceFilter")}</dt><dd className="text-right font-medium">{selectedWorkspace?.name ?? "—"}</dd></div>
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">{t("workspace.createdAt")}</dt><dd className="text-right font-medium">{selectedSession ? formatDisplayDateTime(selectedSession.createdAt, "—", locale) : "—"}</dd></div>
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">{t("chatHistory.lastUpdated")}</dt><dd className="text-right font-medium">{selectedSession ? formatDisplayDateTime(selectedSession.updatedAt, "—", locale) : "—"}</dd></div>
              </dl>
              <p className="label-uppercase">{t("chat.switchWorkspace")}</p>
              <select
                className="form-field"
                value={selectedWorkspaceId}
                onChange={(event) => handleWorkspaceChange(event.target.value)}
                disabled={loading}
              >
                <option value="">
                  {loading ? t("chat.loadingWorkspaces") : t("chat.selectWorkspace")}
                </option>
                {workspaces.map((workspace) => (
                  <option key={workspace.workspaceId} value={workspace.workspaceId}>
                    {workspace.name}
                  </option>
                ))}
              </select>

              {selectedWorkspace ? (
                <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                  <p className="font-semibold">{selectedWorkspace.name}</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    {selectedWorkspace.description || t("workspace.noDescription")}
                  </p>
                  <div className="mt-sm flex items-center gap-sm">
                    <StatusBadge status={selectedWorkspace.status} />
                    <Link
                      className="text-sm font-semibold text-primary hover:underline dark:text-inverse-primary"
                      to={`/projects/${selectedWorkspace.workspaceId}`}
                    >
                      {t("actions.viewDetails")}
                    </Link>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("chat.createWorkspaceFirst")}
                </p>
              )}
            </div>
          </Card>

          <Card className={activeDrawer === "documents" ? "" : "hidden"} title={t("chat.documentsInWorkspace")} actions={<Button size="sm" leftIcon={<Paperclip className="h-4 w-4" />} disabled={!selectedWorkspaceId || openingDocumentModal} onClick={() => void handleOpenDocumentModal()}>{openingDocumentModal ? t("chat.documents.opening") : t("chat.documents.add")}</Button>}>
            <div className="space-y-md">
              {loadingContext && (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("upload.loadingDocuments")}
                </p>
              )}

              {attachedDocuments.length === 0 && !loadingContext ? (
                <div className="rounded-lg border border-dashed border-outline-variant p-md text-sm text-on-surface-variant dark:border-slate-700 dark:text-slate-400">
                  <p className="font-semibold text-on-surface dark:text-slate-200">{t("chat.systemKnowledge.title")}</p>
                  <p className="mt-xs">{t("chat.systemKnowledge.description")}</p>
                </div>
              ) : (
                attachedDocuments.map((document) => (
                  <article key={document.documentId} className="w-full overflow-hidden rounded-xl border border-primary bg-surface-container-high p-md dark:border-inverse-primary dark:bg-slate-800">
                    <div className="flex items-start gap-md">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                        <FileText className="h-5 w-5" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-semibold" title={document.originalFileName}>
                          {document.originalFileName}
                        </p>
                        <p className="text-sm text-on-surface-variant dark:text-slate-400">
                          {formatMegabytes(document.size, locale)} · {document.contentType || "-"}
                        </p>
                      </div>
                    </div>
                    <div className="mt-md flex items-center justify-between gap-sm">
                      <Badge tone={document.uploadStatus.toUpperCase() === "READY" ? "green" : document.uploadStatus.toUpperCase() === "FAILED" ? "red" : "amber"}>{localizeEnumValue(document.uploadStatus, documentStatusLabelKeys, t)}</Badge>
                      <Button size="sm" variant="danger" leftIcon={<Trash2 className="h-4 w-4" />} disabled={documentActionBusy} onClick={() => void handleDetachDocument(document.documentId)}>{t("chat.documents.remove")}</Button>
                    </div>
                    <DocumentProcessingProgress status={document.uploadStatus} />
                  </article>
                ))
              )}
            </div>
          </Card>

          <Card className={activeDrawer === "settings" ? "" : "hidden"} title={t("chatHistory.conversations")}>
            <div className="space-y-md">
              {sessionActionError && (
                <p className="rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
                  {sessionActionError}
                </p>
              )}
              {sessionActionMessage && (
                <p className="rounded-lg bg-emerald-50 px-md py-sm text-sm font-semibold text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200">
                  {sessionActionMessage}
                </p>
              )}
              {chatSessions.length === 0 ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("chat.noSessions")}
                </p>
              ) : (
                chatSessions.map((session) => (
                  <article
                    key={session.chatSessionId}
                    role="button"
                    tabIndex={0}
                    onClick={() => void handleSelectSession(session)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        void handleSelectSession(session);
                      }
                    }}
                    className={`relative w-full rounded-lg border px-md py-sm text-left transition ${
                      selectedSessionId === session.chatSessionId
                        ? "border-primary bg-surface-container-high dark:border-inverse-primary dark:bg-slate-800"
                        : "border-legal-border hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-sm">
                      <div className="min-w-0 flex-1">
                        {renamingSessionId === session.chatSessionId ? (
                          <div
                            className="space-y-sm"
                            onClick={(event) => event.stopPropagation()}
                          >
                            <input
                              className="form-field"
                              value={renameTitle}
                              onChange={(event) => setRenameTitle(event.target.value)}
                              maxLength={255}
                              autoFocus
                            />
                            <div className="flex gap-xs">
                              <Button
                                size="sm"
                                leftIcon={<Check className="h-4 w-4" />}
                                onClick={() => void handleRenameSession(session.chatSessionId)}
                                disabled={sessionActionBusyId === session.chatSessionId}
                              >
                                {t("actions.save")}
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                leftIcon={<X className="h-4 w-4" />}
                                onClick={() => {
                                  setRenamingSessionId("");
                                  setRenameTitle("");
                                }}
                              >
                                {t("actions.cancel")}
                              </Button>
                            </div>
                          </div>
                        ) : (
                          <p className="truncate text-sm font-semibold" title={session.title}>{session.title}</p>
                        )}
                      </div>
                      {renamingSessionId !== session.chatSessionId && (
                        <div className="relative shrink-0" onClick={(event) => event.stopPropagation()}>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={t("chat.openSessionActions", { title: session.title })}
                            aria-expanded={sessionMenuOpenId === session.chatSessionId}
                            onClick={() => setSessionMenuOpenId((current) => current === session.chatSessionId ? "" : session.chatSessionId)}
                          >
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                          {sessionMenuOpenId === session.chatSessionId && (
                            <div className="absolute right-0 top-full z-20 mt-xs w-36 overflow-hidden rounded-lg border border-legal-border bg-white p-xs shadow-lg dark:border-slate-700 dark:bg-slate-900">
                              <button
                                type="button"
                                className="flex w-full items-center gap-sm rounded-md px-sm py-xs text-left text-sm hover:bg-surface-container-low dark:hover:bg-slate-800"
                                onClick={() => {
                                  setSessionMenuOpenId("");
                                  setRenamingSessionId(session.chatSessionId);
                                  setRenameTitle(session.title);
                                  setSessionActionError("");
                                  setSessionActionMessage("");
                                }}
                              >
                                <Pencil className="h-4 w-4" />{t("chat.renameSession")}
                              </button>
                              <button
                                type="button"
                                className="flex w-full items-center gap-sm rounded-md px-sm py-xs text-left text-sm text-error hover:bg-error/10"
                                disabled={sessionActionBusyId === session.chatSessionId}
                                onClick={() => {
                                  setSessionMenuOpenId("");
                                  handleDeleteSessionClick(session.chatSessionId);
                                }}
                              >
                                <Trash2 className="h-4 w-4" />{t("chat.deleteSession")}
                              </button>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </article>
                ))
              )}
            </div>
          </Card>

        </aside>

        <section className="h-full min-h-0 min-w-0">
          <div className="relative flex h-full min-h-0 min-w-0 flex-col gap-xs sm:gap-sm">
            <section className="shrink-0 rounded-xl border border-legal-border bg-white px-sm py-xs shadow-sm dark:border-slate-700 dark:bg-slate-900">
              <div className="flex items-center gap-sm">
                <label className="shrink-0 text-sm font-semibold text-on-surface-variant" htmlFor="chat-workspace-select">
                  {t("chatHistory.workspaceFilter")}
                </label>
                <select
                  id="chat-workspace-select"
                  className="form-field min-w-0 flex-1"
                  value={selectedWorkspaceId}
                  onChange={(event) => handleWorkspaceChange(event.target.value)}
                  disabled={loading || sending}
                >
                  <option value="">
                    {loading ? t("chat.loadingWorkspaces") : t("chat.selectWorkspace")}
                  </option>
                  {workspaces.map((workspace) => (
                    <option key={workspace.workspaceId} value={workspace.workspaceId}>
                      {workspace.name}
                    </option>
                  ))}
                </select>
              </div>
            </section>
            <section className={`shrink-0 rounded-xl border bg-white p-xs shadow-sm dark:bg-slate-900 ${chatBlockedByAttachedDocuments ? "border-amber-300 dark:border-amber-900" : "border-legal-border dark:border-slate-700"}`} aria-labelledby="chat-documents-heading">
              <div className="flex min-w-0 items-center gap-xs">
                {attachedDocuments.length > 0 ? (
                  <button
                    type="button"
                    className="group flex min-h-10 min-w-0 flex-1 items-center gap-sm rounded-lg p-xs text-left transition hover:bg-surface-container-low dark:hover:bg-slate-800"
                    aria-expanded={documentsExpanded}
                    aria-controls="chat-attached-document-details"
                    onClick={() => setDocumentsExpanded((current) => !current)}
                  >
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary dark:bg-slate-800 dark:text-inverse-primary"><Files className="h-4 w-4" aria-hidden="true" /></span>
                    <span className="min-w-0 flex-1">
                      <span id="chat-documents-heading" className="block truncate text-sm font-semibold text-on-surface dark:text-slate-100">
                        {t("chat.documents.usedCount", { count: formatNumber(attachedDocuments.length, locale) })}
                      </span>
                      <span className="block truncate text-xs font-medium text-on-surface-variant dark:text-slate-400">{sourceStatusText}</span>
                    </span>
                    {documentsExpanded ? <ChevronUp className="h-4 w-4 shrink-0" aria-hidden="true" /> : <ChevronDown className="h-4 w-4 shrink-0" aria-hidden="true" />}
                  </button>
                ) : (
                  <div className="flex min-h-10 min-w-0 flex-1 items-center gap-sm px-xs">
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary dark:bg-slate-800 dark:text-inverse-primary"><Bot className="h-4 w-4" aria-hidden="true" /></span>
                    <span className="min-w-0 flex-1">
                      <span id="chat-documents-heading" className="block truncate text-sm font-semibold text-on-surface dark:text-slate-100">
                        {t("chat.documents.usedCount", { count: formatNumber(0, locale) })}
                      </span>
                      <span className="block truncate text-xs font-medium text-on-surface-variant dark:text-slate-400">{sourceStatusText}</span>
                    </span>
                  </div>
                )}
                <Badge tone="gold">RAG</Badge>
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  className="h-10 w-10 shrink-0 sm:h-9 sm:w-9"
                  aria-label={openingDocumentModal ? t("chat.documents.opening") : t("chat.documents.add")}
                  title={openingDocumentModal ? t("chat.documents.opening") : t("chat.documents.add")}
                  disabled={!selectedWorkspaceId || openingDocumentModal}
                  onClick={() => void handleOpenDocumentModal()}
                >
                  {openingDocumentModal ? <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Paperclip className="h-4 w-4" aria-hidden="true" />}
                </Button>
                <Button
                  type="button"
                  size="icon"
                  variant={activeDrawer === "documents" ? "primary" : "ghost"}
                  className="h-10 w-10 shrink-0 sm:h-9 sm:w-9"
                  aria-label={t("chat.viewSources")}
                  title={t("chat.viewSources")}
                  onClick={() => setActiveDrawer((current) => current === "documents" ? null : "documents")}
                >
                  <Info className="h-4 w-4" aria-hidden="true" />
                </Button>
              </div>

              {documentsExpanded && attachedDocuments.length > 0 && (
                <div id="chat-attached-document-details" role="region" aria-labelledby="chat-documents-heading" className="mt-xs flex max-w-full gap-sm overflow-x-auto border-t border-legal-border pb-xs pt-sm dark:border-slate-700">
                  {attachedDocuments.map((document) => (
                    <article key={document.documentId} className="min-w-[min(17rem,78vw)] max-w-[19rem] flex-1 rounded-lg border border-legal-border bg-surface-container-low p-sm dark:border-slate-700 dark:bg-slate-800">
                      <div className="flex items-center gap-sm">
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary dark:bg-slate-700 dark:text-inverse-primary"><FileText className="h-4 w-4" aria-hidden="true" /></div>
                        <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold" title={document.originalFileName}>{document.originalFileName}</p><p className="truncate text-xs text-on-surface-variant dark:text-slate-400">{formatMegabytes(document.size, locale)}</p></div>
                        <button type="button" className="shrink-0 rounded-md p-xs text-on-surface-variant transition hover:bg-surface-container-high hover:text-error disabled:cursor-not-allowed disabled:opacity-50 dark:text-slate-400 dark:hover:bg-slate-700" aria-label={t("chat.documents.detachAria", { name: document.originalFileName })} disabled={documentActionBusy} onClick={() => void handleDetachDocument(document.documentId)}><X className="h-4 w-4" aria-hidden="true" /></button>
                      </div>
                      <DocumentProcessingProgress status={document.uploadStatus} />
                    </article>
                  ))}
                </div>
              )}
              {documentActionError && !documentModalOpen && <p className="mt-xs rounded-lg bg-error/10 px-sm py-xs text-xs font-medium text-error" role="alert">{documentActionError}</p>}
            </section>
            <div className="relative min-h-0 flex-1">
              <div
                ref={chatScrollContainerRef}
                onScroll={(event) => {
                  const container = event.currentTarget;
                  const nearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 120;
                  shouldAutoScrollRef.current = nearBottom;
                  if (nearBottom) setShowNewResponseButton(false);
                }}
                className="h-full min-h-0 space-y-md overflow-y-auto overflow-x-hidden px-xs py-sm sm:px-md"
              >
                {messages.length === 0 ? (
                  <div className="flex min-h-full items-center justify-center py-lg">
                    <div className="w-full max-w-xl rounded-xl border border-legal-border bg-white/80 p-xl text-center shadow-sm dark:border-slate-700 dark:bg-slate-900/80">
                      <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-primary/10 text-primary dark:bg-slate-800 dark:text-inverse-primary"><Bot className="h-5 w-5" aria-hidden="true" /></span>
                      <p className="mt-md text-sm font-medium text-on-surface-variant dark:text-slate-300">
                        {selectedWorkspaceId
                          ? t("chat.askToStart")
                          : t("chat.pickWorkspaceFirst")}
                      </p>
                    </div>
                  </div>
                ) : (
                  messages.map((message, messageIndex) => {
                    const assistant = message.role === "assistant";
                    const isError = message.status === "error";
                    const isThinking = message.status === "queued" || message.status === "thinking";
                    const isCompleted = message.status === "completed";
                    const previousUserMessage = assistant
                      ? [...messages]
                          .slice(0, messageIndex)
                          .reverse()
                          .find((item) => item.role === "user")
                      : undefined;
                    const shouldShowTicketAction = assistant && isCompleted && !message.id.startsWith("local-");
                    return (
                    <article
                      key={message.id}
                      className="mx-auto flex w-full max-w-6xl items-start gap-sm sm:gap-md"
                    >
                      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-white sm:h-9 sm:w-9 ${assistant ? "bg-primary" : "bg-blue-600"}`}>
                        {assistant ? <Bot className="h-5 w-5" aria-hidden="true" /> : <UserRound className="h-5 w-5" aria-hidden="true" />}
                      </div>
                      <div className="min-w-0 max-w-[calc(100%-2.5rem)] overflow-hidden sm:max-w-[88%] xl:max-w-[85%]">
                        <div className="mb-xs flex items-center justify-between gap-lg text-xs"><span className="font-semibold text-on-surface dark:text-slate-100">{assistant ? "LexiGuard AI" : t("chat.role.user")}</span><span className="text-on-surface-variant">{message.timestamp}</span></div>
                        <div
                          className={`min-w-0 overflow-hidden rounded-xl border p-sm text-sm leading-6 shadow-sm [overflow-wrap:anywhere] sm:p-md ${
                            assistant
                              ? "border-legal-border bg-white text-on-surface dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                              : "border-blue-100 bg-blue-50 text-on-surface dark:border-blue-900 dark:bg-blue-950/40 dark:text-slate-100"
                          }`}
                        >
                        {assistant && isThinking ? (
                          <div className="flex items-center gap-sm" role="status" aria-live="polite">
                            <span>{message.statusMessage ?? t("chat.thinking")}</span>
                            <span className="flex gap-1" aria-hidden="true">
                              {[0, 1, 2].map((index) => <span key={index} className="h-1.5 w-1.5 rounded-full bg-primary motion-safe:animate-pulse" style={{ animationDelay: `${index * 160}ms` }} />)}
                            </span>
                          </div>
                        ) : assistant && isError ? (
                          <div className="space-y-sm">
                            <p className="font-medium text-error">{t("chat.responseFailed")}</p>
                            <p className="text-sm text-on-surface-variant dark:text-slate-400">
                              {t("chat.messageSendError")}
                            </p>
                            {lastSubmissionRef.current && (
                              <Button
                                variant="secondary"
                                size="sm"
                                onClick={() => {
                                  const payload = lastSubmissionRef.current;
                                  if (!payload) return;
                                  setSelectedWorkspaceId(payload.workspaceId);
                                  setSelectedSessionId(payload.sessionId);
                                  setSelectedDocumentId(payload.documentId ?? "");
                                  void handleSend(payload);
                                }}
                              >
                                {t("actions.tryAgain")}
                              </Button>
                            )}
                          </div>
                        ) : (
                          <>
                            <ChatMessageContent
                              content={message.content}
                              className="text-on-surface dark:text-slate-100"
                            />
                            {message.intent === "CONTRACT_PROMPT_GENERATION" && message.draftingPrompt && (
                              <div className="mt-md rounded-lg border border-legal-border bg-surface-container-low p-md dark:border-slate-700 dark:bg-slate-800">
                                <p className="mb-sm text-xs font-semibold uppercase tracking-wide text-on-surface-variant dark:text-slate-300">
                                  Drafting prompt {message.redactionRequired ? "· dữ liệu nhạy cảm đã được ẩn" : ""}
                                </p>
                                <pre className="max-h-80 overflow-auto whitespace-pre-wrap rounded-md bg-white p-sm text-xs text-on-surface dark:bg-slate-950 dark:text-slate-100">
                                  {message.draftingPrompt}
                                </pre>
                                <div className="mt-sm flex flex-wrap gap-sm">
                                  <Button
                                    variant="secondary"
                                    size="sm"
                                    leftIcon={<ClipboardCheck className="h-4 w-4" />}
                                    onClick={() => void navigator.clipboard.writeText(message.draftingPrompt ?? "")}
                                  >
                                    Sao chép prompt
                                  </Button>
                                  <Button
                                    variant="secondary"
                                    size="sm"
                                    onClick={() => window.open("https://chatgpt.com/", "_blank", "noopener,noreferrer")}
                                  >
                                    Mở ChatGPT
                                  </Button>
                                </div>
                              </div>
                            )}
                            {message.status === "streaming" && <span className="ml-1 inline-block h-4 w-0.5 bg-primary motion-safe:animate-pulse" aria-hidden="true" />}
                            {message.status === "cancelled" && <p className="mt-sm text-xs font-medium text-on-surface-variant">{message.statusMessage ?? t("chat.status.stopped")}</p>}
                            {assistant && isCompleted && !message.id.startsWith('local-') && <ChatMessageFeedbackControls messageId={message.id} language={language} />}
                            {shouldShowTicketAction && (
                              <div className="mt-md rounded-lg border border-legal-border bg-surface-container-low p-sm dark:border-slate-700 dark:bg-slate-800">
                                <div className="flex flex-wrap gap-xs">
                                  {message.riskLevel && (
                                    <Badge tone={message.riskLevel.toUpperCase() === "HIGH" ? "red" : "amber"}>
                                      {localizeEnumValue(message.riskLevel, riskLevelLabelKeys, t)}
                                    </Badge>
                                  )}
                                  {typeof message.confidenceScore === "number" && (
                                    <Badge tone="blue">
                                      {t("chat.confidence")} {formatPercent(message.confidenceScore * 100, locale)}
                                    </Badge>
                                  )}
                                  {message.suggestionType && (
                                    <Badge tone="purple">{localizeEnumValue(message.suggestionType, suggestionTypeLabelKeys, t)}</Badge>
                                  )}
                                </div>
                                {message.suggestionReason && (
                                  <p className="mt-sm text-xs text-on-surface-variant dark:text-slate-400">
                                    {message.suggestionReason}
                                  </p>
                                )}
                                <div className="mt-sm flex flex-wrap items-center gap-sm">
                                  <Button
                                    variant="secondary"
                                    size="sm"
                                    leftIcon={<ClipboardCheck className="h-4 w-4" />}
                                    onClick={() => handleCreateTicket(message, previousUserMessage)}
                                    disabled={creatingTicketMessageId === message.id}
                                  >
                                    {creatingTicketMessageId === message.id
                                      ? t("chat.creatingTicket")
                                      : t("chat.createTicket")}
                                  </Button>
                                  {ticketNotices[message.id] && (
                                    <span className="text-xs font-semibold text-emerald-700 dark:text-emerald-300">
                                      {ticketNotices[message.id]}
                                    </span>
                                  )}
                                </div>
                              </div>
                            )}
                          </>
                        )}
                        <div className="mt-sm flex items-center justify-end gap-sm text-[11px] opacity-70">
                          {isCompleted && !message.id.startsWith("local-") && (
                            <button
                              type="button"
                              className="font-semibold text-primary hover:underline dark:text-inverse-primary"
                              onClick={() => void handleOpenMessageDetail(message.id)}
                            >
                              {t("actions.details")}
                            </button>
                          )}
                        </div>
                        </div>
                      </div>
                    </article>
                  );
                })
              )}
              </div>

              {showNewResponseButton && (
                <button
                  type="button"
                  onClick={() => scrollChatToBottom()}
                  className="absolute bottom-sm left-1/2 z-10 flex max-w-[calc(100%-1rem)] -translate-x-1/2 items-center gap-xs whitespace-nowrap rounded-full border border-primary/30 bg-white px-md py-sm text-xs font-semibold text-primary shadow-lg hover:bg-primary/5 dark:bg-slate-900"
                >
                  <ArrowDown className="h-4 w-4" />
                  {t("chat.viewNewResponse")}
                </button>
              )}
            </div>

            <form
              className="shrink-0"
              onSubmit={(event) => {
                event.preventDefault();
                void handleSend();
              }}
            >
              {chatBlockedByAttachedDocuments && (
                <p className="mb-sm rounded-lg bg-amber-50 px-md py-sm text-xs font-semibold text-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
                  {failedAttachedDocuments.length > 0
                    ? t("chat.documents.failedBlockMessage")
                    : t("chat.documents.processingBlockMessage")}
                </p>
              )}
              <label className="sr-only" htmlFor="legal-chat-input">
                {t("chat.inputLabel")}
              </label>
              <div className="rounded-xl border border-legal-border bg-white p-xs shadow-sm dark:border-slate-700 dark:bg-slate-950 2xl:p-sm">
                <div className="flex items-end gap-xs sm:gap-sm">
                <textarea
                  ref={chatInputRef}
                  id="legal-chat-input"
                  rows={1}
                  className="max-h-28 min-h-10 min-w-0 flex-1 resize-none border-0 bg-transparent px-sm py-sm outline-none placeholder:text-on-surface-variant"
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" && !event.shiftKey) {
                      event.preventDefault();
                      if (input.trim() && selectedWorkspaceId && !sending && !chatBlockedByAttachedDocuments) void handleSend();
                    }
                  }}
                  placeholder={attachedDocuments.length > 0
                    ? (language === "vi" ? "Hỏi về tài liệu hoặc vấn đề pháp lý liên quan..." : "Ask about the document or a related legal issue...")
                    : (language === "vi" ? "Nhập câu hỏi pháp luật của bạn..." : "Enter your legal question...")}
                  disabled={!selectedWorkspaceId || chatBlockedByAttachedDocuments}
                />
                {sending ? (
                  <Button
                    type="button"
                    size="icon"
                    variant="danger"
                    aria-label={t("chat.stopResponse")}
                    title={t("chat.stopResponse")}
                    onClick={handleStopResponse}
                  >
                    <Square className="h-4 w-4 fill-current" />
                  </Button>
                ) : (
                  <Button
                    type="submit"
                    size="icon"
                    aria-label={t("actions.ask")}
                    disabled={!input.trim() || !selectedWorkspaceId || chatBlockedByAttachedDocuments}
                  >
                    <Send className="h-5 w-5" />
                  </Button>
                )}
                </div>
                <div className="mt-xs flex max-w-full gap-xs overflow-x-auto border-t border-legal-border pt-xs dark:border-slate-800 2xl:mt-sm 2xl:pt-sm">
                  <Button className="min-h-9 shrink-0" type="button" size="sm" variant="ghost" leftIcon={<Paperclip className="h-4 w-4" />} onClick={() => void handleOpenDocumentModal()}>{t("chat.documents.add")}</Button>
                  <Button className="min-h-9 shrink-0" type="button" size="sm" variant="ghost" onClick={() => setInput(t("chat.quick.extractPrompt"))}>{t("chat.quick.extract")}</Button>
                  <Button className="min-h-9 shrink-0" type="button" size="sm" variant="ghost" onClick={() => setInput(t("chat.quick.summarizePrompt"))}>{t("chat.quick.summarize")}</Button>
                  <Button className="min-h-9 shrink-0" type="button" size="sm" variant="ghost" leftIcon={<MoreHorizontal className="h-4 w-4" />} onClick={() => setActiveDrawer("settings")}>{t("chat.more")}</Button>
                </div>
              </div>
            </form>
          </div>
        </section>
      </div>

      {ticketDraft && <CreateTicketModal
        open
        workspaceId={selectedWorkspaceId}
        sessionId={selectedSessionId}
        userMessageId={ticketDraft.user?.id}
        assistantMessageId={ticketDraft.assistant.id}
        requestId={ticketDraft.assistant.requestId ?? undefined}
        question={ticketDraft.user?.content ?? ticketDraft.assistant.suggestionReason ?? t("chat.ticketVerificationFallback")}
        answer={ticketDraft.assistant.content}
        documents={attachedDocuments.map((document) => ({ id: document.documentId, name: document.originalFileName }))}
        focusedDocumentId={selectedDocumentId || undefined}
        citationIds={Array.from(ticketDraft.assistant.content.matchAll(/\[((?:KB|USER)-\d+)]/gi), (match) => match[1].toUpperCase())}
        submitting={creatingTicketMessageId === ticketDraft.assistant.id}
        onClose={() => setTicketDraft(null)}
        onSubmit={submitTicket}
      />}

      <Modal
        open={policyModalOpen}
        title="Chấp thuận chính sách xử lý tài liệu"
        onClose={() => !policyAccepting && setPolicyModalOpen(false)}
      >
        <div className="space-y-md text-sm text-on-surface dark:text-slate-100">
          <p>
            Trước khi tải lên hoặc phân tích tài liệu, bạn cần chấp thuận phiên bản hiện hành của
            Điều khoản sử dụng và Chính sách xử lý dữ liệu. Câu hỏi pháp lý chung không dùng tài liệu vẫn hoạt động.
          </p>
          <label className="flex items-start gap-sm rounded-lg border border-legal-border p-md dark:border-slate-700">
            <input
              type="checkbox"
              className="mt-1"
              checked={policyAccepted}
              onChange={(event) => setPolicyAccepted(event.target.checked)}
            />
            <span>Tôi đã đọc và đồng ý với Điều khoản sử dụng và Chính sách xử lý dữ liệu hiện hành.</span>
          </label>
          <div className="flex justify-end gap-sm">
            <Button variant="secondary" disabled={policyAccepting} onClick={() => setPolicyModalOpen(false)}>
              Hủy
            </Button>
            <Button
              disabled={!policyAccepted || policyAccepting}
              onClick={() => void (async () => {
                setPolicyAccepting(true);
                try {
                  await acceptCurrentPolicies(getAccessToken());
                  setPolicyModalOpen(false);
                  setPolicyAccepted(false);
                  await handleUploadAndAttach();
                } catch {
                  toast.error("Không thể lưu chấp thuận điều khoản.");
                } finally {
                  setPolicyAccepting(false);
                }
              })()}
            >
              {policyAccepting ? "Đang lưu..." : "Đồng ý và tiếp tục"}
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        open={documentModalOpen}
        title={t("chat.documentModal.title")}
        onClose={() => setDocumentModalOpen(false)}
        footer={<div className="flex w-full justify-end"><Button variant="secondary" onClick={() => setDocumentModalOpen(false)}>{t("actions.close")}</Button></div>}
      >
        <div className="space-y-lg">
          {documentActionError && <p className="rounded-lg bg-error/10 p-sm text-sm text-error">{documentActionError}</p>}
          <section className="space-y-sm">
            <div className="space-y-xs">
              <p className="font-semibold">{t("chat.documentModal.uploadTitle")}</p>
              <p className="text-xs text-on-surface-variant dark:text-slate-400">{t("chat.documentModal.uploadDescription")}</p>
            </div>
            <p className="text-xs text-on-surface-variant dark:text-slate-400">{supportedContractScopeText(language)} {t("chat.documentModal.autoDetectHint")}</p>
            <div className="grid gap-sm sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
              <input
                className="form-field h-11 min-w-0 px-0 py-0 pr-md text-sm file:mr-md file:h-full file:border-0 file:border-r file:border-outline-variant file:bg-surface-container-low file:px-md file:text-sm file:font-semibold file:text-primary dark:file:border-slate-700 dark:file:bg-slate-800 dark:file:text-inverse-primary"
                type="file"
                aria-label={t("chat.documentModal.uploadTitle")}
                onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
              />
              <Button className="h-11 w-full sm:min-w-40 sm:w-auto" disabled={!uploadFile || documentActionBusy} onClick={() => void handleUploadAndAttach()}>{documentActionBusy ? t("chat.documentModal.processing") : t("chat.documentModal.uploadAndAttach")}</Button>
            </div>
          </section>
          <section className="space-y-sm">
            <p className="font-semibold">{t("chat.documentModal.chooseFromWorkspace")}</p>
            <div className="max-h-72 space-y-sm overflow-y-auto">
              {workspaceDocuments.length === 0 ? <p className="text-sm text-on-surface-variant">{t("chat.noDocuments")}</p> : workspaceDocuments.map((document) => {
                const attached = attachedDocuments.some((item) => item.documentId === document.documentId);
                return <div key={document.documentId} className="flex flex-col gap-sm rounded-lg border border-legal-border p-sm dark:border-slate-700 sm:flex-row sm:items-center sm:justify-between sm:gap-md">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold" title={document.originalFileName}>{document.originalFileName}</p>
                    <div className="mt-xs"><StatusBadge status={document.status} /></div>
                    <DocumentProcessingProgress status={document.status} />
                    {document.errorMessage && <p className="mt-xs text-xs text-error">{t("upload.documentProcessingError")}</p>}
                  </div>
                  <div className="flex flex-wrap justify-end gap-xs">
                    <Button size="icon" variant="ghost" aria-label={t("actions.download")} onClick={() => void handleDownloadWorkspaceDocument(document)}><Download className="h-4 w-4" /></Button>
                    {attached ? (
                      <Button size="sm" variant="danger" leftIcon={<Trash2 className="h-4 w-4" />} disabled={documentActionBusy} onClick={() => void handleDetachDocument(document.documentId)}>{t("chat.documentModal.removeFromChat")}</Button>
                    ) : (
                      <Button size="sm" variant="secondary" disabled={documentActionBusy} onClick={() => void handleAttachDocument(document.documentId)}>{t("chat.documentModal.attach")}</Button>
                    )}
                  </div>
                </div>;
              })}
            </div>
          </section>
        </div>
      </Modal>

      <Modal
        open={Boolean(shareUrl)}
        title={t("chat.shareModal.title")}
        onClose={() => setShareUrl('')}
        footer={<Button variant="secondary" onClick={() => setShareUrl('')}>{t('actions.close')}</Button>}
      >
        <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("chat.shareModal.description")}</p>
        <div className="mt-md flex gap-sm"><input className="form-field" readOnly value={shareUrl} /><Button onClick={() => void navigator.clipboard.writeText(shareUrl)}>{t("chat.shareModal.copy")}</Button></div>
      </Modal>

      <Modal
        open={messageDetailOpen}
        title={t("chat.messageDetailTitle")}
        onClose={() => setMessageDetailOpen(false)}
        footer={
          <Button variant="secondary" onClick={() => setMessageDetailOpen(false)}>
            {t("actions.close")}
          </Button>
        }
      >
        {messageDetailLoading ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("chat.loadingMessageMetadata")}</p>
        ) : messageDetail ? (
          <div className="space-y-md text-sm">
            <dl className="grid gap-md sm:grid-cols-2">
              <div><dt className="label-uppercase">{t("chat.messageId")}</dt><dd className="mt-xs break-all font-semibold">{messageDetail.messageId}</dd></div>
              <div><dt className="label-uppercase">{t("chat.sessionIdLabel")}</dt><dd className="mt-xs break-all">{messageDetail.chatSessionId}</dd></div>
              <div><dt className="label-uppercase">{t("chat.role")}</dt><dd className="mt-xs">{localizeEnumValue(messageDetail.role, messageRoleLabelKeys, t)}</dd></div>
              <div><dt className="label-uppercase">{t("chat.status")}</dt><dd className="mt-xs"><Badge tone="slate">{localizeEnumValue(messageDetail.status, messageStatusLabelKeys, t)}</Badge></dd></div>
              <div><dt className="label-uppercase">{t("chat.model")}</dt><dd className="mt-xs">{messageDetail.aiModel ?? '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.requestId")}</dt><dd className="mt-xs break-all">{messageDetail.requestId ?? '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.promptTokens")}</dt><dd className="mt-xs">{typeof messageDetail.promptTokens === "number" ? formatNumber(messageDetail.promptTokens, locale) : "-"}</dd></div>
              <div><dt className="label-uppercase">{t("chat.completionTokens")}</dt><dd className="mt-xs">{typeof messageDetail.completionTokens === "number" ? formatNumber(messageDetail.completionTokens, locale) : "-"}</dd></div>
              <div><dt className="label-uppercase">{t("chat.totalTokens")}</dt><dd className="mt-xs">{typeof messageDetail.totalTokens === "number" ? formatNumber(messageDetail.totalTokens, locale) : "-"}</dd></div>
              <div><dt className="label-uppercase">{t("chat.confidence")}</dt><dd className="mt-xs">{typeof messageDetail.confidenceScore === "number" ? formatPercent(messageDetail.confidenceScore * 100, locale) : "-"}</dd></div>
              <div><dt className="label-uppercase">{t("chat.riskLevel")}</dt><dd className="mt-xs">{localizeEnumValue(messageDetail.riskLevel, riskLevelLabelKeys, t)}</dd></div>
              <div><dt className="label-uppercase">{t("chat.legalDomain")}</dt><dd className="mt-xs">{messageDetail.legalDomain ?? '-'}</dd></div>
            </dl>
            {messageDetail.suggestionReason && (
              <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <p className="label-uppercase mb-xs">{t("chat.suggestionReason")}</p>
                {messageDetail.suggestionReason}
              </div>
            )}
            <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
              <p className="label-uppercase mb-xs">{t("chat.aiCitations")}</p>
              {messageCitations.length === 0 ? (
                <p className="text-on-surface-variant dark:text-slate-400">
                  {t("common.noData")}
                </p>
              ) : (
                <div className="space-y-sm">
                  {messageCitations.map((citation) => (
                    <article key={citation.id} className="rounded-lg border border-legal-border p-sm dark:border-slate-700">
                      <p className="font-semibold">{citation.label || citation.sourceReferenceId || citation.id}</p>
                      <p className="mt-xs text-on-surface-variant dark:text-slate-400">
                        {citation.excerpt || citation.uri || "-"}
                      </p>
                      <div className="mt-sm flex flex-wrap items-center justify-between gap-xs">
                        <div className="flex flex-wrap gap-xs">
                          {citation.sourceType && <Badge>{localizeEnumValue(citation.sourceType, citationSourceTypeLabelKeys, t)}</Badge>}
                          {typeof citation.score === "number" && (
                            <Badge tone="blue">{formatPercent(citation.score * 100, locale)}</Badge>
                          )}
                          {typeof citation.pageNumber === "number" && (
                            <Badge tone="gold">{t("common.page")} {formatNumber(citation.pageNumber, locale)}</Badge>
                          )}
                        </div>
                        {citation.uri && (
                          <Button
                            size="sm"
                            variant="secondary"
                            leftIcon={<Download className="h-4 w-4" />}
                            onClick={async () => {
                              try {
                                const response = await fetch(citation.uri!, {
                                  headers: {
                                    Authorization: `Bearer ${getAccessToken()}`,
                                  },
                                });
                                if (!response.ok) throw new Error("Tải file thất bại");
                                const blob = await response.blob();
                                const blobUrl = URL.createObjectURL(blob);
                                const anchor = document.createElement("a");
                                anchor.href = blobUrl;
                                let fileName = citation.label || "document";
                                if (citation.uri!.includes("filename=")) {
                                  const match = citation.uri!.match(/filename=([^&]+)/);
                                  if (match) {
                                    fileName = decodeURIComponent(match[1]);
                                  }
                                }
                                anchor.download = fileName;
                                anchor.click();
                                window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
                              } catch (downloadError) {
                                toast.error(
                                  downloadError instanceof Error ? downloadError.message : "Unable to download document."
                                );
                              }
                            }}
                          >
                            {language === "vi" ? "Tải xuống" : "Download"}
                          </Button>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </div>
        ) : (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("chat.noMessageDetail")}</p>
        )}
      </Modal>

      {/* Custom Chat Session Delete Confirmation Modal */}
      {isDeleteModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/70 p-md" role="dialog" aria-modal="true">
          <div className="w-full max-w-md overflow-hidden rounded-2xl border border-slate-800 bg-[#202123] p-lg shadow-2xl text-left">
            <h3 className="text-lg font-bold text-white">
              {t("chat.deleteSessionModalTitle")}
            </h3>
            <p className="mt-md text-sm text-slate-300">
              {t("chat.deleteSessionModalBodyPrefix")}
              <strong className="font-semibold text-white">{sessionToDelete?.title || t("chat.defaultSession")}</strong>
              {t("chat.deleteSessionModalBodySuffix")}
            </p>
            <div className="flex justify-end gap-sm mt-lg">
              <button
                type="button"
                className="px-lg py-sm rounded-full border border-slate-600 text-slate-200 hover:bg-slate-800 text-sm font-semibold transition"
                onClick={() => {
                  setIsDeleteModalOpen(false);
                  setSessionToDelete(null);
                }}
              >
                {t("actions.cancel")}
              </button>
              <button
                type="button"
                className="px-lg py-sm rounded-full bg-[#ff003c] text-white hover:bg-red-700 text-sm font-semibold transition disabled:opacity-50"
                onClick={() => void handleConfirmDeleteSession()}
                disabled={sessionActionBusyId === (sessionToDelete?.chatSessionId ?? "")}
              >
                {t("actions.delete")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
