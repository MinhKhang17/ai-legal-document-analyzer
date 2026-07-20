import { Bot, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getSharedChatSession } from '../../api/chatApi';
import { ChatMessageContent } from '../../components/chat/ChatMessageContent';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';
import { getAccessToken } from '../../services/authSession';
import type { SharedChatSession } from '../../types/chat';

export function SharedChatPage() {
  const { t } = useI18n();
  const { shareToken = '' } = useParams();
  const [session, setSession] = useState<SharedChatSession | null>(null);
  const [error, setError] = useState('');
  useEffect(() => { let active = true; void getSharedChatSession(getAccessToken() ?? '', shareToken).then((value) => { if (active) setSession(value); }).catch(() => { if (active) setError(t('sharedChat.loadError')); }); return () => { active = false; }; }, [shareToken, t]);
  return <div><PageHeader title={session?.title ?? t('sharedChat.title')} subtitle={session ? t('sharedChat.ownerReadOnly', { owner: session.ownerName }) : t('sharedChat.subtitle')} />{error && <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error" role="alert">{error}</div>}<Card>{!session ? <p className="text-sm text-on-surface-variant" role="status">{t('common.loading')}</p> : <div className="space-y-md">{session.messages.map((message) => { const assistant = message.role.toLowerCase() === 'assistant'; return <article key={message.messageId} className={`flex gap-md ${assistant ? '' : 'justify-end'}`}>{assistant ? <Bot className="mt-sm h-5 w-5 text-primary" aria-hidden="true" /> : null}<div className={`max-w-[82%] rounded-xl border p-md ${assistant ? 'border-legal-border bg-white dark:border-slate-700 dark:bg-slate-900' : 'border-primary bg-primary text-white'}`}><ChatMessageContent content={message.content} /></div>{!assistant ? <UserRound className="mt-sm h-5 w-5" aria-hidden="true" /> : null}</article>; })}</div>}</Card></div>;
}
