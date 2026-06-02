import { AlertTriangle, FileText, Plus, Sparkles } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { ProgressBar } from '../../components/common/ProgressBar';
import { StatusBadge } from '../../components/common/StatusBadge';
import { FileUploadZone } from '../../components/upload/FileUploadZone';
import { ProcessingTimeline } from '../../components/upload/ProcessingTimeline';
import { documents } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function UploadPage() {
  const { t } = useI18n();
  const [processing, setProcessing] = useState(false);
  const [progress, setProgress] = useState(65);

  useEffect(() => {
    if (!processing) return undefined;
    const timer = window.setInterval(() => {
      setProgress((previous) => (previous >= 96 ? 65 : previous + 5));
    }, 900);
    return () => window.clearInterval(timer);
  }, [processing]);

  return (
    <div>
      <PageHeader title={t('upload.title')} subtitle={t('upload.subtitle')} />

      <div className="grid gap-gutter xl:grid-cols-[1.1fr_360px]">
        <div className="space-y-gutter">
          <FileUploadZone onFakeUpload={() => setProcessing(true)} />

          <Card title={t('upload.processingQueue')} actions={<Badge tone="gold">{t('upload.estimatedTime')}</Badge>}>
            <div className="space-y-md">
              {documents.slice(1, 3).map((document, index) => (
                <div key={document.id} className="rounded-xl border border-legal-border p-md dark:border-slate-700">
                  <div className="flex items-start justify-between gap-md">
                    <div className="flex items-start gap-md">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                        <FileText className="h-5 w-5" />
                      </div>
                      <div>
                        <p className="font-semibold">{document.name}</p>
                        <p className="text-sm text-on-surface-variant dark:text-slate-400">{document.size} · {document.type}</p>
                      </div>
                    </div>
                    <StatusBadge status={index === 0 || processing ? 'processing' : 'pending'} />
                  </div>
                  {(index === 0 || processing) && (
                    <div className="mt-md">
                      <div className="mb-xs flex items-center justify-between text-sm">
                        <span>{t('upload.extractingTextAndTables')}</span>
                        <span>{progress}%</span>
                      </div>
                      <ProgressBar value={progress} />
                    </div>
                  )}
                </div>
              ))}
            </div>
          </Card>
        </div>

        <aside className="space-y-gutter">
          <Card title={t('upload.settings')}>
            <div className="space-y-md">
              <div>
                <label className="label-uppercase" htmlFor="projectAssignment">{t('upload.projectAssignment')}</label>
                <select id="projectAssignment" className="form-field mt-xs">
                  <option>{t('upload.projectOption1')}</option>
                  <option>{t('upload.projectOption2')}</option>
                  <option>{t('upload.projectOption3')}</option>
                </select>
              </div>
              <div>
                <label className="label-uppercase" htmlFor="documentType">{t('upload.documentType')}</label>
                <select id="documentType" className="form-field mt-xs">
                  <option>{t('upload.documentTypeOption1')}</option>
                  <option>{t('upload.documentTypeOption2')}</option>
                  <option>{t('upload.documentTypeOption3')}</option>
                  <option>{t('upload.documentTypeOption4')}</option>
                </select>
              </div>
              <label className="flex items-start gap-sm rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <input className="mt-1 h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary" type="checkbox" defaultChecked />
                <span>
                  <span className="block font-semibold">{t('upload.advancedOcr')}</span>
                  <span className="text-sm text-on-surface-variant dark:text-slate-400">{t('upload.advancedOcrDescription')}</span>
                </span>
              </label>
            </div>
          </Card>

          <Card title={t('upload.pipeline')}>
            <ProcessingTimeline />
          </Card>

          <Card tone="ai">
            <div className="flex items-center gap-sm">
              <Sparkles className="h-5 w-5 text-secondary dark:text-accent-gold" />
              <h2 className="text-title-lg font-semibold">{t('upload.recentIntelligence')}</h2>
            </div>
            <div className="mt-md space-y-md text-sm">
              <div className="rounded-lg bg-white p-md dark:bg-slate-950">
                <p className="label-uppercase text-secondary dark:text-accent-gold">{t('upload.aiSuggestion')}</p>
                <p className="mt-xs font-semibold">{t('upload.aiSuggestionTitle')}</p>
                <p className="mt-xs text-on-surface-variant dark:text-slate-400">{t('upload.aiSuggestionDescription')}</p>
              </div>
              <div className="flex items-start gap-sm text-on-surface-variant dark:text-slate-400">
                <AlertTriangle className="mt-0.5 h-4 w-4 text-error" />
                {t('upload.riskVariance')}
              </div>
              <Button variant="gold" rightIcon={<Plus className="h-4 w-4" />}>{t('actions.viewDetails')}</Button>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
