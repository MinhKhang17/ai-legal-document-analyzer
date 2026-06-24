import { Activity, RefreshCw } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';

export function JobsPage() {
  const { t } = useI18n();
  const toast = useToast();

  return (
    <div>
      <PageHeader
        title={t('jobs.title')}
        subtitle={t('jobs.subtitle')}
        actions={
          <Button
            variant="secondary"
            leftIcon={<RefreshCw className="h-4 w-4" />}
            onClick={() => toast.info(t('jobs.refreshNeedsApi'), t('toast.infoTitle'))}
          >
            {t('billing.refresh')}
          </Button>
        }
      />

      <Card title={t('system.liveProcessingQueue')}>
        <EmptyState
          icon={<Activity className="h-6 w-6" aria-hidden="true" />}
          title={t('jobs.emptyTitle')}
          description={t('jobs.emptyDescription')}
        />
      </Card>
    </div>
  );
}
