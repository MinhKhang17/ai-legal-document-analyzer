import {
  ArrowRight,
  Building2,
  Clock3,
  ExternalLink,
  MessageSquarePlus,
  MessageSquareText,
  RefreshCw,
  SearchX,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { getChatSessionMessages, getWorkspaceChatSessions } from "../../api/chatApi";
import { getWorkspaces } from "../../api/workspaceApi";
import { ChatMessageContent } from "../../components/chat/ChatMessageContent";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { SearchInput } from "../../components/common/SearchInput";
import { useI18n } from "../../hooks/useI18n";
import { getAccessToken as getSessionAccessToken } from "../../services/authSession";
import type {
  ChatMessage,
  WorkspaceChatMessage,
  WorkspaceChatSession,
} from "../../types/chat";
import type { Workspace } from "../../types/workspace";
import { formatDisplayDateTime } from "../../utils/format";
import { mergeUniqueBy } from "../../utils/mergeUnique";

const getAccessToken = () => getSessionAccessToken() ?? "";

const formatTimestamp = (
  value: string | null | undefined,
  language: "en" | "vi",
  fallback: string,
) =>
  value
    ? formatDisplayDateTime(
        value,
        fallback,
        language === "vi" ? "vi-VN" : "en-US",
      )
    : fallback;

const toDisplayMessage = (
  message: WorkspaceChatMessage,
  language: "en" | "vi",
  fallback: string,
): ChatMessage => ({
  id: message.messageId,
  role: message.role.toLowerCase() === "assistant" ? "assistant" : "user",
  content: message.content,
  timestamp: formatTimestamp(message.createdAt, language, fallback),
});

const getChatUrl = (workspaceId: string, sessionId?: string) => {
  const params = new URLSearchParams({ workspaceId });
  if (sessionId) params.set("sessionId", sessionId);
  return `/chat?${params.toString()}`;
};

function ListSkeleton() {
  return (
    <div className="space-y-sm p-md" aria-hidden="true">
      {[0, 1, 2, 3].map((item) => (
        <div
          key={item}
          className="animate-pulse rounded-xl border border-legal-border p-md dark:border-slate-700"
        >
          <div className="h-4 w-2/3 rounded bg-surface-container-high dark:bg-slate-700" />
          <div className="mt-sm h-3 w-1/2 rounded bg-surface-container dark:bg-slate-800" />
          <div className="mt-md h-5 w-20 rounded-full bg-surface-container dark:bg-slate-800" />
        </div>
      ))}
    </div>
  );
}

function PreviewSkeleton() {
  return (
    <div className="space-y-md" aria-hidden="true">
      {["w-3/4", "w-5/6", "w-2/3"].map((width, index) => (
        <div
          key={width}
          className={`animate-pulse rounded-xl bg-surface-container-low p-md dark:bg-slate-800 ${
            index === 1 ? "ml-auto" : ""
          }`}
        >
          <div className="h-3 w-20 rounded bg-surface-container-high dark:bg-slate-700" />
          <div className={`mt-sm h-4 ${width} rounded bg-surface-container-high dark:bg-slate-700`} />
          <div className="mt-sm h-4 w-1/2 rounded bg-surface-container dark:bg-slate-700" />
        </div>
      ))}
    </div>
  );
}

export function ChatHistoryPage() {
  const { t, language } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const workspaceIdParam = searchParams.get("workspaceId") ?? "";
  const sessionIdParam = searchParams.get("sessionId") ?? "";

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [sessions, setSessions] = useState<WorkspaceChatSession[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(workspaceIdParam);
  const [selectedSessionId, setSelectedSessionId] = useState(sessionIdParam);
  const [query, setQuery] = useState("");
  const [loadingWorkspaces, setLoadingWorkspaces] = useState(true);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [loadingMoreSessions, setLoadingMoreSessions] = useState(false);
  const [loadingMoreMessages, setLoadingMoreMessages] = useState(false);
  const [workspaceError, setWorkspaceError] = useState("");
  const [sessionsError, setSessionsError] = useState("");
  const [messagesError, setMessagesError] = useState("");
  const [workspaceReloadKey, setWorkspaceReloadKey] = useState(0);
  const [sessionsReloadKey, setSessionsReloadKey] = useState(0);
  const [messagesReloadKey, setMessagesReloadKey] = useState(0);
  const [sessionPage, setSessionPage] = useState(0);
  const [sessionTotalPages, setSessionTotalPages] = useState(0);
  const [sessionTotalItems, setSessionTotalItems] = useState(0);
  const [messagePage, setMessagePage] = useState(0);
  const [messageTotalPages, setMessageTotalPages] = useState(0);

  const selectedWorkspace = useMemo(
    () => workspaces.find((workspace) => workspace.workspaceId === selectedWorkspaceId),
    [selectedWorkspaceId, workspaces],
  );
  const selectedSession = useMemo(
    () => sessions.find((session) => session.chatSessionId === selectedSessionId),
    [selectedSessionId, sessions],
  );
  const activeSessionId = selectedSession?.chatSessionId ?? "";
  const filteredSessions = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase(language === "vi" ? "vi" : "en");
    if (!normalizedQuery) return sessions;
    return sessions.filter((session) =>
      session.title
        .toLocaleLowerCase(language === "vi" ? "vi" : "en")
        .includes(normalizedQuery),
    );
  }, [language, query, sessions]);

  useEffect(() => {
    let active = true;
    const loadWorkspaces = async () => {
      setLoadingWorkspaces(true);
      setWorkspaceError("");
      try {
        const data = await getWorkspaces(getAccessToken());
        if (active) setWorkspaces(data);
      } catch {
        if (active) {
          setWorkspaceError(t("chat.loadWorkspaceError"));
        }
      } finally {
        if (active) setLoadingWorkspaces(false);
      }
    };
    void loadWorkspaces();
    return () => {
      active = false;
    };
  }, [t, workspaceReloadKey]);

  useEffect(() => {
    if (loadingWorkspaces || workspaces.length === 0) return;
    const nextWorkspace =
      workspaces.find((workspace) => workspace.workspaceId === workspaceIdParam) ?? workspaces[0];
    if (selectedWorkspaceId !== nextWorkspace.workspaceId) {
      setSelectedWorkspaceId(nextWorkspace.workspaceId);
      setSelectedSessionId("");
      setSessions([]);
      setMessages([]);
      setQuery("");
    }
    if (workspaceIdParam !== nextWorkspace.workspaceId) {
      setSearchParams({ workspaceId: nextWorkspace.workspaceId }, { replace: true });
    }
  }, [
    loadingWorkspaces,
    selectedWorkspaceId,
    setSearchParams,
    workspaceIdParam,
    workspaces,
  ]);

  useEffect(() => {
    if (!selectedWorkspaceId) {
      setSessions([]);
      setMessages([]);
      return;
    }
    let active = true;
    const loadSessions = async () => {
      setLoadingSessions(true);
      setSessionsError("");
      setMessages([]);
      try {
        const data = await getWorkspaceChatSessions(
          getAccessToken(),
          selectedWorkspaceId,
          0,
          20,
        );
        if (!active) return;
        setSessions(data.items);
        setSessionPage(0);
        setSessionTotalPages(data.totalPages);
        setSessionTotalItems(data.totalItems);
      } catch {
        if (active) {
          setSessions([]);
          setSessionsError(t("chat.loadHistoryError"));
        }
      } finally {
        if (active) setLoadingSessions(false);
      }
    };
    void loadSessions();
    return () => {
      active = false;
    };
  }, [selectedWorkspaceId, sessionsReloadKey, t]);

  useEffect(() => {
    if (loadingSessions || sessionsError) return;
    if (sessions.length === 0) {
      setSelectedSessionId("");
      if (sessionIdParam) {
        setSearchParams({ workspaceId: selectedWorkspaceId }, { replace: true });
      }
      return;
    }
    const nextSessionId =
      sessions.find((session) => session.chatSessionId === sessionIdParam)?.chatSessionId ??
      sessions.find((session) => session.isDefault)?.chatSessionId ??
      sessions[0].chatSessionId;
    if (selectedSessionId !== nextSessionId) setSelectedSessionId(nextSessionId);
    if (sessionIdParam !== nextSessionId) {
      setSearchParams(
        { workspaceId: selectedWorkspaceId, sessionId: nextSessionId },
        { replace: true },
      );
    }
  }, [
    loadingSessions,
    selectedSessionId,
    selectedWorkspaceId,
    sessionIdParam,
    sessions,
    sessionsError,
    setSearchParams,
  ]);

  useEffect(() => {
    if (!activeSessionId) {
      setMessages([]);
      return;
    }
    let active = true;
    const loadMessages = async () => {
      setLoadingMessages(true);
      setMessagesError("");
      try {
        const data = await getChatSessionMessages(
          getAccessToken(),
          activeSessionId,
          0,
          50,
        );
        if (!active) return;
        setMessages(
          data.items.map((message) =>
            toDisplayMessage(message, language, t("common.justNow")),
          ),
        );
        setMessagePage(0);
        setMessageTotalPages(data.totalPages);
      } catch {
        if (active) {
          setMessages([]);
          setMessagesError(t("chat.loadHistoryError"));
        }
      } finally {
        if (active) setLoadingMessages(false);
      }
    };
    void loadMessages();
    return () => {
      active = false;
    };
  }, [activeSessionId, language, messagesReloadKey, t]);

  const selectWorkspace = (workspaceId: string) => {
    setSelectedWorkspaceId(workspaceId);
    setSelectedSessionId("");
    setSessions([]);
    setMessages([]);
    setQuery("");
    if (workspaceId) setSearchParams({ workspaceId });
    else setSearchParams({});
  };

  const selectSession = (sessionId: string) => {
    setSelectedSessionId(sessionId);
    setSearchParams({ workspaceId: selectedWorkspaceId, sessionId });
  };

  const loadMoreSessions = async () => {
    if (
      !selectedWorkspaceId ||
      loadingMoreSessions ||
      sessionPage + 1 >= sessionTotalPages
    ) {
      return;
    }
    setLoadingMoreSessions(true);
    setSessionsError("");
    try {
      const nextPage = sessionPage + 1;
      const data = await getWorkspaceChatSessions(
        getAccessToken(),
        selectedWorkspaceId,
        nextPage,
        20,
      );
      setSessions((current) =>
        mergeUniqueBy(current, data.items, (item) => item.chatSessionId),
      );
      setSessionPage(nextPage);
      setSessionTotalItems(data.totalItems);
    } catch {
      setSessionsError(t("chat.loadHistoryError"));
    } finally {
      setLoadingMoreSessions(false);
    }
  };

  const loadMoreMessages = async () => {
    if (
      !selectedSessionId ||
      loadingMoreMessages ||
      messagePage + 1 >= messageTotalPages
    ) {
      return;
    }
    setLoadingMoreMessages(true);
    setMessagesError("");
    try {
      const nextPage = messagePage + 1;
      const data = await getChatSessionMessages(
        getAccessToken(),
        selectedSessionId,
        nextPage,
        50,
      );
      const mapped = data.items.map((message) =>
        toDisplayMessage(message, language, t("common.justNow")),
      );
      setMessages((current) => mergeUniqueBy(current, mapped, (item) => item.id));
      setMessagePage(nextPage);
    } catch {
      setMessagesError(t("chat.loadHistoryError"));
    } finally {
      setLoadingMoreMessages(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-[1400px]">
      <PageHeader
        className="mb-lg md:!items-center"
        title={t("chatHistory.title")}
        subtitle={t("chatHistory.subtitleWorkspace")}
        actions={
          selectedWorkspaceId ? (
            <Link to={getChatUrl(selectedWorkspaceId)}>
              <Button
                variant="secondary"
                leftIcon={<MessageSquarePlus className="h-4 w-4" aria-hidden="true" />}
              >
                {t("chatHistory.openWorkspaceChat")}
              </Button>
            </Link>
          ) : undefined
        }
      />

      {workspaceError ? (
        <div
          className="mb-lg flex flex-col gap-md rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error sm:flex-row sm:items-center sm:justify-between"
          role="alert"
        >
          <span>{workspaceError}</span>
          <Button
            variant="secondary"
            size="sm"
            leftIcon={<RefreshCw className="h-4 w-4" aria-hidden="true" />}
            onClick={() => setWorkspaceReloadKey((key) => key + 1)}
          >
            {t("common.retry")}
          </Button>
        </div>
      ) : null}

      <Card className="mb-md p-md" padded={false} aria-busy={loadingWorkspaces}>
        <div className="grid gap-md lg:grid-cols-[minmax(240px,0.34fr)_minmax(0,1fr)] lg:items-center">
          <div className="min-w-0">
            <label className="label-uppercase mb-xs block" htmlFor="chat-history-workspace">
              {t("chatHistory.workspaceFilter")}
            </label>
            <select
              id="chat-history-workspace"
              className="form-field"
              value={selectedWorkspaceId}
              onChange={(event) => selectWorkspace(event.target.value)}
              disabled={loadingWorkspaces || workspaces.length === 0}
            >
              <option value="">
                {loadingWorkspaces
                  ? t("chat.loadingWorkspaces")
                  : t("chat.selectWorkspace")}
              </option>
              {workspaces.map((workspace) => (
                <option key={workspace.workspaceId} value={workspace.workspaceId}>
                  {workspace.name}
                </option>
              ))}
            </select>
          </div>

          {selectedWorkspace ? (
            <div className="flex min-w-0 items-center gap-sm rounded-xl bg-surface-container-low p-sm dark:bg-slate-800">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-700 dark:text-inverse-primary">
                <Building2 className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <p className="truncate font-semibold" title={selectedWorkspace.name}>
                  {selectedWorkspace.name}
                </p>
                <p className="mt-xs line-clamp-1 text-sm text-on-surface-variant dark:text-slate-400">
                  {selectedWorkspace.description || t("workspace.noDescription")}
                </p>
              </div>
            </div>
          ) : (
            <p className="text-sm text-on-surface-variant dark:text-slate-400">
              {t("chatHistory.selectWorkspaceHint")}
            </p>
          )}
        </div>
      </Card>

      {!loadingWorkspaces && !workspaceError && workspaces.length === 0 ? (
        <Card>
          <div className="py-xl text-center">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-xl bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
              <Building2 className="h-7 w-7" aria-hidden="true" />
            </div>
            <h2 className="mt-md text-title-lg font-semibold">
              {t("chatHistory.noWorkspacesTitle")}
            </h2>
            <p className="mx-auto mt-sm max-w-lg text-sm text-on-surface-variant dark:text-slate-400">
              {t("chatHistory.noWorkspacesDescription")}
            </p>
          </div>
        </Card>
      ) : selectedWorkspaceId ? (
        <div className="grid min-w-0 gap-md xl:min-h-[clamp(480px,calc(100vh-22rem),620px)] xl:grid-cols-[minmax(320px,0.82fr)_minmax(0,1.18fr)] xl:items-stretch xl:gap-gutter">
          <Card className="flex min-w-0 flex-col overflow-hidden xl:h-full" padded={false}>
            <div className="shrink-0 border-b border-legal-border p-md dark:border-slate-700">
              <div className="flex items-center justify-between gap-md">
                <div>
                  <h2 className="text-title-lg font-semibold">
                    {t("chatHistory.conversations")}
                  </h2>
                  <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">
                    {t("chatHistory.resultCount", { count: sessionTotalItems })}
                  </p>
                </div>
                {sessionTotalItems > 0 ? <Badge tone="blue">{sessionTotalItems}</Badge> : null}
              </div>
              <label className="sr-only" htmlFor="chat-history-search">
                {t("chatHistory.searchLabel")}
              </label>
              <SearchInput
                id="chat-history-search"
                className="mt-md"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={t("chatHistory.searchPlaceholder")}
                disabled={loadingSessions || sessions.length === 0}
              />
              {sessionTotalItems > sessions.length ? (
                <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">
                  {t("chatHistory.loadedSearchHint", {
                    loaded: sessions.length,
                    total: sessionTotalItems,
                  })}
                </p>
              ) : null}
            </div>

            <div
              className="flex min-h-[240px] min-w-0 flex-1 flex-col overflow-y-auto xl:min-h-0 xl:max-h-[calc(100vh-24rem)]"
              aria-busy={loadingSessions}
            >
              {loadingSessions ? (
                <div className="flex-1" role="status" aria-live="polite">
                  <span className="sr-only">{t("chatHistory.loadingSessions")}</span>
                  <ListSkeleton />
                </div>
              ) : sessionsError ? (
                <div className="flex flex-1 flex-col items-center justify-center p-md text-center" role="alert">
                  <p className="text-sm text-error">{sessionsError}</p>
                  <Button
                    className="mt-md"
                    variant="secondary"
                    size="sm"
                    leftIcon={<RefreshCw className="h-4 w-4" aria-hidden="true" />}
                    onClick={() => setSessionsReloadKey((key) => key + 1)}
                  >
                    {t("common.retry")}
                  </Button>
                </div>
              ) : sessions.length === 0 ? (
                <div className="flex flex-1 flex-col items-center justify-center p-md text-center">
                  <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                    <MessageSquareText className="h-6 w-6" aria-hidden="true" />
                  </div>
                  <h3 className="mt-md font-semibold">{t("chatHistory.emptyTitle")}</h3>
                  <p className="mx-auto mt-sm max-w-sm text-sm text-on-surface-variant dark:text-slate-400">
                    {t("chatHistory.emptyDescription")}
                  </p>
                  <Link
                    className="mt-md inline-flex items-center gap-xs text-sm font-semibold text-primary hover:underline dark:text-inverse-primary"
                    to={getChatUrl(selectedWorkspaceId)}
                  >
                    {t("chatHistory.openWorkspaceChat")}
                    <ArrowRight className="h-4 w-4" aria-hidden="true" />
                  </Link>
                </div>
              ) : filteredSessions.length === 0 ? (
                <div className="flex flex-1 flex-col items-center justify-center p-md text-center">
                  <SearchX className="mx-auto h-8 w-8 text-outline" aria-hidden="true" />
                  <h3 className="mt-md font-semibold">{t("chatHistory.noSearchResults")}</h3>
                  <p className="mt-sm text-sm text-on-surface-variant dark:text-slate-400">
                    {t("chatHistory.noSearchResultsDescription")}
                  </p>
                  <Button className="mt-md" variant="secondary" size="sm" onClick={() => setQuery("")}>
                    {t("chatHistory.clearSearch")}
                  </Button>
                </div>
              ) : (
                <ul
                  className="divide-y divide-legal-border dark:divide-slate-700"
                  role="listbox"
                  aria-label={t("chatHistory.conversationListLabel")}
                >
                  {filteredSessions.map((session) => {
                    const isSelected = session.chatSessionId === selectedSessionId;
                    return (
                      <li
                        key={session.chatSessionId}
                        className={`group flex items-stretch gap-xs border-l-4 transition ${
                          isSelected
                            ? "border-l-primary bg-surface-container-low dark:border-l-inverse-primary dark:bg-slate-800"
                            : "border-l-transparent hover:bg-surface-container-low/70 dark:hover:bg-slate-800/70"
                        }`}
                      >
                        <button
                          type="button"
                          role="option"
                          aria-selected={isSelected}
                          className="min-w-0 flex-1 p-md text-left sm:p-lg"
                          onClick={() => selectSession(session.chatSessionId)}
                        >
                          <span className="block truncate font-semibold text-on-surface dark:text-slate-100" title={session.title}>
                            {session.title}
                          </span>
                          <span className="mt-sm flex items-center gap-xs text-xs text-on-surface-variant dark:text-slate-400">
                            <Clock3 className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                            <span>
                              {t("chatHistory.lastUpdated")} {formatTimestamp(
                                session.lastMessageAt || session.updatedAt,
                                language,
                                t("common.justNow"),
                              )}
                            </span>
                          </span>
                          <span className="mt-md flex flex-wrap gap-xs">
                            <Badge tone={session.isDefault ? "gold" : "slate"}>
                              {session.isDefault
                                ? t("chat.defaultSession")
                                : t("chat.sessionLabel")}
                            </Badge>
                            {isSelected ? (
                              <Badge tone="blue">{t("chatHistory.selected")}</Badge>
                            ) : null}
                          </span>
                        </button>
                        <Link
                          className="mr-sm mt-sm flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-on-surface-variant transition hover:bg-white hover:text-primary focus-visible:outline-offset-0 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-inverse-primary"
                          to={getChatUrl(selectedWorkspaceId, session.chatSessionId)}
                          aria-label={`${t("chatHistory.openChat")}: ${session.title}`}
                          title={t("chatHistory.openChat")}
                        >
                          <ExternalLink className="h-4 w-4" aria-hidden="true" />
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            {sessionPage + 1 < sessionTotalPages && !loadingSessions ? (
              <div className="shrink-0 border-t border-legal-border p-md dark:border-slate-700">
                <Button
                  className="w-full"
                  variant="secondary"
                  disabled={loadingMoreSessions}
                  onClick={() => void loadMoreSessions()}
                >
                  {loadingMoreSessions ? t("common.loading") : t("actions.loadMore")}
                </Button>
              </div>
            ) : null}
          </Card>

          <aside className="min-w-0 xl:h-full">
            <Card
              className="flex min-w-0 flex-col xl:h-full"
              title={selectedSession?.title ?? t("chatHistory.previewTitle")}
              subtitle={selectedSession ? selectedWorkspace?.name : t("chatHistory.previewSubtitle")}
              actions={
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                  <MessageSquareText className="h-5 w-5" aria-hidden="true" />
                </div>
              }
              aria-busy={loadingMessages}
            >
              <div className="flex min-h-0 flex-1 flex-col">
                {!selectedSession ? (
                  <div className="flex min-h-[240px] flex-1 flex-col items-center justify-center rounded-xl bg-surface-container-low/60 p-lg text-center dark:bg-slate-800/60">
                    <MessageSquareText className="h-9 w-9 text-outline" aria-hidden="true" />
                    <h3 className="mt-md font-semibold">{t("chatHistory.selectConversationTitle")}</h3>
                    <p className="mt-sm max-w-sm text-sm text-on-surface-variant dark:text-slate-400">
                      {t("chatHistory.selectConversationDescription")}
                    </p>
                  </div>
                ) : loadingMessages ? (
                  <div className="flex-1" role="status" aria-live="polite">
                    <span className="sr-only">{t("chatHistory.loadingMessages")}</span>
                    <PreviewSkeleton />
                  </div>
                ) : messagesError ? (
                  <div className="flex min-h-[240px] flex-1 flex-col items-center justify-center text-center" role="alert">
                    <p className="text-sm text-error">{messagesError}</p>
                    <Button
                      className="mt-md"
                      variant="secondary"
                      size="sm"
                      leftIcon={<RefreshCw className="h-4 w-4" aria-hidden="true" />}
                      onClick={() => setMessagesReloadKey((key) => key + 1)}
                    >
                      {t("common.retry")}
                    </Button>
                  </div>
                ) : (
                  <div className="flex min-h-0 flex-1 flex-col">
                    <div className="min-h-[240px] flex-1 space-y-md overflow-y-auto pr-xs xl:min-h-0">
                    {messages.length === 0 ? (
                      <div className="flex h-full min-h-[220px] flex-col items-center justify-center rounded-xl bg-surface-container-low p-lg text-center dark:bg-slate-800">
                        <MessageSquareText className="h-8 w-8 text-outline" aria-hidden="true" />
                        <h3 className="mt-md font-semibold">{t("chatHistory.noMessagesTitle")}</h3>
                        <p className="mt-sm text-sm text-on-surface-variant dark:text-slate-400">
                          {t("chatHistory.noMessagesDescription")}
                        </p>
                      </div>
                    ) : (
                      messages.map((message) => (
                        <article
                          key={message.id}
                          className={`rounded-xl border p-md text-sm leading-6 ${
                            message.role === "assistant"
                              ? "border-legal-border bg-surface-container-low dark:border-slate-700 dark:bg-slate-800"
                              : "ml-auto max-w-[92%] border-primary/20 bg-primary/5 dark:border-slate-700 dark:bg-slate-900"
                          }`}
                        >
                          <div className="flex flex-wrap items-center justify-between gap-sm">
                            <p className="label-uppercase">
                              {message.role === "assistant"
                                ? t("chat.role.assistant")
                                : t("chat.role.user")}
                            </p>
                            <time className="text-xs text-on-surface-variant dark:text-slate-400">
                              {message.timestamp}
                            </time>
                          </div>
                          <ChatMessageContent content={message.content} className="mt-sm" />
                        </article>
                      ))
                    )}
                    {messagePage + 1 < messageTotalPages ? (
                      <Button
                        className="w-full"
                        variant="secondary"
                        disabled={loadingMoreMessages}
                        onClick={() => void loadMoreMessages()}
                      >
                        {loadingMoreMessages
                          ? t("common.loading")
                          : t("chat.loadOlderMessages")}
                      </Button>
                    ) : null}
                    </div>
                    <div className="mt-md shrink-0 border-t border-legal-border pt-md dark:border-slate-700">
                      <Link to={getChatUrl(selectedWorkspaceId, selectedSession.chatSessionId)}>
                        <Button
                          className="w-full"
                          rightIcon={<ArrowRight className="h-4 w-4" aria-hidden="true" />}
                        >
                          {t("chatHistory.resumeDiscussion")}
                        </Button>
                      </Link>
                    </div>
                  </div>
                )}
              </div>
            </Card>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
