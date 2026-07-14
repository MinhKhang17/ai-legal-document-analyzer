import { FileText } from 'lucide-react';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

export function ReportsPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader
        title={t('reports.title')}
        subtitle={t('reports.subtitle')}
      />

      <Card title={t('reports.generatorUnavailableTitle')}>
        <EmptyState
          icon={<FileText className="h-6 w-6" aria-hidden="true" />}
          title={t('reports.generatorUnavailableTitle')}
          description={t('reports.generatorUnavailableDescription')}
        />
      </Card>
    </div>
  );
}
