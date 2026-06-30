import { ArrowLeft, Archive, CheckCircle2, RefreshCw, Send } from "lucide-react";
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
  ingestKnowledgeBaseEntry,
  publishKnowledgeBaseEntry,
  reviewKnowledgeBaseEntry,
} from "../../services/knowledgeBase.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { KnowledgeBaseEntry, KnowledgeBaseVersion } from "../../types/knowledgeBase";
import { formatDisplayDate } from "../../utils/format";

export function KnowledgeBaseDetailPage() {
  const { id = "" } = useParams();
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [entry, setEntry] = useState<KnowledgeBaseEntry | null>(null);
  const [versions, setVersions] = useState<KnowledgeBaseVersion[]>([]);
  const [requestId, setRequestId] = useState("");
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

  const versionColumns: DataTableColumn<KnowledgeBaseVersion>[] = [
    { header: t("contracts.version"), cell: (version) => <span className="font-semibold">v{version.versionNo}</span> },
    { header: t("table.status"), cell: (version) => <Badge>{version.status || t("common.unknown")}</Badge> },
    { header: t("knowledge.review"), cell: (version) => version.reviewDecision || "-" },
    { header: t("contracts.created"), cell: (version) => formatDisplayDate(version.createdAt, "-", locale) },
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
                <div><dt className="label-uppercase">{t("table.updated")}</dt><dd>{formatDisplayDate(entry.updatedAt, "-", locale)}</dd></div>
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
            <Card title={t("knowledge.ingest")}>
              <div className="space-y-md">
                <input className="form-field" value={requestId} onChange={(event) => setRequestId(event.target.value)} placeholder={t("chat.requestId")} />
                <Button
                  leftIcon={<Send className="h-4 w-4" />}
                  disabled={saving || !requestId.trim()}
                  onClick={() => void runAction(
                    () => ingestKnowledgeBaseEntry(id, { requestId: requestId.trim(), jobPayload: null }).then((job) => ({
                      id: job.knowledgeBaseVersionId,
                      knowledgeBaseEntryId: id,
                      versionNo: entry.currentVersionNo ?? 0,
                      sourceDocumentId: null,
                      rawContent: null,
                      extractedContent: null,
                      status: job.status,
                      reviewDecision: null,
                      reviewedById: null,
                      reviewedAt: null,
                      publishedById: null,
                      publishedAt: null,
                      archivedById: null,
                      archivedAt: null,
                      failedReason: job.errorMessage,
                      createdAt: job.createdAt,
                      updatedAt: job.createdAt,
                    })),
                    t("knowledge.ingestSuccess"),
                  )}
                >
                  {t("knowledge.ingest")}
                </Button>
              </div>
            </Card>

            <Card title={t("knowledge.reviewPublishArchive")}>
              <div className="space-y-md">
                <textarea className="form-field min-h-24" value={note} onChange={(event) => setNote(event.target.value)} placeholder={t("knowledge.noteReason")} />
                <div className="flex flex-col gap-sm">
                  <Button
                    variant="secondary"
                    leftIcon={<CheckCircle2 className="h-4 w-4" />}
                    disabled={saving}
                    onClick={() => void runAction(
                      () => reviewKnowledgeBaseEntry(id, { decision: "APPROVED", note: note.trim() || null }),
                      t("knowledge.reviewSuccess"),
                    )}
                  >
                    {t("knowledge.approve")}
                  </Button>
                  <Button
                    disabled={saving || !note.trim()}
                    onClick={() => void runAction(
                      () => publishKnowledgeBaseEntry(id, { note: note.trim() }),
                      t("knowledge.publishSuccess"),
                    )}
                  >
                    {t("knowledge.publish")}
                  </Button>
                  <Button
                    variant="danger"
                    leftIcon={<Archive className="h-4 w-4" />}
                    disabled={saving || !note.trim()}
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
