import { MessageSquare, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  createChatSession,
  getWorkspaceChatSessions,
} from "../../api/chatSessionApi";
import type { ChatSession } from "../../api/chatSessionApi";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function ChatSessionListPage() {
  const { t } = useI18n();
  const { workspaceId } = useParams();

  const [chatSessions, setChatSessions] = useState<ChatSession[]>([]);
  const [title, setTitle] = useState("");
  const [showCreateForm, setShowCreateForm] = useState(false);

  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState("");

  const loadChatSessions = async () => {
    if (!workspaceId) return;

    try {
      setLoading(true);
      setError("");

      const data = await getWorkspaceChatSessions(getAccessToken(), workspaceId);
      setChatSessions(data.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("chatSession.loadFailed"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadChatSessions();
  }, [workspaceId, t]);

  const handleCreateChatSession = async () => {
    if (!workspaceId) return;

    const trimmedTitle = title.trim();

    if (!trimmedTitle) {
      setError(t("chatSession.titleRequired"));
      return;
    }

    try {
      setCreating(true);
      setError("");

      await createChatSession(getAccessToken(), workspaceId, {
        title: trimmedTitle,
      });

      setTitle("");
      setShowCreateForm(false);
      await loadChatSessions();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : t("chatSession.createFailed"),
      );
    } finally {
      setCreating(false);
    }
  };

  const handleCancelCreate = () => {
    setTitle("");
    setShowCreateForm(false);
    setError("");
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        title={t("chatSession.title")}
        subtitle={t("chatSession.subtitle")}
      />

      <Card title={t("chatSession.myChatSessions")}>
        <div className="mb-md flex justify-end">
          <Button
            variant="primary"
            rightIcon={<Plus className="h-4 w-4" />}
            onClick={() => setShowCreateForm(true)}
            disabled={creating || showCreateForm}
          >
            {t("chatSession.create")}
          </Button>
        </div>

        {showCreateForm && (
          <div className="mb-md rounded-xl border border-legal-border bg-surface-container-low p-md dark:border-slate-700 dark:bg-slate-900">
            <div className="flex flex-col gap-sm sm:flex-row">
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder={t("chatSession.titlePlaceholder")}
                disabled={creating}
                className="flex-1 rounded-xl border border-legal-border bg-surface px-md py-sm text-sm outline-none transition focus:border-primary dark:border-slate-700 dark:bg-slate-950"
              />

              <Button
                variant="primary"
                onClick={handleCreateChatSession}
                disabled={creating}
              >
                {creating ? t("chatSession.creating") : t("actions.save")}
              </Button>

              <Button
                variant="secondary"
                onClick={handleCancelCreate}
                disabled={creating}
              >
                {t("actions.cancel")}
              </Button>
            </div>
          </div>
        )}

        {loading && (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("chatSession.loading")}
          </p>
        )}

        {error && (
          <div className="mb-md rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
            {error}
          </div>
        )}

        {!loading && !error && chatSessions.length === 0 && (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("chatSession.empty")}
          </p>
        )}

        <div className="grid gap-md md:grid-cols-2 xl:grid-cols-3">
          {chatSessions.map((session) => (
            <Link
              key={session.chatSessionId}
              to={`/chat-sessions/${session.chatSessionId}`}
              className="rounded-xl border border-legal-border p-md transition hover:border-primary hover:shadow-raised dark:border-slate-700"
            >
              <div className="flex items-center gap-sm">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                  <MessageSquare className="h-5 w-5" />
                </div>

                <div>
                  <p className="font-semibold">{session.title}</p>

                  {session.lastMessageAt && (
                    <p className="mt-1 text-xs text-on-surface-variant dark:text-slate-500">
                      {t("chatSession.lastMessage")}:{" "}
                      {new Date(session.lastMessageAt).toLocaleString()}
                    </p>
                  )}
                </div>
              </div>
            </Link>
          ))}
        </div>
      </Card>
    </div>
  );
}