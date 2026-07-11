import { RefreshCw, Upload } from "lucide-react";
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
  uploadKnowledgeBaseEntry,
} from "../../services/knowledgeBase.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import { useAppStore } from "../../store/AppStore";
import type { KnowledgeBaseEntry, UploadKnowledgeRequest } from "../../types/knowledgeBase";
import { formatDisplayDate } from "../../utils/format";

const emptyForm = {
  code: "",
  title: "",
  category: "LEGAL_SOURCE",
  scope: "GLOBAL",
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

  const handleUpload = async () => {
    if (!user?.id || !form.code.trim() || !form.title.trim() || !form.extractedContent.trim()) {
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
        extractedContent: form.extractedContent.trim(),
        rawContent: form.rawContent.trim() || null,
      };
      await uploadKnowledgeBaseEntry(payload);
      toast.success(t("knowledge.uploadSuccess"));
      setForm(emptyForm);
      await loadEntries();
    } catch (uploadError) {
      const message = uploadError instanceof Error ? uploadError.message : t("knowledge.uploadError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
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
    { header: t("table.status"), cell: (entry) => <Badge>{entry.currentStatus || t("common.unknown")}</Badge> },
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

      <section className="mb-xl grid gap-gutter xl:grid-cols-[0.85fr_1.15fr]">
        <Card title={t("knowledge.uploadEntry")} subtitle={t("knowledge.uploadEntrySubtitle")}>
          <div className="space-y-md">
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
              {t("knowledge.extractedContent")}
              <textarea className="form-field mt-xs min-h-40" value={form.extractedContent} onChange={(event) => setForm((previous) => ({ ...previous, extractedContent: event.target.value }))} />
            </label>
            <label className="block text-sm font-semibold">
              {t("knowledge.rawContent")}
              <textarea className="form-field mt-xs min-h-24" value={form.rawContent} onChange={(event) => setForm((previous) => ({ ...previous, rawContent: event.target.value }))} />
            </label>
            <Button leftIcon={<Upload className="h-4 w-4" />} onClick={() => void handleUpload()} disabled={saving}>
              {saving ? t("knowledge.uploading") : t("knowledge.upload")}
            </Button>
          </div>
        </Card>

        <Card title={t("knowledge.entries")} actions={<Badge tone="blue">{totalItems}</Badge>}>
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
