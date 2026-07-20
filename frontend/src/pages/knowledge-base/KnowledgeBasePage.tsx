import { ArrowRight, FileText, RefreshCw, ShieldCheck, Upload, UploadCloud } from "lucide-react";
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

export function KnowledgeBasePage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { user } = useAppStore();
  const [searchParams, setSearchParams] = useSearchParams();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [entries, setEntries] = useState<KnowledgeBaseEntry[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(() => parsePageParam(searchParams.get("page")));
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
      const response = await getKnowledgeBaseEntries(page, 20);
      setEntries(response.items ?? []);
      setTotalItems(response.totalItems ?? 0);
      setTotalPages(response.totalPages ?? 0);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : t("knowledge.loadError");
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [page, t]);

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
          toast.error(job.errorMessage || t("knowledge.backgroundIngestFailed"));
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
      toast.success(startIngest ? t("knowledge.backgroundIngestStarted") : (language === "vi" ? "Đã lưu bản nháp riêng tư." : "Private draft saved."));
      setForm(emptyForm);
      setSelectedFileName("");
      setSelectedFile(null);
      await loadEntries();
    } catch (uploadError) {
      const message = uploadError instanceof Error ? uploadError.message : t("knowledge.uploadError");
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
      const safePendingContent = content || `[Tệp nguồn chờ AI trích xuất: ${file.name}]`;
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
    { header: t("knowledge.category"), cell: (entry) => entry.category },
    { header: t("knowledge.scope"), cell: (entry) => <Badge>{entry.scope}</Badge> },
    { header: t("table.status"), cell: (entry) => <div><Badge>{entry.currentStatus === "UPLOADED" ? "PENDING" : entry.currentStatus || t("common.unknown")}</Badge>{!entry.active && <p className="mt-xs text-xs text-on-surface-variant">Chưa đưa vào retrieval</p>}</div> },
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

      <section className="mb-xl grid gap-gutter xl:grid-cols-[1.35fr_0.65fr]">
        <Card title={t("knowledge.uploadEntry")} subtitle={t("knowledge.uploadEntrySubtitle")}>
          <div className="space-y-md">
            <div className="rounded-2xl border border-primary/20 bg-primary/5 p-md dark:border-indigo-400/20 dark:bg-indigo-950/20">
              <div className="flex items-start gap-md">
                <span className="rounded-xl bg-primary/10 p-sm text-primary dark:bg-indigo-400/10 dark:text-indigo-300">
                  <ShieldCheck className="h-5 w-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{t("knowledge.newDocumentDefaults")}</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    {t("knowledge.newDocumentDefaultsDescription")}
                  </p>
                  <div className="mt-sm flex flex-wrap gap-xs">
                    <Badge tone="amber">PENDING</Badge>
                    <Badge tone="purple">PRIVATE</Badge>
                    <Badge tone="slate">INACTIVE</Badge>
                  </div>
                </div>
              </div>
            </div>

            <label className="block cursor-pointer rounded-2xl border-2 border-dashed border-legal-border bg-surface-container-low p-lg text-center transition hover:border-primary/60 hover:bg-primary/5 dark:border-slate-700 dark:bg-slate-900/60">
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
                  <option value="GLOBAL">GLOBAL</option>
                  <option value="WORKSPACE">WORKSPACE</option>
                </select>
              </label>
            </div>
            <label className="block text-sm font-semibold">
              {language === "vi" ? "Mô tả ngắn (tùy chọn)" : "Short description (optional)"}
              <textarea className="form-field mt-xs min-h-24" maxLength={500} value={form.description} onChange={(event) => setForm((previous) => ({ ...previous, description: event.target.value }))} />
            </label>
            {selectedFile && (
              <div className="grid gap-sm rounded-xl border border-legal-border p-md text-sm dark:border-slate-700 sm:grid-cols-2">
                <p><span className="text-on-surface-variant">File:</span> <strong>{selectedFile.name}</strong></p>
                <p><span className="text-on-surface-variant">Content type:</span> {selectedFile.type || "application/octet-stream"}</p>
                <p><span className="text-on-surface-variant">Dung lượng:</span> {(selectedFile.size / 1024 / 1024).toFixed(2)} MB</p>
                <p><span className="text-on-surface-variant">Uploaded at:</span> {new Date().toLocaleString(locale)}</p>
              </div>
            )}
            <div className="flex flex-wrap justify-end gap-sm border-t border-legal-border pt-md dark:border-slate-700">
              <Button variant="secondary" onClick={() => { setForm(emptyForm); setSelectedFile(null); setSelectedFileName(""); }} disabled={saving}>Hủy</Button>
              <Button variant="secondary" onClick={() => void handleUpload(false)} disabled={saving}>{saving ? (language === "vi" ? "Đang gửi tới backend..." : "Submitting to backend...") : "Lưu nháp"}</Button>
              <Button leftIcon={<Upload className="h-4 w-4" />} onClick={() => void handleUpload(true)} disabled={saving || !selectedFile}>
                {saving ? (language === "vi" ? "Đang gửi tới backend..." : "Submitting to backend...") : "Bắt đầu ingest"}
              </Button>
            </div>

            {activeJob && (
              <div className={`rounded-2xl border p-md ${activeJob.status === "FAILED" ? "border-error/40 bg-error/10" : activeJob.status === "INGESTED" ? "border-green-500/30 bg-green-500/10" : "border-primary/20 bg-primary/5 dark:border-indigo-400/20 dark:bg-indigo-950/20"}`}>
                <div className="flex items-center justify-between gap-md text-sm">
                  <div>
                    <p className="font-semibold">{t("knowledge.ingestProgress")}</p>
                    <p className="text-on-surface-variant dark:text-slate-400">
                      {activeJob.status === "PROCESSING" ? t("knowledge.ingestRunningInBackground") : activeJob.status === "INGESTED" ? t("knowledge.backgroundIngestSuccess") : activeJob.errorMessage || t("knowledge.backgroundIngestFailed")}
                    </p>
                  </div>
                  <Badge tone={activeJob.status === "INGESTED" ? "green" : activeJob.status === "FAILED" ? "red" : "amber"}>
                    {activeJob.status} · {activeJob.progressPercent ?? 0}%
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
              <div><span className="font-semibold text-on-surface dark:text-slate-200">3</span><br />{language === "vi" ? "Duyệt & công khai" : "Approve & publish"}</div>
            </div>
          </div>
        </Card>

        <Card title={t("knowledge.entries")} actions={<Badge tone="blue">{totalItems}</Badge>}>
          <div className="mb-md space-y-md rounded-2xl border border-primary/20 bg-primary/5 p-md dark:border-indigo-400/20 dark:bg-indigo-950/20">
            <h3 className="font-semibold">Hướng dẫn nhập tài liệu</h3>
            <ul className="space-y-xs text-sm text-on-surface-variant dark:text-slate-300">
              <li>✓ PDF, DOC, DOCX, TXT, Markdown, CSV, JSON</li>
              <li>✓ AI xử lý nền; bạn có thể rời trang</li>
              <li>✓ Khi admin duyệt, tài liệu được công khai và dùng cho RAG ngay</li>
              <li>✓ Có thể tải lại file gốc sau khi upload</li>
            </ul>
            <div className="flex flex-wrap gap-xs"><Badge tone="amber">PENDING</Badge><Badge tone="amber">PROCESSING</Badge><Badge tone="green">INGESTED</Badge><Badge tone="purple">REVIEWING</Badge><Badge tone="green">PUBLIC</Badge><Badge tone="red">FAILED</Badge></div>
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
