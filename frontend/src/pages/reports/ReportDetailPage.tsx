import { Download, FileText, Link as LinkIcon, Maximize2, PenLine, XCircle } from 'lucide-react';
import { useParams } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { ReportPreview } from '../../components/editor/ReportPreview';
import { reports } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function ReportDetailPage() {
  const { id = 'lg-2024-0812-x' } = useParams();
  const { t } = useI18n();
  const report = reports.find((item) => item.id === id) ?? reports[0];

  return (
    <div>
      <PageHeader
        title={t('reportDetail.title')}
        subtitle={`Report ID: ${report.id.toUpperCase()} · ${report.createdAt}`}
        actions={<Button variant="secondary" leftIcon={<Maximize2 className="h-4 w-4" />}>Fullscreen</Button>}
      />

      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_320px]">
        <ReportPreview report={report} />
        <aside className="space-y-gutter">
          <Card title="Export & Share">
            <div className="grid gap-sm">
              <Button variant="secondary" leftIcon={<FileText className="h-4 w-4" />}>Export as PDF <Download className="h-4 w-4" /></Button>
              <Button variant="secondary" leftIcon={<FileText className="h-4 w-4" />}>Export as Word <Download className="h-4 w-4" /></Button>
              <Button variant="secondary" leftIcon={<LinkIcon className="h-4 w-4" />}>Share Secure Link</Button>
            </div>
          </Card>
          <Card title="Document Status">
            <div className="rounded-lg bg-emerald-100 p-md text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
              <p className="font-semibold">Ready for Signature</p>
              <p className="mt-xs text-sm">Validated by AI & senior reviewer.</p>
            </div>
            <div className="mt-md grid grid-cols-2 gap-sm">
              <Button leftIcon={<PenLine className="h-4 w-4" />}>Sign Now</Button>
              <Button variant="danger" leftIcon={<XCircle className="h-4 w-4" />}>Reject</Button>
            </div>
          </Card>
          <Card tone="ai">
            <p className="italic text-on-surface-variant dark:text-slate-300">“Justice is truth in action.” — Joseph Joubert</p>
          </Card>
          <Card title="Analysis Metadata">
            <dl className="space-y-md text-sm">
              <div><dt className="label-uppercase">Model Version</dt><dd className="mt-xs font-semibold">LG-Titan 4.0</dd></div>
              <div><dt className="label-uppercase">Processing Time</dt><dd className="mt-xs">12.4s</dd></div>
              <div><dt className="label-uppercase">Jurisdiction</dt><dd className="mt-xs">United Kingdom</dd></div>
            </dl>
          </Card>
        </aside>
      </div>
    </div>
  );
}
