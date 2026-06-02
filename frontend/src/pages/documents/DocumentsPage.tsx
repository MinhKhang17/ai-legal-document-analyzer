import { Download, Eye, Filter, MessageSquareText, MoreHorizontal, UploadCloud } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileUploadZone } from '../../components/upload/FileUploadZone';
import { ProcessingTimeline } from '../../components/upload/ProcessingTimeline';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { RiskBadge } from '../../components/common/RiskBadge';
import { SearchInput } from '../../components/common/SearchInput';
import { StatusBadge } from '../../components/common/StatusBadge';
import { documents } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';
import type { LegalDocument } from '../../types/document';

export function DocumentsPage() {
  const { t } = useI18n();
  const [query, setQuery] = useState('');
  const [fakeUploadActive, setFakeUploadActive] = useState(false);
  const filteredDocuments = useMemo(
    () => documents.filter((document) => `${document.name} ${document.type} ${document.projectName}`.toLowerCase().includes(query.toLowerCase())),
    [query],
  );

  const columns: DataTableColumn<LegalDocument>[] = [
    {
      header: t('table.document'),
      cell: (document) => (
        <div>
          <Link to={`/documents/${document.id}`} className="font-semibold text-primary hover:underline dark:text-inverse-primary">
            {document.name}
          </Link>
          <p className="text-xs text-on-surface-variant dark:text-slate-400">{document.size} · {document.pages} pages</p>
        </div>
      ),
    },
    { header: t('documents.type'), cell: (document) => document.type },
    { header: t('table.project'), cell: (document) => document.projectName },
    { header: t('table.risk'), cell: (document) => <RiskBadge level={document.riskLevel} /> },
    { header: t('table.status'), cell: (document) => <StatusBadge status={document.status} /> },
    {
      header: t('table.actions'),
      cell: (document) => (
        <div className="flex items-center gap-xs">
          <Link to={`/documents/${document.id}`} aria-label={`${t('actions.open')} ${document.name}`}>
            <Button variant="ghost" size="icon"><Eye className="h-4 w-4" /></Button>
          </Link>
          <Button variant="ghost" size="icon" aria-label={t('actions.download')}><Download className="h-4 w-4" /></Button>
          <Button variant="ghost" size="icon" aria-label={t('table.actions')}><MoreHorizontal className="h-4 w-4" /></Button>
        </div>
      ),
    },
  ];

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

      <section className="grid gap-gutter xl:grid-cols-[0.85fr_1.15fr]">
        <FileUploadZone onFakeUpload={() => setFakeUploadActive(true)} />
        <Card title={t('documents.aiProcessingFlow')} actions={<Badge tone={fakeUploadActive ? 'gold' : 'green'}>{fakeUploadActive ? t('status.processing') : t('status.ready')}</Badge>}>
          <ProcessingTimeline orientation="horizontal" />
        </Card>
      </section>

      <Card className="mt-xl" title={t('documents.recent')} actions={<Button variant="secondary" leftIcon={<Filter className="h-4 w-4" />}>{t('actions.filter')}</Button>}>
        <div className="mb-lg flex flex-col gap-md lg:flex-row lg:items-center lg:justify-between">
          <SearchInput value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('documents.searchPlaceholder')} containerClassName="lg:w-96" />
          <div className="flex flex-wrap gap-sm">
            <Badge tone="blue">{t('documents.reviewed')}</Badge>
            <Badge tone="gold">{t('status.processing')}</Badge>
            <Badge tone="red">{t('documents.highRisk')}</Badge>
          </div>
        </div>
        <DataTable columns={columns} data={filteredDocuments} getRowKey={(document) => document.id} />
      </Card>

      <div className="fixed bottom-lg right-lg hidden rounded-full shadow-raised xl:block">
        <Link to="/chat">
          <Button size="lg" leftIcon={<MessageSquareText className="h-5 w-5" />}>{t('actions.ask')}</Button>
        </Link>
      </div>
    </div>
  );
}
