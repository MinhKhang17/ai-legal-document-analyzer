import { Download, Eye, FileDown, FileText, Lightbulb, Settings, Sparkles, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { RiskBadge } from '../../components/common/RiskBadge';
import { StatusBadge } from '../../components/common/StatusBadge';
import { reports } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function ReportsPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader
        title={t('reports.title')}
        subtitle={t('reports.subtitle')}
        actions={<Button variant="secondary" leftIcon={<FileDown className="h-4 w-4" />}>{t('actions.exportHistory')}</Button>}
      />

      <div className="grid gap-gutter xl:grid-cols-[0.95fr_1.05fr]">
        <Card title={t('reports.generateNewReport')} actions={<Sparkles className="h-5 w-5 text-secondary dark:text-accent-gold" />}>
          <form className="space-y-md">
            <div>
              <label className="label-uppercase" htmlFor="reviewedContract">{t('reports.reviewedContract')}</label>
              <select className="form-field mt-xs" id="reviewedContract">
                <option>Master Service Agreement - TechFlow Inc.</option>
                <option>Employment Contract - Sarah Jenkins</option>
                <option>NDA - Project Artemis</option>
              </select>
            </div>
            <div>
              <p className="label-uppercase mb-sm">{t('reports.reportTemplate')}</p>
              <div className="grid gap-md sm:grid-cols-2">
                <label className="rounded-xl border border-primary bg-surface-container-low p-md dark:border-inverse-primary dark:bg-slate-800">
                  <input type="radio" className="sr-only" name="template" defaultChecked />
                  <p className="font-semibold">{t('reports.executiveSummary')}</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{t('reports.executiveSummaryDescription')}</p>
                </label>
                <label className="rounded-xl border border-legal-border p-md dark:border-slate-700">
                  <input type="radio" className="sr-only" name="template" />
                  <p className="font-semibold">{t('reports.fullRiskReview')}</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{t('reports.fullRiskReviewDescription')}</p>
                </label>
              </div>
            </div>
            <div>
              <p className="label-uppercase mb-sm">{t('reports.configureSections')}</p>
              <div className="flex flex-wrap gap-xs">
                {[t('reports.sectionLiabilities'), t('reports.sectionTerminations'), t('reports.sectionIpRights'), t('reports.sectionGoverningLaw')].map((section) => (
                  <Badge key={section} tone="blue">{section}</Badge>
                ))}
              </div>
            </div>
            <Button className="w-full" leftIcon={<Settings className="h-4 w-4" />}>{t('actions.generate')}</Button>
          </form>
        </Card>

        <div className="space-y-gutter">
          <Card tone="ai">
            <div className="flex gap-md">
              <Lightbulb className="h-6 w-6 shrink-0 text-secondary dark:text-accent-gold" />
              <div>
                <h2 className="text-title-lg font-semibold">{t('reports.lexiguardTip')}</h2>
                <p className="mt-xs text-sm leading-6 text-on-surface-variant dark:text-slate-300">
                  {t('reports.lexiguardTipText')}
                </p>
              </div>
            </div>
          </Card>
          <Card className="bg-primary text-white dark:bg-slate-900">
            <p className="label-uppercase text-secondary-container">{t('reports.advancedAudit')}</p>
            <h2 className="mt-xs text-title-lg font-semibold">{t('reports.unlockTrendAnalysis')}</h2>
            <Button className="mt-md" variant="gold">{t('reports.upgradeNow')}</Button>
          </Card>
        </div>
      </div>

      <Card className="mt-xl" title={t('reports.recent')}>
        <div className="space-y-md">
          {reports.map((report) => (
            <article key={report.id} className="flex flex-col gap-md rounded-xl border border-legal-border p-md dark:border-slate-700 md:flex-row md:items-center md:justify-between">
              <div className="flex items-start gap-md">
                <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                  <FileText className="h-5 w-5" />
                </div>
                <div>
                  <Link to={`/reports/${report.id}`} className="font-semibold text-primary hover:underline dark:text-inverse-primary">{report.title}</Link>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{report.createdAt} · {report.author}</p>
                  <div className="mt-sm flex flex-wrap gap-xs"><RiskBadge level={report.riskLevel} /><StatusBadge status={report.status} /></div>
                </div>
              </div>
              <div className="flex gap-xs">
                <Link to={`/reports/${report.id}`}><Button variant="ghost" size="icon" aria-label={t('reports.previewReport')}><Eye className="h-4 w-4" /></Button></Link>
                <Button variant="ghost" size="icon" aria-label={t('reports.downloadReport')}><Download className="h-4 w-4" /></Button>
                <Button variant="ghost" size="icon" aria-label={t('reports.deleteReport')}><Trash2 className="h-4 w-4" /></Button>
              </div>
            </article>
          ))}
        </div>
      </Card>
    </div>
  );
}
