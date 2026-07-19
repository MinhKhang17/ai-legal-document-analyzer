import { AlertTriangle, FileText, RefreshCw, Search, UploadCloud } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { EmptyState } from '../../components/common/EmptyState';
import { PageHeader } from '../../components/common/PageHeader';
import { RiskReviewPanel } from '../../components/editor/RiskReviewPanel';
import { FileUploadZone } from '../../components/upload/FileUploadZone';
import { getContractSupportedFormats, uploadContractForAnalysis } from '../../services/aiContracts.service';
import {
  getAiRiskKnowledgeSupportedFormats,
  importAiRiskKnowledgeDocument,
  importAiRiskKnowledgeDocumentV2,
  queryAiRiskKnowledge,
  queryAiRiskKnowledgeV2,
} from '../../services/aiRiskKnowledge.service';
import { AI_SERVICE_UNAVAILABLE_MESSAGE, getReadableErrorMessage, getUniqueErrorMessages } from '../../services/http';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import type { AiContractAnalysisResponse, AiContractClauseFinding, AiIngestionResult, AiKnowledgeQueryResponse } from '../../types/ai';
import type { RiskFinding, RiskLevel } from '../../types/risk';
import { supportedContractScopeText } from '../../config/supportedContractTypes';

type RiskKnowledgeVersion = 'v1' | 'v2';

const toRiskLevel = (severity: string): RiskLevel => {
  const normalized = severity.toLowerCase();
  if (normalized.includes('critical')) return 'critical';
  if (normalized.includes('high')) return 'high';
  if (normalized.includes('medium')) return 'medium';
  if (normalized.includes('low')) return 'low';
  return 'none';
};

const toRiskFinding = (clause: AiContractClauseFinding): RiskFinding => ({
  id: clause.clause_id,
  title: clause.title || clause.risk_concept,
  level: toRiskLevel(clause.severity),
  clauseRef: clause.clause_id,
  summary: clause.explanation,
  plainLanguage: clause.text,
  legalBasis: clause.legal_basis.map((basis) => basis.title || basis.content).filter(Boolean).join(' | ') || clause.detection_method,
  citation: clause.legal_basis[0]?.source_id ?? clause.detection_method,
  suggestedAction: clause.risk_concept,
});

export function RiskReviewPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const riskImportInputRef = useRef<HTMLInputElement | null>(null);
  const [contractFormats, setContractFormats] = useState<string[]>([]);
  const [riskFormats, setRiskFormats] = useState<string[]>([]);
  const [contractReport, setContractReport] = useState<AiContractAnalysisResponse | null>(null);
  const [riskQuery, setRiskQuery] = useState('');
  const [riskKnowledgeVersion, setRiskKnowledgeVersion] = useState<RiskKnowledgeVersion>('v2');
  const [riskQueryResult, setRiskQueryResult] = useState<AiKnowledgeQueryResponse | null>(null);
  const [riskImportResult, setRiskImportResult] = useState<AiIngestionResult | null>(null);
  const [loadingFormats, setLoadingFormats] = useState(true);
  const [uploadingContract, setUploadingContract] = useState(false);
  const [uploadingRiskKnowledge, setUploadingRiskKnowledge] = useState(false);
  const [queryingRisk, setQueryingRisk] = useState(false);
  const [error, setError] = useState('');

  const loadFormats = useCallback(async () => {
    setLoadingFormats(true);
    setError('');

    const [contractResult, riskResult] = await Promise.allSettled([
      getContractSupportedFormats(),
      getAiRiskKnowledgeSupportedFormats(),
    ]);

    if (contractResult.status === 'fulfilled') {
      setContractFormats(contractResult.value);
    } else {
      setContractFormats([]);
    }

    if (riskResult.status === 'fulfilled') {
      setRiskFormats(riskResult.value);
    } else {
      setRiskFormats([]);
    }

    const errors = [contractResult, riskResult]
      .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
      .map((result) => result.reason);

    setError(errors.length > 0 ? getUniqueErrorMessages(errors, AI_SERVICE_UNAVAILABLE_MESSAGE) : '');
    setLoadingFormats(false);
  }, []);

  useEffect(() => {
    void loadFormats();
  }, [loadFormats]);

  const directFindings = useMemo(
    () => contractReport?.clauses.map(toRiskFinding) ?? [],
    [contractReport],
  );

  const aiServiceUnavailable =
    Boolean(error) && !loadingFormats && contractFormats.length === 0 && riskFormats.length === 0;

  const handleContractUpload = async (file: File) => {
    setUploadingContract(true);
    setError('');
    setContractReport(null);

    try {
      const result = await uploadContractForAnalysis(file, file.name);
      setContractReport(result);
      toast.success(t('risk.contractUploadSuccess'), t('toast.successTitle'));
    } catch (err) {
      const message = getReadableErrorMessage(err, AI_SERVICE_UNAVAILABLE_MESSAGE);
      setError(message);
      toast.error(message || t('risk.contractUploadError'), t('toast.errorTitle'));
    } finally {
      setUploadingContract(false);
    }
  };

  const handleRiskImport = async (file: File) => {
    setUploadingRiskKnowledge(true);
    setError('');

    try {
      const result =
        riskKnowledgeVersion === 'v2'
          ? await importAiRiskKnowledgeDocumentV2(file, file.name)
          : await importAiRiskKnowledgeDocument(file, file.name);
      setRiskImportResult(result);
      toast.success(t('risk.importSuccess'), t('toast.successTitle'));
    } catch (err) {
      const message = getReadableErrorMessage(err, AI_SERVICE_UNAVAILABLE_MESSAGE);
      setError(message);
      toast.error(message || t('risk.importError'), t('toast.errorTitle'));
    } finally {
      setUploadingRiskKnowledge(false);
    }
  };

  const handleRiskQuery = async () => {
    const normalizedQuery = riskQuery.trim();
    if (!normalizedQuery) {
      toast.warning(t('risk.queryRequired'), t('toast.warningTitle'));
      return;
    }

    setQueryingRisk(true);
    setError('');

    try {
      const payload = { query: normalizedQuery, top_k: 8 };
      const result =
        riskKnowledgeVersion === 'v2'
          ? await queryAiRiskKnowledgeV2(payload)
          : await queryAiRiskKnowledge(payload);
      setRiskQueryResult(result);
      toast.success(t('risk.querySuccess'), t('toast.successTitle'));
    } catch (err) {
      setRiskQueryResult(null);
      const message = getReadableErrorMessage(err, AI_SERVICE_UNAVAILABLE_MESSAGE);
      setError(message);
      toast.error(message || t('risk.queryError'), t('toast.errorTitle'));
    } finally {
      setQueryingRisk(false);
    }
  };

  const riskAcceptedFormats = riskFormats.map((format) => `.${format.replace(/^\./, '')}`).join(',');

  return (
    <div>
      <PageHeader
        title={t('riskReview.title')}
        subtitle={t('riskReview.subtitle')}
        actions={
          <>
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadFormats()}
              disabled={loadingFormats}
            >
              {t('billing.refresh')}
            </Button>
            <Badge tone={aiServiceUnavailable ? 'amber' : 'gold'}>
              {aiServiceUnavailable ? t('status.offline') : t('riskReview.liveScanning')}
            </Badge>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-amber-300 bg-amber-50 p-md text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/30 dark:text-amber-100">
          <div className="flex flex-col gap-md md:flex-row md:items-start md:justify-between">
            <div className="flex gap-sm">
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
              <div>
                <p className="font-semibold">{t('risk.serviceUnavailableTitle')}</p>
                <p className="mt-xs leading-6">{error}</p>
                <p className="mt-xs text-xs opacity-80">{t('risk.serviceUnavailableHint')}</p>
              </div>
            </div>
            <Button variant="secondary" size="sm" onClick={() => void loadFormats()} disabled={loadingFormats}>
              {t('billing.refresh')}
            </Button>
          </div>
        </div>
      )}

      <section className="mb-xl grid gap-gutter xl:grid-cols-[0.9fr_1.1fr]">
        <Card
          title={t('risk.contractAnalysis')}
          subtitle={t('risk.contractAnalysisSubtitle')}
          actions={<Badge tone="amber">{t('risk.directAiService')}</Badge>}
        >
          <p className="mb-md rounded-xl bg-surface-container-low p-sm text-xs text-on-surface-variant dark:bg-slate-800 dark:text-slate-400">
            {supportedContractScopeText(language)} {language === 'vi' ? 'Bạn chỉ cần tải tài liệu lên.' : 'You only need to upload the document.'}
          </p>
          <FileUploadZone
            onUpload={handleContractUpload}
            disabled={uploadingContract || loadingFormats || aiServiceUnavailable}
            compact
          />
          {loadingFormats && (
            <p className="mt-md text-sm text-on-surface-variant dark:text-slate-400">{t('risk.checkingContractFormats')}</p>
          )}
          {!loadingFormats && contractFormats.length === 0 && (
            <p className="mt-md rounded-lg bg-surface-container-low p-sm text-sm text-on-surface-variant dark:bg-slate-800 dark:text-slate-400">
              {t('risk.noContractFormats')}
            </p>
          )}
          {contractFormats.length > 0 && (
            <div className="mt-md flex flex-wrap gap-xs">
              {contractFormats.map((format) => (
                <Badge key={format} tone="blue">{format}</Badge>
              ))}
            </div>
          )}
          {contractReport && (
            <div className="mt-md rounded-xl bg-surface-container-low p-md text-sm dark:bg-slate-800">
              <p className="font-semibold">{contractReport.filename}</p>
              <p className="mt-xs text-on-surface-variant dark:text-slate-400">
                {contractReport.summary.finding_count} {t('risk.findings')} · {contractReport.summary.high_risk_count} {t('risk.high')} · {contractReport.summary.medium_risk_count} {t('risk.medium')}
              </p>
            </div>
          )}
        </Card>

        <Card
          title={t('risk.knowledgeTitle')}
          subtitle={t('risk.knowledgeSubtitle')}
          actions={<Badge tone="red">{t('risk.adminDirect')}</Badge>}
        >
          <input
            ref={riskImportInputRef}
            type="file"
            hidden
            accept={riskAcceptedFormats || undefined}
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = '';
              if (file) void handleRiskImport(file);
            }}
          />
          <div className="flex flex-col gap-sm md:flex-row">
            <input
              className="form-field"
              value={riskQuery}
              onChange={(event) => setRiskQuery(event.target.value)}
              placeholder={t('risk.queryPlaceholder')}
            />
            <select
              className="form-field md:max-w-32"
              value={riskKnowledgeVersion}
              onChange={(event) => setRiskKnowledgeVersion(event.target.value as RiskKnowledgeVersion)}
              disabled={queryingRisk || uploadingRiskKnowledge}
            >
              <option value="v2">v2</option>
              <option value="v1">v1</option>
            </select>
            <Button
              leftIcon={<Search className="h-4 w-4" />}
              onClick={() => void handleRiskQuery()}
              disabled={!riskQuery.trim() || queryingRisk || loadingFormats || aiServiceUnavailable}
            >
              {queryingRisk ? t('actions.querying') : t('actions.query')}
            </Button>
            <Button
              variant="secondary"
              leftIcon={<UploadCloud className="h-4 w-4" />}
              onClick={() => riskImportInputRef.current?.click()}
              disabled={uploadingRiskKnowledge || loadingFormats || aiServiceUnavailable}
            >
              {uploadingRiskKnowledge ? t('actions.importing') : t('actions.import')}
            </Button>
          </div>
          {loadingFormats && (
            <p className="mt-md text-sm text-on-surface-variant dark:text-slate-400">{t('risk.checkingFormats')}</p>
          )}
          {!loadingFormats && riskFormats.length === 0 && (
            <p className="mt-md rounded-lg bg-surface-container-low p-sm text-sm text-on-surface-variant dark:bg-slate-800 dark:text-slate-400">
              {t('risk.noFormats')}
            </p>
          )}
          {riskFormats.length > 0 && (
            <div className="mt-md flex flex-wrap gap-xs">
              {riskFormats.map((format) => (
                <Badge key={format} tone="slate">{format}</Badge>
              ))}
            </div>
          )}
          {riskImportResult && (
            <p className="mt-md rounded-lg bg-emerald-50 p-sm text-sm font-semibold text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200">
              {t('risk.importedChunks')
                .replace('{title}', riskImportResult.title)
                .replace('{chunks}', String(riskImportResult.total_chunks))}
            </p>
          )}
          {riskQueryResult && (
            <div className="mt-md rounded-xl bg-surface-container-low p-md text-sm leading-6 dark:bg-slate-800">
              <p className="label-uppercase mb-xs">{t('risk.answerPreview')}</p>
              <p>{riskQueryResult.answer_preview || t('risk.noPreview')}</p>
              <p className="mt-sm text-xs text-on-surface-variant dark:text-slate-400">
                {riskQueryResult.chunks.length} chunks từ {riskQueryResult.source}
              </p>
            </div>
          )}
        </Card>
      </section>

      {contractReport ? (
        <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_440px]">
          <Card title={t('risk.contractResultTitle')}>
            <div className="flex items-start gap-md">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                <FileText className="h-5 w-5" aria-hidden="true" />
              </div>
              <div>
                <p className="font-semibold text-on-surface dark:text-slate-100">{contractReport.filename}</p>
                <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                  {contractReport.summary.finding_count} {t('risk.findings')} · {contractReport.summary.high_risk_count} {t('risk.high')} · {contractReport.summary.medium_risk_count} {t('risk.medium')}
                </p>
                <p className="mt-md text-sm leading-6 text-on-surface-variant dark:text-slate-400">
                  {t('risk.contractResultDescription')}
                </p>
              </div>
            </div>
          </Card>
          <RiskReviewPanel findings={directFindings} />
        </div>
      ) : (
        <Card>
          <EmptyState
            icon={<FileText className="h-6 w-6" aria-hidden="true" />}
            title={t('risk.noContractTitle')}
            description={t('risk.noContractDescription')}
          />
        </Card>
      )}
    </div>
  );
}
