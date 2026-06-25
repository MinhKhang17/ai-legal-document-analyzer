import { Database, Search } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

export function KnowledgeBaseDetailPage() {
  const { id } = useParams();
  const { t } = useI18n();

  return (
    <div>
      <PageHeader
        title={t('knowledgeDetail.title')}
        subtitle={id ? `${t('knowledge.source')}: ${id}` : t('knowledge.detailUnavailableSubtitle')}
        actions={
          <Link to="/knowledge-base">
            <Button variant="secondary" leftIcon={<Search className="h-4 w-4" />}>
              {t('nav.knowledgeBase')}
            </Button>
          </Link>
        }
      />

      <Card>
        <EmptyState
          icon={<Database className="h-6 w-6" aria-hidden="true" />}
          title={t('knowledge.detailUnavailableTitle')}
          description={t('knowledge.detailUnavailableDescription')}
        />
      </Card>
    </div>
  );
}
