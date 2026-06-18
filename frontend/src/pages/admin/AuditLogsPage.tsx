import { ChevronRight, Download, Filter, Timer, X } from 'lucide-react';
import { useState } from 'react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { Modal } from '../../components/common/Modal';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { auditLogs } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { AuditLog } from '../../types/audit';

export function AuditLogsPage() {
  const { t } = useI18n();
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(auditLogs[0]);
  const [detailsOpen, setDetailsOpen] = useState(false);

  const columns: DataTableColumn<AuditLog>[] = [
    { header: t('audit.timestamp'), cell: (log) => log.timestamp },
    {
      header: t('table.user'),
      cell: (log) => (
        <div className="flex items-center gap-sm">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">{log.initials}</div>
          <span className="font-semibold">{log.user}</span>
        </div>
      ),
    },
    { header: t('table.activity'), cell: (log) => log.action },
    { header: t('audit.documentRef'), cell: (log) => log.documentRef },
    { header: t('table.latency'), cell: (log) => log.latency },
    { header: t('table.status'), cell: (log) => <StatusBadge status={log.status} /> },
    {
      header: t('table.details'),
      cell: (log) => (
        <Button
          variant="ghost"
          size="icon"
          aria-label={`Open ${log.id}`}
          onClick={(event) => {
            event.stopPropagation();
            setSelectedLog(log);
            setDetailsOpen(true);
          }}
        >
          <ChevronRight className="h-4 w-4" />
        </Button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('audit.title')}
        subtitle={t('audit.subtitle')}
        actions={
          <>
            <Button variant="secondary" leftIcon={<Filter className="h-4 w-4" />}>{t('actions.filter')}</Button>
            <Button leftIcon={<Download className="h-4 w-4" />}>{t('actions.exportCsv')}</Button>
          </>
        }
      />

      <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
        <StatCard label={t('audit.aiRequestsToday')} value="1,284" change={t('audit.aiRequestsTodayChange')} trend="up" />
        <StatCard label={t('audit.avgProcessingTime')} value="1.4s" change={t('audit.avgProcessingTimeChange')} trend="down" icon={<Timer className="h-5 w-5" />} accent="green" />
        <StatCard label={t('audit.tokenUsage')} value="842k" change={t('audit.tokenUsageTier')} trend="neutral" accent="gold" />
        <StatCard label={t('audit.storageUsage')} value="64.2%" change={t('audit.stable')} trend="neutral" />
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[1fr_360px]">
        <Card title={t('audit.aiRequestsOverTime')} actions={<Badge tone="blue">{t('audit.last7Days')}</Badge>}>
          <div className="flex h-56 items-end gap-md border-b border-l border-outline-variant p-md dark:border-slate-700">
            {[32, 48, 54, 66, 59, 78, 92].map((value, index) => (
              <div key={index} className="flex flex-1 flex-col items-center gap-sm">
                <div className="w-full rounded-t-lg bg-primary dark:bg-inverse-primary" style={{ height: `${value * 1.7}px` }} />
                <span className="text-xs text-on-surface-variant dark:text-slate-400">D{index + 1}</span>
              </div>
            ))}
          </div>
        </Card>
        <Card tone="ai">
          <p className="label-uppercase">{t('audit.errorRateTrend')}</p>
          <p className="mt-xs text-4xl font-bold text-primary dark:text-inverse-primary">0.8%</p>
          <p className="mt-md text-sm leading-6 text-on-surface-variant dark:text-slate-300">{t('audit.systemStability')}</p>
          <ProgressBar className="mt-md" value={8} />
        </Card>
      </section>

      <Card className="mt-xl" title={t('audit.systemActivityLogs')}>
        <DataTable
          columns={columns}
          data={auditLogs}
          getRowKey={(log) => log.id}
          onRowClick={(log) => {
            setSelectedLog(log);
            setDetailsOpen(true);
          }}
        />
      </Card>

      <Modal open={detailsOpen} onClose={() => setDetailsOpen(false)} title={t('audit.logDetails')} size="lg" footer={<Button variant="secondary" onClick={() => setDetailsOpen(false)} leftIcon={<X className="h-4 w-4" />}>{t('actions.cancel')}</Button>}>
        {selectedLog && (
          <div className="grid gap-lg lg:grid-cols-[0.8fr_1.2fr]">
            <Card title={t('audit.generalInformation')}>
              <dl className="space-y-md text-sm">
                <div><dt className="label-uppercase">{t('audit.requestId')}</dt><dd className="mt-xs font-semibold">{selectedLog.id}</dd></div>
                <div><dt className="label-uppercase">{t('table.user')}</dt><dd className="mt-xs">{selectedLog.user}</dd></div>
                <div><dt className="label-uppercase">{t('audit.ipAddress')}</dt><dd className="mt-xs">{selectedLog.ipAddress}</dd></div>
                <div><dt className="label-uppercase">{t('audit.model')}</dt><dd className="mt-xs">{selectedLog.model}</dd></div>
              </dl>
            </Card>
            <Card tone="ai">
              <h3 className="text-title-lg font-semibold">{t('audit.lexiguardInsight')}</h3>
              <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
                {t('audit.insightDetailStart')} {selectedLog.latency}. {t('audit.insightDetailEnd')}
              </p>
              <pre className="mt-md overflow-x-auto rounded-lg bg-slate-950 p-md text-xs text-slate-100">
{`{
  "action": "${selectedLog.action}",
  "document_id": "${selectedLog.documentRef}",
  "tokens": ${selectedLog.tokens},
  "model": "${selectedLog.model}"
}`}
              </pre>
            </Card>
          </div>
        )}
      </Modal>
    </div>
  );
}
