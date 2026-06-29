import { BarChart3, FileText, FolderOpen, MessageSquareText, SearchCheck, ShieldAlert, UploadCloud } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { getWorkspaceDocuments, getWorkspaces } from '../../api/workspaceApi';
import { useI18n } from '../../hooks/useI18n';
import type { Document, Workspace } from '../../types/workspace';

const getAccessToken = () => localStorage.getItem('accessToken') ?? '';

type WorkspaceWithDocs = Workspace & { documents: Document[] };

export function DashboardPage() {
  const { t, language } = useI18n();
  const [workspaces, setWorkspaces] = useState<WorkspaceWithDocs[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const locale = language === 'vi' ? 'vi-VN' : 'en-US';

  useEffect(() => {
    let active = true;

    const loadDashboard = async () => {
      try {
        setLoading(true);
        setError('');

        const workspaceList = await getWorkspaces(getAccessToken());
        const workspaceDetails = await Promise.all(
          workspaceList.map(async (workspace) => ({
            ...workspace,
            documents: await getWorkspaceDocuments(getAccessToken(), workspace.workspaceId),
          })),
        );

        if (active) {
          setWorkspaces(workspaceDetails);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : t('dashboard.loadError'));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadDashboard();

    return () => {
      active = false;
    };
  }, [t]);

  const stats = useMemo(() => {
    const totalWorkspaces = workspaces.length;
    const totalDocuments = workspaces.reduce((sum, workspace) => sum + workspace.documents.length, 0);
    const readyDocuments = workspaces.reduce(
      (sum, workspace) => sum + workspace.documents.filter((document) => document.status === 'ready').length,
      0,
    );
    const processingDocuments = workspaces.reduce(
      (sum, workspace) => sum + workspace.documents.filter((document) => document.status === 'processing').length,
      0,
    );

    return {
      totalWorkspaces,
      totalDocuments,
      readyDocuments,
      processingDocuments,
    };
  }, [workspaces]);

  const recentWorkspaces = useMemo(
    () => [...workspaces].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 3),
    [workspaces],
  );

  const recentDocuments = useMemo(() => {
    return workspaces
      .flatMap((workspace) =>
        workspace.documents.map((document) => ({
          ...document,
          workspaceName: workspace.name,
        })),
      )
      .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
      .slice(0, 5);
  }, [workspaces]);

  const readyPercent = stats.totalDocuments === 0 ? 0 : Math.round((stats.readyDocuments / stats.totalDocuments) * 100);
  const processingPercent = stats.totalDocuments === 0 ? 0 : Math.round((stats.processingDocuments / stats.totalDocuments) * 100);

  return (
    <div>
      <PageHeader
        title={t('dashboard.title')}
        subtitle={t('dashboard.subtitle')}
        actions={
          <>
            <Link to="/chat/history">
              <Button variant="secondary" leftIcon={<MessageSquareText className="h-4 w-4" />}>
                {t('actions.history')}
              </Button>
            </Link>
            <Link to="/upload">
              <Button leftIcon={<UploadCloud className="h-4 w-4" />}>{t('actions.upload')}</Button>
            </Link>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
        <StatCard label={t('dashboard.totalDocuments')} value={loading ? '...' : String(stats.totalDocuments)} change={loading ? undefined : t('dashboard.readyCount').replace('{count}', String(stats.readyDocuments))} trend="up" icon={<FileText className="h-5 w-5" />} />
        <StatCard label={t('dashboard.reviewedContracts')} value={loading ? '...' : String(stats.readyDocuments)} change={loading ? undefined : t('dashboard.processingPercent').replace('{percent}', String(processingPercent))} trend="up" icon={<SearchCheck className="h-5 w-5" />} accent="green" />
        <StatCard label={t('dashboard.processingDocuments')} value={loading ? '...' : String(stats.processingDocuments)} change={loading ? undefined : t('dashboard.fromActiveUploads')} trend="neutral" icon={<ShieldAlert className="h-5 w-5" />} accent="red" />
        <StatCard label={t('dashboard.activeProjects')} value={loading ? '...' : String(stats.totalWorkspaces)} change={loading ? undefined : t('dashboard.userWorkspaces')} trend="neutral" icon={<FolderOpen className="h-5 w-5" />} accent="gold" />
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[1.2fr_0.8fr]">
        <Card title={t('dashboard.documentReadiness')} actions={<Badge tone="blue">{t('dashboard.liveData')}</Badge>}>
          <div className="space-y-md">
            <div className="flex items-center justify-between text-sm">
              <span className="font-semibold">{t('dashboard.documentsReady')}</span>
              <span>{readyPercent}%</span>
            </div>
            <ProgressBar value={readyPercent} />
            <div className="flex items-center justify-between text-sm">
              <span className="font-semibold">{t('dashboard.documentsProcessing')}</span>
              <span>{processingPercent}%</span>
            </div>
            <ProgressBar value={processingPercent} />
            <p className="text-sm text-on-surface-variant dark:text-slate-300">
              {t('dashboard.realDataDescription')}
            </p>
          </div>
        </Card>

        <Card tone="ai">
          <h2 className="text-title-lg font-semibold">{t('dashboard.aiInsight')}</h2>
          <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
            {t('dashboard.workspaceInsight')}
          </p>
          <Link to="/upload">
            <Button className="mt-md" variant="gold" leftIcon={<UploadCloud className="h-4 w-4" />}>
              {t('actions.upload')}
            </Button>
          </Link>
        </Card>
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[1.1fr_0.9fr]">
        <Card title={t('dashboard.activeProjects')}>
          {loading ? (
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('workspace.loading')}</p>
          ) : recentWorkspaces.length === 0 ? (
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('workspace.empty')}</p>
          ) : (
            <div className="space-y-md">
              {recentWorkspaces.map((workspace) => {
                const readyCount = workspace.documents.filter((document) => document.status === 'ready').length;
                return (
                  <Link
                    key={workspace.workspaceId}
                    to={`/projects/${workspace.workspaceId}`}
                    className="block rounded-lg border border-legal-border p-md transition hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                  >
                    <div className="flex items-start justify-between gap-md">
                      <div>
                        <p className="font-semibold">{workspace.name}</p>
                        <p className="text-sm text-on-surface-variant dark:text-slate-400">
                          {workspace.description || t('workspace.noDescription')}
                        </p>
                      </div>
                      <StatusBadge status={workspace.status} />
                    </div>
                    <div className="mt-md flex items-center justify-between text-sm text-on-surface-variant dark:text-slate-400">
                      <span>{workspace.documents.length} {t('nav.documents').toLowerCase()}</span>
                      <span>{readyCount} {t('status.ready').toLowerCase()}</span>
                    </div>
                  </Link>
                );
              })}
            </div>
          )}
        </Card>

        <Card title={t('dashboard.processingQueue')} actions={<Badge tone="gold">{t('dashboard.activeCount').replace('{count}', String(stats.processingDocuments))}</Badge>}>
          <div className="space-y-md">
            {recentDocuments.length === 0 ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('documents.emptyTitle')}</p>
            ) : (
              recentDocuments.map((document) => (
                <div key={document.documentId} className="rounded-lg border border-legal-border p-md dark:border-slate-700">
                  <div className="flex items-center justify-between gap-md">
                    <div>
                      <p className="font-semibold">{document.originalFileName}</p>
                      <p className="text-sm text-on-surface-variant dark:text-slate-400">{document.workspaceName}</p>
                    </div>
                    <StatusBadge status={document.status} />
                  </div>
                  <p className="mt-sm text-xs text-on-surface-variant dark:text-slate-400">
                    {new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(document.uploadedAt))}
                  </p>
                </div>
              ))
            )}
          </div>
        </Card>
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[1.35fr_0.65fr]">
        <section className="paper-card p-md">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">
              {t('dashboard.recentReviews')}
            </h2>
            <Link className="text-sm font-semibold text-primary dark:text-inverse-primary" to="/projects">
              {t('actions.viewAll')}
            </Link>
          </div>

          <div className="hidden overflow-hidden rounded-xl border border-legal-border md:block dark:border-slate-700">
            <table className="w-full table-fixed divide-y divide-legal-border text-sm dark:divide-slate-700">
              <colgroup>
                <col className="w-[40%]" />
                <col className="w-[24%]" />
                <col className="w-[14%]" />
                <col className="w-[22%]" />
              </colgroup>
              <thead className="bg-surface-container-low dark:bg-slate-800">
                <tr>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('table.document')}
                  </th>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('workspace.title')}
                  </th>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('table.status')}
                  </th>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('documents.uploadedAt')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-legal-border bg-white dark:divide-slate-800 dark:bg-slate-900">
                {recentDocuments.map((document) => (
                  <tr key={document.documentId} className="align-top">
                    <td className="px-md py-sm">
                      <p className="line-clamp-2 break-words font-semibold text-on-surface dark:text-slate-100">
                        {document.originalFileName}
                      </p>
                      <p className="mt-0.5 text-xs text-on-surface-variant dark:text-slate-400">
                        {document.fileType}
                      </p>
                    </td>
                    <td className="px-md py-sm text-on-surface dark:text-slate-100">
                      <p className="line-clamp-2 break-words">{document.workspaceName}</p>
                    </td>
                    <td className="px-md py-sm">
                      <StatusBadge
                        status={document.status}
                        className="inline-flex whitespace-nowrap px-2.5 py-1 text-xs normal-case tracking-normal"
                      />
                    </td>
                    <td className="px-md py-sm text-xs text-on-surface-variant dark:text-slate-400">
                      <span className="whitespace-nowrap">
                        {new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(document.uploadedAt))}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="space-y-3 md:hidden">
            {recentDocuments.map((document) => (
              <article
                key={document.documentId}
                className="rounded-xl border border-legal-border bg-white p-sm dark:border-slate-700 dark:bg-slate-900"
              >
                <h3 className="line-clamp-2 break-words text-sm font-semibold text-on-surface dark:text-slate-100">
                  {document.originalFileName}
                </h3>
                <p className="mt-1 text-xs text-on-surface-variant dark:text-slate-400">
                  {document.fileType}
                </p>

                <div className="mt-3 grid grid-cols-2 gap-2">
                  <div className="col-span-2">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.05em] text-on-surface-variant dark:text-slate-400">
                      {t('workspace.title')}
                    </p>
                    <p className="mt-0.5 break-words text-sm text-on-surface dark:text-slate-100">
                      {document.workspaceName}
                    </p>
                  </div>

                  <div>
                    <p className="text-[11px] font-semibold uppercase tracking-[0.05em] text-on-surface-variant dark:text-slate-400">
                      {t('table.status')}
                    </p>
                    <div className="mt-1">
                      <StatusBadge
                        status={document.status}
                        className="inline-flex whitespace-nowrap px-2.5 py-1 text-xs normal-case tracking-normal"
                      />
                    </div>
                  </div>

                  <div className="col-span-2">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.05em] text-on-surface-variant dark:text-slate-400">
                      {t('documents.uploadedAt')}
                    </p>
                    <p className="mt-0.5 whitespace-nowrap text-sm text-on-surface dark:text-slate-100">
                      {new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(document.uploadedAt))}
                    </p>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>

        <Card title={t('dashboard.quickActions')}>
          <div className="grid gap-md">
            <Link to="/upload" className="rounded-xl border border-legal-border bg-white p-md transition hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800">
              <div className="flex items-center gap-md">
                <UploadCloud className="h-5 w-5 text-primary dark:text-inverse-primary" />
                <div>
                  <p className="font-semibold">{t('actions.upload')}</p>
                  <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('dashboard.uploadActionDescription')}</p>
                </div>
              </div>
            </Link>
            <Link to="/projects" className="rounded-xl border border-legal-border bg-white p-md transition hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800">
              <div className="flex items-center gap-md">
                <BarChart3 className="h-5 w-5 text-secondary dark:text-accent-gold" />
                <div>
                  <p className="font-semibold">{t('actions.openWorkspace')}</p>
                  <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('dashboard.openWorkspaceDescription')}</p>
                </div>
              </div>
            </Link>
          </div>
        </Card>
      </section>
    </div>
  );
}
