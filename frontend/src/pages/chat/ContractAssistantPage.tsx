import { Bot, Check, ClipboardCheck, Pencil, Plus, RefreshCw, Send, Trash2, UserRound, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { Modal } from "../../components/common/Modal";
import { PageHeader } from "../../components/common/PageHeader";
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
import { getWorkspaces, createWorkspace } from "../../api/workspaceApi";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { ChatMessageContent } from "../../components/chat/ChatMessageContent";
import { CreateTicketModal } from "../../components/tickets/CreateTicketModal";
import type { CreateLegalTicketRequest } from "../../types/legalTicket";
import type { ChatMessage } from "../../types/chat";
import type { WorkspaceChatMessage, WorkspaceChatSession } from "../../types/chat";
import { useRef } from "react";

import { getAccessToken as getSessionAccessToken } from "../../services/authSession";
const getAccessToken = () => getSessionAccessToken() ?? "";

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

const createOptimisticUserMessage = (message: string, language: "en" | "vi"): ChatMessage => ({
  id: `local-user-${Date.now()}`,
  role: "user",
  content: message,
  timestamp: formatTimestamp(null, language),
  status: "completed",
});

type DisplayMessage = ChatMessage;

export function ContractAssistantPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [sandboxWorkspaceId, setSandboxWorkspaceId] = useState("");
  const [chatSessions, setChatSessions] = useState<WorkspaceChatSession[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState(
    searchParams.get("sessionId") ?? "",
  );
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [sessionActionError, setSessionActionError] = useState("");
  const [sessionActionMessage, setSessionActionMessage] = useState("");
  const [renamingSessionId, setRenamingSessionId] = useState("");
  const [renameTitle, setRenameTitle] = useState("");
  const [sessionActionBusyId, setSessionActionBusyId] = useState("");
  const [creatingTicketMessageId, setCreatingTicketMessageId] = useState("");
  const [ticketNotices, setTicketNotices] = useState<Record<string, string>>({});
  const [ticketDraft, setTicketDraft] = useState<{ assistant: DisplayMessage; user?: DisplayMessage } | null>(null);
  const [messageDetailOpen, setMessageDetailOpen] = useState(false);
  const [messageDetail, setMessageDetail] = useState<WorkspaceChatMessage | null>(null);
  const [messageDetailLoading, setMessageDetailLoading] = useState(false);
  const lastSubmissionRef = useRef<{
    workspaceId: string;
    sessionId: string;
    message: string;
  } | null>(null);

  // Phase 1: Resolve Sandbox Workspace on mount
  useEffect(() => {
    let active = true;

    const resolveSandboxWorkspace = async () => {
      try {
        setLoading(true);
        setError("");
        const workspaces = await getWorkspaces(getAccessToken());
        if (!active) return;

        // Find existing system sandbox workspace
        let sandbox = workspaces.find(
          (ws) => ws.description === "System workspace for general contract assistant chat"
        );

        if (!sandbox) {
          // Create the hidden sandbox workspace automatically
          sandbox = await createWorkspace(getAccessToken(), {
            name: "Contract Assistant Sandbox",
            description: "System workspace for general contract assistant chat",
          });
        }

        if (sandbox && active) {
          setSandboxWorkspaceId(sandbox.workspaceId);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không thể khởi tạo trợ lý");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void resolveSandboxWorkspace();

    return () => {
      active = false;
    };
  }, []);

  // Phase 2: Load chat sessions once sandbox workspace is resolved
  useEffect(() => {
    if (!sandboxWorkspaceId) {
      setChatSessions([]);
      setMessages([]);
      return;
    }

    let active = true;

    const loadSessions = async () => {
      try {
        setLoadingSessions(true);
        const sessions = await getWorkspaceChatSessions(getAccessToken(), sandboxWorkspaceId);
        if (!active) return;

        setChatSessions(sessions.items);

        const sessionFromQuery = searchParams.get("sessionId") ?? "";
        const nextSessionId =
          sessions.items.find((session) => session.chatSessionId === sessionFromQuery)
            ?.chatSessionId ??
          sessions.items.find((session) => session.isDefault)?.chatSessionId ??
          sessions.items[0]?.chatSessionId ??
          "";

        setSelectedSessionId(nextSessionId);
        if (nextSessionId) {
          if (searchParams.get("sessionId") !== nextSessionId) {
            setSearchParams({ sessionId: nextSessionId });
          }
        } else {
          setSearchParams({});
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không thể tải danh sách phiên chat");
        }
      } finally {
        if (active) {
          setLoadingSessions(false);
        }
      }
    };

    void loadSessions();

    return () => {
      active = false;
    };
  }, [sandboxWorkspaceId, searchParams, setSearchParams]);

  // Phase 3: Load messages once a session is selected
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
    if (!sandboxWorkspaceId) {
      return;
    }

    try {
      const session = await createChatSession(
        getAccessToken(),
        sandboxWorkspaceId,
        `Trợ lý hợp đồng ${new Date().toLocaleString()}`,
      );
      setChatSessions((previous) => [session, ...previous.filter((item) => item.chatSessionId !== session.chatSessionId)]);
      setSelectedSessionId(session.chatSessionId);
      setSearchParams({ sessionId: session.chatSessionId });
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

    if (searchParams.get("sessionId") !== session.chatSessionId) {
      setSearchParams({ sessionId: session.chatSessionId });
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
        setSearchParams(nextSessionId ? { sessionId: nextSessionId } : {});
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

  const handleCreateTicket = (assistantMessage: DisplayMessage, userMessage?: DisplayMessage) => {
    if (!sandboxWorkspaceId) {
      setError(t("chat.ticketSelectWorkspace"));
      toast.warning(t("chat.ticketSelectWorkspace"), t("toast.warningTitle"));
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

      setTicketNotices((previous) => ({
        ...previous,
        [assistantMessage.id]: `${t("chat.ticketCreated")} ${ticket.id}.`,
      }));
      toast.success(`${t("chat.ticketCreated")} ${ticket.id}.`, t("toast.successTitle"));
      setTicketDraft(null);
    } catch (err) {
      const message = err instanceof Error ? err.message : t("chat.ticketCreateError");
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
  }) => {
    const question = (override?.message ?? input).trim();
    const targetWorkspaceId = override?.workspaceId ?? sandboxWorkspaceId;
    const targetSessionId = override?.sessionId ?? selectedSessionId;

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
      message: question,
    };

    try {
      const conversation = targetSessionId
        ? await sendChatSessionMessage(
            getAccessToken(),
            targetSessionId,
            question,
          )
        : await sendWorkspaceMessage(
            getAccessToken(),
            targetWorkspaceId,
            question,
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
        title={t("nav.contractAssistant")}
        subtitle={t("chat.contractAssistantSubtitle")}
        actions={
          <>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => {
                if (!sandboxWorkspaceId) return;
                setSearchParams(
                  selectedSessionId ? { sessionId: selectedSessionId } : {}
                );
              }}
            >
              {t("chat.refreshContext")}
            </Button>
            <Button
              leftIcon={<Plus className="h-4 w-4" />}
              onClick={handleCreateSession}
              disabled={!sandboxWorkspaceId}
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
              {loadingSessions ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("chatHistory.loadingSessions")}
                </p>
              ) : chatSessions.length === 0 ? (
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
                          aria-label={t("chat.renameSession")}
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
                          aria-label={t("chat.deleteSession")}
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
            title={t("nav.contractAssistant")}
            subtitle={
              selectedSessionId
                ? `${t("chat.sessionIdLabel")}: ${selectedSessionId}`
                : t("chat.noSessionSelected")
            }
            actions={<Badge tone="gold">AI Chat</Badge>}
          >
            <div className="flex-1 space-y-md overflow-y-auto bg-surface-container-low/60 p-lg dark:bg-slate-950/40">
              {messages.length === 0 ? (
                <div className="rounded-xl border border-dashed border-outline-variant p-lg text-sm text-on-surface-variant dark:border-slate-700 dark:text-slate-400">
                  {loading ? t("common.loading") : t("chat.askToStart")}
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
                  const shouldShowTicketAction = assistant && !isError && !message.id.startsWith("local-");
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
                  disabled={!sandboxWorkspaceId || sending}
                />
                <Button
                  type="submit"
                  size="icon"
                  aria-label={t("actions.ask")}
                  disabled={!input.trim() || !sandboxWorkspaceId || sending}
                >
                  <Send className="h-5 w-5" />
                </Button>
              </div>
            </form>
          </Card>
        </section>
      </div>

      {ticketDraft && <CreateTicketModal
        open
        workspaceId={sandboxWorkspaceId}
        sessionId={selectedSessionId}
        userMessageId={ticketDraft.user?.id}
        assistantMessageId={ticketDraft.assistant.id}
        requestId={ticketDraft.assistant.requestId ?? undefined}
        question={ticketDraft.user?.content ?? ticketDraft.assistant.suggestionReason ?? "Cần hỗ trợ xác minh câu trả lời AI"}
        answer={ticketDraft.assistant.content}
        documents={[]}
        citationIds={Array.from(ticketDraft.assistant.content.matchAll(/\[((?:KB|USER)-\d+)]/gi), (match) => match[1].toUpperCase())}
        submitting={creatingTicketMessageId === ticketDraft.assistant.id}
        onClose={() => setTicketDraft(null)}
        onSubmit={submitTicket}
      />}

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
          </div>
        ) : (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("chat.noMessageDetail")}</p>
        )}
      </Modal>
    </div>
  );
}
