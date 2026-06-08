import { FileText, RefreshCw } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { DocumentPreview } from '../../components/editor/DocumentPreview';
import { LegalChatPanel } from '../../components/editor/LegalChatPanel';
import { chatThreads, documents } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function LegalChatPage() {
  const { t } = useI18n();
  const document = documents[1];
  const thread = chatThreads[0];

  return (
    <div>
      <PageHeader
        title={t('chat.title')}
        subtitle={t('chat.subtitle')}
        actions={<Button variant="secondary" leftIcon={<RefreshCw className="h-4 w-4" />}>{t('chat.resetContext')}</Button>}
      />
      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_460px]">
        <section className="space-y-md">
          <Card className="p-md">
            <div className="flex items-center gap-sm font-semibold text-primary dark:text-inverse-primary">
              <FileText className="h-5 w-5" /> {document.name}
            </div>
          </Card>
          <DocumentPreview document={document} dense />
        </section>
        <LegalChatPanel initialMessages={thread.messages} />
      </div>
    </div>
  );
}
