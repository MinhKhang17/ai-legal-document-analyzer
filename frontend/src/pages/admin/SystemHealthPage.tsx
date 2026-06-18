import { AlertTriangle, Database, Trash2 } from 'lucide-react';
import { SystemHealthCard } from '../../components/admin/SystemHealthCard';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatusBadge } from '../../components/common/StatusBadge';
import { processingJobs, systemServices } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { ProcessingJob } from '../../types/system';

export function SystemHealthPage() {
  const { t } = useI18n();
  const failedJobs = processingJobs.filter((job) => job.status === 'failed');
  const liveJobs = processingJobs.filter((job) => job.status !== 'failed');
  const columns: DataTableColumn<ProcessingJob>[] = [
    { header: t('system.jobId'), cell: (job) => <span className="font-semibold">{job.id}</span> },
    { header: t('system.resource'), cell: (job) => job.resource },
    { header: t('system.errorType'), cell: (job) => <Badge tone="red">{job.errorType}</Badge> },
    { header: t('audit.timestamp'), cell: (job) => job.timestamp ?? '—' },
    { header: t('table.actions'), cell: () => <Button variant="ghost">{t('actions.retry')}</Button> },
  ];

  return (
    <div>
      <PageHeader title={t('system.title')} subtitle={t('system.subtitle')} />

      <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
        {systemServices.map((service) => <SystemHealthCard key={service.id} service={service} />)}
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[360px_1fr]">
        <aside className="space-y-gutter">
          <Card title={t('system.performanceMetrics')}>
            <div className="space-y-md">
              <div>
                <p className="label-uppercase">{t('system.avgResponseTime')}</p>
                <p className="mt-xs text-3xl font-bold">1.4s</p>
                <p className="mt-xs text-sm text-emerald-700 dark:text-emerald-300">↓ 12%</p>
              </div>
              <div className="h-px bg-legal-border dark:bg-slate-700" />
              <div>
                <p className="label-uppercase">{t('system.currentQueue')}</p>
                <p className="mt-xs text-3xl font-bold">{t('system.currentQueueValue')}</p>
                <p className="mt-xs text-sm text-secondary dark:text-accent-gold">{t('system.lowLoad')}</p>
              </div>
              <div className="h-px bg-legal-border dark:bg-slate-700" />
              <div>
                <p className="label-uppercase">{t('system.failedJobs24h')}</p>
                <p className="mt-xs text-3xl font-bold text-error">12</p>
              </div>
            </div>
          </Card>
          <Card tone="ai">
            <div className="flex items-start gap-sm">
              <Database className="mt-0.5 h-5 w-5 text-secondary dark:text-accent-gold" />
              <div>
                <h2 className="text-title-lg font-semibold">{t('system.systemRecommendation')}</h2>
                <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
                  {t('system.systemRecommendationText')}
                </p>
              </div>
            </div>
          </Card>
        </aside>

        <main className="space-y-gutter">
          <Card title={t('system.liveProcessingQueue')} actions={<Badge tone="slate">{t('system.activeWorkers')}</Badge>}>
            <div className="space-y-md">
              {liveJobs.map((job) => (
                <div key={job.id} className="rounded-xl border border-legal-border p-md dark:border-slate-700">
                  <div className="flex flex-col gap-md md:flex-row md:items-center md:justify-between">
                    <div>
                      <p className="font-semibold">{job.resource}</p>
                      <p className="text-sm text-on-surface-variant dark:text-slate-400">{job.description}</p>
                    </div>
                    <StatusBadge status={job.status} />
                  </div>
                  {job.status === 'processing' && (
                    <div className="mt-md">
                      <div className="mb-xs flex items-center justify-between text-sm"><span>{t('system.progress')}</span><span>{job.progress}%</span></div>
                      <ProgressBar value={job.progress} />
                    </div>
                  )}
                </div>
              ))}
            </div>
          </Card>

          <Card title={t('system.recentFailedJobs')} actions={<Button variant="danger" leftIcon={<Trash2 className="h-4 w-4" />}>{t('actions.clearAll')}</Button>}>
            <DataTable columns={columns} data={failedJobs} getRowKey={(job) => job.id} />
          </Card>

          <Card className="border-error/30" title={t('system.operationalNote')} actions={<AlertTriangle className="h-5 w-5 text-error" />}>
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('system.operationalNoteText')}</p>
          </Card>
        </main>
      </section>
    </div>
  );
}
