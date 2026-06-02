import { AlertTriangle, Quote, ZoomIn, ZoomOut } from 'lucide-react';
import { Button } from '../common/Button';
import { RiskBadge } from '../common/RiskBadge';
import type { LegalDocument } from '../../types/document';
import { cn } from '../../utils/cn';
import { useI18n } from '../../hooks/useI18n';

interface DocumentPreviewProps {
  document: LegalDocument;
  dense?: boolean;
}

const highlightClass = {
  critical: 'legal-highlight-high',
  high: 'legal-highlight-high',
  medium: 'legal-highlight-medium',
  low: 'border-l-4 border-l-primary bg-surface-container-low p-md text-primary dark:bg-slate-800 dark:text-inverse-primary',
  none: 'p-md',
};

export function DocumentPreview({ document, dense = false }: DocumentPreviewProps) {
  const { language, t } = useI18n();
  const documentFontClass = language === 'vi' ? 'font-legal' : 'legal-text';

  return (
    <article className="rounded-xl border border-legal-border bg-surface-container-low p-md dark:border-slate-700 dark:bg-slate-900">
      <header className="mb-md flex flex-wrap items-center justify-between gap-md border-b border-legal-border pb-md dark:border-slate-700">
        <div className="flex items-center gap-sm">
          <Quote className="h-5 w-5 text-primary dark:text-inverse-primary" aria-hidden="true" />
          <div>
            <h2 className="font-semibold text-on-surface dark:text-slate-100">{document.name}</h2>
            <p className="text-xs text-on-surface-variant dark:text-slate-400">
              {t('editor.page')} 4 {t('editor.of')} {document.pages} · {document.type}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-xs">
          <Button variant="ghost" size="icon" aria-label={t('editor.zoomOut')}>
            <ZoomOut className="h-4 w-4" />
          </Button>
          <span className="text-sm font-semibold text-on-surface-variant dark:text-slate-400">100%</span>
          <Button variant="ghost" size="icon" aria-label={t('editor.zoomIn')}>
            <ZoomIn className="h-4 w-4" />
          </Button>
        </div>
      </header>
      <div className={cn('document-paper', documentFontClass, dense ? 'p-lg text-sm' : 'p-xl text-base')}>
        <h1 className="mb-lg text-center text-xl font-bold uppercase tracking-wide">{t('editor.legalServiceAgreement')}</h1>
        {document.clauses.map((clause) => (
          <section key={clause.ref} className="mb-lg">
            <div className="mb-sm flex items-center justify-between gap-sm">
              <h2 className="text-lg font-bold">
                {clause.ref}. {clause.title}
              </h2>
              {clause.riskLevel !== 'none' && <RiskBadge level={clause.riskLevel} />}
            </div>
            <div className={cn('leading-8', highlightClass[clause.riskLevel])}>
              <p>{clause.text}</p>
              {clause.riskLevel === 'high' || clause.riskLevel === 'medium' ? (
                <p className="mt-sm flex items-start gap-xs text-xs italic">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  {clause.riskLevel === 'high'
                    ? t('editor.highRiskNote')
                    : t('editor.mediumRiskNote')}
                </p>
              ) : null}
            </div>
          </section>
        ))}
      </div>
    </article>
  );
}
