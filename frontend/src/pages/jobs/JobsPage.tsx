import { Activity, RotateCcw } from 'lucide-react';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatusBadge } from '../../components/common/StatusBadge';
import { processingJobs } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { ProcessingJob } from '../../types/system';

export function JobsPage() {
  const { t } = useI18n();
  const columns: DataTableColumn<ProcessingJob>[] = [
    { header: 'Job ID', cell: (job) => <span className="font-semibold text-primary dark:text-inverse-primary">{job.id}</span> },
    { header: 'Resource', cell: (job) => job.resource },
    { header: 'Stage', cell: (job) => job.description },
    { header: t('table.status'), cell: (job) => <StatusBadge status={job.status} /> },
    { header: 'Progress', cell: (job) => <div className="min-w-32"><ProgressBar value={job.progress} /></div> },
    { header: t('table.actions'), cell: () => <Button variant="ghost" leftIcon={<RotateCcw className="h-4 w-4" />}>{t('actions.retry')}</Button> },
  ];

  return (
    <div>
      <PageHeader title={t('jobs.title')} subtitle={t('jobs.subtitle')} actions={<Button leftIcon={<Activity className="h-4 w-4" />}>Start worker</Button>} />
      <Card title="Processing Workloads">
        <DataTable columns={columns} data={processingJobs} getRowKey={(job) => job.id} />
      </Card>
    </div>
  );
}
