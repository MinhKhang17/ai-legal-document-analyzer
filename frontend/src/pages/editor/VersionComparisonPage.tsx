import { PageHeader } from '../../components/common/PageHeader';
import { VersionComparisonView } from '../../components/editor/VersionComparisonView';
import { useI18n } from '../../hooks/useI18n';

export function VersionComparisonPage() {
  const { t } = useI18n();
  return (
    <div>
      <PageHeader title={t('version.title')} subtitle={t('version.subtitle')} />
      <VersionComparisonView />
    </div>
  );
}
