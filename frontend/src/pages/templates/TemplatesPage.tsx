import { FileText, Plus, ShieldCheck } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { useI18n } from '../../hooks/useI18n';

const templates = [
  {
    id: 'executive-summary',
    name: 'Executive Summary Report',
    category: 'Reports',
    description: 'High-level risk heatmap and narrative for C-level stakeholders.',
    tags: ['Risk heatmap', 'Board-ready'],
  },
  {
    id: 'full-risk-review',
    name: 'Full Risk Review',
    category: 'Reports',
    description: 'Clause-by-clause legal redlines with AI rationale and source citations.',
    tags: ['Redlines', 'Citations'],
  },
  {
    id: 'msa-baseline',
    name: 'Corporate MSA Baseline',
    category: 'Clause Playbook',
    description: 'Fallback positions for indemnity, limitation of liability, confidentiality, and termination.',
    tags: ['Negotiation', 'MSA'],
  },
  {
    id: 'data-privacy-policy',
    name: 'Data Privacy Risk Policy',
    category: 'Policy',
    description: 'Risk rules for data residency, transfer safeguards, and subprocessors.',
    tags: ['GDPR', 'Decree 13'],
  },
];

export function TemplatesPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader title={t('templates.title')} subtitle={t('templates.subtitle')} actions={<Button leftIcon={<Plus className="h-4 w-4" />}>New template</Button>} />
      <div className="grid gap-gutter md:grid-cols-2 xl:grid-cols-4">
        {templates.map((template) => (
          <Card key={template.id} className="h-full">
            <div className="mb-md flex h-11 w-11 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
              {template.category === 'Policy' ? <ShieldCheck className="h-5 w-5" /> : <FileText className="h-5 w-5" />}
            </div>
            <Badge tone="blue">{template.category}</Badge>
            <h2 className="mt-md text-title-lg font-semibold text-primary dark:text-inverse-primary">{template.name}</h2>
            <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-400">{template.description}</p>
            <div className="mt-md flex flex-wrap gap-xs">
              {template.tags.map((tag) => <Badge key={tag} tone="slate">{tag}</Badge>)}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
