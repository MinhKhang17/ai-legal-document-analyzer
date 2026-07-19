import { ArrowLeft, Archive, CheckCircle2, Download, EyeOff, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { KnowledgeBaseIngestedDocumentsCard } from "../../components/knowledge-base/KnowledgeBaseIngestedDocumentsCard";
import {
  archiveKnowledgeBaseEntry,
  getKnowledgeBaseEntry,
  getKnowledgeBaseVersions,
  publishKnowledgeBaseEntry,
  reviewKnowledgeBaseEntry,
  unpublishKnowledgeBaseEntry,
  downloadKnowledgeBaseSourceFile,
  getKnowledgeBaseSourceFile,
  ingestKnowledgeBaseEntry,
  ingestKnowledgeBaseFile,
  failKnowledgeIngestionJob,
} from "../../services/knowledgeBase.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { KnowledgeBaseEntry, KnowledgeBaseVersion } from "../../types/knowledgeBase";
import { formatDisplayDate } from "../../utils/format";
import { canKnowledgeAction } from "../../utils/knowledgeLifecycle";
import { downloadStaffDocument } from "../../services/legalTicket.service";
import { useAppStore } from "../../store/AppStore";

export function KnowledgeBaseDetailPage() {
  const { id = "" } = useParams();
  const { t, language } = useI18n();
  const toast = useToast();
  const { user } = useAppStore();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [entry, setEntry] = useState<KnowledgeBaseEntry | null>(null);
  const [versions, setVersions] = useState<KnowledgeBaseVersion[]>([]);
  const [note, setNote] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const loadEntry = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError("");

    try {
      const [entryData, versionData] = await Promise.all([
        getKnowledgeBaseEntry(id),
        getKnowledgeBaseVersions(id),
      ]);
      setEntry(entryData);
      setVersions(versionData);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : t("knowledge.loadError");
      setError(message);
      setEntry(null);
      setVersions([]);
    } finally {
      setLoading(false);
    }
  }, [id, t]);

  useEffect(() => {
    void loadEntry();
  }, [loadEntry]);

  const runAction = async (action: () => Promise<KnowledgeBaseVersion>, successMessage: string) => {
    setSaving(true);
    setError("");

    try {
      await action();
      toast.success(successMessage);
      await loadEntry();
    } catch (actionError) {
      const message = actionError instanceof Error ? actionError.message : t("knowledge.updateError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const retryIngest = async () => {
    if (!entry || !user?.id) return;
    setSaving(true);
    setError("");
    let jobId: string | null = null;
    try {
      const file = await getKnowledgeBaseSourceFile(entry.id);
      const job = await ingestKnowledgeBaseEntry(entry.id, { requestId: `kb-retry-${Date.now()}`, jobPayload: JSON.stringify({ filename: file.name, retry: true }) });
      jobId = job.id;
      await ingestKnowledgeBaseFile(file, entry.id, entry.title, user.id, job.id);
      toast.success(language === "vi" ? "Đã bắt đầu ingest lại ở chế độ nền." : "Background retry started.");
      await loadEntry();
    } catch (retryError) {
      const message = retryError instanceof Error ? retryError.message : "Unable to retry ingest";
      if (jobId) { try { await failKnowledgeIngestionJob(jobId, message); } catch { /* keep original error */ } }
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const versionColumns: DataTableColumn<KnowledgeBaseVersion>[] = [
    { header: t("contracts.version"), cell: (version) => <span className="font-semibold">v{version.versionNo}</span> },
    { header: t("table.status"), cell: (version) => <Badge>{version.status || t("common.unknown")}</Badge> },
    { header: t("knowledge.ingestedDocuments.ingestStatus"), cell: (version) => <Badge>{version.ingestStatus || "-"}</Badge> },
    { header: t("knowledge.ingestedDocuments.visibility"), cell: (version) => <Badge>{version.visibility || "-"}</Badge> },
    { header: t("knowledge.ingestedDocuments.active"), cell: (version) => version.active ? t("admin.active") : t("admin.inactive") },
    { header: t("knowledge.review"), cell: (version) => version.reviewDecision || "-" },
    { header: t("contracts.created"), cell: (version) => formatDisplayDate(version.createdAt, "-", locale) },
    { header: language === "vi" ? "File đã upload" : "Uploaded file", cell: (version) => version.sourceFileAvailable ? <Button size="sm" variant="secondary" leftIcon={<Download className="h-4 w-4" />} onClick={async () => { try { await downloadKnowledgeBaseSourceFile(id); } catch (downloadError) { toast.error(downloadError instanceof Error ? downloadError.message : "Unable to download original file"); } }}>{language === "vi" ? "Tải file gốc" : "Download original"}</Button> : "-" },
    { header: t("table.actions"), cell: (version) => version.sourceDocumentId ? <Button size="sm" variant="secondary" leftIcon={<Download className="h-4 w-4" />} onClick={async () => { try { const url = await downloadStaffDocument(version.sourceDocumentId!); const anchor = document.createElement("a"); anchor.href = url; anchor.download = `${entry?.code ?? "knowledge"}-v${version.versionNo}`; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000); } catch (downloadError) { toast.error(downloadError instanceof Error ? downloadError.message : "Unable to download original file"); } }}>{language === "vi" ? "Tải file gốc" : "Download original"}</Button> : "-" },
  ];

  return (
    <div>
      <PageHeader
        title={t("knowledgeDetail.title")}
        subtitle={id}
        actions={
          <>
            <Link to="/knowledge-base">
              <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>{t("nav.knowledgeBase")}</Button>
            </Link>
            <Button variant="secondary" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void loadEntry()} disabled={loading}>
              {t("common.refresh")}
            </Button>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      {loading ? (
        <Card>{t("knowledge.loading")}</Card>
      ) : !entry ? (
        <EmptyState title={t("knowledge.notFound")} description={t("knowledge.notFoundDescription")} />
      ) : (
        <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_360px]">
          <main className="space-y-gutter">
            <Card title={entry.title} subtitle={entry.code} actions={<Badge>{entry.currentStatus || t("common.unknown")}</Badge>}>
              <dl className="grid gap-md text-sm md:grid-cols-2">
                <div><dt className="label-uppercase">{t("knowledge.category")}</dt><dd>{entry.category}</dd></div>
                <div><dt className="label-uppercase">{t("knowledge.scope")}</dt><dd>{entry.scope}</dd></div>
                <div><dt className="label-uppercase">{t("contracts.version")}</dt><dd>{entry.currentVersionNo ?? "-"}</dd></div>
                <div><dt className="label-uppercase">{t("knowledge.ingestedDocuments.active")}</dt><dd>{entry.active ? t("admin.active") : t("admin.inactive")}</dd></div>
                <div><dt className="label-uppercase">{t("table.updated")}</dt><dd>{formatDisplayDate(entry.updatedAt, "-", locale)}</dd></div>
                <div><dt className="label-uppercase">File name</dt><dd>{versions[0]?.fileName || "-"}</dd></div>
                <div><dt className="label-uppercase">Content type</dt><dd>{versions[0]?.contentType || "-"}</dd></div>
                <div><dt className="label-uppercase">Size</dt><dd>{versions[0]?.size != null ? `${(versions[0].size! / 1024 / 1024).toFixed(2)} MB` : "-"}</dd></div>
                <div><dt className="label-uppercase">Uploaded at</dt><dd>{formatDisplayDate(versions[0]?.uploadedAt, "-", locale)}</dd></div>
              </dl>
            </Card>

            <KnowledgeBaseIngestedDocumentsCard
              knowledgeBaseEntryId={entry.id}
              title={entry.title}
              documentCode={entry.code}
            />

            <Card title={t("knowledge.versions")}>
              <DataTable columns={versionColumns} data={versions} getRowKey={(version) => version.id} emptyMessage={t("contracts.noVersions")} />
            </Card>

            {versions[0] && (
              <Card title={t("knowledge.latestExtractedContent")}>
                <p className="whitespace-pre-line text-sm leading-6 text-on-surface-variant dark:text-slate-400">
                  {versions[0].extractedContent || versions[0].rawContent || t("knowledge.noContent")}
                </p>
              </Card>
            )}
          </main>

          <aside className="space-y-gutter">
            <Card title={t("knowledge.reviewPublishArchive")}>
              <div className="space-y-md">
                {versions[0]?.ingestStatus === "FAILED" && (
                  <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
                    <p className="font-semibold">Ingest thất bại</p>
                    <p className="mt-xs">{versions[0].errorMessage || versions[0].failedReason || "Unknown ingest error"}</p>
                    <Button className="mt-sm" variant="secondary" disabled={saving || !versions[0].sourceFileAvailable} onClick={() => void retryIngest()}>Retry ingest</Button>
                  </div>
                )}
                {entry.currentStatus === "PUBLIC" ? <p className="rounded-lg bg-emerald-500/10 p-sm text-sm font-semibold text-emerald-600">Đang được AI sử dụng</p> : <p className="rounded-lg bg-slate-500/10 p-sm text-sm text-on-surface-variant">PRIVATE / inactive · Chưa đưa vào retrieval</p>}
                <textarea className="form-field min-h-24" value={note} onChange={(event) => setNote(event.target.value)} placeholder={t("knowledge.noteReason")} />
                <div className="flex flex-col gap-sm">
                  <Button
                    variant="secondary"
                    leftIcon={<CheckCircle2 className="h-4 w-4" />}
                    disabled={saving || !canKnowledgeAction(entry.currentStatus, 'REVIEW')}
                    onClick={() => void runAction(
                      () => reviewKnowledgeBaseEntry(id, { decision: "APPROVE", note: note.trim() || null }),
                      t("knowledge.reviewSuccess"),
                    )}
                  >
                    {t("knowledge.approve")}
                  </Button>
                  <Button
                    disabled={saving || !note.trim() || !canKnowledgeAction(entry.currentStatus, 'PUBLISH')}
                    onClick={() => void runAction(
                      () => publishKnowledgeBaseEntry(id, { note: note.trim() }),
                      t("knowledge.publishSuccess"),
                    )}
                  >
                    {t("knowledge.publish")}
                  </Button>
                  <Button
                    variant="secondary"
                    leftIcon={<EyeOff className="h-4 w-4" />}
                    disabled={saving || !canKnowledgeAction(entry.currentStatus, 'UNPUBLISH')}
                    onClick={() => void runAction(
                      () => unpublishKnowledgeBaseEntry(id),
                      t("knowledge.unpublishSuccess"),
                    )}
                  >
                    {t("knowledge.unpublish")}
                  </Button>
                  <Button
                    variant="danger"
                    leftIcon={<Archive className="h-4 w-4" />}
                    disabled={saving || !note.trim() || !canKnowledgeAction(entry.currentStatus, 'ARCHIVE')}
                    onClick={() => void runAction(
                      () => archiveKnowledgeBaseEntry(id, { reason: note.trim() }),
                      t("knowledge.archiveSuccess"),
                    )}
                  >
                    {t("knowledge.archive")}
                  </Button>
                </div>
              </div>
            </Card>
          </aside>
        </div>
      )}
    </div>
  );
}
