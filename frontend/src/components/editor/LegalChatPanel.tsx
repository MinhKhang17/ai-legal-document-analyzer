import { Bot, Copy, Send, UserRound } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { Card } from '../common/Card';
import type { ChatMessage } from '../../types/chat';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';
import { ChatMessageContent } from '../chat/ChatMessageContent';

interface LegalChatPanelProps {
  initialMessages: ChatMessage[];
  compact?: boolean;
}

const createAssistantMessage = (query: string, language: 'en' | 'vi'): ChatMessage => ({
  id: `assistant-${Date.now()}`,
  role: 'assistant',
  timestamp: language === 'vi' ? 'Bây giờ' : 'Now',
  content:
    language === 'vi'
      ? `LexiGuard AI đánh dấu đây là điểm cần đàm phán thay vì chặn tự động. Với “${query.slice(0, 80)}”, hãy dùng ngôn ngữ điều khoản có dẫn chiếu, đề nghị trần trách nhiệm, và chỉ giữ ngoại lệ cho hành vi cố ý, vi phạm bảo mật, hoặc sự cố dữ liệu.`
      : `LexiGuard AI flags this as a negotiation issue rather than an automatic blocker. For “${query.slice(0, 80)}”, use the cited clause language, ask for a liability cap, and preserve carve-outs only for willful misconduct, confidentiality breach, or data incidents.`,
  citations: ['Clause 8.1', 'Corporate MSA Baseline', 'Article 301 Law on Commerce 2005'],
});

export function LegalChatPanel({ initialMessages, compact = false }: LegalChatPanelProps) {
  const { t, language } = useI18n();
  const [messages, setMessages] = useState<ChatMessage[]>(initialMessages);
  const [input, setInput] = useState('');
  const hasInput = input.trim().length > 0;

  const lastAssistantMessage = useMemo(() => messages.filter((message) => message.role === 'assistant').at(-1), [messages]);

  const sendMessage = () => {
    if (!hasInput) return;
    const query = input.trim();
    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: query,
      timestamp: language === 'vi' ? 'Bây giờ' : 'Now',
    };
    setMessages((previous) => [...previous, userMessage, createAssistantMessage(query, language)]);
    setInput('');
  };

  return (
    <Card className={cn('flex min-h-[620px] flex-col p-0', compact && 'min-h-[520px]')}>
      <header className="border-b border-legal-border p-lg dark:border-slate-700">
        <div className="flex items-center justify-between gap-md">
          <div>
            <h2 className="font-sans text-headline-md text-primary dark:text-inverse-primary">{t('chat.title')}</h2>
            <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{t('chat.subtitle')}</p>
          </div>
          <Badge tone="gold">RAG</Badge>
        </div>
        {lastAssistantMessage?.citations && (
          <div className="mt-md flex flex-wrap gap-xs">
            {lastAssistantMessage.citations.map((citation) => (
              <Badge key={citation} tone="blue">
                {citation}
              </Badge>
            ))}
          </div>
        )}
      </header>

      <div className="flex-1 space-y-md overflow-y-auto bg-surface-container-low/60 p-lg dark:bg-slate-950/40">
        {messages.map((message) => {
          const assistant = message.role === 'assistant';
          return (
            <article key={message.id} className={cn('flex gap-md', !assistant && 'justify-end')}>
              {assistant && (
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary text-white">
                  <Bot className="h-5 w-5" aria-hidden="true" />
                </div>
              )}
              <div
                className={cn(
                  'max-w-[82%] rounded-xl border p-md text-sm leading-6 shadow-sm',
                  assistant
                    ? 'border-legal-border bg-white text-on-surface dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100'
                    : 'border-primary bg-primary text-white',
                )}
              >
                {assistant ? (
                  <ChatMessageContent content={message.content} className="text-on-surface dark:text-slate-100" />
                ) : (
                  <p className="whitespace-pre-wrap">{message.content}</p>
                )}
                {assistant && message.citations && (
                  <div className="mt-md flex flex-wrap gap-xs">
                    {message.citations.map((citation) => (
                      <Badge key={citation} tone="slate">
                        {citation}
                      </Badge>
                    ))}
                  </div>
                )}
                <div className="mt-sm flex items-center justify-between gap-sm text-[11px] opacity-70">
                  <span>{message.timestamp}</span>
                  {assistant && <Copy className="h-3 w-3" aria-hidden="true" />}
                </div>
              </div>
              {!assistant && (
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-secondary-container text-secondary">
                  <UserRound className="h-5 w-5" aria-hidden="true" />
                </div>
              )}
            </article>
          );
        })}
      </div>

      <form
        className="border-t border-legal-border p-md dark:border-slate-700"
        onSubmit={(event) => {
          event.preventDefault();
          sendMessage();
        }}
      >
        <label className="sr-only" htmlFor="legal-chat-input">
          {t('chat.placeholder')}
        </label>
        <div className="flex gap-sm">
          <input
            id="legal-chat-input"
            className="form-field"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder={t('chat.placeholder')}
          />
          <Button type="submit" size="icon" aria-label={t('actions.ask')} disabled={!hasInput}>
            <Send className="h-5 w-5" />
          </Button>
        </div>
      </form>
    </Card>
  );
}
