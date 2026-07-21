import { AlertTriangle, FolderOpen, MessageSquareText, Trash2, UploadCloud } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { SearchInput } from '../../components/common/SearchInput';
import { StatusBadge } from '../../components/common/StatusBadge';
import { deleteWorkspaceDocument, getWorkspaceDocuments, getWorkspaces } from '../../api/workspaceApi';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import type { Document, Workspace } from '../../types/workspace';
import { formatDisplayDateTime, formatFileSize, localeForLanguage } from '../../utils/format';

import { getAccessToken as getSessionAccessToken } from '../../services/authSession';
const getAccessToken = () => getSessionAccessToken() ?? '';

type DocumentRow = Document & {
  workspaceName: string;
};

export function DocumentsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = localeForLanguage(language);
  const [query, setQuery] = useState('');
  const [documents, setDocuments] = useState<DocumentRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [documentToDelete, setDocumentToDelete] = useState<DocumentRow | null>(null);
  const [deleting, setDeleting] = useState(false);

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
              workspaceId: document.workspaceId || workspace.workspaceId,
              workspaceName: workspace.name,
            }));
          }),
        );

        if (active) {
          setDocuments(documentGroups.flat());
        }
      } catch {
        if (active) {
          setError(t('documents.loadError'));
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

  const handleConfirmDelete = async () => {
    if (!documentToDelete) return;
    setDeleting(true);
    try {
      await deleteWorkspaceDocument(getAccessToken(), documentToDelete.workspaceId, documentToDelete.documentId);
      setDocuments((prev) => prev.filter((d) => d.documentId !== documentToDelete.documentId));
      toast.success(t('documents.deleteSuccess'), t('toast.successTitle'));
      setDocumentToDelete(null);
    } catch (err) {
      toast.error(t('documents.deleteFailed'), t('toast.errorTitle'));
    } finally {
      setDeleting(false);
    }
  };

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
            {document.fileType} · {formatFileSize(document.fileSize, locale)}
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
        formatDisplayDateTime(document.uploadedAt, '-', locale),
    },
    {
      header: t('table.actions'),
      cell: (document) => (
        <div className="flex items-center gap-xs">
          <Link to={`/projects/${document.workspaceId}`} aria-label={`${t('actions.openWorkspace')} ${document.workspaceName}`}>
            <Button variant="ghost" size="sm" leftIcon={<FolderOpen className="h-4 w-4" />}>
              {t('actions.openWorkspace')}
            </Button>
          </Link>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-error hover:bg-error/10 hover:text-error dark:text-red-400 dark:hover:bg-red-950/40"
            onClick={() => setDocumentToDelete(document)}
            aria-label={`Xóa tài liệu ${document.originalFileName}`}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ),
    },
  ];

  const renderContent = () => {
    if (loading) {
      return <p aria-live="polite" className="text-sm text-on-surface-variant dark:text-slate-400">{t('documents.loading')}</p>;
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

      {documentToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-md backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-slate-700 bg-slate-900 p-lg text-slate-100 shadow-2xl">
            <div className="flex items-center gap-md">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-red-950/60 text-red-400">
                <AlertTriangle className="h-6 w-6" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-slate-100">{t('documents.deleteConfirmTitle')}</h3>
                <p className="mt-xs text-xs text-slate-400">
                  {documentToDelete.originalFileName}
                </p>
              </div>
            </div>

            <p className="mt-md text-sm text-slate-300">
              {t('documents.deleteConfirmMessage').replace('{name}', documentToDelete.originalFileName)}
            </p>

            <div className="mt-lg flex justify-end gap-sm">
              <Button
                type="button"
                variant="ghost"
                disabled={deleting}
                onClick={() => setDocumentToDelete(null)}
              >
                {t('actions.cancel')}
              </Button>
              <Button
                type="button"
                variant="primary"
                className="bg-red-600 hover:bg-red-700 dark:bg-red-600 dark:hover:bg-red-700"
                disabled={deleting}
                onClick={handleConfirmDelete}
              >
                {deleting ? 'Đang xóa...' : t('documents.deleteConfirmTitle')}
              </Button>
            </div>
          </div>
        </div>
      )}

      <div className="fixed bottom-lg right-lg hidden rounded-full shadow-raised xl:block">
        <Link to="/chat">
          <Button size="lg" leftIcon={<MessageSquareText className="h-5 w-5" />}>{t('actions.ask')}</Button>
        </Link>
      </div>
    </div>
  );
}
