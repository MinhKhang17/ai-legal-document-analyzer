import { Download, Highlighter, MessageSquareText, PenLine, Save } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { PageHeader } from '../../components/common/PageHeader';
import { DocumentPreview } from '../../components/editor/DocumentPreview';
import { documents, riskFindings } from '../../api/mockData';
import { useI18n } from '../../hooks/useI18n';

export function EditorPage() {
  const { t } = useI18n();
  const document = documents[0];

  return (
    <div>
      <PageHeader
        title={t('documentDetail.title')}
        subtitle={t('editor.workspaceSubtitle')}
        actions={
          <>
            <Button variant="secondary" leftIcon={<Download className="h-4 w-4" />}>{t('actions.download')}</Button>
            <Button leftIcon={<Save className="h-4 w-4" />}>{t('actions.save')}</Button>
          </>
        }
      />
      <div className="grid gap-gutter xl:grid-cols-[1fr_360px]">
        <DocumentPreview document={document} />
        <aside className="space-y-gutter">
          <Card title={t('editor.reviewerTools')}>
            <div className="grid gap-sm">
              <Button variant="secondary" leftIcon={<PenLine className="h-4 w-4" />}>{t('editor.addRedlineNote')}</Button>
              <Button variant="secondary" leftIcon={<Highlighter className="h-4 w-4" />}>{t('editor.highlightClause')}</Button>
              <Link to="/chat"><Button className="w-full" leftIcon={<MessageSquareText className="h-4 w-4" />}>{t('editor.askAiAboutClause')}</Button></Link>
            </div>
          </Card>
          <Card tone="ai">
            <h2 className="text-title-lg font-semibold">{t('editor.aiRevisionGuidance')}</h2>
            <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">{riskFindings[0].suggestedAction}</p>
          </Card>
          <Card title={t('editor.openFindings')}>
            <div className="space-y-md">
              {riskFindings.map((finding) => (
                <Link key={finding.id} to="/editor/risk-review" className="block rounded-lg border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800">
                  <p className="font-semibold">{finding.title}</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">{finding.clauseRef}</p>
                </Link>
              ))}
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
