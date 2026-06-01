import { Download, Eye, Filter, Sparkles } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { comparisonRows } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

type ComparisonRow = (typeof comparisonRows)[number];

export function ComparisonHistoryPage() {
  const { t } = useI18n();
  const columns: DataTableColumn<ComparisonRow>[] = [
    { header: 'Comparison Name', cell: (row) => <span className="font-semibold text-primary dark:text-inverse-primary">{row.name}</span> },
    { header: 'Original Doc', cell: (row) => row.original },
    { header: 'Revised Doc', cell: (row) => row.revised },
    { header: t('table.project'), cell: (row) => row.project },
    { header: 'High-Risk', cell: (row) => <Badge tone={row.highRisks > 2 ? 'red' : row.highRisks > 0 ? 'amber' : 'green'}>{row.highRisks} risks</Badge> },
    { header: t('table.date'), cell: (row) => row.date },
    {
      header: t('table.actions'),
      cell: () => (
        <div className="flex gap-xs">
          <Button variant="ghost" size="icon" aria-label="View"><Eye className="h-4 w-4" /></Button>
          <Button variant="ghost" size="icon" aria-label="Download"><Download className="h-4 w-4" /></Button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('comparisonHistory.title')}
        subtitle={t('comparisonHistory.subtitle')}
        actions={<Button variant="secondary" leftIcon={<Filter className="h-4 w-4" />}>Advanced Filters</Button>}
      />

      <section className="grid gap-gutter md:grid-cols-3">
        <Card>
          <p className="label-uppercase">Total Comparisons</p>
          <p className="mt-xs text-3xl font-bold">1,284</p>
          <p className="mt-sm text-sm text-emerald-700 dark:text-emerald-300">+12% this month</p>
        </Card>
        <Card>
          <p className="label-uppercase">High-Risk Found</p>
          <p className="mt-xs text-3xl font-bold text-error">42</p>
          <p className="mt-sm text-sm text-on-surface-variant dark:text-slate-400">Requires attention</p>
        </Card>
        <Card tone="ai">
          <p className="label-uppercase">Latest Insight</p>
          <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
            Section 14.2 in the Global Services Agreement contains non-standard indemnity clauses compared to corporate baseline.
          </p>
        </Card>
      </section>

      <Card className="mt-xl" title="Comparison Records">
        <DataTable columns={columns} data={comparisonRows} getRowKey={(row) => row.id} />
      </Card>

      <Card tone="ai" className="mt-xl">
        <div className="flex flex-col gap-md md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-title-lg font-semibold">AI-Powered Compliance Audit</h2>
            <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-300">
              Generate a comprehensive summary of all inconsistencies mapped to your firm's standard policy guidelines.
            </p>
          </div>
          <Button variant="gold" leftIcon={<Sparkles className="h-4 w-4" />}>Run Bulk Audit Review</Button>
        </div>
      </Card>
    </div>
  );
}
