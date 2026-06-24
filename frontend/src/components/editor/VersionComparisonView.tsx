import { GitCompare, UploadCloud } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../common/Button';
import { Card } from '../common/Card';
import { EmptyState } from '../common/EmptyState';
import { useI18n } from '../../hooks/useI18n';

export function VersionComparisonView() {
  const { t } = useI18n();

  return (
    <Card>
      <EmptyState
        icon={<GitCompare className="h-6 w-6" aria-hidden="true" />}
        title={t('version.emptyTitle')}
        description={t('version.emptyDescription')}
      />
      <div className="mt-lg flex justify-center">
        <Link to="/upload">
          <Button leftIcon={<UploadCloud className="h-4 w-4" />}>{t('actions.upload')}</Button>
        </Link>
      </div>
    </Card>
  );
}
