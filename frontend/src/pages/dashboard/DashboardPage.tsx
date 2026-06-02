import { BarChart3, FileText, FolderOpen, MessageSquareText, SearchCheck, ShieldAlert, UploadCloud } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { RiskBadge } from '../../components/common/RiskBadge';
import { StatCard } from '../../components/common/StatCard';
import { StatusBadge } from '../../components/common/StatusBadge';
import { dashboardTrend, documents, processingJobs, projects, riskDistribution } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function DashboardPage() {
  const { t } = useI18n();
  const recentDocs = documents.slice(0, 4);
  const activeProjects = projects.slice(0, 3);

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

      <section className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
        <StatCard label={t('dashboard.totalDocuments')} value="1,284" change="+12%" trend="up" icon={<FileText className="h-5 w-5" />} />
        <StatCard label={t('dashboard.reviewedContracts')} value="856" change="+5%" trend="up" icon={<SearchCheck className="h-5 w-5" />} accent="green" />
        <StatCard label={t('dashboard.highRiskClauses')} value="42" change="+8%" trend="up" icon={<ShieldAlert className="h-5 w-5" />} accent="red" />
        <StatCard label={t('dashboard.activeProjects')} value="15" change="Stable" trend="neutral" icon={<FolderOpen className="h-5 w-5" />} accent="gold" />
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[1.35fr_0.95fr]">
        <Card title={t('dashboard.riskTrend')} actions={<Badge tone="blue">Last 7 days</Badge>}>
          <div className="mt-md flex h-72 items-end gap-md border-b border-l border-outline-variant px-md pb-md dark:border-slate-700">
            {dashboardTrend.map((value, index) => (
              <div key={index} className="flex flex-1 flex-col items-center gap-sm">
                <div className="w-full rounded-t-lg bg-primary dark:bg-inverse-primary" style={{ height: `${value * 2.2}px` }} />
                <span className="text-xs text-on-surface-variant dark:text-slate-400">D{index + 1}</span>
              </div>
            ))}
          </div>
        </Card>

        <div className="space-y-gutter">
          <Card title={t('dashboard.activeProjects')}>
            <div className="space-y-md">
              {activeProjects.map((project) => (
                <Link key={project.id} to={`/projects/${project.id}`} className="block rounded-lg border border-legal-border p-md transition hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800">
                  <div className="flex items-start justify-between gap-md">
                    <div>
                      <p className="font-semibold">{project.name}</p>
                      <p className="text-sm text-on-surface-variant dark:text-slate-400">{project.client}</p>
                    </div>
                    <RiskBadge level={project.riskLevel} />
                  </div>
                  <ProgressBar className="mt-md" value={project.progress} />
                </Link>
              ))}
            </div>
          </Card>
          <Card tone="ai">
            <h2 className="text-title-lg font-semibold">{t('dashboard.aiInsight')}</h2>
            <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
              Clause-risk density increased in vendor renewals. Prioritize indemnity caps, unilateral scope changes, and data residency terms before generating executive reports.
            </p>
            <Link to="/editor/risk-review">
              <Button className="mt-md" variant="gold" leftIcon={<ShieldAlert className="h-4 w-4" />}>
                {t('nav.riskReview')}
              </Button>
            </Link>
          </Card>
        </div>
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[0.85fr_1.15fr]">
        <Card title={t('risk.distribution')}>
          <div className="space-y-md">
            {([
              ['critical', riskDistribution.critical],
              ['high', riskDistribution.high],
              ['medium', riskDistribution.medium],
              ['low', riskDistribution.low],
            ] as const).map(([level, value]) => (
              <div key={level}>
                <div className="mb-xs flex items-center justify-between text-sm">
                  <RiskBadge level={level} />
                  <span className="font-semibold">{value}</span>
                </div>
                <ProgressBar value={value} />
              </div>
            ))}
          </div>
        </Card>

        <Card title={t('dashboard.processingQueue')} actions={<Badge tone="gold">{processingJobs.filter((job) => job.status === 'processing').length} active</Badge>}>
          <div className="space-y-md">
            {processingJobs.slice(0, 3).map((job) => (
              <div key={job.id} className="rounded-lg border border-legal-border p-md dark:border-slate-700">
                <div className="flex items-center justify-between gap-md">
                  <div>
                    <p className="font-semibold">{job.resource}</p>
                    <p className="text-sm text-on-surface-variant dark:text-slate-400">{job.description}</p>
                  </div>
                  <StatusBadge status={job.status} />
                </div>
                {job.status === 'processing' && <ProgressBar className="mt-md" value={job.progress} />}
              </div>
            ))}
          </div>
        </Card>
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[1.25fr_0.75fr]">
        <section className="paper-card p-md">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">
              {t('dashboard.recentReviews')}
            </h2>
            <Link className="text-sm font-semibold text-primary dark:text-inverse-primary" to="/documents">
              {t('actions.viewAll')}
            </Link>
          </div>

          <div className="hidden overflow-hidden rounded-xl border border-legal-border md:block dark:border-slate-700">
            <table className="w-full table-fixed divide-y divide-legal-border text-sm dark:divide-slate-700">
              <colgroup>
                <col className="w-[36%]" />
                <col className="w-[25%]" />
                <col className="w-[13%]" />
                <col className="w-[14%]" />
                <col className="w-[12%]" />
              </colgroup>
              <thead className="bg-surface-container-low dark:bg-slate-800">
                <tr>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('table.document')}
                  </th>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('table.project')}
                  </th>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('table.risk')}
                  </th>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('table.status')}
                  </th>
                  <th className="px-md py-sm text-left text-label-md font-bold uppercase tracking-wider leading-4 text-on-surface-variant dark:text-slate-400">
                    {t('table.updated')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-legal-border bg-white dark:divide-slate-800 dark:bg-slate-900">
                {recentDocs.map((document) => (
                  <tr key={document.id} className="align-top">
                    <td className="px-md py-sm">
                      <p className="line-clamp-2 break-words font-semibold text-on-surface dark:text-slate-100">
                        {document.name}
                      </p>
                      <p className="mt-0.5 text-xs text-on-surface-variant dark:text-slate-400">
                        {document.type}
                      </p>
                    </td>
                    <td className="px-md py-sm text-on-surface dark:text-slate-100">
                      <p className="line-clamp-2 break-words">{document.projectName}</p>
                    </td>
                    <td className="px-md py-sm">
                      <RiskBadge
                        level={document.riskLevel}
                        className="inline-flex whitespace-nowrap px-2.5 py-1 text-xs normal-case tracking-normal"
                      />
                    </td>
                    <td className="px-md py-sm">
                      <StatusBadge
                        status={document.status}
                        className="inline-flex whitespace-nowrap px-2.5 py-1 text-xs normal-case tracking-normal"
                      />
                    </td>
                    <td className="px-md py-sm text-xs text-on-surface-variant dark:text-slate-400">
                      <span className="whitespace-nowrap">{document.updatedAt}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="space-y-3 md:hidden">
            {recentDocs.map((document) => (
              <article
                key={document.id}
                className="rounded-xl border border-legal-border bg-white p-sm dark:border-slate-700 dark:bg-slate-900"
              >
                <h3 className="line-clamp-2 break-words text-sm font-semibold text-on-surface dark:text-slate-100">
                  {document.name}
                </h3>
                <p className="mt-1 text-xs text-on-surface-variant dark:text-slate-400">
                  {document.type}
                </p>

                <div className="mt-3 grid grid-cols-2 gap-2">
                  <div className="col-span-2">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.05em] text-on-surface-variant dark:text-slate-400">
                      {t('table.project')}
                    </p>
                    <p className="mt-0.5 break-words text-sm text-on-surface dark:text-slate-100">
                      {document.projectName}
                    </p>
                  </div>

                  <div>
                    <p className="text-[11px] font-semibold uppercase tracking-[0.05em] text-on-surface-variant dark:text-slate-400">
                      {t('table.risk')}
                    </p>
                    <div className="mt-1">
                      <RiskBadge
                        level={document.riskLevel}
                        className="inline-flex whitespace-nowrap px-2.5 py-1 text-xs normal-case tracking-normal"
                      />
                    </div>
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
                      {t('table.updated')}
                    </p>
                    <p className="mt-0.5 whitespace-nowrap text-sm text-on-surface dark:text-slate-100">
                      {document.updatedAt}
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
                  <p className="text-sm text-on-surface-variant dark:text-slate-400">Secure document intake and AI pipeline.</p>
                </div>
              </div>
            </Link>
            <Link to="/editor/version-comparison" className="rounded-xl border border-legal-border bg-white p-md transition hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800">
              <div className="flex items-center gap-md">
                <BarChart3 className="h-5 w-5 text-secondary dark:text-accent-gold" />
                <div>
                  <p className="font-semibold">{t('nav.versionComparison')}</p>
                  <p className="text-sm text-on-surface-variant dark:text-slate-400">Compare draft deltas and flag exposure.</p>
                </div>
              </div>
            </Link>
          </div>
        </Card>
      </section>
    </div>
  );
}
