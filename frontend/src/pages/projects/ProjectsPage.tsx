import { Grid2X2, List, Plus, Search, SlidersHorizontal } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { RiskBadge } from '../../components/common/RiskBadge';
import { SearchInput } from '../../components/common/SearchInput';
import { StatusBadge } from '../../components/common/StatusBadge';
import { projects } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { Project } from '../../types/project';

export function ProjectsPage() {
  const { t } = useI18n();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<'all' | 'active' | 'processing' | 'finalized'>('all');
  const [view, setView] = useState<'grid' | 'list'>('grid');

  const filteredProjects = useMemo(
    () =>
      projects.filter((project) => {
        const matchesQuery = `${project.name} ${project.client} ${project.description}`.toLowerCase().includes(query.toLowerCase());
        const matchesStatus = status === 'all' || project.status === status;
        return matchesQuery && matchesStatus;
      }),
    [query, status],
  );

  const columns: DataTableColumn<Project>[] = [
    {
      header: t('table.project'),
      cell: (project) => (
        <Link to={`/projects/${project.id}`} className="font-semibold text-primary hover:underline dark:text-inverse-primary">
          {project.name}
        </Link>
      ),
    },
    { header: t('projects.client'), cell: (project) => project.client },
    { header: t('table.owner'), cell: (project) => project.owner },
    { header: t('table.risk'), cell: (project) => <RiskBadge level={project.riskLevel} /> },
    { header: t('table.status'), cell: (project) => <StatusBadge status={project.status} /> },
    { header: t('table.updated'), cell: (project) => project.updatedAt },
  ];

  return (
    <div>
      <PageHeader
        title={t('projects.title')}
        subtitle={t('projects.subtitle')}
        actions={
          <Button leftIcon={<Plus className="h-4 w-4" />}>
            {t('actions.createProject')}
          </Button>
        }
      />

      <Card className="mb-xl">
        <div className="flex flex-col gap-md lg:flex-row lg:items-center lg:justify-between">
          <SearchInput value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('projects.searchPlaceholder')} containerClassName="lg:w-96" />
          <div className="flex flex-wrap items-center gap-sm">
            {(['all', 'active', 'processing', 'finalized'] as const).map((item) => (
              <button
                key={item}
                className={`rounded-lg border px-md py-sm text-sm font-semibold transition ${status === item ? 'border-primary bg-primary text-white' : 'border-legal-border bg-white text-on-surface-variant hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'}`}
                type="button"
                onClick={() => setStatus(item)}
              >
                {item === 'all' ? t('projects.statusAll') : t(`status.${item}`)}
              </button>
            ))}
            <Button variant="secondary" leftIcon={<SlidersHorizontal className="h-4 w-4" />}>{t('actions.filter')}</Button>
            <div className="flex rounded-lg border border-legal-border bg-white p-xs dark:border-slate-700 dark:bg-slate-900">
              <button className={`rounded-md p-sm ${view === 'grid' ? 'bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary' : 'text-on-surface-variant'}`} type="button" aria-label={t('projects.gridView')} onClick={() => setView('grid')}>
                <Grid2X2 className="h-4 w-4" />
              </button>
              <button className={`rounded-md p-sm ${view === 'list' ? 'bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary' : 'text-on-surface-variant'}`} type="button" aria-label={t('projects.listView')} onClick={() => setView('list')}>
                <List className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </Card>

      {view === 'list' ? (
        <DataTable columns={columns} data={filteredProjects} getRowKey={(project) => project.id} />
      ) : (
        <div className="grid gap-gutter md:grid-cols-2 xl:grid-cols-3">
          {filteredProjects.map((project) => (
            <Link key={project.id} to={`/projects/${project.id}`} className="group block">
              <Card className="h-full transition group-hover:-translate-y-1 group-hover:shadow-raised">
                <div className="flex items-start justify-between gap-md">
                  <div>
                    <h2 className="text-title-lg font-semibold text-primary dark:text-inverse-primary">{project.name}</h2>
                    <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{project.client}</p>
                  </div>
                  <RiskBadge level={project.riskLevel} />
                </div>
                <p className="mt-md line-clamp-3 text-sm leading-6 text-on-surface-variant dark:text-slate-400">{project.description}</p>
                <div className="mt-md flex flex-wrap gap-xs">
                  {project.tags.map((tag) => (
                    <Badge key={tag} tone="blue">{tag}</Badge>
                  ))}
                </div>
                <div className="mt-lg grid grid-cols-3 gap-sm text-center">
                  <div className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">
                    <p className="font-bold">{project.documentCount}</p>
                    <p className="text-[11px] text-on-surface-variant dark:text-slate-400">{t('projects.docs')}</p>
                  </div>
                  <div className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">
                    <p className="font-bold text-error">{project.highRiskCount}</p>
                    <p className="text-[11px] text-on-surface-variant dark:text-slate-400">{t('risk.high')}</p>
                  </div>
                  <div className="rounded-lg bg-surface-container-low p-sm dark:bg-slate-800">
                    <p className="font-bold">{project.progress}%</p>
                    <p className="text-[11px] text-on-surface-variant dark:text-slate-400">{t('projects.done')}</p>
                  </div>
                </div>
                <ProgressBar className="mt-md" value={project.progress} />
                <div className="mt-md flex items-center justify-between text-sm text-on-surface-variant dark:text-slate-400">
                  <span>{project.owner}</span>
                  <StatusBadge status={project.status} />
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <Card tone="ai" className="mt-xl">
        <div className="flex flex-col gap-md md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-title-lg font-semibold">{t('projects.caseVelocity')}</h2>
            <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-300">
              {t('projects.caseVelocityInsight')}
            </p>
          </div>
          <Button variant="gold" leftIcon={<Search className="h-4 w-4" />}>{t('projects.runPortfolioAudit')}</Button>
        </div>
      </Card>
    </div>
  );
}
