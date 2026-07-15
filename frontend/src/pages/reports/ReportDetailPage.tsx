import { FileText } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

export function ReportDetailPage() {
  const { id } = useParams();
  const { t } = useI18n();

  return (
    <div>
      <PageHeader
        title={t('reportDetail.title')}
        subtitle={id ? `${t('reports.reportId')}: ${id}` : t('reports.detailUnavailableSubtitle')}
        actions={
          <Link to="/reports">
            <Button variant="secondary">{t('nav.reports')}</Button>
          </Link>
        }
      />

      <Card>
        <div role="status" aria-live="polite"><EmptyState
          icon={<FileText className="h-6 w-6" aria-hidden="true" />}
          title={t('reports.detailUnavailableTitle')}
          description={t('reports.detailUnavailableDescription')}
        /></div>
      </Card>
    </div>
  );
}
