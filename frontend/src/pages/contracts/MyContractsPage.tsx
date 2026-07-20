import { FileText, PlayCircle, RefreshCw, Save } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/common/Pagination";
import { parsePageParam, toPageParam } from "../../utils/pagination";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import {
  generateContract,
  getMyContracts,
  saveContract,
} from "../../services/contract.service";
import { getStoredAccessToken } from "../../services/http";
import { getWorkspaces } from "../../services/workspace.service";
import type {
  ContractGenerationJob,
  UserContract,
} from "../../types/contract";
import type { Workspace } from "../../types/workspace";
import { formatDisplayDate, localeForLanguage } from "../../utils/format";
import { normalizeWorkspaceId } from "../../utils/workspaceId";
import { getSupportedContractTypeLabel } from "../../config/supportedContractTypes";

const getStatusTone = (status?: string) => {
  if (status === "ACTIVE" || status === "COMPLETED" || status === "GENERATED") return "green";
  if (status === "FAILED" || status === "ARCHIVED") return "red";
  if (status === "PROCESSING" || status === "QUEUED") return "amber";
  return "slate";
};

const initialGenerationForm = () => ({
  requestId: `contract-${Date.now()}`,
  workspaceId: "",
  sourceDocumentId: "",
  inputJson: "{}",
});

const initialSaveForm = {
  workspaceId: "",
  generationJobId: "",
  sourceDocumentId: "",
  title: "",
  contractType: "",
  content: "",
};

export function MyContractsPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const locale = localeForLanguage(language);
  const [contracts, setContracts] = useState<UserContract[]>([]);
  const [page, setPage] = useState(() => parsePageParam(searchParams.get("page")));
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [latestJob, setLatestJob] = useState<ContractGenerationJob | null>(null);
  const [generationForm, setGenerationForm] = useState(initialGenerationForm);
  const [saveForm, setSaveForm] = useState(initialSaveForm);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const loadContracts = useCallback(async () => {
    setLoading(true);
    setError("");

    const token = getStoredAccessToken();
    const [contractsResult, workspacesResult] = await Promise.allSettled([
      getMyContracts(page, 20),
      token ? getWorkspaces(token) : Promise.resolve([]),
    ]);

    if (contractsResult.status === "fulfilled") {
      setContracts(contractsResult.value.items ?? []);
      setTotalItems(contractsResult.value.totalItems ?? 0);
      setTotalPages(contractsResult.value.totalPages ?? 0);
    } else {
      const message = t("contracts.loadError");
      setError(message);
      toast.error(message);
    }

    setWorkspaces(workspacesResult.status === "fulfilled" ? workspacesResult.value : []);
    setLoading(false);
  }, [page, toast, t]);

  useEffect(() => {
    void loadContracts();
  }, [loadContracts]);

  const parseWorkspaceId = (value: string) => {
    const normalizedValue = normalizeWorkspaceId(value);

    if (!normalizedValue) {
      toast.warning(t("contracts.workspaceRequired"));
      return null;
    }

    return normalizedValue;
  };

  const handleGenerate = async () => {
    const workspaceId = parseWorkspaceId(generationForm.workspaceId);
    if (workspaceId === null) return;

    if (!generationForm.requestId.trim() || !generationForm.inputJson.trim()) {
      toast.warning(t("contracts.requestAndInputRequired"));
      return;
    }

    setGenerating(true);
    setError("");

    try {
      const job = await generateContract({
        requestId: generationForm.requestId.trim(),
        workspaceId,
        sourceDocumentId: generationForm.sourceDocumentId.trim() || null,
        inputJson: generationForm.inputJson,
      });
      setLatestJob(job);
      setSaveForm((current) => ({
        ...current,
        workspaceId: String(job.workspaceId),
        generationJobId: job.id,
        sourceDocumentId: job.sourceDocumentId ?? current.sourceDocumentId,
        content: job.outputDraft ?? current.content,
      }));
      toast.success(t("contracts.generateSuccess"));
    } catch {
      const message = t("contracts.generateError");
      setError(message);
      toast.error(message);
    } finally {
      setGenerating(false);
    }
  };

  const handleSaveContract = async () => {
    const workspaceId = parseWorkspaceId(saveForm.workspaceId);
    if (workspaceId === null) return;

    if (!saveForm.title.trim() || !saveForm.contractType.trim() || !saveForm.content.trim()) {
      toast.warning(t("contracts.requiredSaveFields"));
      return;
    }

    setSaving(true);
    setError("");

    try {
      await saveContract({
        workspaceId,
        generationJobId: saveForm.generationJobId.trim() || null,
        sourceDocumentId: saveForm.sourceDocumentId.trim() || null,
        title: saveForm.title.trim(),
        contractType: saveForm.contractType.trim(),
        content: saveForm.content,
      });
      toast.success(t("contracts.saveSuccess"));
      setSaveForm(initialSaveForm);
      await loadContracts();
    } catch {
      const message = t("contracts.saveError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const columns: DataTableColumn<UserContract>[] = [
    {
      header: t("contracts.contract"),
      cell: (contract) => (
        <Link
          className="font-semibold text-primary hover:underline dark:text-inverse-primary"
          to={`/contracts/${contract.id}`}
        >
          {contract.title}
        </Link>
      ),
    },
    { header: t("contracts.type"), cell: (contract) => getSupportedContractTypeLabel(contract.contractType, language) ?? t("contracts.outsideSupportedScope") },
    {
      header: t("table.status"),
      cell: (contract) => <Badge tone={getStatusTone(contract.status)}>{t(`contracts.status.${contract.status || "UNKNOWN"}`)}</Badge>,
    },
    { header: t("contracts.version"), cell: (contract) => contract.currentVersionNo ?? "-" },
    {
      header: t("table.updated"),
      cell: (contract) => formatDisplayDate(contract.updatedAt, "-", locale),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t("contracts.title")}
        subtitle={t("contracts.subtitle")}
        actions={
          <Button
            variant="secondary"
            leftIcon={<RefreshCw className="h-4 w-4" />}
            onClick={() => void loadContracts()}
            disabled={loading}
          >
            {t("common.refresh")}
          </Button>
        }
      />

      {error && (
        <div role="alert" className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_420px]">
        <main className="space-y-gutter">
          <Card title={t("contracts.savedContracts")} actions={<Badge tone="blue">{contracts.length}</Badge>}>
            {error ? (
              <div role="alert" className="text-sm text-error">{error} <Button variant="secondary" onClick={() => void loadContracts()}>{t("common.retry")}</Button></div>
            ) : loading && contracts.length === 0 ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("contracts.loading")}
              </p>
            ) : contracts.length === 0 ? (
              <EmptyState
                icon={<FileText className="h-6 w-6" />}
                title={t("contracts.emptyTitle")}
                description={t("contracts.emptyDescription")}
              />
            ) : (
              <DataTable
                columns={columns}
                data={contracts}
                getRowKey={(contract) => contract.id}
              />
            )}
            <Pagination page={page} totalPages={totalPages} totalItems={totalItems} disabled={loading} onPageChange={(nextPage) => { setPage(nextPage); const next = new URLSearchParams(searchParams); next.set("page", toPageParam(nextPage)); setSearchParams(next); }} />
          </Card>

          {latestJob && (
            <Card
              title={t("contracts.latestGenerationJob")}
              actions={<Badge tone={getStatusTone(latestJob.status)}>{t(`contracts.status.${latestJob.status || "UNKNOWN"}`)}</Badge>}
            >
              <dl className="grid gap-md text-sm md:grid-cols-2">
                <div>
                  <dt className="label-uppercase">{t("contracts.jobId")}</dt>
                  <dd className="mt-xs break-all">{latestJob.id}</dd>
                </div>
                <div>
                  <dt className="label-uppercase">{t("contracts.requestId")}</dt>
                  <dd className="mt-xs break-all">{latestJob.requestId}</dd>
                </div>
                <div>
                  <dt className="label-uppercase">{t("contracts.created")}</dt>
                  <dd className="mt-xs">{formatDisplayDate(latestJob.createdAt, "-", locale)}</dd>
                </div>
              </dl>
              {latestJob.errorMessage && (
                <p className="mt-md rounded-lg bg-error/10 p-md text-sm text-error">
                  {t("contracts.generationJobFailed")}
                </p>
              )}
            </Card>
          )}
        </main>

        <aside className="space-y-gutter">
          <Card title={t("contracts.generateDraft")}>
            <div className="space-y-md">
              <label className="block text-sm font-semibold">
                {t("contracts.requestId")}
                <input
                  className="form-field mt-xs"
                  value={generationForm.requestId}
                  onChange={(event) =>
                    setGenerationForm((current) => ({
                      ...current,
                      requestId: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.workspace")}
                <select
                  className="form-field mt-xs"
                  value={generationForm.workspaceId}
                  onChange={(event) =>
                    setGenerationForm((current) => ({
                      ...current,
                      workspaceId: event.target.value,
                    }))
                  }
                >
                  <option value="">{t("contracts.selectWorkspace")}</option>
                  {workspaces.map((workspace) => (
                    <option key={workspace.workspaceId} value={workspace.workspaceId}>
                      {workspace.name}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.sourceDocumentId")}
                <input
                  className="form-field mt-xs"
                  value={generationForm.sourceDocumentId}
                  onChange={(event) =>
                    setGenerationForm((current) => ({
                      ...current,
                      sourceDocumentId: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.inputJson")}
                <textarea
                  className="form-field mt-xs min-h-32 font-mono text-xs"
                  value={generationForm.inputJson}
                  onChange={(event) =>
                    setGenerationForm((current) => ({
                      ...current,
                      inputJson: event.target.value,
                    }))
                  }
                />
              </label>

              <Button
                leftIcon={<PlayCircle className="h-4 w-4" />}
                onClick={() => void handleGenerate()}
                disabled={generating || !generationForm.workspaceId.trim()}
              >
                {generating ? t("contracts.generating") : t("contracts.generate")}
              </Button>
            </div>
          </Card>

          <Card title={t("contracts.saveContract")}>
            <div className="space-y-md">
              <label className="block text-sm font-semibold">
                {t("contracts.workspace")}
                <select
                  className="form-field mt-xs"
                  value={saveForm.workspaceId}
                  onChange={(event) =>
                    setSaveForm((current) => ({ ...current, workspaceId: event.target.value }))
                  }
                >
                  <option value="">{t("contracts.selectWorkspace")}</option>
                  {workspaces.map((workspace) => (
                    <option key={workspace.workspaceId} value={workspace.workspaceId}>
                      {workspace.name}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.titleField")}
                <input
                  className="form-field mt-xs"
                  value={saveForm.title}
                  onChange={(event) =>
                    setSaveForm((current) => ({ ...current, title: event.target.value }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.type")}
                <input
                  className="form-field mt-xs"
                  value={saveForm.contractType}
                  onChange={(event) =>
                    setSaveForm((current) => ({
                      ...current,
                      contractType: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.generationJobId")}
                <input
                  className="form-field mt-xs"
                  value={saveForm.generationJobId}
                  onChange={(event) =>
                    setSaveForm((current) => ({
                      ...current,
                      generationJobId: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.sourceDocumentId")}
                <input
                  className="form-field mt-xs"
                  value={saveForm.sourceDocumentId}
                  onChange={(event) =>
                    setSaveForm((current) => ({
                      ...current,
                      sourceDocumentId: event.target.value,
                    }))
                  }
                />
              </label>

              <label className="block text-sm font-semibold">
                {t("contracts.content")}
                <textarea
                  className="form-field mt-xs min-h-48 font-mono text-xs"
                  value={saveForm.content}
                  onChange={(event) =>
                    setSaveForm((current) => ({ ...current, content: event.target.value }))
                  }
                />
              </label>

              <Button
                leftIcon={<Save className="h-4 w-4" />}
                onClick={() => void handleSaveContract()}
                disabled={saving || !saveForm.workspaceId.trim()}
              >
                {saving ? t("contracts.saving") : t("contracts.saveContract")}
              </Button>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
