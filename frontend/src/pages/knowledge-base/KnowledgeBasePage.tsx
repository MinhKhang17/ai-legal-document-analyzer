import { AlertTriangle, RefreshCw, Search, Upload } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { DataTable, type DataTableColumn } from '../../components/common/DataTable';
import { PageHeader } from '../../components/common/PageHeader';
import { StatCard } from '../../components/common/StatCard';
import {
  askAiKnowledge,
  getAiKnowledgeSupportedFormats,
  ingestAiKnowledgeDocument,
  ingestAiKnowledgeDocumentV2,
  queryAiKnowledgeV2,
  searchAiKnowledge,
} from '../../services/aiKnowledge.service';
import { AI_SERVICE_UNAVAILABLE_MESSAGE, getReadableErrorMessage } from '../../services/http';
import { useI18n } from '../../hooks/useI18n';
import { useToast } from '../../hooks/useToast';
import type { AiChunk, AiIngestionResult, AiKnowledgeAskResponse, AiKnowledgeQueryResponse } from '../../types/ai';

type KnowledgeMode = 'search' | 'ask' | 'query-v2';
type KnowledgeIngestVersion = 'v1' | 'v2';

export function KnowledgeBasePage() {
  const { t } = useI18n();
  const toast = useToast();
  const [formats, setFormats] = useState<string[]>([]);
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<KnowledgeMode>('query-v2');
  const [ingestVersion, setIngestVersion] = useState<KnowledgeIngestVersion>('v2');
  const [topK, setTopK] = useState(5);
  const [chunks, setChunks] = useState<AiChunk[]>([]);
  const [answer, setAnswer] = useState('');
  const [llmStatus, setLlmStatus] = useState('');
  const [ingestionResult, setIngestionResult] = useState<AiIngestionResult | null>(null);
  const [loadingFormats, setLoadingFormats] = useState(true);
  const [runningQuery, setRunningQuery] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const loadFormats = useCallback(async () => {
    setLoadingFormats(true);
    setError('');

    try {
      setFormats(await getAiKnowledgeSupportedFormats());
    } catch (err) {
      setFormats([]);
      setError(getReadableErrorMessage(err, AI_SERVICE_UNAVAILABLE_MESSAGE));
    } finally {
      setLoadingFormats(false);
    }
  }, []);

  useEffect(() => {
    void loadFormats();
  }, [loadFormats]);

  const handleUpload = async (file: File, useV2: boolean) => {
    setUploading(true);
    setError('');
    setIngestionResult(null);

    try {
      const result = useV2
        ? await ingestAiKnowledgeDocumentV2(file, file.name)
        : await ingestAiKnowledgeDocument(file, file.name);
      setIngestionResult(result);
      toast.success(t('knowledge.importSuccess'), t('toast.successTitle'));
    } catch (err) {
      const message = getReadableErrorMessage(err, AI_SERVICE_UNAVAILABLE_MESSAGE);
      setError(message);
      toast.error(message || t('knowledge.importError'), t('toast.errorTitle'));
    } finally {
      setUploading(false);
    }
  };

  const handleRunQuery = async () => {
    const normalizedQuery = query.trim();

    if (!normalizedQuery) {
      setError(t('knowledge.queryRequired'));
      toast.warning(t('knowledge.queryRequired'), t('toast.warningTitle'));
      return;
    }

    setRunningQuery(true);
    setError('');
    setAnswer('');
    setLlmStatus('');
    setChunks([]);

    try {
      if (mode === 'search') {
        const response = await searchAiKnowledge({ query: normalizedQuery, top_k: topK });
        setChunks(response);
        toast.success(t('knowledge.querySuccess'), t('toast.successTitle'));
        return;
      }

      if (mode === 'ask') {
        const response: AiKnowledgeQueryResponse = await askAiKnowledge({ query: normalizedQuery, top_k: topK });
        setAnswer(response.answer_preview);
        setChunks(response.chunks);
        toast.success(t('knowledge.querySuccess'), t('toast.successTitle'));
        return;
      }

      const response: AiKnowledgeAskResponse = await queryAiKnowledgeV2({ query: normalizedQuery, top_k: topK });
      setAnswer(response.answer);
      setLlmStatus(response.llm_status + (response.llm_error ? `: ${response.llm_error}` : ''));
      setChunks(response.chunks);
      toast.success(t('knowledge.querySuccess'), t('toast.successTitle'));
    } catch (err) {
      const message = getReadableErrorMessage(err, AI_SERVICE_UNAVAILABLE_MESSAGE);
      setError(message);
      toast.error(message || t('knowledge.queryError'), t('toast.errorTitle'));
    } finally {
      setRunningQuery(false);
    }
  };

  const columns: DataTableColumn<AiChunk>[] = [
    { header: 'Chunk', cell: (chunk) => <span className="break-all font-semibold">{chunk.chunk_id}</span> },
    { header: 'Tiêu đề', cell: (chunk) => chunk.title },
    { header: 'Điểm', cell: (chunk) => chunk.score.toFixed(3) },
    { header: 'Xem trước', cell: (chunk) => <span className="line-clamp-3">{chunk.text}</span> },
  ];

  const supportedFormatsLabel = useMemo(
    () => (formats.length > 0 ? formats.join(', ') : loadingFormats ? t('common.loading') : t('knowledge.noFormats')),
    [formats, loadingFormats, t],
  );

  return (
    <div>
      <PageHeader
        title={t('knowledge.title')}
        subtitle={t('knowledge.subtitle')}
        actions={
          <Button variant="secondary" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void loadFormats()} disabled={loadingFormats}>
            {t('billing.refresh')}
          </Button>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-amber-300 bg-amber-50 p-md text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/30 dark:text-amber-100">
          <div className="flex flex-col gap-md md:flex-row md:items-start md:justify-between">
            <div className="flex gap-sm">
              <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
              <div>
                <p className="font-semibold">{t('knowledge.serviceUnavailableTitle')}</p>
                <p className="mt-xs leading-6">{error}</p>
                <p className="mt-xs text-xs opacity-80">{t('knowledge.serviceUnavailableHint')}</p>
              </div>
            </div>
            <Button variant="secondary" size="sm" onClick={() => void loadFormats()} disabled={loadingFormats}>
              {t('billing.refresh')}
            </Button>
          </div>
        </div>
      )}

      <section className="grid gap-gutter md:grid-cols-3">
        <StatCard label={t('knowledge.supportedFormats')} value={String(formats.length)} change={supportedFormatsLabel} trend="neutral" />
        <StatCard label={t('knowledge.retrievedChunks')} value={String(chunks.length)} change={mode} trend="neutral" accent="green" />
        <StatCard label={t('knowledge.lastImport')} value={ingestionResult ? String(ingestionResult.total_chunks) : '-'} change={ingestionResult?.title ?? t('knowledge.noUpload')} trend="neutral" accent="gold" />
      </section>

      <section className="mt-xl grid gap-gutter xl:grid-cols-[0.85fr_1.15fr]">
        <Card title={t('knowledge.uploadImportTitle')} subtitle={t('knowledge.uploadImportSubtitle')}>
          <div className="space-y-md">
            <label className="block rounded-xl border border-dashed border-outline-variant p-lg text-center dark:border-slate-700">
              <Upload className="mx-auto h-8 w-8 text-primary dark:text-inverse-primary" />
              <span className="mt-sm block text-sm font-semibold">{t('knowledge.chooseFile')}</span>
              <input
                className="sr-only"
                type="file"
                disabled={uploading}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void handleUpload(file, ingestVersion === 'v2');
                  event.currentTarget.value = '';
                }}
              />
            </label>
            <select
              className="form-field"
              value={ingestVersion}
              onChange={(event) => setIngestVersion(event.target.value as KnowledgeIngestVersion)}
              disabled={uploading}
            >
              <option value="v2">Import v2</option>
              <option value="v1">Import v1</option>
            </select>
            {loadingFormats && (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('knowledge.checkingFormats')}</p>
            )}
            {!loadingFormats && formats.length === 0 && (
              <p className="rounded-lg bg-surface-container-low p-sm text-sm text-on-surface-variant dark:bg-slate-800 dark:text-slate-400">
                {t('knowledge.noSupportedFormats')}
              </p>
            )}
            <div className="flex flex-wrap gap-xs">
              {formats.map((format) => <Badge key={format} tone="blue">{format}</Badge>)}
            </div>
            {uploading && <p className="text-sm text-on-surface-variant dark:text-slate-400">{t('knowledge.uploading')}</p>}
            {ingestionResult && (
              <div className="rounded-lg bg-surface-container-low p-md text-sm dark:bg-slate-800">
                <p className="font-semibold">{ingestionResult.title}</p>
                <p className="mt-xs text-on-surface-variant dark:text-slate-400">
                  {ingestionResult.total_chunks} chunks · {ingestionResult.file_type} · v{ingestionResult.ingestion_version ?? 1}
                </p>
              </div>
            )}
          </div>
        </Card>

        <Card title={t('knowledge.queryTitle')} subtitle={t('knowledge.querySubtitle')}>
          <div className="grid gap-md lg:grid-cols-[1fr_160px_140px_auto]">
            <input
              className="form-field"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t('knowledge.searchPlaceholder')}
            />
            <select className="form-field" value={mode} onChange={(event) => setMode(event.target.value as KnowledgeMode)}>
              <option value="query-v2">Query v2</option>
              <option value="ask">Ask</option>
              <option value="search">Search</option>
            </select>
            <input
              className="form-field"
              type="number"
              min={1}
              max={20}
              value={topK}
              onChange={(event) => setTopK(Number(event.target.value))}
            />
            <Button leftIcon={<Search className="h-4 w-4" />} onClick={() => void handleRunQuery()} disabled={runningQuery}>
              {runningQuery ? t('actions.running') : t('actions.run')}
            </Button>
          </div>
          {answer && (
            <div className="mt-md rounded-xl bg-surface-container-low p-md text-sm leading-6 dark:bg-slate-800">
              <p className="label-uppercase mb-xs">{t('knowledge.answer')}</p>
              <p>{answer}</p>
              {llmStatus && <p className="mt-sm text-xs font-semibold text-on-surface-variant dark:text-slate-400">{llmStatus}</p>}
            </div>
          )}
        </Card>
      </section>

      <Card className="mt-xl" title={t('knowledge.chunksReturned')}>
        <DataTable columns={columns} data={chunks} getRowKey={(chunk) => chunk.chunk_id} emptyMessage={t('knowledge.noChunks')} />
      </Card>
    </div>
  );
}
