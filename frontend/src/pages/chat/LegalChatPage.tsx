import { Bot, Check, ClipboardCheck, FileText, Pencil, Plus, RefreshCw, Send, Trash2, UserRound, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
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
  sendWorkspaceMessage,
  updateChatSession,
} from "../../api/chatApi";
import { createLegalTicket } from "../../api/legalTicketApi";
import { getWorkspaceDocuments, getWorkspaces } from "../../api/workspaceApi";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { ChatMessageContent } from "../../components/chat/ChatMessageContent";
import type { ChatMessage } from "../../types/chat";
import type { Document, Workspace } from "../../types/workspace";
import type { WorkspaceChatMessage, WorkspaceChatSession } from "../../types/chat";
import { useRef } from "react";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

const formatTimestamp = (value: string | null | undefined, language: "en" | "vi") => {
  if (!value) {
    return language === "vi" ? "Vừa xong" : "Just now";
  }

  return new Intl.DateTimeFormat(language === "vi" ? "vi-VN" : "en-US", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
};

const toDisplayMessage = (
  message: WorkspaceChatMessage,
  language: "en" | "vi",
): ChatMessage => ({
  id: message.messageId,
  role: message.role.toLowerCase() === "assistant" ? "assistant" : "user",
  content: message.content,
  timestamp: formatTimestamp(message.createdAt, language),
  status: message.status.toLowerCase() === "failed"
    ? "error"
    : message.status.toLowerCase() === "processing"
      ? "done"
      : "done",
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

const createOptimisticUserMessage = (message: string, language: "en" | "vi"): ChatMessage => ({
  id: `local-user-${Date.now()}`,
  role: "user",
  content: message,
  timestamp: formatTimestamp(null, language),
  status: "done",
});

type DisplayMessage = ChatMessage;

export function LegalChatPage() {
  const { t, language } = useI18n();
  const toast = useToast();
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
  const [renameTitle, setRenameTitle] = useState("");
  const [sessionActionBusyId, setSessionActionBusyId] = useState("");
  const [creatingTicketMessageId, setCreatingTicketMessageId] = useState("");
  const [ticketNotices, setTicketNotices] = useState<Record<string, string>>({});
  const [messageDetailOpen, setMessageDetailOpen] = useState(false);
  const [messageDetail, setMessageDetail] = useState<WorkspaceChatMessage | null>(null);
  const [messageDetailLoading, setMessageDetailLoading] = useState(false);
  const lastSubmissionRef = useRef<{
    workspaceId: string;
    sessionId: string;
    documentId?: string;
    message: string;
  } | null>(null);

  const selectedWorkspace = useMemo(
    () => workspaces.find((workspace) => workspace.workspaceId === selectedWorkspaceId),
    [selectedWorkspaceId, workspaces],
  );

  const selectedDocument = useMemo(
    () => workspaceDocuments.find((document) => document.documentId === selectedDocumentId),
    [selectedDocumentId, workspaceDocuments],
  );

  useEffect(() => {
    let active = true;

    const loadWorkspaces = async () => {
      try {
        setLoading(true);
        const data = await getWorkspaces(getAccessToken());
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
          setError(err instanceof Error ? err.message : "Không thể tải workspace");
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

        const documentFromQuery = searchParams.get("documentId") ?? "";
        const readyDocument =
          documents.find((document) => document.documentId === documentFromQuery) ??
          documents.find((document) => document.status.toUpperCase() === "READY") ??
          documents[0];

        if (readyDocument) {
          setSelectedDocumentId(readyDocument.documentId);
        } else {
          setSelectedDocumentId("");
        }

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
            searchParams.get("documentId") !== (readyDocument?.documentId ?? "")
          ) {
            const nextParams: Record<string, string> = {
              workspaceId: selectedWorkspaceId,
              sessionId: nextSessionId,
            };
            if (readyDocument?.documentId) {
              nextParams.documentId = readyDocument.documentId;
            }
            setSearchParams(nextParams);
          }
        } else if (searchParams.get("workspaceId") !== selectedWorkspaceId) {
          const nextParams: Record<string, string> = { workspaceId: selectedWorkspaceId };
          if (readyDocument?.documentId) {
            nextParams.documentId = readyDocument.documentId;
          }
          setSearchParams(nextParams);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không thể tải dữ liệu workspace");
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
        setMessages(data.items.map((message) => toDisplayMessage(message, language)));
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không thể tải lịch sử chat");
        }
      }
    };

    void loadMessages();

    return () => {
      active = false;
    };
  }, [language, selectedSessionId]);

  const handleCreateSession = async () => {
    if (!selectedWorkspaceId) {
      return;
    }

    try {
      const session = await createChatSession(
        getAccessToken(),
        selectedWorkspaceId,
        `Workspace chat ${new Date().toLocaleString()}`,
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

  const handleCreateTicket = async (assistantMessage: DisplayMessage, question: string) => {
    if (!selectedWorkspaceId) {
      setError(t("chat.ticketSelectWorkspace"));
      toast.warning(t("chat.ticketSelectWorkspace"), t("toast.warningTitle"));
      return;
    }

    setCreatingTicketMessageId(assistantMessage.id);
    setError("");

    try {
      const ticket = await createLegalTicket({
        request_id: assistantMessage.requestId,
        workspace_id: selectedWorkspaceId,
        document_id: selectedDocumentId || null,
        question,
        answer: assistantMessage.content,
        confidence_score: assistantMessage.confidenceScore,
        should_suggest_ticket: assistantMessage.shouldSuggestTicket,
        suggestion_type: assistantMessage.suggestionType,
        suggestion_reason: assistantMessage.suggestionReason,
        missing_information: assistantMessage.missingInformation,
        risk_level: assistantMessage.riskLevel,
        legal_domain: assistantMessage.legalDomain,
        user_action_hint: assistantMessage.userActionHint,
      });

      setTicketNotices((previous) => ({
        ...previous,
        [assistantMessage.id]: `${t("chat.ticketCreated")} ${ticket.id}.`,
      }));
      toast.success(`${t("chat.ticketCreated")} ${ticket.id}.`, t("toast.successTitle"));
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.ticketCreateError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
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
    setError("");

    try {
      const detail = await getChatMessageDetail(getAccessToken(), messageId);
      setMessageDetail(detail);
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.messageDetailError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setMessageDetailLoading(false);
    }
  };

  const handleSend = async (override?: {
    message: string;
    workspaceId: string;
    sessionId?: string;
    documentId?: string;
  }) => {
    const question = (override?.message ?? input).trim();
    const targetWorkspaceId = override?.workspaceId ?? selectedWorkspaceId;
    const targetSessionId = override?.sessionId ?? selectedSessionId;
    const targetDocumentId = override?.documentId ?? (selectedDocumentId || undefined);

    if (!question || !targetWorkspaceId) {
      return;
    }

    const optimisticUserMessage = createOptimisticUserMessage(question, language);
    const assistantMessageId = `local-assistant-${Date.now()}`;

    setMessages((previous) => [
      ...previous,
      optimisticUserMessage,
    ]);
    setInput("");
    setSending(true);
    setError("");
    lastSubmissionRef.current = {
      workspaceId: targetWorkspaceId,
      sessionId: targetSessionId ?? "",
      documentId: targetDocumentId,
      message: question,
    };

    try {
      const conversation = targetSessionId
        ? await sendChatSessionMessage(
            getAccessToken(),
            targetSessionId,
            question,
            targetDocumentId,
          )
        : await sendWorkspaceMessage(
            getAccessToken(),
            targetWorkspaceId,
            question,
            targetDocumentId,
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
        setMessages((previous) => [
          ...previous,
          {
            ...toDisplayMessage(conversation.assistantMessage, language),
            id: conversation.assistantMessage.messageId ?? assistantMessageId,
          },
        ]);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.messageSendError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setSending(false);
    }
  };

  return (
    <div>
      <PageHeader
        title={t("chat.title")}
        subtitle={t("chat.subtitleWorkspace")}
        actions={
          <>
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

      <div className="grid gap-gutter xl:grid-cols-[360px_minmax(0,1fr)]">
        <aside className="space-y-gutter">
          <Card title={t("admin.workspace")}>
            <div className="space-y-md">
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

          <Card title="Documents in workspace">
            <div className="space-y-md">
              {workspaceDocuments.length > 0 && (
                <div className="space-y-sm">
                  <label className="label-uppercase" htmlFor="documentSelect">
                    Current document
                  </label>
                  <select
                    id="documentSelect"
                    className="form-field"
                    value={selectedDocumentId}
                    onChange={(event) => {
                      const nextDocumentId = event.target.value;
                      setSelectedDocumentId(nextDocumentId);
                      const nextParams: Record<string, string> = {
                        workspaceId: selectedWorkspaceId,
                      };
                      if (selectedSessionId) {
                        nextParams.sessionId = selectedSessionId;
                      }
                      if (nextDocumentId) {
                        nextParams.documentId = nextDocumentId;
                      }
                      setSearchParams(nextParams);
                    }}
                    disabled={loadingContext}
                  >
                    {workspaceDocuments.map((document) => (
                      <option key={document.documentId} value={document.documentId}>
                        {document.originalFileName}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {loadingContext && (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("upload.loadingDocuments")}
                </p>
              )}

              {workspaceDocuments.length === 0 && !loadingContext ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("chat.noDocuments")}
                </p>
              ) : (
                workspaceDocuments.map((document) => (
                  <button
                    type="button"
                    key={document.documentId}
                    onClick={() => {
                      setSelectedDocumentId(document.documentId);
                      const nextParams: Record<string, string> = {
                        workspaceId: selectedWorkspaceId,
                      };
                      if (selectedSessionId) {
                        nextParams.sessionId = selectedSessionId;
                      }
                      nextParams.documentId = document.documentId;
                      setSearchParams(nextParams);
                    }}
                    className={`w-full rounded-xl border p-md text-left transition ${
                      selectedDocumentId === document.documentId
                        ? "border-primary bg-surface-container-high dark:border-inverse-primary dark:bg-slate-800"
                        : "border-legal-border hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                    }`}
                  >
                    <div className="flex items-start gap-md">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                        <FileText className="h-5 w-5" />
                      </div>
                      <div className="min-w-0">
                        <p className="font-semibold">{document.originalFileName}</p>
                        <p className="text-sm text-on-surface-variant dark:text-slate-400">
                          {(document.fileSize / 1024 / 1024).toFixed(2)} MB · {document.fileType}
                        </p>
                      </div>
                    </div>
                    <div className="mt-md">
                      <StatusBadge status={document.status} />
                    </div>
                  </button>
                ))
              )}
            </div>
          </Card>

          <Card title={t("chatHistory.conversations")}>
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
                    className={`w-full rounded-xl border p-md text-left transition ${
                      selectedSessionId === session.chatSessionId
                        ? "border-primary bg-surface-container-high dark:border-inverse-primary dark:bg-slate-800"
                        : "border-legal-border hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-md">
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
                          <>
                            <p className="font-semibold">{session.title}</p>
                            <p className="break-all text-xs text-on-surface-variant dark:text-slate-400">
                              {session.chatSessionId}
                            </p>
                          </>
                        )}
                      </div>
                      <div
                        className="flex shrink-0 items-center gap-xs"
                        onClick={(event) => event.stopPropagation()}
                      >
                        <Badge tone={session.isDefault ? "gold" : "blue"}>
                          {session.isDefault ? t("chat.defaultSession") : t("chat.sessionLabel")}
                        </Badge>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label="Rename chat session"
                          onClick={() => {
                            setRenamingSessionId(session.chatSessionId);
                            setRenameTitle(session.title);
                            setSessionActionError("");
                            setSessionActionMessage("");
                          }}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label="Delete chat session"
                          disabled={sessionActionBusyId === session.chatSessionId}
                          onClick={() => void handleDeleteSession(session.chatSessionId)}
                        >
                          <Trash2 className="h-4 w-4 text-error" />
                        </Button>
                      </div>
                    </div>
                    <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">
                      {t("chat.updated")} {formatTimestamp(session.updatedAt, language)}
                    </p>
                  </article>
                ))
              )}
            </div>
          </Card>
        </aside>

        <section className="space-y-gutter">
          <Card
            className="flex min-h-[680px] flex-col p-0"
            title={selectedWorkspace?.name ?? t("chat.legalChat")}
            subtitle={
              selectedDocument
                ? `${t("chat.documentLabel")}: ${selectedDocument.originalFileName}`
                : selectedSessionId
                  ? `${t("chat.sessionIdLabel")}: ${selectedSessionId}`
                  : t("chat.noSessionSelected")
            }
            actions={<Badge tone="gold">RAG</Badge>}
          >
            <div className="flex-1 space-y-md overflow-y-auto bg-surface-container-low/60 p-lg dark:bg-slate-950/40">
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
                    const previousUserMessage = assistant
                      ? [...messages]
                          .slice(0, messageIndex)
                          .reverse()
                          .find((item) => item.role === "user")
                      : undefined;
                    const shouldShowTicketAction =
                      assistant &&
                      !isError &&
                      Boolean(
                        message.shouldSuggestTicket ||
                          message.userActionHint ||
                          message.suggestionType ||
                          message.riskLevel ||
                          typeof message.confidenceScore === "number",
                      );
                    return (
                    <article
                      key={message.id}
                      className={`flex gap-md ${assistant ? "" : "justify-end"}`}
                    >
                      {assistant && (
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary text-white">
                          <Bot className="h-5 w-5" aria-hidden="true" />
                        </div>
                      )}
                      <div
                        className={`max-w-[82%] rounded-xl border p-md text-sm leading-6 shadow-sm ${
                          assistant
                            ? "border-legal-border bg-white text-on-surface dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                            : "border-primary bg-primary text-white"
                        }`}
                      >
                        {assistant && isError ? (
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
                                      Confidence {Math.round(message.confidenceScore * 100)}%
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
                                    onClick={() =>
                                      void handleCreateTicket(
                                        message,
                                        previousUserMessage?.content ?? "",
                                      )
                                    }
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
                        <div className="mt-sm flex items-center justify-between gap-sm text-[11px] opacity-70">
                          <span>{message.timestamp}</span>
                          {!message.id.startsWith("local-") && (
                            <button
                              type="button"
                              className={assistant ? "font-semibold text-primary hover:underline dark:text-inverse-primary" : "font-semibold text-white underline-offset-2 hover:underline"}
                              onClick={() => void handleOpenMessageDetail(message.id)}
                            >
                              {t("actions.details")}
                            </button>
                          )}
                        </div>
                      </div>
                      {!assistant && (
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-secondary-container text-secondary">
                          <UserRound className="h-5 w-5" aria-hidden="true" />
                        </div>
                      )}
                    </article>
                  );
                })
              )}
            </div>

            <form
              className="border-t border-legal-border p-md dark:border-slate-700"
              onSubmit={(event) => {
                event.preventDefault();
                void handleSend();
              }}
            >
              <label className="sr-only" htmlFor="legal-chat-input">
                {t("chat.inputLabel")}
              </label>
              <div className="flex gap-sm">
                <input
                  id="legal-chat-input"
                  className="form-field"
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder={t("chat.inputPlaceholder")}
                  disabled={!selectedWorkspaceId || sending}
                />
                <Button
                  type="submit"
                  size="icon"
                  aria-label="Ask"
                  disabled={!input.trim() || !selectedWorkspaceId || sending}
                >
                  <Send className="h-5 w-5" />
                </Button>
              </div>
            </form>
          </Card>
        </section>
      </div>

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
              <div><dt className="label-uppercase">Message ID</dt><dd className="mt-xs break-all font-semibold">{messageDetail.messageId}</dd></div>
              <div><dt className="label-uppercase">Session ID</dt><dd className="mt-xs break-all">{messageDetail.chatSessionId}</dd></div>
              <div><dt className="label-uppercase">Role</dt><dd className="mt-xs">{messageDetail.role}</dd></div>
              <div><dt className="label-uppercase">Status</dt><dd className="mt-xs"><Badge tone="slate">{messageDetail.status}</Badge></dd></div>
              <div><dt className="label-uppercase">Model</dt><dd className="mt-xs">{messageDetail.aiModel ?? '-'}</dd></div>
              <div><dt className="label-uppercase">Request ID</dt><dd className="mt-xs break-all">{messageDetail.requestId ?? '-'}</dd></div>
              <div><dt className="label-uppercase">Prompt tokens</dt><dd className="mt-xs">{messageDetail.promptTokens ?? '-'}</dd></div>
              <div><dt className="label-uppercase">Completion tokens</dt><dd className="mt-xs">{messageDetail.completionTokens ?? '-'}</dd></div>
              <div><dt className="label-uppercase">Total tokens</dt><dd className="mt-xs">{messageDetail.totalTokens ?? '-'}</dd></div>
              <div><dt className="label-uppercase">Confidence</dt><dd className="mt-xs">{typeof messageDetail.confidenceScore === 'number' ? `${Math.round(messageDetail.confidenceScore * 100)}%` : '-'}</dd></div>
              <div><dt className="label-uppercase">Risk level</dt><dd className="mt-xs">{messageDetail.riskLevel ?? '-'}</dd></div>
              <div><dt className="label-uppercase">Legal domain</dt><dd className="mt-xs">{messageDetail.legalDomain ?? '-'}</dd></div>
            </dl>
            {messageDetail.suggestionReason && (
              <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <p className="label-uppercase mb-xs">Suggestion reason</p>
                {messageDetail.suggestionReason}
              </div>
            )}
          </div>
        ) : (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("chat.noMessageDetail")}</p>
        )}
      </Modal>
    </div>
  );
}
