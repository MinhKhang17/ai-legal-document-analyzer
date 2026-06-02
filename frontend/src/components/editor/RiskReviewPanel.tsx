import { BookOpen, Gavel, Link as LinkIcon, Sparkles } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Button } from '../common/Button';
import { Card } from '../common/Card';
import { RiskBadge } from '../common/RiskBadge';
import { Tabs } from '../common/Tabs';
import { ProgressBar } from '../common/ProgressBar';
import type { RiskFinding, RiskLevel } from '../../types/risk';
import { useI18n } from '../../hooks/useI18n';

interface RiskReviewPanelProps {
  findings: RiskFinding[];
}

const filterTabs: Array<{ id: RiskLevel | 'all'; labelKey: string }> = [
  { id: 'all', labelKey: 'actions.filter' },
  { id: 'critical', labelKey: 'risk.critical' },
  { id: 'high', labelKey: 'risk.high' },
  { id: 'medium', labelKey: 'risk.medium' },
  { id: 'low', labelKey: 'risk.low' },
];

export function RiskReviewPanel({ findings }: RiskReviewPanelProps) {
  const { t } = useI18n();
  const [filter, setFilter] = useState<RiskLevel | 'all'>('critical');
  const filteredFindings = useMemo(
    () => (filter === 'all' ? findings : findings.filter((finding) => finding.level === filter || (filter === 'critical' && finding.level === 'high'))),
    [filter, findings],
  );

  return (
    <aside className="space-y-md">
      <Card className="bg-surface-container-low dark:bg-slate-900">
        <div className="flex items-center justify-between gap-md">
          <div>
            <h2 className="font-sans text-headline-md text-primary dark:text-inverse-primary">{t('riskReview.title')}</h2>
            <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{t('riskReview.liveScanning')}</p>
          </div>
          <div className="relative h-20 w-20 shrink-0 rounded-full border-[8px] border-error border-l-error-container bg-white text-center dark:bg-slate-950">
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-xl font-bold">75</span>
              <span className="text-[10px] font-bold uppercase text-on-surface-variant dark:text-slate-400">{t('risk.score')}</span>
            </div>
          </div>
        </div>
        <p className="mt-md rounded-lg border border-outline-variant bg-white p-md text-sm italic text-on-surface-variant dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300">
          {t('riskReview.contractAssessmentQuote')}
        </p>
      </Card>

      <Tabs
        variant="pill"
        activeId={filter}
        onChange={(id) => setFilter(id as RiskLevel | 'all')}
        items={filterTabs.map((item) => ({ id: item.id, label: t(item.labelKey) }))}
      />

      <div className="space-y-md">
        {filteredFindings.map((finding) => (
          <Card key={finding.id} className="overflow-hidden p-0">
            <div className="flex items-start justify-between gap-md bg-error-container/45 p-md dark:bg-red-950/30">
              <div>
                <h3 className="font-semibold text-error dark:text-red-200">{finding.title}</h3>
                <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">{finding.clauseRef}</p>
              </div>
              <RiskBadge level={finding.level} />
            </div>
            <div className="space-y-md p-md">
              <div className="flex gap-sm">
                <Sparkles className="mt-1 h-4 w-4 shrink-0 text-primary dark:text-inverse-primary" aria-hidden="true" />
                <div>
                  <p className="label-uppercase text-primary dark:text-inverse-primary">{t('riskReview.plainLanguage')}</p>
                  <p className="mt-xs text-sm leading-6 text-on-surface dark:text-slate-100">{finding.plainLanguage}</p>
                </div>
              </div>
              <div className="rounded-lg border border-legal-border bg-white p-md dark:border-slate-700 dark:bg-slate-950">
                <div className="mb-xs flex items-center gap-xs font-semibold text-on-surface dark:text-slate-100">
                  <Gavel className="h-4 w-4" aria-hidden="true" />
                  {t('riskReview.legalRationale')}
                </div>
                <p className="text-sm leading-6 text-on-surface-variant dark:text-slate-400">{finding.legalBasis}</p>
              </div>
              <div className="flex flex-wrap gap-sm">
                <Button variant="ghost" leftIcon={<LinkIcon className="h-4 w-4" />}>
                  {finding.citation}
                </Button>
                <Button variant="secondary">{t('riskReview.edit')}</Button>
                <Button>{t('riskReview.suggestReplacement')}</Button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <Card title={t('riskReview.pipelineVerification')} subtitle={t('riskReview.sourceGroundedStatus')}>
        <div className="space-y-md">
          {[t('riskReview.stepReadingContract'), t('riskReview.stepExtracting'), t('riskReview.stepEvaluatingRisk'), t('riskReview.stepComparingPrecedent')].map((step, index) => (
            <div key={step} className="flex items-center gap-md">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-white">{index + 1}</div>
              <div className="flex-1">
                <p className="text-sm font-semibold">{step}</p>
                <ProgressBar value={index < 2 ? 100 : index === 2 ? 75 : 30} />
              </div>
            </div>
          ))}
          <p className="flex items-center gap-xs text-xs text-on-surface-variant dark:text-slate-400">
            <BookOpen className="h-4 w-4" aria-hidden="true" />
            {t('riskReview.citationsValidated')}
          </p>
        </div>
      </Card>
    </aside>
  );
}
