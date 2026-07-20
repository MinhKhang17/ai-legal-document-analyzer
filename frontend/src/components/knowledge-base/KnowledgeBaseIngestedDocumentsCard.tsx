import { ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Badge } from "../common/Badge";
import { Button } from "../common/Button";
import { Card } from "../common/Card";
import { EmptyState } from "../common/EmptyState";
import { useI18n } from "../../hooks/useI18n";
import { getKnowledgeBaseIngestedDocuments } from "../../services/knowledgeBase.service";
import type {
  KnowledgeBaseIngestedDocument,
  KnowledgeBaseIngestedDocumentVersion,
  PageResponse,
} from "../../types/knowledgeBase";
import { formatDisplayDate } from "../../utils/format";

interface KnowledgeBaseIngestedDocumentsCardProps {
  knowledgeBaseEntryId: string;
  title: string;
  documentCode: string;
}

const statusOptions = ["", "PENDING", "PROCESSING", "INGESTED", "FAILED"];
const visibilityOptions = ["", "PRIVATE", "PUBLIC"];

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

const getTone = (status?: string | null) => {
  if (!status) return "slate";
  if (status === "INGESTED") return "green";
  if (["PROCESSING", "REVIEWING"].includes(status)) return "amber";
  if (["FAILED", "ARCHIVED"].includes(status)) return "red";
  return "slate";
};

const getVisibilityTone = (visibility?: string | null) => {
  if (!visibility) return "slate";
  if (visibility === "PUBLIC") return "green";
  return "purple";
};

const getVersionLabel = (version: KnowledgeBaseIngestedDocumentVersion, fallback: string) =>
  version.versionLabel || fallback;

export function KnowledgeBaseIngestedDocumentsCard({
  knowledgeBaseEntryId,
  title,
  documentCode,
}: KnowledgeBaseIngestedDocumentsCardProps) {
  const { t, language } = useI18n();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const numberFormatter = new Intl.NumberFormat(locale);
  const translateEnum = (value: string | null | undefined, keys: Record<string, string>, fallback = "-") => {
    if (!value) return fallback;
    const key = keys[value];
    return key ? t(key) : value;
  };
  const [data, setData] = useState<PageResponse<KnowledgeBaseIngestedDocument> | null>(null);
  const [keyword, setKeyword] = useState("");
  const [ingestStatus, setIngestStatus] = useState("");
  const [visibility, setVisibility] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const loadDocuments = useCallback(async () => {
    if (!knowledgeBaseEntryId) {
      return;
    }

    setLoading(true);
    setError("");

    try {
      const response = await getKnowledgeBaseIngestedDocuments(knowledgeBaseEntryId, {
        keyword: keyword.trim() || undefined,
        ingestStatus: ingestStatus || undefined,
        visibility: visibility || undefined,
        page,
        size,
      });
      setData(response);
    } catch {
      const message = t("knowledge.ingestedDocuments.loadError");
      setError(message);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [ingestStatus, keyword, knowledgeBaseEntryId, page, size, t, visibility]);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  const resolvedItem = data?.items?.[0] ?? null;
  const versions = resolvedItem?.versions ?? [];
  const totalItems = data?.totalItems ?? 0;
  const totalPages = data?.totalPages ?? 0;
  const hasVersions = versions.length > 0;
  const pageLabel = useMemo(() => {
    if (totalItems === 0) {
      return t("knowledge.ingestedDocuments.zeroItems");
    }
    const start = page * size + 1;
    const end = Math.min((page + 1) * size, totalItems);
    return t("knowledge.ingestedDocuments.range", {
      start: numberFormatter.format(start),
      end: numberFormatter.format(end),
      total: numberFormatter.format(totalItems),
    });
  }, [numberFormatter, page, size, t, totalItems]);

  return (
    <Card
      title={t("knowledge.ingestedDocuments.title")}
      subtitle={t("knowledge.ingestedDocuments.subtitle")}
      actions={
        <Button
          variant="secondary"
          leftIcon={<RefreshCw className="h-4 w-4" />}
          onClick={() => void loadDocuments()}
          disabled={loading}
        >
          {t("common.refresh")}
        </Button>
      }
    >
      <div className="space-y-md">
        {error && (
          <p className="rounded-lg bg-error-container px-md py-sm text-sm font-semibold text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        )}

        <div className="grid gap-md md:grid-cols-2 xl:grid-cols-5">
          <label className="text-sm font-semibold xl:col-span-2">
            {t("knowledge.ingestedDocuments.keyword")}
            <input
              className="form-field mt-xs"
              value={keyword}
              onChange={(event) => {
                setPage(0);
                setKeyword(event.target.value);
              }}
              placeholder={t("knowledge.ingestedDocuments.searchPlaceholder")}
            />
          </label>
          <label className="text-sm font-semibold">
            {t("knowledge.ingestedDocuments.ingestStatus")}
            <select
              className="form-field mt-xs"
              value={ingestStatus}
              onChange={(event) => {
                setPage(0);
                setIngestStatus(event.target.value);
              }}
            >
              {statusOptions.map((option) => (
                <option key={option || "ALL"} value={option}>
                  {option ? translateEnum(option, knowledgeStatusKeys) : t("common.all")}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-semibold">
            {t("knowledge.ingestedDocuments.visibility")}
            <select
              className="form-field mt-xs"
              value={visibility}
              onChange={(event) => {
                setPage(0);
                setVisibility(event.target.value);
              }}
            >
              {visibilityOptions.map((option) => (
                <option key={option || "ALL"} value={option}>
                  {option ? translateEnum(option, knowledgeVisibilityKeys) : t("common.all")}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-semibold">
            {t("knowledge.ingestedDocuments.pageSize")}
            <select
              className="form-field mt-xs"
              value={size}
              onChange={(event) => {
                setPage(0);
                setSize(Number(event.target.value));
              }}
            >
              {[5, 10, 20].map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-sm rounded-xl bg-surface-container-low px-md py-sm dark:bg-slate-800/70">
          <div className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("knowledge.ingestedDocuments.knowledgeBase")}: <span className="font-semibold text-on-surface dark:text-slate-100">{title}</span>
            <span className="mx-sm">·</span>
            {t("knowledge.ingestedDocuments.code")}: <span className="font-semibold text-on-surface dark:text-slate-100">{documentCode}</span>
          </div>
          <div className="flex items-center gap-sm text-sm">
            <Badge tone="blue">{t("knowledge.ingestedDocuments.versions")} {numberFormatter.format(totalItems)}</Badge>
            <span className="text-on-surface-variant dark:text-slate-400">{pageLabel}</span>
          </div>
        </div>

        {loading ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("knowledge.ingestedDocuments.loading")}
          </p>
        ) : !hasVersions ? (
          <EmptyState
            title={t("knowledge.ingestedDocuments.emptyTitle")}
            description={t("knowledge.ingestedDocuments.emptyDescription")}
          />
        ) : (
          <div className="space-y-md">
            {resolvedItem && (
              <div className="rounded-xl border border-legal-border bg-white p-md dark:border-slate-700 dark:bg-slate-900">
                <div className="flex flex-wrap items-center justify-between gap-sm">
                  <div>
                    <p className="text-title-md font-semibold">{resolvedItem.title || title}</p>
                    <p className="text-sm text-on-surface-variant dark:text-slate-400">
                      {resolvedItem.legalDocumentId}
                    </p>
                  </div>
                  <Badge tone="gold">{resolvedItem.documentCode || documentCode}</Badge>
                </div>

                <div className="mt-md space-y-sm">
                  {versions.map((version) => (
                    <article key={version.versionId} className="rounded-xl border border-legal-border p-md dark:border-slate-700">
                      <div className="flex flex-wrap items-center justify-between gap-sm">
                        <div>
                          <p className="font-semibold">
                            {getVersionLabel(version, t("contracts.version"))} · {version.versionId}
                          </p>
                          <p className="text-xs text-on-surface-variant dark:text-slate-400">
                            {version.sourceFileId || "-"}
                          </p>
                        </div>
                        <div className="flex flex-wrap gap-xs">
                          <Badge tone={getTone(version.ingestStatus)}>{translateEnum(version.ingestStatus, knowledgeStatusKeys)}</Badge>
                          <Badge tone={getVisibilityTone(version.visibility)}>{translateEnum(version.visibility, knowledgeVisibilityKeys)}</Badge>
                          <Badge tone={version.active ? "green" : "slate"}>
                            {version.active ? t("admin.active") : t("admin.inactive")}
                          </Badge>
                        </div>
                      </div>

                      <dl className="mt-md grid gap-sm sm:grid-cols-2 xl:grid-cols-4">
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.effectiveFrom")}</dt>
                          <dd className="mt-xs">{formatDisplayDate(version.effectiveFrom, "-", locale)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.effectiveTo")}</dt>
                          <dd className="mt-xs">{formatDisplayDate(version.effectiveTo, "-", locale)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.chunkCount")}</dt>
                          <dd className="mt-xs font-semibold">{numberFormatter.format(version.chunkCount ?? 0)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.embeddedCount")}</dt>
                          <dd className="mt-xs font-semibold">{numberFormatter.format(version.embeddedCount ?? 0)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.sourceFile")}</dt>
                          <dd className="mt-xs break-all">{version.sourceFileId || "-"}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.contentHash")}</dt>
                          <dd className="mt-xs break-all">{version.contentHash || "-"}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.ingestedAt")}</dt>
                          <dd className="mt-xs">{formatDisplayDate(version.ingestedAt, "-", locale)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.versionLabel")}</dt>
                          <dd className="mt-xs">{version.versionLabel || "-"}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.publishedAt")}</dt>
                          <dd className="mt-xs">{formatDisplayDate(version.publishedAt, "-", locale)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{t("knowledge.ingestedDocuments.ingestedBy")}</dt>
                          <dd className="mt-xs">{version.ingestedById ?? "-"}</dd>
                        </div>
                      </dl>
                      {version.errorMessage && (
                        <div className="mt-md rounded-lg bg-error-container px-md py-sm text-sm text-risk-high-text dark:bg-red-950/40 dark:text-red-200">
                          <span className="font-semibold">{t("knowledge.ingestedDocuments.errorMessage")}:</span> {t("knowledge.backgroundIngestFailed")}
                        </div>
                      )}
                    </article>
                  ))}
                </div>

                <div className="mt-md flex flex-wrap items-center justify-between gap-sm">
                  <p className="text-sm text-on-surface-variant dark:text-slate-400">
                    {t("pagination.page")} {numberFormatter.format(page + 1)} / {numberFormatter.format(Math.max(totalPages, 1))}
                  </p>
                  <div className="flex gap-sm">
                    <Button
                      variant="secondary"
                      leftIcon={<ChevronLeft className="h-4 w-4" />}
                      disabled={page <= 0}
                      onClick={() => setPage((previous) => Math.max(previous - 1, 0))}
                    >
                      {t("pagination.previous")}
                    </Button>
                    <Button
                      variant="secondary"
                      rightIcon={<ChevronRight className="h-4 w-4" />}
                      disabled={(page + 1) >= totalPages}
                      onClick={() => setPage((previous) => previous + 1)}
                    >
                      {t("pagination.next")}
                    </Button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}
