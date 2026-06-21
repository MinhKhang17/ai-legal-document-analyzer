import { FileText } from 'lucide-react';
import { Badge } from '../../components/common/Badge';
import { PageHeader } from '../../components/common/PageHeader';
import { DocumentPreview } from '../../components/editor/DocumentPreview';
import { RiskReviewPanel } from '../../components/editor/RiskReviewPanel';
import { documents, riskFindings } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function RiskReviewPage() {
  const { t } = useI18n();
  const document = documents[1];

  return (
    <div>
      <PageHeader
        title={t('riskReview.title')}
        subtitle={t('riskReview.subtitle')}
        actions={<Badge tone="gold">{t('riskReview.liveScanning')}</Badge>}
      />
      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_440px]">
        <div>
          <div className="mb-md flex items-center gap-sm rounded-xl border border-legal-border bg-white p-md font-semibold text-primary dark:border-slate-700 dark:bg-slate-900 dark:text-inverse-primary">
            <FileText className="h-5 w-5" /> {document.name}
          </div>
          <DocumentPreview document={document} />
          
        </div>
        <RiskReviewPanel findings={riskFindings} />
      </div>
    </div>
  );
}
