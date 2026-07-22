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
  reviewKnowledgeBaseEntry,
  unpublishKnowledgeBaseEntry,
  downloadKnowledgeBaseSourceFile,
  ingestKnowledgeBaseEntry,
} from "../../services/knowledgeBase.service";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import type { KnowledgeBaseEntry, KnowledgeBaseVersion } from "../../types/knowledgeBase";
import { formatDisplayDate } from "../../utils/format";
import { canKnowledgeAction } from "../../utils/knowledgeLifecycle";

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

const knowledgeVisibilityKeys: Record<string, string> = {
  PRIVATE: "knowledge.visibility.PRIVATE",
  PUBLIC: "knowledge.visibility.PUBLIC",
};

const knowledgeScopeKeys: Record<string, string> = {
  GLOBAL: "knowledge.scopeValue.GLOBAL",
  WORKSPACE: "knowledge.scopeValue.WORKSPACE",
};

const knowledgeCategoryKeys: Record<string, string> = {
  LEGAL_SOURCE: "knowledge.categoryValue.LEGAL_SOURCE",
};

const knowledgeReviewDecisionKeys: Record<string, string> = {
  APPROVE: "knowledge.reviewDecision.APPROVE",
  REQUEST_CHANGES: "knowledge.reviewDecision.REQUEST_CHANGES",
  REJECT: "knowledge.reviewDecision.REJECT",
};

export function KnowledgeBaseDetailPage() {
  const { id = "" } = useParams();
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const fileSizeFormatter = new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  const translateEnum = (value: string | null | undefined, keys: Record<string, string>, fallback = "-") => {
    if (!value) return fallback;
    const key = keys[value];
    return key ? t(key) : value;
  };
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
    } catch {
      const message = t("knowledge.loadError");
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
    } catch {
      const message = t("knowledge.updateError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const retryIngest = async () => {
    if (!entry) return;
    setSaving(true);
    setError("");
    try {
      await ingestKnowledgeBaseEntry(entry.id, {
        requestId: `kb-retry-${Date.now()}`,
        jobPayload: JSON.stringify({ filename: versions[0]?.fileName ?? null, retry: true }),
      });
      toast.success(t("knowledge.retryIngestStarted"));
      await loadEntry();
    } catch {
      const message = t("knowledge.retryIngestError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const versionColumns: DataTableColumn<KnowledgeBaseVersion>[] = [
    { header: t("contracts.version"), cell: (version) => <span className="font-semibold">v{version.versionNo}</span> },
    { header: t("table.status"), cell: (version) => <Badge>{translateEnum(version.status, knowledgeStatusKeys, t("common.unknown"))}</Badge> },
    { header: t("knowledge.ingestedDocuments.ingestStatus"), cell: (version) => <Badge>{translateEnum(version.ingestStatus, knowledgeStatusKeys)}</Badge> },
    { header: t("knowledge.ingestedDocuments.visibility"), cell: (version) => <Badge>{translateEnum(version.visibility, knowledgeVisibilityKeys)}</Badge> },
    { header: t("knowledge.ingestedDocuments.active"), cell: (version) => version.active ? t("admin.active") : t("admin.inactive") },
    { header: t("knowledge.review"), cell: (version) => translateEnum(version.reviewDecision, knowledgeReviewDecisionKeys) },
    { header: t("contracts.created"), cell: (version) => formatDisplayDate(version.createdAt, "-", locale) },
    { header: t("knowledge.uploadedFile"), cell: (version) => version.sourceFileAvailable ? <Button size="sm" variant="secondary" leftIcon={<Download className="h-4 w-4" />} onClick={async () => { try { await downloadKnowledgeBaseSourceFile(id); } catch { toast.error(t("knowledge.downloadOriginalError"), t("toast.errorTitle")); } }}>{t("knowledge.downloadOriginal")}</Button> : "-" },
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
            <Card title={entry.title} subtitle={entry.code} actions={<Badge>{translateEnum(entry.currentStatus, knowledgeStatusKeys, t("common.unknown"))}</Badge>}>
              <dl className="grid gap-md text-sm md:grid-cols-2">
                <div><dt className="label-uppercase">{t("knowledge.category")}</dt><dd>{translateEnum(entry.category, knowledgeCategoryKeys)}</dd></div>
                <div><dt className="label-uppercase">{t("knowledge.scope")}</dt><dd>{translateEnum(entry.scope, knowledgeScopeKeys)}</dd></div>
                <div><dt className="label-uppercase">{t("contracts.version")}</dt><dd>{entry.currentVersionNo ?? "-"}</dd></div>
                <div><dt className="label-uppercase">{t("knowledge.ingestedDocuments.active")}</dt><dd>{entry.active ? t("admin.active") : t("admin.inactive")}</dd></div>
                <div><dt className="label-uppercase">{t("table.updated")}</dt><dd>{formatDisplayDate(entry.updatedAt, "-", locale)}</dd></div>
                <div><dt className="label-uppercase">{t("knowledge.fileName")}</dt><dd>{versions[0]?.fileName || "-"}</dd></div>
                <div><dt className="label-uppercase">{t("knowledge.contentType")}</dt><dd>{versions[0]?.contentType || "-"}</dd></div>
                <div><dt className="label-uppercase">{t("knowledge.fileSize")}</dt><dd>{versions[0]?.size != null ? `${fileSizeFormatter.format(versions[0].size / 1024 / 1024)} MB` : "-"}</dd></div>
                <div><dt className="label-uppercase">{t("documents.uploadedAt")}</dt><dd>{formatDisplayDate(versions[0]?.uploadedAt, "-", locale)}</dd></div>
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
                    <p className="font-semibold">{t("knowledge.ingestFailed")}</p>
                    <p className="mt-xs">{t("knowledge.unknownIngestError")}</p>
                    <Button className="mt-sm" variant="secondary" disabled={saving || !versions[0].sourceFileAvailable} onClick={() => void retryIngest()}>{t("knowledge.retryIngest")}</Button>
                  </div>
                )}
                {entry.currentStatus === "PUBLIC" && <p className="rounded-lg bg-emerald-500/10 p-sm text-sm font-semibold text-emerald-600">{t("knowledge.activeForAi")}</p>}
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
