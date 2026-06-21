import { ArrowLeft, Send } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getChatSessionDetail } from "../../api/chatSessionApi";
import type { ChatSession } from "../../api/chatSessionApi";
import {
  getChatSessionMessages,
  sendChatSessionMessage,
} from "../../api/chatMessageApi";
import type { ChatMessage } from "../../api/chatMessageApi";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function ChatSessionDetailPage() {
  const { t } = useI18n();
  const { chatSessionId } = useParams();

  const [chatSession, setChatSession] = useState<ChatSession | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [messageInput, setMessageInput] = useState("");

  const [loading, setLoading] = useState(true);
  const [messageLoading, setMessageLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [messageError, setMessageError] = useState("");

  useEffect(() => {
    const loadChatSession = async () => {
      if (!chatSessionId) return;

      try {
        setLoading(true);
        setError("");

        const data = await getChatSessionDetail(getAccessToken(), chatSessionId);
        setChatSession(data);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : t("chatSession.detailLoadFailed"),
        );
      } finally {
        setLoading(false);
      }
    };

    loadChatSession();
  }, [chatSessionId, t]);

  useEffect(() => {
    const loadMessages = async () => {
      if (!chatSessionId) return;

      try {
        setMessageLoading(true);
        setMessageError("");

        const data = await getChatSessionMessages(
          getAccessToken(),
          chatSessionId,
        );

        setMessages(data.items);
      } catch (err) {
        setMessageError(
          err instanceof Error ? err.message : t("chatMessage.loadFailed"),
        );
      } finally {
        setMessageLoading(false);
      }
    };

    loadMessages();
  }, [chatSessionId, t]);

  const handleSendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!chatSessionId) return;

    const trimmedMessage = messageInput.trim();

    if (!trimmedMessage) {
      setMessageError(t("chatMessage.required"));
      return;
    }

    try {
      setSending(true);
      setMessageError("");

      const data = await sendChatSessionMessage(getAccessToken(), chatSessionId, {
        message: trimmedMessage,
      });

      setMessages((currentMessages) => [
        ...currentMessages,
        data.userMessage,
        data.assistantMessage,
      ]);

      setMessageInput("");
    } catch (err) {
      setMessageError(
        err instanceof Error ? err.message : t("chatMessage.sendFailed"),
      );
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title={
          loading
            ? t("chatSession.loadingDetail")
            : chatSession?.title ?? t("chatSession.detail")
        }
        subtitle={t("chatSession.detailSubtitle")}
        actions={
          <Link
            to={
              chatSession
                ? `/workspaces/${chatSession.workspaceId}/chat-sessions`
                : "/workspaces"
            }
          >
            <Button
              variant="secondary"
              leftIcon={<ArrowLeft className="h-4 w-4" />}
            >
              {t("workspace.back")}
            </Button>
          </Link>
        }
      />

      {error && (
        <div className="mb-md rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <Card title={t("chatMessage.title")}>
        {messageLoading && (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("chatMessage.loading")}
          </p>
        )}

        {messageError && (
          <div className="mb-md rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
            {messageError}
          </div>
        )}

        {!messageLoading && messages.length === 0 && (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("chatMessage.empty")}
          </p>
        )}

        <div className="space-y-sm">
          {messages.map((message) => {
            const isUser = message.role === "user";

            return (
              <div
                key={message.messageId}
                className={`flex ${isUser ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[80%] rounded-2xl p-md text-sm ${
                    isUser
                      ? "bg-primary text-on-primary"
                      : "bg-surface-container-high text-on-surface dark:bg-slate-800"
                  }`}
                >
                  <p className="mb-1 text-xs opacity-80">
                    {isUser ? t("chat.role.user") : t("chat.role.assistant")}
                  </p>

                  <p className="whitespace-pre-wrap">{message.content}</p>

                  <p className="mt-2 text-xs opacity-70">
                    {new Date(message.createdAt).toLocaleString()}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        <form onSubmit={handleSendMessage} className="mt-md flex items-end gap-sm">
          <textarea
            value={messageInput}
            onChange={(event) => setMessageInput(event.target.value)}
            placeholder={t("chat.placeholder")}
            disabled={sending}
            rows={3}
            className="min-h-[48px] flex-1 resize-none rounded-xl border border-legal-border bg-surface px-md py-sm text-sm outline-none transition focus:border-primary dark:border-slate-700 dark:bg-slate-900"
          />

          <Button
            type="submit"
            variant="primary"
            rightIcon={<Send className="h-4 w-4" />}
            disabled={sending}
          >
            {sending ? t("chatMessage.sending") : t("actions.ask")}
          </Button>
        </form>
      </Card>
    </div>
  );
}