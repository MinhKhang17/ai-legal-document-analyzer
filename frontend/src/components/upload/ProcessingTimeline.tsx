import { Check, Clock, FileText, ShieldCheck, Sparkles } from 'lucide-react';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';

interface Step {
  id: string;
  label: string;
  detail: string;
  status: 'complete' | 'active' | 'pending';
}

interface ProcessingTimelineProps {
  steps?: Step[];
  orientation?: 'vertical' | 'horizontal';
}

const iconByStatus = {
  complete: Check,
  active: Sparkles,
  pending: Clock,
};

export function ProcessingTimeline({ steps, orientation = 'vertical' }: ProcessingTimelineProps) {
  const { t } = useI18n();
  const defaultSteps: Step[] = [
    { id: 'extract', label: t('upload.timeline.extractingText'), detail: t('upload.timeline.layoutAnalysisComplete'), status: 'complete' },
    { id: 'chunk', label: t('upload.timeline.chunkingClauses'), detail: t('upload.timeline.segmentingSemanticUnits'), status: 'active' },
    { id: 'embed', label: t('upload.timeline.creatingEmbeddings'), detail: t('upload.timeline.vectorizingRetrieval'), status: 'pending' },
    { id: 'risk', label: t('upload.timeline.riskReview'), detail: t('upload.timeline.modelGroundedEvaluation'), status: 'pending' },
    { id: 'verify', label: t('upload.timeline.aiVerification'), detail: t('upload.timeline.checkingCitationsEvidence'), status: 'pending' },
  ];
  const displaySteps = steps ?? defaultSteps;

  if (orientation === 'horizontal') {
    return (
      <ol className="grid gap-md md:grid-cols-5">
        {displaySteps.map((step) => {
          const Icon = iconByStatus[step.status];
          return (
            <li key={step.id} className="relative rounded-xl border border-legal-border bg-white p-md dark:border-slate-700 dark:bg-slate-900">
              <div
                className={cn(
                  'mb-sm flex h-9 w-9 items-center justify-center rounded-full',
                  step.status === 'complete' && 'bg-primary text-white',
                  step.status === 'active' && 'bg-secondary-container text-secondary',
                  step.status === 'pending' && 'bg-surface-container-low text-outline dark:bg-slate-800',
                )}
              >
                <Icon className="h-4 w-4" aria-hidden="true" />
              </div>
              <p className="text-sm font-semibold text-on-surface dark:text-slate-100">{step.label}</p>
              <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">{step.detail}</p>
            </li>
          );
        })}
      </ol>
    );
  }

  return (
    <ol className="space-y-md">
      {displaySteps.map((step) => {
        const Icon = iconByStatus[step.status];
        return (
          <li key={step.id} className="flex gap-md">
            <div
              className={cn(
                'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
                step.status === 'complete' && 'bg-primary text-white',
                step.status === 'active' && 'bg-secondary-container text-secondary',
                step.status === 'pending' && 'bg-surface-container-low text-outline dark:bg-slate-800',
              )}
            >
              <Icon className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <p className="font-semibold text-on-surface dark:text-slate-100">{step.label}</p>
              <p className="text-sm text-on-surface-variant dark:text-slate-400">{step.detail}</p>
            </div>
          </li>
        );
      })}
      <li className="flex items-center gap-sm rounded-lg bg-surface-container-low p-md text-sm font-semibold text-primary dark:bg-slate-800 dark:text-inverse-primary">
        <ShieldCheck className="h-5 w-5" aria-hidden="true" />
        {t('upload.timeline.aesEncrypted')}
      </li>
      <li className="flex items-center gap-sm text-xs text-on-surface-variant dark:text-slate-500">
        <FileText className="h-4 w-4" aria-hidden="true" />
        {t('upload.timeline.processingEvidenceRetained')}
      </li>
    </ol>
  );
}
