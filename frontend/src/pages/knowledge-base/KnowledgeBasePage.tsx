import { ArrowRight, ArrowUpDown, FileText, RefreshCw, RotateCcw, Search, SlidersHorizontal, Upload, UploadCloud } from "lucide-react";
import type { ChangeEvent } from "react";
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Pagination } from "../../components/common/Pagination";
import { parsePageParam, toPageParam } from "../../utils/pagination";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { PageHeader } from "../../components/common/PageHeader";
import {
  getKnowledgeBaseEntries,
  getKnowledgeIngestionJob,
  ingestKnowledgeBaseEntry,
  uploadKnowledgeBaseEntry,
  uploadKnowledgeBaseSourceFile,
} from "../../services/knowledgeBase.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { useAppStore } from "../../store/AppStore";
import type { KnowledgeBaseEntry, KnowledgeIngestionJob, UploadKnowledgeRequest } from "../../types/knowledgeBase";
import { formatDisplayDate } from "../../utils/format";

const emptyForm = {
  code: "",
  title: "",
  category: "LEGAL_SOURCE",
  scope: "GLOBAL",
  description: "",
  extractedContent: "",
  rawContent: "",
};

const knowledgeStatusKeys: Record<string, string> = {
  PENDING: "knowledge.status.PENDING",
  UPLOADED: "knowledge.status.PENDING",
  PROCESSING: "knowledge.status.PROCESSING",
  INGESTED: "knowledge.status.INGESTED",
  REVIEWING: "knowledge.status.REVIEWING",
  PUBLIC: "knowledge.status.PUBLIC",
  ARCHIVED: "knowledge.status.ARCHIVED",
  FAILED: "knowledge.status.FAILED",
};

const knowledgeScopeKeys: Record<string, string> = {
  GLOBAL: "knowledge.scopeValue.GLOBAL",
  WORKSPACE: "knowledge.scopeValue.WORKSPACE",
};

const knowledgeCategoryKeys: Record<string, string> = {
  LEGAL_SOURCE: "knowledge.categoryValue.LEGAL_SOURCE",
};

const knowledgeStatuses = ["PENDING", "UPLOADED", "PROCESSING", "INGESTED", "REVIEWING", "PUBLIC", "ARCHIVED", "FAILED"];

const sortValues = [
  "updatedAt,desc",
  "updatedAt,asc",
  "createdAt,desc",
  "createdAt,asc",
  "title,asc",
  "title,desc",
  "code,asc",
  "code,desc",
  "currentStatus,asc",
  "currentStatus,desc",
  "currentVersionNo,desc",
  "currentVersionNo,asc",
];

const sortField = (sort: string) => sort.split(",")[0];

export function KnowledgeBasePage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { user } = useAppStore();
  const [searchParams, setSearchParams] = useSearchParams();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const fileSizeFormatter = new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  const translateEnum = (value: string | null | undefined, keys: Record<string, string>) => {
    if (!value) return t("common.unknown");
    const key = keys[value];
    return key ? t(key) : value;
  };
  const [entries, setEntries] = useState<KnowledgeBaseEntry[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(() => parsePageParam(searchParams.get("page")));
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [scopeFilter, setScopeFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [activeFilter, setActiveFilter] = useState("");
  const [primarySort, setPrimarySort] = useState("updatedAt,desc");
  const [secondarySort, setSecondarySort] = useState("title,asc");
  const [form, setForm] = useState(emptyForm);
  const [selectedFileName, setSelectedFileName] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [activeJob, setActiveJob] = useState<KnowledgeIngestionJob | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const loadEntries = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const sorts = [primarySort, secondarySort]
        .filter(Boolean)
        .filter((sort, index, values) => values.findIndex((candidate) => sortField(candidate) === sortField(sort)) === index);
      const response = await getKnowledgeBaseEntries(page, 20, {
        keyword,
        status: statusFilter || undefined,
        scope: scopeFilter || undefined,
        category: categoryFilter || undefined,
        active: activeFilter === "" ? undefined : activeFilter === "true",
        sort: sorts,
      });
      setEntries(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch {
      const message = t("knowledge.loadError");
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [activeFilter, categoryFilter, keyword, page, primarySort, scopeFilter, secondarySort, statusFilter, t]);

  const applySearch = () => {
    setPage(0);
    setKeyword(searchInput.trim());
  };

  const resetFilters = () => {
    setSearchInput("");
    setKeyword("");
    setStatusFilter("");
    setScopeFilter("");
    setCategoryFilter("");
    setActiveFilter("");
    setPrimarySort("updatedAt,desc");
    setSecondarySort("title,asc");
    setPage(0);
  };

  useEffect(() => {
    void loadEntries();
  }, [loadEntries]);

  useEffect(() => {
    if (!activeJob?.id || activeJob.status !== "PROCESSING") return;
    let cancelled = false;
    const refreshProgress = async () => {
      try {
        const job = await getKnowledgeIngestionJob(activeJob.id);
        if (cancelled) return;
        setActiveJob(job);
        if (job.status === "INGESTED") {
          toast.success(t("knowledge.backgroundIngestSuccess"));
          await loadEntries();
          setActiveJob(null);
        } else if (job.status === "FAILED") {
          toast.error(t("knowledge.backgroundIngestFailed"));
          await loadEntries();
          setActiveJob(null);
        }
      } catch {
        // Keep the last known progress and retry; navigation or a brief restart must not lose the job.
      }
    };
    void refreshProgress();
    const timer = window.setInterval(() => void refreshProgress(), 2500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [activeJob?.id, activeJob?.status, loadEntries, t, toast]);

  const handleUpload = async (startIngest: boolean) => {
    if (!user?.id || !form.code.trim() || !form.title.trim() || (startIngest && !selectedFile)) {
      toast.warning(t("knowledge.requiredFields"));
      return;
    }

    setSaving(true);
    setError("");
    try {
      const payload: UploadKnowledgeRequest = {
        code: form.code.trim(),
        title: form.title.trim(),
        category: form.category.trim() || "LEGAL_SOURCE",
        scope: form.scope,
        createdById: user.id,
        description: form.description.trim() || null,
        // Compatibility with older deployed backends where this hidden field was @NotBlank.
        // The actual content is still extracted asynchronously by the AI ingest service.
        extractedContent: form.extractedContent.trim()
          || `[PENDING_AI_EXTRACTION:${selectedFile?.name ?? form.code.trim()}]`,
        rawContent: null,
      };
      const uploadedVersion = await uploadKnowledgeBaseEntry(payload);
      if (selectedFile) {
        await uploadKnowledgeBaseSourceFile(uploadedVersion.knowledgeBaseEntryId, selectedFile);
      }
      if (selectedFile && startIngest) {
        const job = await ingestKnowledgeBaseEntry(uploadedVersion.knowledgeBaseEntryId, {
          requestId: `kb-file-${Date.now()}-${selectedFile.name}`,
          jobPayload: JSON.stringify({ filename: selectedFile.name }),
        });
        setActiveJob(job);
      }
      toast.success(startIngest ? t("knowledge.backgroundIngestStarted") : t("knowledge.privateDraftSaved"));
      setForm(emptyForm);
      setSelectedFileName("");
      setSelectedFile(null);
      await loadEntries();
    } catch {
      const message = t("knowledge.uploadError");
      setActiveJob(null);
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const handleTextFileSelect = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      const fileStem = file.name.replace(/\.[^.]+$/, "");
      const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
      const isTextDocument = ["txt", "md", "csv", "json"].includes(extension);
      const content = isTextDocument ? (await file.text()).replace(/\u0000/g, "") : "";
      const safePendingContent = content || t("knowledge.pendingExtraction", { fileName: file.name });
      setSelectedFile(file);
      setSelectedFileName(file.name);
      setForm((previous) => ({
        ...previous,
        title: previous.title.trim() ? previous.title : fileStem,
        code: previous.code.trim() ? previous.code : fileStem.toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_+|_+$/g, ""),
        extractedContent: safePendingContent,
        rawContent: content,
      }));
    } catch {
      toast.error(t("knowledge.fileReadError"));
    } finally {
      event.target.value = "";
    }
  };

  const columns: DataTableColumn<KnowledgeBaseEntry>[] = [
    {
      header: t("knowledge.titleField"),
      cell: (entry) => (
        <Link className="font-semibold text-primary hover:underline dark:text-inverse-primary" to={`/knowledge-base/${entry.id}`}>
          {entry.title}
        </Link>
      ),
    },
    { header: t("knowledge.code"), cell: (entry) => entry.code },
    { header: t("knowledge.category"), cell: (entry) => translateEnum(entry.category, knowledgeCategoryKeys) },
    { header: t("knowledge.scope"), cell: (entry) => <Badge>{translateEnum(entry.scope, knowledgeScopeKeys)}</Badge> },
    { header: t("table.status"), cell: (entry) => <Badge>{translateEnum(entry.currentStatus, knowledgeStatusKeys)}</Badge> },
    { header: t("knowledge.ingestedDocuments.active"), cell: (entry) => <Badge tone={entry.active ? "green" : "slate"}>{entry.active ? t("admin.active") : t("admin.inactive")}</Badge> },
    { header: t("table.updated"), cell: (entry) => formatDisplayDate(entry.updatedAt, "-", locale) },
  ];

  return (
    <div>
      <PageHeader
        title={t("knowledge.title")}
        subtitle={t("knowledge.subtitle")}
        actions={
          <Button
            variant="secondary"
            leftIcon={<RefreshCw className="h-4 w-4" />}
            onClick={() => void loadEntries()}
            disabled={loading}
          >
            {t("common.refresh")}
          </Button>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <section className="mb-xl grid grid-cols-1 items-start gap-gutter xl:grid-cols-[minmax(420px,0.85fr)_minmax(0,1.65fr)] [&>*]:min-w-0">
        <Card title={t("knowledge.uploadEntry")} subtitle={t("knowledge.uploadEntrySubtitle")}>
          <div className="space-y-lg">
           

            <label className="block cursor-pointer rounded-2xl border-2 border-dashed border-legal-border bg-surface-container-low px-lg py-xl text-center transition hover:border-primary/60 hover:bg-primary/5 dark:border-slate-700 dark:bg-slate-900/60">
              <input
                className="sr-only"
                type="file"
                accept=".pdf,.doc,.docx,.txt,.md,.csv,.json,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown,text/csv,application/json"
                onChange={(event) => void handleTextFileSelect(event)}
              />
              <UploadCloud className="mx-auto h-8 w-8 text-primary dark:text-indigo-300" />
              <p className="mt-sm font-semibold">
                {selectedFileName || t("knowledge.selectTextFile")}
              </p>
              <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">
                {t("knowledge.textFileSupportHint")}
              </p>
            </label>

            <div className="grid gap-md md:grid-cols-2">
              <label className="text-sm font-semibold">
                {t("knowledge.code")}
                <input className="form-field mt-xs" value={form.code} onChange={(event) => setForm((previous) => ({ ...previous, code: event.target.value }))} />
              </label>
              <label className="text-sm font-semibold">
                {t("knowledge.titleField")}
                <input className="form-field mt-xs" value={form.title} onChange={(event) => setForm((previous) => ({ ...previous, title: event.target.value }))} />
              </label>
              <label className="text-sm font-semibold">
                {t("knowledge.category")}
                <input className="form-field mt-xs" value={form.category} onChange={(event) => setForm((previous) => ({ ...previous, category: event.target.value }))} />
              </label>
              <label className="text-sm font-semibold">
                {t("knowledge.scope")}
                <select className="form-field mt-xs" value={form.scope} onChange={(event) => setForm((previous) => ({ ...previous, scope: event.target.value }))}>
                  <option value="GLOBAL">{t("knowledge.scopeValue.GLOBAL")}</option>
                  <option value="WORKSPACE">{t("knowledge.scopeValue.WORKSPACE")}</option>
                </select>
              </label>
            </div>
            <label className="block text-sm font-semibold">
              {t("knowledge.shortDescriptionOptional")}
              <textarea className="form-field mt-xs min-h-24" maxLength={500} value={form.description} onChange={(event) => setForm((previous) => ({ ...previous, description: event.target.value }))} />
            </label>
            {selectedFile && (
              <div className="grid gap-sm rounded-xl border border-legal-border p-md text-sm dark:border-slate-700 sm:grid-cols-2">
                <p><span className="text-on-surface-variant">{t("common.file")}:</span> <strong>{selectedFile.name}</strong></p>
                <p><span className="text-on-surface-variant">{t("knowledge.contentType")}:</span> {selectedFile.type || "application/octet-stream"}</p>
                <p><span className="text-on-surface-variant">{t("knowledge.fileSize")}:</span> {fileSizeFormatter.format(selectedFile.size / 1024 / 1024)} MB</p>
                <p><span className="text-on-surface-variant">{t("documents.uploadedAt")}:</span> {new Date().toLocaleString(locale)}</p>
              </div>
            )}
            <div className="flex flex-wrap justify-end gap-sm border-t border-legal-border pt-lg dark:border-slate-700">
              <Button variant="secondary" onClick={() => { setForm(emptyForm); setSelectedFile(null); setSelectedFileName(""); }} disabled={saving}>{t("actions.cancel")}</Button>
              <Button variant="secondary" onClick={() => void handleUpload(false)} disabled={saving}>{saving ? t("knowledge.submitting") : t("knowledge.saveDraft")}</Button>
              <Button leftIcon={<Upload className="h-4 w-4" />} onClick={() => void handleUpload(true)} disabled={saving || !selectedFile}>
                {saving ? t("knowledge.submitting") : t("knowledge.startIngest")}
              </Button>
            </div>

            {activeJob && (
              <div className={`rounded-2xl border p-md ${activeJob.status === "FAILED" ? "border-error/40 bg-error/10" : activeJob.status === "INGESTED" ? "border-green-500/30 bg-green-500/10" : "border-primary/20 bg-primary/5 dark:border-indigo-400/20 dark:bg-indigo-950/20"}`}>
                <div className="flex items-center justify-between gap-md text-sm">
                  <div>
                    <p className="font-semibold">{t("knowledge.ingestProgress")}</p>
                    <p className="text-on-surface-variant dark:text-slate-400">
                      {activeJob.status === "PROCESSING" ? t("knowledge.ingestRunningInBackground") : activeJob.status === "INGESTED" ? t("knowledge.backgroundIngestSuccess") : t("knowledge.backgroundIngestFailed")}
                    </p>
                  </div>
                  <Badge tone={activeJob.status === "INGESTED" ? "green" : activeJob.status === "FAILED" ? "red" : "amber"}>
                    {translateEnum(activeJob.status, knowledgeStatusKeys)} · {activeJob.progressPercent ?? 0}%
                  </Badge>
                </div>
                <div className="mt-sm h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${activeJob.status === "FAILED" ? "bg-error" : activeJob.status === "INGESTED" ? "bg-green-500" : "bg-primary"}`}
                    style={{ width: `${Math.max(0, Math.min(100, activeJob.progressPercent ?? 0))}%` }}
                  />
                </div>
              </div>
            )}

            <div className="grid grid-cols-[1fr_auto_1fr_auto_1fr] items-center gap-xs text-center text-xs text-on-surface-variant dark:text-slate-400">
              <div><FileText className="mx-auto mb-xs h-4 w-4" /><span>{t("knowledge.lifecycleUpload")}</span></div>
              <ArrowRight className="h-4 w-4" />
              <div><span className="font-semibold text-on-surface dark:text-slate-200">2</span><br />{t("knowledge.ingest")}</div>
              <ArrowRight className="h-4 w-4" />
              <div><span className="font-semibold text-on-surface dark:text-slate-200">3</span><br />{t("knowledge.approvePublishStep")}</div>
            </div>
          </div>
        </Card>

        <Card title={t("knowledge.entries")}>
          <div className="mb-lg rounded-2xl border border-legal-border bg-surface-container-low p-md dark:border-slate-700 dark:bg-slate-900/60">
            <div className="mb-md flex items-center gap-sm">
              <SlidersHorizontal className="h-4 w-4 text-primary" />
              <h3 className="text-sm font-semibold">{t("knowledge.listTools")}</h3>
            </div>
            <form
              className="flex flex-col gap-sm sm:flex-row"
              onSubmit={(event) => {
                event.preventDefault();
                applySearch();
              }}
            >
              <label className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
                <input
                  className="form-field pl-10"
                  value={searchInput}
                  onChange={(event) => setSearchInput(event.target.value)}
                  placeholder={t("knowledge.listSearchPlaceholder")}
                  aria-label={t("knowledge.listSearchPlaceholder")}
                />
              </label>
              <Button type="submit" leftIcon={<Search className="h-4 w-4" />} disabled={loading}>
                {t("knowledge.searchButton")}
              </Button>
              <Button type="button" variant="secondary" leftIcon={<RotateCcw className="h-4 w-4" />} onClick={resetFilters} disabled={loading}>
                {t("knowledge.resetFilters")}
              </Button>
            </form>

            <div className="mt-md grid gap-sm sm:grid-cols-2 2xl:grid-cols-3">
              <label className="text-xs font-semibold text-on-surface-variant">
                {t("table.status")}
                <select className="form-field mt-xs" value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(0); }}>
                  <option value="">{t("knowledge.allStatuses")}</option>
                  {knowledgeStatuses.map((status) => <option key={status} value={status}>{translateEnum(status, knowledgeStatusKeys)}</option>)}
                </select>
              </label>
              <label className="text-xs font-semibold text-on-surface-variant">
                {t("knowledge.scope")}
                <select className="form-field mt-xs" value={scopeFilter} onChange={(event) => { setScopeFilter(event.target.value); setPage(0); }}>
                  <option value="">{t("knowledge.allScopes")}</option>
                  <option value="GLOBAL">{t("knowledge.scopeValue.GLOBAL")}</option>
                  <option value="WORKSPACE">{t("knowledge.scopeValue.WORKSPACE")}</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-on-surface-variant">
                {t("knowledge.category")}
                <select className="form-field mt-xs" value={categoryFilter} onChange={(event) => { setCategoryFilter(event.target.value); setPage(0); }}>
                  <option value="">{t("knowledge.allCategories")}</option>
                  <option value="LEGAL_SOURCE">{t("knowledge.categoryValue.LEGAL_SOURCE")}</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-on-surface-variant">
                {t("knowledge.ingestedDocuments.active")}
                <select className="form-field mt-xs" value={activeFilter} onChange={(event) => { setActiveFilter(event.target.value); setPage(0); }}>
                  <option value="">{t("knowledge.allActivity")}</option>
                  <option value="true">{t("knowledge.activeOnly")}</option>
                  <option value="false">{t("knowledge.inactiveOnly")}</option>
                </select>
              </label>
              <label className="text-xs font-semibold text-on-surface-variant">
                <span className="flex items-center gap-xs"><ArrowUpDown className="h-3.5 w-3.5" />{t("knowledge.primarySort")}</span>
                <select className="form-field mt-xs" value={primarySort} onChange={(event) => {
                  const nextPrimarySort = event.target.value;
                  setPrimarySort(nextPrimarySort);
                  if (sortField(nextPrimarySort) === sortField(secondarySort)) setSecondarySort("");
                  setPage(0);
                }}>
                  {sortValues.map((sort) => <option key={sort} value={sort}>{t(`knowledge.sort.${sort.replace(",", ".")}`)}</option>)}
                </select>
              </label>
              <label className="text-xs font-semibold text-on-surface-variant">
                <span className="flex items-center gap-xs"><ArrowUpDown className="h-3.5 w-3.5" />{t("knowledge.secondarySort")}</span>
                <select className="form-field mt-xs" value={secondarySort} onChange={(event) => { setSecondarySort(event.target.value); setPage(0); }}>
                  <option value="">{t("knowledge.noSecondarySort")}</option>
                  {sortValues.filter((sort) => sortField(sort) !== sortField(primarySort)).map((sort) => <option key={sort} value={sort}>{t(`knowledge.sort.${sort.replace(",", ".")}`)}</option>)}
                </select>
              </label>
            </div>
            <p className="mt-md text-xs text-on-surface-variant">{t("knowledge.filteredResults", { count: totalItems })}</p>
          </div>

          {error ? (
            <div role="alert" className="text-sm text-error">{error} <Button variant="secondary" onClick={() => void loadEntries()}>{t("common.retry")}</Button></div>
          ) : loading && entries.length === 0 ? (
            <p className="text-sm text-on-surface-variant dark:text-slate-400">{t("knowledge.loading")}</p>
          ) : (
            <DataTable columns={columns} data={entries} getRowKey={(entry) => entry.id} emptyMessage={t("knowledge.empty")} />
          )}
          <Pagination page={page} totalPages={totalPages} totalItems={totalItems} disabled={loading} onPageChange={(nextPage) => { setPage(nextPage); const next = new URLSearchParams(searchParams); next.set("page", toPageParam(nextPage)); setSearchParams(next); }} />
        </Card>
      </section>
    </div>
  );
}
