import { AlertTriangle, BarChart3, FileText, Link as LinkIcon, RefreshCw, UploadCloud } from 'lucide-react';
import { useParams } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatusBadge } from '../../components/common/StatusBadge';
import { knowledgeArticles } from '../../api/mockData';

export function KnowledgeBaseDetailPage() {
  const { id = 'decree-13-2023' } = useParams();
  const article = knowledgeArticles.find((item) => item.id === id) ?? knowledgeArticles[0];

  return (
    <div>
      <PageHeader
        title={article.title}
        subtitle={article.summary}
        eyebrow={`${article.category} · ${article.jurisdiction}`}
        actions={
          <>
            <Button variant="secondary" leftIcon={<UploadCloud className="h-4 w-4" />}>Replace</Button>
            <Button leftIcon={<RefreshCw className="h-4 w-4" />}>Re-index</Button>
            <Button variant="danger" leftIcon={<AlertTriangle className="h-4 w-4" />}>Mark Outdated</Button>
          </>
        }
      />

      <div className="grid gap-gutter xl:grid-cols-[360px_1fr]">
        <aside className="space-y-gutter">
          <Card title="Document Metadata" actions={<FileText className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
            <dl className="space-y-md text-sm">
              <div><dt className="label-uppercase">Category</dt><dd className="mt-xs">{article.category}</dd></div>
              <div><dt className="label-uppercase">Jurisdiction</dt><dd className="mt-xs">{article.jurisdiction}</dd></div>
              <div><dt className="label-uppercase">Status</dt><dd className="mt-xs"><StatusBadge status={article.status} /></dd></div>
              <div><dt className="label-uppercase">Indexed chunks</dt><dd className="mt-xs font-bold">{article.chunks.toLocaleString()}</dd></div>
            </dl>
          </Card>

          <Card title="Usage Analytics" actions={<BarChart3 className="h-5 w-5 text-secondary dark:text-accent-gold" />}>
            <div className="space-y-md">
              <div>
                <div className="mb-xs flex items-center justify-between text-sm"><span>Contract references</span><span>{article.impactedContracts}</span></div>
                <ProgressBar value={68} />
              </div>
              <div>
                <div className="mb-xs flex items-center justify-between text-sm"><span>Citation confidence</span><span>94%</span></div>
                <ProgressBar value={94} />
              </div>
            </div>
          </Card>

          <Card title="Impacted Contracts" actions={<LinkIcon className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
            <div className="space-y-sm">
              {['MSA_Global_Tech_2024.pdf', 'Service_Agreement_v4.pdf', 'Data Processing Addendum'].map((item) => (
                <div key={item} className="rounded-lg border border-legal-border p-sm text-sm dark:border-slate-700">{item}</div>
              ))}
              <Button className="w-full" variant="secondary">View all {article.impactedContracts} contracts</Button>
            </div>
          </Card>
        </aside>

        <main className="space-y-gutter">
          <Card title="Original Text Preview">
            <div className="document-paper max-w-none legal-text text-sm leading-7">
              <h2 className="mb-md text-center text-xl font-bold uppercase">Personal Data Protection Obligations</h2>
              <p>
                Controllers and processors must adopt appropriate technical and organizational measures to protect personal data,
                maintain records of processing activities, and ensure that cross-border transfers are conducted with adequate
                safeguards and notifications to competent authorities when required.
              </p>
              <p className="mt-md border-l-4 border-l-secondary bg-risk-medium-bg p-md text-risk-medium-text dark:bg-amber-950/30 dark:text-amber-200">
                AI extraction identified this paragraph as highly relevant to data transfer warranties in active DPAs.
              </p>
            </div>
          </Card>

          <Card title="AI Indexed Chunks" actions={<Badge tone="blue">RAG-ready</Badge>}>
            <div className="grid gap-md md:grid-cols-2">
              {[1, 2, 3, 4].map((index) => (
                <div key={index} className="rounded-xl border border-legal-border p-md dark:border-slate-700">
                  <div className="mb-sm flex items-center justify-between"><Badge tone="blue">Chunk {index}</Badge><span className="text-xs text-on-surface-variant dark:text-slate-400">0.{90 + index} confidence</span></div>
                  <p className="text-sm leading-6 text-on-surface-variant dark:text-slate-400">
                    Extracted statutory obligation related to personal data processing, transfer notice, consent basis, and accountability evidence.
                  </p>
                </div>
              ))}
            </div>
          </Card>

          <Card tone="ai">
            <h2 className="text-title-lg font-semibold">AI impact note</h2>
            <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
              This source should be prioritized in risk reviews for data processing addenda and vendor agreements involving Vietnamese personal data subjects.
            </p>
          </Card>
        </main>
      </div>
    </div>
  );
}
