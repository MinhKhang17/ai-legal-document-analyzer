import { ArrowDown, Bot, Check, ClipboardCheck, Download, FileText, Files, Info, MoreHorizontal, Paperclip, Pencil, Plus, RefreshCw, Send, Settings, Share2, Square, Trash2, UserRound, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
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
import { formatDisplayDateTime } from "../../utils/format";
import { simulateStreaming } from "../../utils/simulateStreaming";
import { supportedContractScopeText } from "../../config/supportedContractTypes";

import { getAccessToken as getSessionAccessToken } from "../../services/authSession";
const getAccessToken = () => getSessionAccessToken() ?? "";

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

function DocumentProcessingProgress({ status, language }: { status?: string; language: "en" | "vi" }) {
  if (isDocumentReady(status)) {
    return (
      <div className="mt-xs" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={100}>
        <div className="h-1.5 overflow-hidden rounded-full bg-emerald-100 dark:bg-emerald-950/50">
          <div className="h-full w-full rounded-full bg-emerald-500" />
        </div>
        <p className="mt-1 text-[11px] font-medium text-emerald-700 dark:text-emerald-300">
          {language === "vi" ? "Đã xử lý xong" : "Processing complete"}
        </p>
      </div>
    );
  }

  if (isDocumentFailed(status)) {
    return (
      <p className="mt-xs text-[11px] font-medium text-error">
        {language === "vi" ? "Xử lý thất bại — hãy bỏ file hoặc upload lại." : "Processing failed — remove or upload the file again."}
      </p>
    );
  }

  return (
    <div className="mt-xs" role="progressbar" aria-label={language === "vi" ? "Tài liệu đang được xử lý" : "Document is processing"}>
      <div className="h-1.5 overflow-hidden rounded-full bg-amber-100 dark:bg-amber-950/50">
        <div className="h-full w-2/3 animate-pulse rounded-full bg-amber-500" />
      </div>
      <p className="mt-1 text-[11px] font-medium text-amber-700 dark:text-amber-300">
        {language === "vi" ? "AI đang xử lý, bạn có thể rời trang." : "AI is processing; you may leave this page."}
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
  const [documentModalOpen, setDocumentModalOpen] = useState(false);
  const [documentActionBusy, setDocumentActionBusy] = useState(false);
  const [openingDocumentModal, setOpeningDocumentModal] = useState(false);
  const [documentActionError, setDocumentActionError] = useState("");
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [messageCitations, setMessageCitations] = useState<AiCitation[]>([]);
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);
  const chatInputRef = useRef<HTMLTextAreaElement>(null);
  const activeRequestControllerRef = useRef<AbortController | null>(null);
  const shouldAutoScrollRef = useRef(true);
  const [showNewResponseButton, setShowNewResponseButton] = useState(false);
  const lastSubmissionRef = useRef<{
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
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : t("chat.loadWorkspaceError"));
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
  }, [searchParams, setSearchParams]);

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
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : t("chat.loadWorkspaceDataError"));
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
  }, [searchParams, selectedWorkspaceId, setSearchParams]);

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
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : t("chat.loadHistoryError"));
        }
      }
    };

    void loadMessages();

    return () => {
      active = false;
    };
  }, [language, selectedSessionId]);

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
      } catch (err) {
        if (active) setDocumentActionError(err instanceof Error ? err.message : "Khong the tai tai lieu dinh kem");
      }
    };
    void loadAttached();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [selectedSessionId]);

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

  useEffect(() => () => activeRequestControllerRef.current?.abort(), []);

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
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.sessionCreateError");
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
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.sessionCreateError");
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
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.sessionRenameError");
      setSessionActionError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setSessionActionBusyId("");
    }
  };

  const handleDeleteSession = async (chatSessionId: string) => {
    if (!window.confirm(t("chat.sessionDeleteConfirm"))) {
      return;
    }

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
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.sessionDeleteError");
      setSessionActionError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setSessionActionBusyId("");
    }
  };

  const getCreateTicketErrorMessage = (err: unknown) => {
    if (!(err instanceof Error)) return t("chat.ticketCreateError");
    const normalizedMessage = err.message.toUpperCase();
    if (normalizedMessage.includes("AI_ANALYSIS_NOT_FOUND") || normalizedMessage.includes("ANALYSIS_NOT_FOUND")) {
      return t("chat.ticketMissingAnalysis");
    }
    return err.message || t("chat.ticketCreateError");
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
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.messageDetailError");
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
      toast.warning(language === "vi" ? "Vui lòng chọn workspace trước." : "Select a workspace first.");
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
          language === "vi" ? "Phiên làm việc mới" : "New conversation",
        );
        sessionId = session.chatSessionId;
        setChatSessions((current) => [session, ...current]);
      }
      setSelectedSessionId(sessionId);
      setSearchParams({ workspaceId: selectedWorkspaceId, sessionId });
      setDocumentModalOpen(true);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Không thể mở trình thêm tài liệu";
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
      toast.success(language === "vi" ? "Da them tai lieu vao phien chat." : "Document attached.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Khong the them tai lieu";
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
      toast.success(language === "vi" ? "Da upload va them tai lieu. He thong dang xu ly file." : "Uploaded and attached. Processing started.");
    } catch (err) {
      const raw = err instanceof Error ? err.message : "Upload that bai";
      const message = /quota|storage|limit/i.test(raw) ? (language === "vi" ? "Quota da het. Vui long nang cap goi hoac xoa bot tai lieu." : "Quota is exhausted.") : raw;
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
      toast.success(language === "vi" ? "Đã bỏ tài liệu khỏi phiên chat." : "Document detached from chat.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Khong the go tai lieu");
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
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Khong the tai tai lieu");
    }
  };

  const handleSend = async (override?: {
    message: string;
    workspaceId: string;
    sessionId?: string;
    documentId?: string;
  }) => {
    if (sending || activeRequestControllerRef.current) return;

    const question = (override?.message ?? input).trim();
    const targetWorkspaceId = override?.workspaceId ?? selectedWorkspaceId;
    const targetSessionId = override?.sessionId ?? selectedSessionId;
    const targetDocumentId = override?.documentId ?? (selectedDocumentId || undefined);

    if (!question || !targetWorkspaceId) {
      return;
    }

    if (!targetSessionId) {
      return;
    }

    if (chatBlockedByAttachedDocuments) {
      const message = failedAttachedDocuments.length > 0
        ? (language === "vi" ? "Có tài liệu đính kèm xử lý thất bại. Hãy bỏ tài liệu đó hoặc upload lại trước khi chat." : "An attached document failed processing. Remove it or upload it again before chatting.")
        : (language === "vi" ? "Vui lòng đợi tất cả tài liệu đính kèm xử lý xong trước khi chat." : "Please wait until all attached documents finish processing before chatting.");
      setError(message);
      toast.error(message);
      return;
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
        statusMessage: language === "vi" ? "Đang phân tích câu hỏi và nguồn tài liệu…" : "Analyzing your question and sources…",
      },
    ]);
    setInput("");
    setSending(true);
    setError("");
    shouldAutoScrollRef.current = true;
    window.requestAnimationFrame(() => chatInputRef.current?.focus());
    lastSubmissionRef.current = {
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
            }
          : item));
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        setMessages((previous) => previous.map((item) => item.id === assistantMessageId
          ? {
              ...item,
              status: "cancelled",
              statusMessage: language === "vi" ? "Đã dừng trả lời." : "Response stopped.",
            }
          : item));
        return;
      }
      const message = err instanceof Error ? err.message : t("chat.messageSendError");
      setError(message);
      setMessages((previous) => previous.map((item) => item.id === assistantMessageId
        ? { ...item, status: "error", errorMessage: message }
        : item));
      toast.error(message, t("toast.errorTitle"));
    } finally {
      if (activeRequestControllerRef.current === controller) {
        activeRequestControllerRef.current = null;
      }
      setSending(false);
      window.requestAnimationFrame(() => chatInputRef.current?.focus());
    }
  };

  const handleStopResponse = () => {
    activeRequestControllerRef.current?.abort();
  };

  return (
    <div>
      <PageHeader
        title={`${language === "vi" ? "Phiên chat" : "Chat session"}: ${selectedSession?.title ?? (language === "vi" ? "Cuộc trò chuyện mới" : "New conversation")}`}
        subtitle={`${language === "vi" ? "Dự án" : "Workspace"}: ${selectedWorkspace?.name ?? "—"}`}
        actions={
          <>
            {selectedSessionId && (
              <Button
                variant="secondary"
                leftIcon={<Download className="h-4 w-4" />}
                disabled={!canExportMarkdown || exportingMarkdown}
                title={!canExportMarkdown ? (language === "vi" ? "Phiên chưa có kết quả phân tích để xuất" : "No analysis content to export") : undefined}
                onClick={async () => {
                  setExportingMarkdown(true);
                  try {
                    await exportChatSessionMarkdown(getAccessToken(), selectedSessionId);
                    toast.success(language === "vi" ? "Đã tải bản phân tích Markdown." : "Markdown analysis downloaded.");
                  } catch (exportError) {
                    toast.error(exportError instanceof Error ? exportError.message : "Unable to export Markdown.");
                  } finally {
                    setExportingMarkdown(false);
                  }
                }}
              >
                {exportingMarkdown ? (language === "vi" ? "Đang xuất…" : "Exporting…") : "Export Markdown"}
              </Button>
            )}
            {selectedSessionId && (
              <Button
                variant="secondary"
                leftIcon={<Share2 className="h-4 w-4" />}
                disabled={sharing}
                onClick={async () => {
                  setSharing(true);
                  try {
                    const shared = await shareChatSession(getAccessToken(), selectedSessionId);
                    setShareUrl(shared.shareUrl);
                    await navigator.clipboard.writeText(shared.shareUrl);
                    toast.success(language === "vi" ? "Đã sao chép liên kết chia sẻ." : "Share link copied.");
                  } catch (shareError) {
                    toast.error(shareError instanceof Error ? shareError.message : "Unable to share chat.");
                  } finally {
                    setSharing(false);
                  }
                }}
              >
                {language === "vi" ? "Chia sẻ" : "Share"}
              </Button>
            )}
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => {
                if (!selectedWorkspaceId) return;
                setSearchParams({
                  workspaceId: selectedWorkspaceId,
                  ...(selectedSessionId ? { sessionId: selectedSessionId } : {}),
                });
              }}
            >
              {t("chat.refreshContext")}
            </Button>
            <Button
              leftIcon={<Plus className="h-4 w-4" />}
              onClick={handleCreateSession}
              disabled={!selectedWorkspaceId}
            >
              {t("chat.newSession")}
            </Button>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <div className="grid gap-md xl:grid-cols-[minmax(0,1fr)_60px]">
        {activeDrawer && <button type="button" aria-label={language === "vi" ? "Đóng bảng thông tin" : "Close drawer"} className="fixed inset-0 z-30 bg-slate-950/30" onClick={() => setActiveDrawer(null)} />}
        <aside className={`fixed inset-y-0 right-0 z-40 w-[min(400px,calc(100vw-24px))] overflow-y-auto border-l border-legal-border bg-white p-md shadow-2xl transition-transform dark:border-slate-700 dark:bg-slate-950 ${activeDrawer ? "translate-x-0" : "translate-x-full"}`}>
          <div className="mb-md flex items-center justify-between"><p className="font-semibold">{activeDrawer === "session" ? (language === "vi" ? "Thông tin phiên chat" : "Chat information") : activeDrawer === "documents" ? (language === "vi" ? "Tài liệu workspace" : "Workspace documents") : (language === "vi" ? "Tùy chọn trò chuyện" : "Chat options")}</p><Button size="icon" variant="ghost" aria-label={t("actions.close")} onClick={() => setActiveDrawer(null)}><X className="h-5 w-5" /></Button></div>
          <Card className={activeDrawer === "session" ? "" : "hidden"} title={language === "vi" ? "Thông tin phiên chat" : "Chat session information"}>
            <div className="space-y-md">
              <dl className="space-y-sm text-sm">
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">ID phiên chat</dt><dd className="max-w-40 truncate font-medium" title={selectedSessionId}>{selectedSessionId || "—"}</dd></div>
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">{language === "vi" ? "Dự án" : "Workspace"}</dt><dd className="text-right font-medium">{selectedWorkspace?.name ?? "—"}</dd></div>
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">{language === "vi" ? "Tạo lúc" : "Created"}</dt><dd className="text-right font-medium">{selectedSession ? formatDisplayDateTime(selectedSession.createdAt, locale) : "—"}</dd></div>
                <div className="flex justify-between gap-md"><dt className="text-on-surface-variant">{language === "vi" ? "Cập nhật cuối" : "Last update"}</dt><dd className="text-right font-medium">{selectedSession ? formatDisplayDateTime(selectedSession.updatedAt, locale) : "—"}</dd></div>
              </dl>
              <p className="label-uppercase">{language === "vi" ? "Chuyển dự án" : "Switch workspace"}</p>
              <select
                className="form-field"
                value={selectedWorkspaceId}
                onChange={(event) => {
                  const nextWorkspaceId = event.target.value;
                  setSelectedWorkspaceId(nextWorkspaceId);
                  setSelectedSessionId("");
                  setSelectedDocumentId("");
                  setMessages([]);
                  if (nextWorkspaceId) {
                    if (searchParams.get("workspaceId") !== nextWorkspaceId) {
                      setSearchParams({ workspaceId: nextWorkspaceId });
                    }
                  } else {
                    setSearchParams({});
                  }
                }}
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
                      Open detail
                    </Link>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  Create a workspace first or choose one with uploaded documents.
                </p>
              )}
            </div>
          </Card>

          <Card className={activeDrawer === "documents" ? "" : "hidden"} title={t("chat.documentsInWorkspace")} actions={<Button size="sm" leftIcon={<Paperclip className="h-4 w-4" />} disabled={!selectedWorkspaceId || openingDocumentModal} onClick={() => void handleOpenDocumentModal()}>{openingDocumentModal ? (language === "vi" ? "Đang mở…" : "Opening…") : (language === "vi" ? "Thêm tài liệu" : "Add document")}</Button>}>
            <div className="space-y-md">
              {loadingContext && (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("upload.loadingDocuments")}
                </p>
              )}

              {attachedDocuments.length === 0 && !loadingContext ? (
                <div className="rounded-lg border border-dashed border-outline-variant p-md text-sm text-on-surface-variant dark:border-slate-700 dark:text-slate-400">
                  <p className="font-semibold text-on-surface dark:text-slate-200">{language === "vi" ? "Đang hỏi đáp bằng kho tri thức hệ thống" : "Using the system knowledge base"}</p>
                  <p className="mt-xs">{language === "vi" ? "Bạn có thể hỏi kiến thức pháp lý do admin đã công khai, hoặc nhấn “Thêm tài liệu” để hỏi theo tài liệu riêng." : "Ask about legal knowledge published by admins, or select Add document to include your own documents."}</p>
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
                          {(document.size / 1024 / 1024).toFixed(2)} MB · {document.contentType || "-"}
                        </p>
                      </div>
                    </div>
                    <div className="mt-md flex items-center justify-between gap-sm">
                      <Badge tone={document.uploadStatus.toUpperCase() === "READY" ? "green" : document.uploadStatus.toUpperCase() === "FAILED" ? "red" : "amber"}>{document.uploadStatus}</Badge>
                      <Button size="sm" variant="danger" leftIcon={<Trash2 className="h-4 w-4" />} disabled={documentActionBusy} onClick={() => void handleDetachDocument(document.documentId)}>{language === "vi" ? "Bỏ tài liệu" : "Remove document"}</Button>
                    </div>
                    <DocumentProcessingProgress status={document.uploadStatus} language={language} />
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
                            aria-label={language === "vi" ? `Mở thao tác cho ${session.title}` : `Open actions for ${session.title}`}
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
                                  void handleDeleteSession(session.chatSessionId);
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

        <section className="order-1 min-w-0 space-y-gutter">
          <Card
            className="relative flex min-h-[680px] flex-col overflow-hidden border-legal-border p-0 xl:h-[calc(100vh-3rem)] xl:max-h-[900px]"
          >
            <div className="border-b border-legal-border bg-white p-md dark:border-slate-700 dark:bg-slate-900">
              <div className="mb-sm flex items-center justify-between gap-md">
                <p className="text-sm font-semibold">{language === "vi" ? `Tài liệu đang sử dụng trong phiên chat (${attachedDocuments.length})` : `Documents used in this chat (${attachedDocuments.length})`}</p>
                <Badge tone="gold">RAG</Badge>
              </div>
              <div className="flex gap-sm overflow-x-auto pb-xs">
                {attachedDocuments.map((document) => (
                  <div key={document.documentId} className="min-w-[240px] max-w-[300px] flex-1 rounded-xl border border-legal-border bg-surface-container-low p-sm dark:border-slate-700 dark:bg-slate-800">
                    <div className="flex items-center gap-sm">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-red-50 text-error dark:bg-red-950/40"><FileText className="h-5 w-5" /></div>
                      <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold" title={document.originalFileName}>{document.originalFileName}</p><p className="text-xs text-on-surface-variant">{(document.size / 1024 / 1024).toFixed(2)} MB</p></div>
                      <button type="button" className="rounded-md p-xs text-on-surface-variant hover:bg-surface-container-high hover:text-error" aria-label="Detach document" disabled={documentActionBusy} onClick={() => void handleDetachDocument(document.documentId)}><X className="h-4 w-4" /></button>
                    </div>
                    <DocumentProcessingProgress status={document.uploadStatus} language={language} />
                  </div>
                ))}
                <button type="button" onClick={() => void handleOpenDocumentModal()} disabled={!selectedWorkspaceId || openingDocumentModal} className="flex min-h-16 shrink-0 items-center justify-center gap-sm rounded-xl border border-dashed border-primary/40 bg-primary/5 px-md text-sm font-semibold text-primary transition hover:bg-primary/10 disabled:opacity-50"><Plus className="h-4 w-4" />{language === "vi" ? "Thêm tài liệu" : "Add document"}</button>
              </div>
              <div className={`mt-sm flex items-center gap-sm rounded-lg px-md py-sm text-xs font-medium ${chatBlockedByAttachedDocuments ? "bg-amber-50 text-amber-800 dark:bg-amber-950/30 dark:text-amber-200" : "bg-blue-50 text-blue-700 dark:bg-blue-950/30 dark:text-blue-200"}`}><Bot className="h-4 w-4 shrink-0" /><span className="min-w-0 flex-1">{chatBlockedByAttachedDocuments ? (failedAttachedDocuments.length > 0 ? (language === "vi" ? "Chat đang khóa vì có tài liệu xử lý thất bại. Hãy bỏ tài liệu đó hoặc upload lại." : "Chat is blocked because an attached document failed processing. Remove it or upload it again.") : (language === "vi" ? `Đang xử lý ${unavailableAttachedDocuments.length} tài liệu đính kèm. Chat sẽ mở khi tất cả tài liệu sẵn sàng.` : `Processing ${unavailableAttachedDocuments.length} attached document(s). Chat will unlock when all are ready.`)) : attachedDocuments.length > 0 ? (language === "vi" ? `AI đang trả lời dựa trên ${attachedDocuments.length} tài liệu đã chọn và kiến thức pháp luật được cập nhật.` : `AI is answering from ${attachedDocuments.length} selected document(s) and the published legal knowledge base.`) : (language === "vi" ? "Không có tài liệu đính kèm: AI đang trả lời bằng kho tri thức pháp luật do admin công khai." : "No documents attached: AI is answering from the admin-published legal knowledge base.")}</span><button type="button" className="shrink-0 font-semibold hover:underline" onClick={() => setActiveDrawer("documents")}>{language === "vi" ? "Xem nguồn" : "View sources"}</button></div>
            </div>
            <div
              ref={chatScrollContainerRef}
              onScroll={(event) => {
                const container = event.currentTarget;
                const nearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 120;
                shouldAutoScrollRef.current = nearBottom;
                if (nearBottom) setShowNewResponseButton(false);
              }}
              className="min-h-0 flex-1 space-y-md overflow-y-auto bg-surface-container-low/60 p-lg dark:bg-slate-950/40"
            >
                {messages.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-outline-variant p-lg text-sm text-on-surface-variant dark:border-slate-700 dark:text-slate-400">
                    {selectedWorkspaceId
                      ? t("chat.askToStart")
                      : t("chat.pickWorkspaceFirst")}
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
                      className="flex items-start gap-md"
                    >
                      <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-white ${assistant ? "bg-primary" : "bg-blue-600"}`}>
                        {assistant ? <Bot className="h-5 w-5" aria-hidden="true" /> : <UserRound className="h-5 w-5" aria-hidden="true" />}
                      </div>
                      <div className="min-w-0 max-w-[82%]">
                        <div className="mb-xs flex items-center justify-between gap-lg text-xs"><span className="font-semibold text-on-surface dark:text-slate-100">{assistant ? "LexiGuard AI" : (language === "vi" ? "Bạn" : "You")}</span><span className="text-on-surface-variant">{message.timestamp}</span></div>
                        <div
                          className={`rounded-xl border p-md text-sm leading-6 shadow-sm ${
                            assistant
                              ? "border-legal-border bg-white text-on-surface dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                              : "border-blue-100 bg-blue-50 text-on-surface dark:border-blue-900 dark:bg-blue-950/40 dark:text-slate-100"
                          }`}
                        >
                        {assistant && isThinking ? (
                          <div className="flex items-center gap-sm" role="status" aria-live="polite">
                            <span>{message.statusMessage ?? (language === "vi" ? "Đang suy nghĩ" : "Thinking")}</span>
                            <span className="flex gap-1" aria-hidden="true">
                              {[0, 1, 2].map((index) => <span key={index} className="h-1.5 w-1.5 rounded-full bg-primary motion-safe:animate-pulse" style={{ animationDelay: `${index * 160}ms` }} />)}
                            </span>
                          </div>
                        ) : assistant && isError ? (
                          <div className="space-y-sm">
                            <p className="font-medium text-error">{t("chat.responseFailed")}</p>
                            <p className="text-sm text-on-surface-variant dark:text-slate-400">
                              {message.errorMessage ?? message.content}
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
                              className={assistant ? "text-on-surface dark:text-slate-100" : "text-white"}
                            />
                            {message.status === "streaming" && <span className="ml-1 inline-block h-4 w-0.5 bg-primary motion-safe:animate-pulse" aria-hidden="true" />}
                            {message.status === "cancelled" && <p className="mt-sm text-xs font-medium text-on-surface-variant">{message.statusMessage ?? (language === "vi" ? "Đã dừng trả lời." : "Response stopped.")}</p>}
                            {assistant && isCompleted && !message.id.startsWith('local-') && <ChatMessageFeedbackControls messageId={message.id} language={language} />}
                            {shouldShowTicketAction && (
                              <div className="mt-md rounded-lg border border-legal-border bg-surface-container-low p-sm dark:border-slate-700 dark:bg-slate-800">
                                <div className="flex flex-wrap gap-xs">
                                  {message.riskLevel && (
                                    <Badge tone={message.riskLevel.toUpperCase() === "HIGH" ? "red" : "amber"}>
                                      {message.riskLevel}
                                    </Badge>
                                  )}
                                  {typeof message.confidenceScore === "number" && (
                                    <Badge tone="blue">
                                      {t("chat.confidence")} {Math.round(message.confidenceScore * 100)}%
                                    </Badge>
                                  )}
                                  {message.suggestionType && (
                                    <Badge tone="purple">{message.suggestionType}</Badge>
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
                className="absolute bottom-36 left-1/2 z-10 flex -translate-x-1/2 items-center gap-xs rounded-full border border-primary/30 bg-white px-md py-sm text-xs font-semibold text-primary shadow-lg hover:bg-primary/5 dark:bg-slate-900"
              >
                <ArrowDown className="h-4 w-4" />
                {language === "vi" ? "Xem câu trả lời mới" : "View new response"}
              </button>
            )}

            <form
              className="border-t border-legal-border bg-white p-md dark:border-slate-700 dark:bg-slate-900"
              onSubmit={(event) => {
                event.preventDefault();
                void handleSend();
              }}
            >
              {chatBlockedByAttachedDocuments && (
                <p className="mb-sm rounded-lg bg-amber-50 px-md py-sm text-xs font-semibold text-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
                  {failedAttachedDocuments.length > 0
                    ? (language === "vi" ? "Không thể gửi tin nhắn: hãy bỏ tài liệu lỗi hoặc upload lại." : "Cannot send: remove the failed document or upload it again.")
                    : (language === "vi" ? "Không thể gửi tin nhắn trong khi tài liệu đính kèm đang được xử lý." : "Messages cannot be sent while attached documents are processing.")}
                </p>
              )}
              <label className="sr-only" htmlFor="legal-chat-input">
                {t("chat.inputLabel")}
              </label>
              <div className="rounded-xl border border-legal-border bg-white p-sm shadow-sm dark:border-slate-700 dark:bg-slate-950">
                <div className="flex gap-sm">
                <textarea
                  ref={chatInputRef}
                  id="legal-chat-input"
                  rows={1}
                  className="max-h-32 min-h-12 min-w-0 flex-1 resize-none border-0 bg-transparent px-sm py-md outline-none placeholder:text-on-surface-variant"
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" && !event.shiftKey) {
                      event.preventDefault();
                      if (input.trim() && selectedWorkspaceId && !sending && !chatBlockedByAttachedDocuments) void handleSend();
                    }
                  }}
                  placeholder={t("chat.inputPlaceholder")}
                  disabled={!selectedWorkspaceId || chatBlockedByAttachedDocuments}
                />
                {sending ? (
                  <Button
                    type="button"
                    size="icon"
                    variant="danger"
                    aria-label={language === "vi" ? "Dừng trả lời" : "Stop response"}
                    title={language === "vi" ? "Dừng trả lời" : "Stop response"}
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
                <div className="mt-sm flex flex-wrap gap-xs border-t border-legal-border pt-sm dark:border-slate-800">
                  <Button type="button" size="sm" variant="ghost" leftIcon={<Paperclip className="h-4 w-4" />} onClick={() => void handleOpenDocumentModal()}>{language === "vi" ? "Thêm tài liệu" : "Add document"}</Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setInput(language === "vi" ? "Hãy trích xuất các điều khoản quan trọng trong tài liệu." : "Extract the important clauses from the document.")}>{language === "vi" ? "Trích xuất điều khoản" : "Extract clauses"}</Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setInput(language === "vi" ? "Hãy tóm tắt tài liệu này." : "Summarize this document.")}>{language === "vi" ? "Tóm tắt tài liệu" : "Summarize"}</Button>
                  <Button type="button" size="sm" variant="ghost" leftIcon={<MoreHorizontal className="h-4 w-4" />} onClick={() => setActiveDrawer("settings")}>{language === "vi" ? "Khác" : "More"}</Button>
                </div>
              </div>
            </form>
          </Card>
        </section>
        <aside className="order-2 flex items-start justify-center">
          <nav aria-label={language === "vi" ? "Tiện ích trò chuyện" : "Chat utilities"} className="sticky top-6 flex w-[60px] flex-col items-center gap-sm rounded-xl border border-legal-border bg-white p-sm shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <Button size="icon" variant={activeDrawer === "session" ? "primary" : "ghost"} aria-label={language === "vi" ? "Thông tin phiên chat" : "Chat information"} title={language === "vi" ? "Thông tin phiên chat" : "Chat information"} onClick={() => setActiveDrawer((current) => current === "session" ? null : "session")}><Info className="h-5 w-5" /></Button>
            <Button size="icon" variant={activeDrawer === "documents" ? "primary" : "ghost"} aria-label={language === "vi" ? "Tài liệu workspace" : "Workspace documents"} title={language === "vi" ? "Tài liệu workspace" : "Workspace documents"} onClick={() => setActiveDrawer((current) => current === "documents" ? null : "documents")}><Files className="h-5 w-5" /></Button>
            <Button size="icon" variant={activeDrawer === "settings" ? "primary" : "ghost"} aria-label={language === "vi" ? "Tùy chọn trò chuyện" : "Chat options"} title={language === "vi" ? "Tùy chọn trò chuyện" : "Chat options"} onClick={() => setActiveDrawer((current) => current === "settings" ? null : "settings")}><Settings className="h-5 w-5" /></Button>
          </nav>
        </aside>
      </div>

      {ticketDraft && <CreateTicketModal
        open
        workspaceId={selectedWorkspaceId}
        sessionId={selectedSessionId}
        userMessageId={ticketDraft.user?.id}
        assistantMessageId={ticketDraft.assistant.id}
        requestId={ticketDraft.assistant.requestId ?? undefined}
        question={ticketDraft.user?.content ?? ticketDraft.assistant.suggestionReason ?? "Cần hỗ trợ xác minh câu trả lời AI"}
        answer={ticketDraft.assistant.content}
        documents={attachedDocuments.map((document) => ({ id: document.documentId, name: document.originalFileName }))}
        focusedDocumentId={selectedDocumentId || undefined}
        citationIds={Array.from(ticketDraft.assistant.content.matchAll(/\[((?:KB|USER)-\d+)]/gi), (match) => match[1].toUpperCase())}
        submitting={creatingTicketMessageId === ticketDraft.assistant.id}
        onClose={() => setTicketDraft(null)}
        onSubmit={submitTicket}
      />}

      <Modal
        open={documentModalOpen}
        title={language === "vi" ? "Thêm tài liệu vào phiên chat" : "Add documents to chat"}
        onClose={() => setDocumentModalOpen(false)}
        footer={<Button variant="secondary" onClick={() => setDocumentModalOpen(false)}>{t("actions.close")}</Button>}
      >
        <div className="space-y-lg">
          {documentActionError && <p className="rounded-lg bg-error/10 p-sm text-sm text-error">{documentActionError}</p>}
          <section>
            <p className="font-semibold">{language === "vi" ? "Upload tài liệu mới" : "Upload new document"}</p>
            <p className="mt-xs text-xs text-on-surface-variant">{language === "vi" ? "File được lưu vào workspace và tự động gắn vào phiên chat. Quota được kiểm tra trước khi upload." : "The file is saved to the workspace and attached to this session."}</p>
            <div className="mt-sm space-y-sm">
              <p className="text-xs text-on-surface-variant">{supportedContractScopeText(language)} {language === "vi" ? "Chỉ cần chọn file, không cần khai báo loại hợp đồng." : "Choose a file; no contract-type selection is required."}</p>
              <div className="flex flex-col gap-sm sm:flex-row">
                <input className="form-field" type="file" onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)} />
                <Button disabled={!uploadFile || documentActionBusy} onClick={() => void handleUploadAndAttach()}>{documentActionBusy ? (language === "vi" ? "Đang xử lý…" : "Processing…") : (language === "vi" ? "Upload và thêm" : "Upload & attach")}</Button>
              </div>
            </div>
          </section>
          <section>
            <p className="font-semibold">{language === "vi" ? "Chọn từ workspace" : "Choose from workspace"}</p>
            <div className="mt-sm max-h-72 space-y-sm overflow-y-auto">
              {workspaceDocuments.length === 0 ? <p className="text-sm text-on-surface-variant">{t("chat.noDocuments")}</p> : workspaceDocuments.map((document) => {
                const attached = attachedDocuments.some((item) => item.documentId === document.documentId);
                return <div key={document.documentId} className="flex items-center justify-between gap-md rounded-lg border border-legal-border p-sm dark:border-slate-700">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold" title={document.originalFileName}>{document.originalFileName}</p>
                    <div className="mt-xs"><StatusBadge status={document.status} /></div>
                    <DocumentProcessingProgress status={document.status} language={language} />
                    {document.errorMessage && <p className="mt-xs text-xs text-error">{document.errorMessage}</p>}
                  </div>
                  <div className="flex gap-xs">
                    <Button size="icon" variant="ghost" aria-label="Download" onClick={() => void handleDownloadWorkspaceDocument(document)}><Download className="h-4 w-4" /></Button>
                    {attached ? (
                      <Button size="sm" variant="danger" leftIcon={<Trash2 className="h-4 w-4" />} disabled={documentActionBusy} onClick={() => void handleDetachDocument(document.documentId)}>{language === "vi" ? "Bỏ khỏi chat" : "Remove"}</Button>
                    ) : (
                      <Button size="sm" variant="secondary" disabled={documentActionBusy} onClick={() => void handleAttachDocument(document.documentId)}>{language === "vi" ? "Thêm" : "Attach"}</Button>
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
        title={language === 'vi' ? 'Liên kết chia sẻ chỉ đọc' : 'Read-only share link'}
        onClose={() => setShareUrl('')}
        footer={<Button variant="secondary" onClick={() => setShareUrl('')}>{t('actions.close')}</Button>}
      >
        <p className="text-sm text-on-surface-variant dark:text-slate-400">{language === 'vi' ? 'Chỉ Admin và Expert đã đăng nhập mới truy cập được.' : 'Only signed-in Admin and Expert users can access this link.'}</p>
        <div className="mt-md flex gap-sm"><input className="form-field" readOnly value={shareUrl} /><Button onClick={() => void navigator.clipboard.writeText(shareUrl)}>{language === 'vi' ? 'Sao chép' : 'Copy'}</Button></div>
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
              <div><dt className="label-uppercase">{t("chat.role")}</dt><dd className="mt-xs">{messageDetail.role}</dd></div>
              <div><dt className="label-uppercase">{t("chat.status")}</dt><dd className="mt-xs"><Badge tone="slate">{messageDetail.status}</Badge></dd></div>
              <div><dt className="label-uppercase">{t("chat.model")}</dt><dd className="mt-xs">{messageDetail.aiModel ?? '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.requestId")}</dt><dd className="mt-xs break-all">{messageDetail.requestId ?? '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.promptTokens")}</dt><dd className="mt-xs">{messageDetail.promptTokens ?? '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.completionTokens")}</dt><dd className="mt-xs">{messageDetail.completionTokens ?? '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.totalTokens")}</dt><dd className="mt-xs">{messageDetail.totalTokens ?? '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.confidence")}</dt><dd className="mt-xs">{typeof messageDetail.confidenceScore === 'number' ? `${Math.round(messageDetail.confidenceScore * 100)}%` : '-'}</dd></div>
              <div><dt className="label-uppercase">{t("chat.riskLevel")}</dt><dd className="mt-xs">{messageDetail.riskLevel ?? '-'}</dd></div>
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
                      <div className="mt-sm flex flex-wrap gap-xs">
                        {citation.sourceType && <Badge>{citation.sourceType}</Badge>}
                        {typeof citation.score === "number" && (
                          <Badge tone="blue">{Math.round(citation.score * 100)}%</Badge>
                        )}
                        {typeof citation.pageNumber === "number" && (
                          <Badge tone="gold">{t("common.page")} {citation.pageNumber}</Badge>
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
    </div>
  );
}
