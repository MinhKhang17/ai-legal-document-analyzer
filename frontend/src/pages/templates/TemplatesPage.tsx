import { FileText } from 'lucide-react';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

export function TemplatesPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader title={t('templates.title')} subtitle={t('templates.subtitle')} />
      <Card>
        <EmptyState
          icon={<FileText className="h-6 w-6" aria-hidden="true" />}
          title={t('templates.emptyTitle')}
          description={t('templates.emptyDescription')}
        />
      </Card>
    </div>
  );
}
