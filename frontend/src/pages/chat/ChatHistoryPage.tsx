import { Delete, ExternalLink, MessageSquareText } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { chatThreads } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function ChatHistoryPage() {
  const { t } = useI18n();
  const selectedThread = chatThreads[0];

  return (
    <div>
      <PageHeader title={t('chatHistory.title')} subtitle={t('chatHistory.subtitle')} actions={<Button variant="secondary">{t('chatHistory.allFilters')}</Button>} />

      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_420px]">
        <Card title={t('chatHistory.conversations')}>
          <div className="space-y-md">
            {chatThreads.map((thread) => (
              <article key={thread.id} className="rounded-xl border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800">
                <div className="flex items-start justify-between gap-md">
                  <div>
                    <h2 className="font-semibold text-primary dark:text-inverse-primary">{thread.title}</h2>
                    <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{thread.documentName} · {thread.updatedAt}</p>
                  </div>
                  <div className="flex gap-xs">
                    <Link to="/chat"><Button variant="ghost" size="icon" aria-label={t('chatHistory.openChat')}><ExternalLink className="h-4 w-4" /></Button></Link>
                    <Button variant="ghost" size="icon" aria-label={t('chatHistory.deleteChat')}><Delete className="h-4 w-4" /></Button>
                  </div>
                </div>
                <div className="mt-md flex flex-wrap gap-xs">
                  {thread.riskTags.map((tag) => {
                    const level = tag.toLowerCase();
                    const supportedLevel = level === 'critical' || level === 'high' || level === 'medium' || level === 'low' || level === 'none';
                    return <Badge key={tag} tone={level === 'critical' ? 'red' : 'blue'}>{supportedLevel ? t(`risk.${level}`) : tag}</Badge>;
                  })}
                </div>
              </article>
            ))}
          </div>
        </Card>

        <aside>
          <Card title={selectedThread.title} subtitle={selectedThread.documentName} actions={<MessageSquareText className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
            <div className="space-y-md">
              {selectedThread.messages.map((message) => (
                <div key={message.id} className="rounded-lg bg-surface-container-low p-md text-sm leading-6 dark:bg-slate-800">
                  <p className="label-uppercase">{message.role === 'assistant' ? t('chat.role.assistant') : t('chat.role.user')}</p>
                  <p className="mt-xs">{message.content}</p>
                </div>
              ))}
              <Link to="/chat"><Button className="w-full">{t('chatHistory.resumeDiscussion')}</Button></Link>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
