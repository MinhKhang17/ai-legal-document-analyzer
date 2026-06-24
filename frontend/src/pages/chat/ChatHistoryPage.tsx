import { ExternalLink, MessageSquareText } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { getChatSessionMessages, getWorkspaceChatSessions } from "../../api/chatApi";
import { getWorkspaces } from "../../api/workspaceApi";
import { ChatMessageContent } from "../../components/chat/ChatMessageContent";
import { useI18n } from "../../hooks/useI18n";
import type { ChatMessage } from "../../types/chat";
import type { Workspace } from "../../types/workspace";
import type { WorkspaceChatMessage, WorkspaceChatSession } from "../../types/chat";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

const formatTimestamp = (value: string | null | undefined, language: "en" | "vi") => {
  if (!value) {
    return language === "vi" ? "Vừa xong" : "Just now";
  }

  return new Intl.DateTimeFormat(language === "vi" ? "vi-VN" : "en-US", {
    dateStyle: "medium",
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
});

export function ChatHistoryPage() {
  const { t, language } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [sessions, setSessions] = useState<WorkspaceChatSession[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(
    searchParams.get("workspaceId") ?? "",
  );
  const [selectedSessionId, setSelectedSessionId] = useState(
    searchParams.get("sessionId") ?? "",
  );
  const [loading, setLoading] = useState(true);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [error, setError] = useState("");

  const selectedWorkspace = useMemo(
    () => workspaces.find((workspace) => workspace.workspaceId === selectedWorkspaceId),
    [selectedWorkspaceId, workspaces],
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
  }, [searchParams]);

  useEffect(() => {
    if (!selectedWorkspaceId) {
      setSessions([]);
      setMessages([]);
      return;
    }

    let active = true;

    const loadSessions = async () => {
      try {
        setLoadingSessions(true);
        const data = await getWorkspaceChatSessions(
          getAccessToken(),
          selectedWorkspaceId,
          0,
          20,
        );
        if (!active) return;
        setSessions(data.items);

        const sessionFromQuery = searchParams.get("sessionId") ?? "";
        const nextSessionId =
          data.items.find((session) => session.chatSessionId === sessionFromQuery)
            ?.chatSessionId ??
          data.items.find((session) => session.isDefault)?.chatSessionId ??
          data.items[0]?.chatSessionId ??
          "";

        setSelectedSessionId(nextSessionId);
        if (nextSessionId) {
          if (
            searchParams.get("workspaceId") !== selectedWorkspaceId ||
            searchParams.get("sessionId") !== nextSessionId
          ) {
            setSearchParams({ workspaceId: selectedWorkspaceId, sessionId: nextSessionId });
          }
        } else if (searchParams.get("workspaceId") !== selectedWorkspaceId) {
          setSearchParams({ workspaceId: selectedWorkspaceId });
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không thể tải lịch sử chat");
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
  }, [selectedWorkspaceId, searchParams]);

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
          setError(err instanceof Error ? err.message : "Không thể tải tin nhắn");
        }
      }
    };

    void loadMessages();

    return () => {
      active = false;
    };
  }, [language, selectedSessionId]);

  return (
    <div>
      <PageHeader
        title={t("chatHistory.title")}
        subtitle={t("chatHistory.subtitleWorkspace")}
        actions={<Button variant="secondary">{t("chatHistory.allFilters")}</Button>}
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_420px]">
        <Card title={t("chatHistory.conversations")}>
          <div className="mb-md grid gap-md md:grid-cols-2">
            <select
              className="form-field"
              value={selectedWorkspaceId}
              onChange={(event) => {
                const nextWorkspaceId = event.target.value;
                setSelectedWorkspaceId(nextWorkspaceId);
                setSelectedSessionId("");
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

            {selectedWorkspace && (
              <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <p className="font-semibold">{selectedWorkspace.name}</p>
                <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                  {selectedWorkspace.description || t("workspace.noDescription")}
                </p>
              </div>
            )}
          </div>

          <div className="space-y-md">
            {loadingSessions && (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("chatHistory.loadingSessions")}
              </p>
            )}

            {sessions.length === 0 && !loadingSessions ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("chatHistory.noConversations")}
              </p>
            ) : (
              sessions.map((session) => (
                <article
                  key={session.chatSessionId}
                  className="rounded-xl border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                >
                  <div className="flex items-start justify-between gap-md">
                    <div>
                      <h2 className="font-semibold text-primary dark:text-inverse-primary">
                        {session.title}
                      </h2>
                      <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                      {session.chatSessionId} · {formatTimestamp(session.updatedAt, language)}
                      </p>
                    </div>
                    <div className="flex gap-xs">
                      <Link
                        to={`/chat?workspaceId=${selectedWorkspaceId}&sessionId=${session.chatSessionId}`}
                      >
                        <Button variant="ghost" size="icon" aria-label={t("chatHistory.openChat")}>
                          <ExternalLink className="h-4 w-4" />
                        </Button>
                      </Link>
                    </div>
                  </div>
                  <div className="mt-md flex flex-wrap gap-xs">
                    <Badge tone={session.isDefault ? "gold" : "blue"}>
                      {session.isDefault ? t("chat.defaultSession") : t("chat.sessionLabel")}
                    </Badge>
                  </div>
                </article>
              ))
            )}
          </div>
        </Card>

        <aside>
          <Card
            title={sessions.find((session) => session.chatSessionId === selectedSessionId)?.title ?? t("chatHistory.conversation")}
            subtitle={selectedWorkspace?.name}
            actions={<MessageSquareText className="h-5 w-5 text-primary dark:text-inverse-primary" />}
          >
            <div className="space-y-md">
              {messages.length === 0 ? (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("chatHistory.previewEmpty")}
                </p>
              ) : (
                messages.map((message) => (
                  <div
                    key={message.id}
                    className="rounded-lg bg-surface-container-low p-md text-sm leading-6 dark:bg-slate-800"
                  >
                    <p className="label-uppercase">
                      {message.role === "assistant"
                        ? t("chat.role.assistant")
                        : t("chat.role.user")}
                    </p>
                    <ChatMessageContent content={message.content} className="mt-xs" />
                  </div>
                ))
              )}
              {selectedWorkspaceId && (
                <Link to={`/chat?workspaceId=${selectedWorkspaceId}&sessionId=${selectedSessionId}`}>
                  <Button className="w-full">{t("chatHistory.resumeDiscussion")}</Button>
                </Link>
              )}
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
