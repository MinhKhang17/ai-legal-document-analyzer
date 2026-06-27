import { GitCompare } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

export function ComparisonHistoryPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader
        title={t('comparisonHistory.title')}
        subtitle={t('comparisonHistory.subtitle')}
        actions={
          <Link to="/editor/compare">
            <Button variant="secondary" leftIcon={<GitCompare className="h-4 w-4" />}>
              {t('nav.versionComparison')}
            </Button>
          </Link>
        }
      />

      <Card title={t('comparisonHistory.records')}>
        <EmptyState
          icon={<GitCompare className="h-6 w-6" aria-hidden="true" />}
          title={t('comparisonHistory.emptyTitle')}
          description={t('comparisonHistory.emptyDescription')}
        />
      </Card>
    </div>
  );
}
