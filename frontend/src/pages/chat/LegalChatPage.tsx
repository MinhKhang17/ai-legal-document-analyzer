import { Bot, FileText, Plus, RefreshCw, Send, UserRound } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { StatusBadge } from "../../components/common/StatusBadge";
import {
  createChatSession,
  getChatSessionMessages,
  getWorkspaceChatSessions,
  sendChatSessionMessage,
  sendWorkspaceMessage,
} from "../../api/chatApi";
import { getWorkspaceDocuments, getWorkspaces } from "../../api/workspaceApi";
import { useI18n } from "../../hooks/useI18n";
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
      setError(err instanceof Error ? err.message : "Không thể tạo chat session");
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
            id: conversation.assistantMessage.messageId ?? assistantMessageId,
            role: "assistant",
            content: conversation.assistantMessage.content,
            timestamp: formatTimestamp(conversation.assistantMessage.createdAt, language),
            status: conversation.assistantMessage.status?.toLowerCase() === "failed" ? "error" : "done",
          },
        ]);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Không thể gửi câu hỏi";
      setError(message);
    } finally {
      setSending(false);
    }
  };

  return (
    <div>
      <PageHeader
        title={t("chat.title")}
        subtitle="Ask questions over the documents uploaded into the selected workspace."
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
              Refresh context
            </Button>
            <Button
              leftIcon={<Plus className="h-4 w-4" />}
              onClick={handleCreateSession}
              disabled={!selectedWorkspaceId}
            >
              New session
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
          <Card title="Workspace">
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
                  {loading ? "Loading workspaces..." : "Select workspace"}
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
                    {selectedWorkspace.description || "No description"}
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
                  Loading documents...
                </p>
              )}

              {workspaceDocuments.length === 0 && !loadingContext ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  No documents available yet.
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

          <Card title="Chat sessions">
            <div className="space-y-md">
              {chatSessions.length === 0 ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  No sessions yet. Send your first question to create one.
                </p>
              ) : (
                chatSessions.map((session) => (
                  <button
                    key={session.chatSessionId}
                    type="button"
                    onClick={() => {
                      setSelectedSessionId(session.chatSessionId);
                      if (
                        searchParams.get("workspaceId") !== selectedWorkspaceId ||
                        searchParams.get("sessionId") !== session.chatSessionId
                      ) {
                        setSearchParams({
                          workspaceId: selectedWorkspaceId,
                          sessionId: session.chatSessionId,
                        });
                      }
                    }}
                    className={`w-full rounded-xl border p-md text-left transition ${
                      selectedSessionId === session.chatSessionId
                        ? "border-primary bg-surface-container-high dark:border-inverse-primary dark:bg-slate-800"
                        : "border-legal-border hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-md">
                      <div>
                        <p className="font-semibold">{session.title}</p>
                        <p className="text-xs text-on-surface-variant dark:text-slate-400">
                          {session.chatSessionId}
                        </p>
                      </div>
                      <Badge tone={session.isDefault ? "gold" : "blue"}>
                        {session.isDefault ? "Default" : "Session"}
                      </Badge>
                    </div>
                    <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">
                      Updated {formatTimestamp(session.updatedAt, language)}
                    </p>
                  </button>
                ))
              )}
            </div>
          </Card>
        </aside>

        <section className="space-y-gutter">
          <Card
            className="flex min-h-[680px] flex-col p-0"
            title={selectedWorkspace?.name ?? "Legal chat"}
            subtitle={
              selectedDocument
                ? `Document: ${selectedDocument.originalFileName}`
                : selectedSessionId
                  ? `Session: ${selectedSessionId}`
                  : "No session selected yet"
            }
            actions={<Badge tone="gold">RAG</Badge>}
          >
            <div className="flex-1 space-y-md overflow-y-auto bg-surface-container-low/60 p-lg dark:bg-slate-950/40">
                {messages.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-outline-variant p-lg text-sm text-on-surface-variant dark:border-slate-700 dark:text-slate-400">
                    {selectedWorkspaceId
                      ? "Ask a question to start the chat and create a session automatically."
                      : "Pick a workspace first."}
                  </div>
                ) : (
                  messages.map((message) => {
                    const assistant = message.role === "assistant";
                    const isError = message.status === "error";
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
                            <p className="font-medium text-error">Không thể hoàn tất phản hồi</p>
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
                                Thử lại
                              </Button>
                            )}
                          </div>
                        ) : (
                          <ChatMessageContent
                            content={message.content}
                            className={assistant ? "text-on-surface dark:text-slate-100" : "text-white"}
                          />
                        )}
                        <div className="mt-sm flex items-center justify-between gap-sm text-[11px] opacity-70">
                          <span>{message.timestamp}</span>
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
                Ask legal AI
              </label>
              <div className="flex gap-sm">
                <input
                  id="legal-chat-input"
                  className="form-field"
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="Ask a question about the uploaded documents..."
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
    </div>
  );
}
