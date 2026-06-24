import { FileSearch, FolderOpen, UploadCloud } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

export function DocumentDetailPage() {
  const { id } = useParams();
  const { t } = useI18n();

  return (
    <div>
      <PageHeader
        title={t('documentDetail.title')}
        subtitle={t('documents.detailUnavailableSubtitle')}
        eyebrow={id ? `${t('documents.documentId')}: ${id}` : t('nav.documents')}
        actions={
          <>
            <Link to="/projects">
              <Button variant="secondary" leftIcon={<FolderOpen className="h-4 w-4" />}>
                {t('nav.projects')}
              </Button>
            </Link>
            <Link to="/upload">
              <Button leftIcon={<UploadCloud className="h-4 w-4" />}>{t('actions.upload')}</Button>
            </Link>
          </>
        }
      />

      <Card>
        <EmptyState
          icon={<FileSearch className="h-6 w-6" aria-hidden="true" />}
          title={t('documents.detailUnavailableTitle')}
          description={t('documents.detailUnavailableDescription')}
        />
      </Card>
    </div>
  );
}
