import { AlertTriangle, CheckCircle2, Download, Flag, Sparkles } from 'lucide-react';
import { useState } from 'react';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { Card } from '../common/Card';
import { ProgressBar } from '../common/ProgressBar';
import { useI18n } from '../../hooks/useI18n';

export function VersionComparisonView() {
  const { t } = useI18n();
  const [decision, setDecision] = useState<'accepted' | 'flagged' | null>(null);

  return (
    <div className="grid gap-lg xl:grid-cols-[1fr_360px]">
      <div className="space-y-lg">
        <div className="flex flex-wrap items-center justify-between gap-md rounded-xl border border-legal-border bg-white p-md dark:border-slate-700 dark:bg-slate-900">
          <div className="flex flex-wrap items-center gap-sm text-sm">
            <span className="label-uppercase">{t('version.comparing')}</span>
            <Badge tone="blue">v2.4 - Master Service Agreement</Badge>
            <span className="text-on-surface-variant dark:text-slate-400">{t('version.vs')}</span>
            <Badge tone="gold">v2.5 - Revised Draft (Client)</Badge>
          </div>
          <Button variant="secondary" leftIcon={<Download className="h-4 w-4" />}>
            {t('version.exportDiff')}
          </Button>
        </div>

        <div className="grid gap-lg lg:grid-cols-2">
          <Card className="p-0" title={t('version.original')} subtitle={t('version.lastEdited')}>
            <div className="space-y-lg p-lg legal-text leading-7">
              <section>
                <h2 className="mb-sm text-lg font-bold">8. Limitation of Liability</h2>
                <p>
                  Except for gross negligence or willful misconduct, neither party shall be liable for indirect, incidental,
                  special, consequential, or punitive damages. The total liability of LexiGuard AI under this agreement shall not
                  exceed the total fees paid by the Client in the twelve (12) months preceding the event giving rise to the claim.
                </p>
                <p className="mt-md bg-surface-container-low p-md dark:bg-slate-800">
                  Client shall indemnify and hold LexiGuard AI harmless against any third-party claims arising from unauthorized
                  use of AI generated insights by Client subcontractors.
                </p>
              </section>
              <section>
                <h2 className="mb-sm text-lg font-bold">9. Termination</h2>
                <p>Either party may terminate this Agreement for convenience with sixty (60) days written notice.</p>
              </section>
            </div>
          </Card>

          <Card className="p-0" title={t('version.revised')} subtitle={t('version.liveReview')}>
            <div className="space-y-lg p-lg legal-text leading-7">
              <section>
                <h2 className="mb-sm text-lg font-bold">8. Limitation of Liability</h2>
                <p>
                  Except for gross negligence or willful misconduct, neither party shall be liable for indirect, incidental,
                  special, consequential, or punitive damages.
                </p>
                <p className="mt-md border-l-4 border-l-error bg-error-container p-md text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
                  The total liability of LexiGuard AI shall not exceed the higher of five hundred thousand dollars ($500,000) or
                  two times (2x) the total fees paid by the Client.
                </p>
                <p className="mt-md border-l-4 border-l-secondary bg-risk-medium-bg p-md text-risk-medium-text dark:bg-amber-950/30 dark:text-amber-200">
                  Client's liability for data breaches resulting from negligence shall be uncapped. LexiGuard AI shall have no
                  obligation to indemnify Client for third-party intellectual property claims.
                </p>
              </section>
              <section>
                <h2 className="mb-sm text-lg font-bold">9. Termination</h2>
                <p>
                  Either party may terminate this Agreement for convenience with <mark className="bg-secondary-container px-xs text-secondary">thirty (30)</mark> days written notice.
                </p>
              </section>
            </div>
          </Card>
        </div>
      </div>

      <aside className="space-y-md">
        <Card tone="ai">
          <div className="flex items-center gap-sm">
            <Sparkles className="h-5 w-5 text-secondary dark:text-accent-gold" aria-hidden="true" />
            <h2 className="text-title-lg font-semibold text-on-surface dark:text-slate-100">{t('version.lexiguardInsight')}</h2>
          </div>
          <div className="mt-md space-y-md">
            <Badge tone="red">{t('documents.highRisk')}</Badge>
            <div>
              <p className="label-uppercase">{t('version.section81')}</p>
              <h3 className="mt-xs font-semibold">{t('version.capOnLiabilityIncrease')}</h3>
              <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
                {t('version.capOnLiabilityDescription')}
              </p>
            </div>
            <div className="rounded-lg bg-white p-md text-sm dark:bg-slate-950">
              <p className="font-semibold">{t('version.suggestedAction')}</p>
              <p className="mt-xs text-on-surface-variant dark:text-slate-400">
                {t('version.suggestedActionText')}
              </p>
            </div>
            <div className="flex gap-sm">
              <Button variant="gold" leftIcon={<CheckCircle2 className="h-4 w-4" />} onClick={() => setDecision('accepted')}>
                {t('version.acceptChange')}
              </Button>
              <Button variant="secondary" leftIcon={<Flag className="h-4 w-4" />} onClick={() => setDecision('flagged')}>
                {t('version.flag')}
              </Button>
            </div>
            {decision && (
              <p className="rounded-lg bg-surface-container-low p-sm text-sm font-semibold text-primary dark:bg-slate-800 dark:text-inverse-primary">
                {decision === 'accepted' ? t('version.changeAccepted') : t('version.changeFlagged')}
              </p>
            )}
          </div>
        </Card>

        <Card title={t('version.comparisonSummary')}>
          <div className="grid grid-cols-3 gap-sm text-center">
            <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
              <p className="text-2xl font-bold text-primary dark:text-inverse-primary">04</p>
              <p className="text-xs text-on-surface-variant dark:text-slate-400">{t('version.insertions')}</p>
            </div>
            <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
              <p className="text-2xl font-bold text-error">02</p>
              <p className="text-xs text-on-surface-variant dark:text-slate-400">{t('version.deletions')}</p>
            </div>
            <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
              <p className="text-2xl font-bold text-secondary dark:text-accent-gold">03</p>
              <p className="text-xs text-on-surface-variant dark:text-slate-400">{t('documents.highRisk')}</p>
            </div>
          </div>
          <div className="mt-md space-y-sm">
            <div className="flex items-center justify-between text-sm">
              <span>{t('risk.high')}</span>
              <span>78%</span>
            </div>
            <ProgressBar value={78} />
          </div>
        </Card>

        <Card>
          <div className="flex items-start gap-sm text-sm text-on-surface-variant dark:text-slate-400">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-error" aria-hidden="true" />
            {t('version.exportedDiffNote')}
          </div>
        </Card>
      </aside>
    </div>
  );
}
