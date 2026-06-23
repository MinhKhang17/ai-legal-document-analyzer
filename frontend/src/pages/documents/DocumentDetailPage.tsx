import { ChevronLeft, ChevronRight, FileText, Quote, Share2 } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { RiskBadge } from '../../components/common/RiskBadge';
import { StatusBadge } from '../../components/common/StatusBadge';
import { DocumentPreview } from '../../components/editor/DocumentPreview';
import { documents, riskFindings } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function DocumentDetailPage() {
  const { id = 'doc-msa-global-tech' } = useParams();
  const { t } = useI18n();
  const document = documents.find((item) => item.id === id) ?? documents[0];

  return (
    <div>
      <PageHeader
        title={document.name}
        subtitle={document.summary}
        eyebrow={`${document.type} · ${document.jurisdiction}`}
        actions={
          <>
            <Button variant="secondary" leftIcon={<Share2 className="h-4 w-4" />}>Share</Button>
            <Link to="/editor/risk-review"><Button>{t('nav.riskReview')}</Button></Link>
          </>
        }
      />

      <div className="grid gap-gutter xl:grid-cols-[300px_1fr_340px]">
        <aside className="space-y-gutter">
          <Card title={t('documentDetail.metadata')}>
            <dl className="space-y-md text-sm">
              <div><dt className="label-uppercase">File Name</dt><dd className="mt-xs font-semibold">{document.name}</dd></div>
              <div><dt className="label-uppercase">Type</dt><dd className="mt-xs">{document.type}</dd></div>
              <div><dt className="label-uppercase">Status</dt><dd className="mt-xs"><StatusBadge status={document.status} /></dd></div>
              <div><dt className="label-uppercase">Risk</dt><dd className="mt-xs"><RiskBadge level={document.riskLevel} /></dd></div>
              <div><dt className="label-uppercase">Project</dt><dd className="mt-xs">{document.projectName}</dd></div>
            </dl>
          </Card>

          <Card title={t('documentDetail.outline')}>
            <nav className="space-y-xs text-sm">
              {document.clauses.map((clause) => (
                <a key={clause.ref} href={`#clause-${clause.ref}`} className="flex items-center justify-between rounded-lg px-sm py-sm hover:bg-surface-container-low dark:hover:bg-slate-800">
                  <span>{clause.ref}. {clause.title}</span>
                  {clause.riskLevel !== 'low' && <RiskBadge level={clause.riskLevel} />}
                </a>
              ))}
            </nav>
          </Card>
        </aside>

        <section>
          <div className="mb-md flex items-center justify-between rounded-xl border border-legal-border bg-white p-sm dark:border-slate-700 dark:bg-slate-900">
            <div className="flex items-center gap-sm text-sm font-semibold text-on-surface-variant dark:text-slate-400">
              <FileText className="h-4 w-4" /> Page 4 of {document.pages}
            </div>
            <div className="flex items-center gap-xs">
              <Button variant="ghost" size="icon" aria-label="Previous page"><ChevronLeft className="h-4 w-4" /></Button>
              <Button variant="ghost" size="icon" aria-label="Next page"><ChevronRight className="h-4 w-4" /></Button>
            </div>
          </div>
          <DocumentPreview document={document} />
        </section>

        <aside className="space-y-gutter">
          <Card tone="ai">
            <div className="flex items-center gap-sm">
              <Quote className="h-5 w-5 text-secondary dark:text-accent-gold" />
              <h2 className="text-title-lg font-semibold">AI Summary</h2>
            </div>
            <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">{document.summary}</p>
          </Card>
          {riskFindings.slice(0, 2).map((finding) => (
            <Card key={finding.id} title={finding.title} actions={<RiskBadge level={finding.level} />}>
              <p className="text-sm leading-6 text-on-surface-variant dark:text-slate-400">{finding.summary}</p>
              <Link to="/editor/risk-review"><Button className="mt-md" variant="secondary">Review finding</Button></Link>
            </Card>
          ))}
          <Card title="Related reports">
            <div className="space-y-sm">
              <Link to="/reports/lg-2024-0812-x" className="block rounded-lg border border-legal-border p-sm hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800">
                <Badge tone="blue">Finalized</Badge>
                <p className="mt-sm text-sm font-semibold">Legal Risk Review Report</p>
              </Link>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
