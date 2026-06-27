import { FileText, UploadCloud } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
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
        actions={
          <Link to="/upload">
            <Button leftIcon={<UploadCloud className="h-4 w-4" />}>{t('actions.upload')}</Button>
          </Link>
        }
      />

      <Card title={t('reports.recent')}>
        <EmptyState
          icon={<FileText className="h-6 w-6" aria-hidden="true" />}
          title={t('reports.emptyTitle')}
          description={t('reports.emptyDescription')}
        />
      </Card>

      <Card className="mt-xl" title={t('reports.generateNewReport')}>
        <EmptyState
          title={t('reports.generatorUnavailableTitle')}
          description={t('reports.generatorUnavailableDescription')}
        />
      </Card>
    </div>
  );
}
