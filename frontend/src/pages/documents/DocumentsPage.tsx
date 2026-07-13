import { FolderOpen, MessageSquareText, UploadCloud } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { SearchInput } from '../../components/common/SearchInput';
import { StatusBadge } from '../../components/common/StatusBadge';
import { getWorkspaceDocuments, getWorkspaces } from '../../api/workspaceApi';
import { useI18n } from '../../hooks/useI18n';
import type { Document, Workspace } from '../../types/workspace';
import { formatDisplayDateTime } from '../../utils/format';

import { getAccessToken as getSessionAccessToken } from '../../services/authSession';
const getAccessToken = () => getSessionAccessToken() ?? '';

type DocumentRow = Document & {
  workspaceName: string;
};

export function DocumentsPage() {
  const { t, language } = useI18n();
  const [query, setQuery] = useState('');
  const [documents, setDocuments] = useState<DocumentRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    const loadDocuments = async () => {
      setLoading(true);
      setError('');

      try {
        const workspaces = await getWorkspaces(getAccessToken());
        const documentGroups = await Promise.all(
          workspaces.map(async (workspace: Workspace) => {
            const workspaceDocuments = await getWorkspaceDocuments(getAccessToken(), workspace.workspaceId);
            return workspaceDocuments.map((document) => ({
              ...document,
              workspaceName: workspace.name,
            }));
          }),
        );

        if (active) {
          setDocuments(documentGroups.flat());
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : t('documents.loadError'));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadDocuments();

    return () => {
      active = false;
    };
  }, [t]);

  const filteredDocuments = useMemo(
    () =>
      documents.filter((document) =>
        `${document.originalFileName} ${document.fileType} ${document.workspaceName}`.toLowerCase().includes(query.toLowerCase()),
      ),
    [documents, query],
  );

  const columns: DataTableColumn<DocumentRow>[] = [
    {
      header: t('table.document'),
      cell: (document) => (
        <div>
          <p className="font-semibold text-on-surface dark:text-slate-100">
            {document.originalFileName}
          </p>
          <p className="text-xs text-on-surface-variant dark:text-slate-400">
            {document.fileType} · {new Intl.NumberFormat(language === 'vi' ? 'vi-VN' : 'en-US').format(document.fileSize)} bytes
          </p>
        </div>
      ),
    },
    {
      header: t('table.project'),
      cell: (document) => (
        <Link to={`/projects/${document.workspaceId}`} className="font-semibold text-primary hover:underline dark:text-inverse-primary">
          {document.workspaceName}
        </Link>
      ),
    },
    { header: t('documents.type'), cell: (document) => document.fileType },
    { header: t('table.status'), cell: (document) => <StatusBadge status={document.status} /> },
    {
      header: t('table.date'),
      cell: (document) =>
        formatDisplayDateTime(document.uploadedAt, '-', language === 'vi' ? 'vi-VN' : 'en-US'),
    },
    {
      header: t('table.actions'),
      cell: (document) => (
        <Link to={`/projects/${document.workspaceId}`} aria-label={`${t('actions.openWorkspace')} ${document.workspaceName}`}>
          <Button variant="ghost" size="sm" leftIcon={<FolderOpen className="h-4 w-4" />}>
            {t('actions.openWorkspace')}
          </Button>
        </Link>
      ),
    },
  ];

  const renderContent = () => {
    if (loading) {
      return <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('documents.loading')}</p>;
    }

    if (error) {
      return (
        <EmptyState
          title={t('documents.loadErrorTitle')}
          description={error}
        />
      );
    }

    if (documents.length === 0) {
      return (
        <EmptyState
          title={t('documents.emptyTitle')}
          description={t('documents.emptyDescription')}
        />
      );
    }

    return (
      <>
        <div className="mb-lg">
          <SearchInput value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('documents.searchPlaceholder')} containerClassName="lg:w-96" />
        </div>
        <DataTable columns={columns} data={filteredDocuments} getRowKey={(document) => document.documentId} emptyMessage={t('documents.noSearchResults')} />
      </>
    );
  };

  return (
    <div>
      <PageHeader
        title={t('documents.title')}
        subtitle={t('documents.subtitle')}
        actions={
          <Link to="/upload">
            <Button leftIcon={<UploadCloud className="h-4 w-4" />}>{t('actions.upload')}</Button>
          </Link>
        }
      />

      <Card title={t('documents.recent')}>
        {renderContent()}
      </Card>

      <div className="fixed bottom-lg right-lg hidden rounded-full shadow-raised xl:block">
        <Link to="/chat">
          <Button size="lg" leftIcon={<MessageSquareText className="h-5 w-5" />}>{t('actions.ask')}</Button>
        </Link>
      </div>
    </div>
  );
}
