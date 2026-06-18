import { Download, RefreshCw, Upload, UploadCloud } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { SearchInput } from '../../components/common/SearchInput';
import { StatusBadge } from '../../components/common/StatusBadge';
import { StatCard } from '../../components/common/StatCard';
import { knowledgeArticles } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { KnowledgeArticle } from '../../types/knowledgeBase';

export function KnowledgeBasePage() {
  const { t } = useI18n();
  const columns: DataTableColumn<KnowledgeArticle>[] = [
    {
      header: t('knowledge.source'),
      cell: (article) => (
        <Link to={`/knowledge-base/${article.id}`} className="font-semibold text-primary hover:underline dark:text-inverse-primary">
          {article.title}
        </Link>
      ),
    },
    { header: t('knowledge.category'), cell: (article) => article.category },
    { header: t('knowledge.jurisdiction'), cell: (article) => article.jurisdiction },
    { header: t('knowledge.chunks'), cell: (article) => article.chunks.toLocaleString() },
    { header: t('knowledge.impactedContracts'), cell: (article) => article.impactedContracts },
    { header: t('table.status'), cell: (article) => <StatusBadge status={article.status} /> },
    { header: t('table.updated'), cell: (article) => article.updatedAt },
  ];

  return (
    <div>
      <PageHeader
        title={t('knowledge.title')}
        subtitle={t('knowledge.subtitle')}
        actions={
          <>
            <Button variant="secondary" leftIcon={<RefreshCw className="h-4 w-4" />}>{t('knowledge.reindexAll')}</Button>
            <Button leftIcon={<Upload className="h-4 w-4" />}>{t('knowledge.upload')}</Button>
          </>
        }
      />

      <section className="grid gap-gutter md:grid-cols-3">
        <StatCard label={t('knowledge.indexedChunks')} value="1,284" change={t('knowledge.indexedChunksChange')} trend="up" />
        <StatCard label={t('knowledge.sourceDocuments')} value="1,240" change={t('knowledge.synced')} trend="neutral" accent="green" />
        <StatCard label={t('knowledge.outdatedSources')} value="44" change={t('knowledge.requiresReview')} trend="neutral" accent="red" />
      </section>

      <Card className="mt-xl" title={t('knowledge.masterIndex')} actions={<Button variant="secondary" leftIcon={<Download className="h-4 w-4" />}>{t('actions.download')}</Button>}>
        <div className="mb-lg flex flex-col gap-md lg:flex-row lg:items-center lg:justify-between">
          <SearchInput placeholder={t('knowledge.searchPlaceholder')} containerClassName="lg:w-96" />
          <div className="rounded-lg border border-outline-variant bg-surface-container-low p-xs text-xs font-semibold text-on-surface-variant dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400">
            {t('knowledge.bannerInfo')}
          </div>
        </div>
        <DataTable columns={columns} data={knowledgeArticles} getRowKey={(article) => article.id} />
      </Card>

      <Card tone="ai" className="mt-xl">
        <div className="flex flex-col gap-md md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-title-lg font-semibold">{t('knowledge.citationReliabilityAlert')}</h2>
            <p className="mt-xs text-sm leading-6 text-on-surface-variant dark:text-slate-300">
              {t('knowledge.citationReliabilityDescription')}
            </p>
          </div>
          <Button variant="gold" leftIcon={<UploadCloud className="h-4 w-4" />}>{t('knowledge.upload')}</Button>
        </div>
      </Card>
    </div>
  );
}
