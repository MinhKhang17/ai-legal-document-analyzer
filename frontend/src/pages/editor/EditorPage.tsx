import { FilePenLine, FolderOpen } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

export function EditorPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader
        title={t('editor.title')}
        subtitle={t('editor.workspaceSubtitle')}
        actions={
          <Link to="/projects">
            <Button variant="secondary" leftIcon={<FolderOpen className="h-4 w-4" />}>
              {t('nav.projects')}
            </Button>
          </Link>
        }
      />

      <Card>
        <EmptyState
          icon={<FilePenLine className="h-6 w-6" aria-hidden="true" />}
          title={t('editor.emptyTitle')}
          description={t('editor.emptyDescription')}
        />
      </Card>
    </div>
  );
}
