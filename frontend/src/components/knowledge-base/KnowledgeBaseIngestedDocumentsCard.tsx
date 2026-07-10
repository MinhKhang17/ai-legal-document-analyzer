import { ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
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

const statusOptions = ["", "UPLOADED", "PROCESSING", "INGESTED", "REVIEWING", "PUBLIC", "ARCHIVED", "FAILED"];
const visibilityOptions = ["", "CUSTOMER", "LAWYER", "ADMIN", "ALL_INTERNAL"];

const labelFor = (language: "en" | "vi", en: string, vi: string) => (language === "vi" ? vi : en);

const getTone = (status?: string | null) => {
  if (!status) return "slate";
  if (["INGESTED", "PUBLIC"].includes(status)) return "green";
  if (["PROCESSING", "REVIEWING"].includes(status)) return "amber";
  if (["FAILED", "ARCHIVED"].includes(status)) return "red";
  return "slate";
};

const getVisibilityTone = (visibility?: string | null) => {
  if (!visibility) return "slate";
  if (visibility === "ALL_INTERNAL") return "blue";
  if (visibility === "ADMIN") return "purple";
  if (visibility === "LAWYER") return "amber";
  return "green";
};

const getVersionLabel = (version: KnowledgeBaseIngestedDocumentVersion, language: "en" | "vi") =>
  version.versionLabel || (language === "vi" ? "Phiên bản" : "Version");

export function KnowledgeBaseIngestedDocumentsCard({
  knowledgeBaseEntryId,
  title,
  documentCode,
}: KnowledgeBaseIngestedDocumentsCardProps) {
  const { t, language } = useI18n();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [data, setData] = useState<PageResponse<KnowledgeBaseIngestedDocument> | null>(null);
  const [keyword, setKeyword] = useState("");
  const [ingestStatus, setIngestStatus] = useState("");
  const [visibility, setVisibility] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const loadDocuments = async () => {
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
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : (language === "vi" ? "Không thể tải danh sách tài liệu đã ingest." : "Unable to load ingested documents.");
      setError(message);
      setData(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadDocuments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [knowledgeBaseEntryId, keyword, ingestStatus, visibility, page, size]);

  const resolvedItem = data?.items?.[0] ?? null;
  const versions = resolvedItem?.versions ?? [];
  const totalItems = data?.totalItems ?? 0;
  const totalPages = data?.totalPages ?? 0;
  const hasVersions = versions.length > 0;
  const pageLabel = useMemo(() => {
    if (totalItems === 0) {
      return language === "vi" ? "0 mục" : "0 items";
    }
    const start = page * size + 1;
    const end = Math.min((page + 1) * size, totalItems);
    return language === "vi" ? `${start}-${end} / ${totalItems}` : `${start}-${end} of ${totalItems}`;
  }, [language, page, size, totalItems]);

  return (
    <Card
      title={labelFor(language, "Ingested documents", "Tài liệu đã ingest")}
      subtitle={labelFor(
        language,
        "Grouped by knowledge base document and enriched with AI ingest metadata.",
        "Nhóm theo tài liệu knowledge base và bổ sung metadata ingest từ AI.",
      )}
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
            {labelFor(language, "Keyword", "Từ khóa")}
            <input
              className="form-field mt-xs"
              value={keyword}
              onChange={(event) => {
                setPage(0);
                setKeyword(event.target.value);
              }}
              placeholder={labelFor(language, "Search title, code, source file...", "Tìm theo tiêu đề, mã, file nguồn...")}
            />
          </label>
          <label className="text-sm font-semibold">
            {labelFor(language, "Ingest status", "Trạng thái ingest")}
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
                  {option || labelFor(language, "All", "Tất cả")}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-semibold">
            {labelFor(language, "Visibility", "Hiển thị")}
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
                  {option || labelFor(language, "All", "Tất cả")}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-semibold">
            {labelFor(language, "Page size", "Kích thước trang")}
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
            {labelFor(language, "Knowledge base", "Knowledge base")}: <span className="font-semibold text-on-surface dark:text-slate-100">{title}</span>
            <span className="mx-sm">·</span>
            {labelFor(language, "Code", "Mã")}: <span className="font-semibold text-on-surface dark:text-slate-100">{documentCode}</span>
          </div>
          <div className="flex items-center gap-sm text-sm">
            <Badge tone="blue">{labelFor(language, "Versions", "Phiên bản")} {totalItems}</Badge>
            <span className="text-on-surface-variant dark:text-slate-400">{pageLabel}</span>
          </div>
        </div>

        {loading ? (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {labelFor(language, "Loading ingested documents...", "Đang tải tài liệu đã ingest...")}
          </p>
        ) : !hasVersions ? (
          <EmptyState
            title={labelFor(language, "No ingested documents found", "Chưa có tài liệu đã ingest")}
            description={labelFor(
              language,
              "Try adjusting filters or ingest the current knowledge base version.",
              "Hãy thử đổi bộ lọc hoặc ingest lại version hiện tại của knowledge base.",
            )}
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
                            {getVersionLabel(version, language)} · {version.versionId}
                          </p>
                          <p className="text-xs text-on-surface-variant dark:text-slate-400">
                            {version.sourceFileId || "-"}
                          </p>
                        </div>
                        <div className="flex flex-wrap gap-xs">
                          <Badge tone={getTone(version.ingestStatus)}>{version.ingestStatus || "-"}</Badge>
                          <Badge tone={getVisibilityTone(version.visibility)}>{version.visibility || "-"}</Badge>
                        </div>
                      </div>

                      <dl className="mt-md grid gap-sm sm:grid-cols-2 xl:grid-cols-4">
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Effective from", "Hiệu lực từ")}</dt>
                          <dd className="mt-xs">{formatDisplayDate(version.effectiveFrom, "-", locale)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Effective to", "Hiệu lực đến")}</dt>
                          <dd className="mt-xs">{formatDisplayDate(version.effectiveTo, "-", locale)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Chunk count", "Số chunk")}</dt>
                          <dd className="mt-xs font-semibold">{version.chunkCount ?? 0}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Embedded count", "Số embedding")}</dt>
                          <dd className="mt-xs font-semibold">{version.embeddedCount ?? 0}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Source file", "File nguồn")}</dt>
                          <dd className="mt-xs break-all">{version.sourceFileId || "-"}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Content hash", "Hash nội dung")}</dt>
                          <dd className="mt-xs break-all">{version.contentHash || "-"}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Ingested at", "Đã ingest lúc")}</dt>
                          <dd className="mt-xs">{formatDisplayDate(version.ingestedAt, "-", locale)}</dd>
                        </div>
                        <div>
                          <dt className="label-uppercase">{labelFor(language, "Version label", "Nhãn phiên bản")}</dt>
                          <dd className="mt-xs">{version.versionLabel || "-"}</dd>
                        </div>
                      </dl>
                    </article>
                  ))}
                </div>

                <div className="mt-md flex flex-wrap items-center justify-between gap-sm">
                  <p className="text-sm text-on-surface-variant dark:text-slate-400">
                    {labelFor(language, "Page", "Trang")} {page + 1} / {Math.max(totalPages, 1)}
                  </p>
                  <div className="flex gap-sm">
                    <Button
                      variant="secondary"
                      leftIcon={<ChevronLeft className="h-4 w-4" />}
                      disabled={page <= 0}
                      onClick={() => setPage((previous) => Math.max(previous - 1, 0))}
                    >
                      {labelFor(language, "Previous", "Trước")}
                    </Button>
                    <Button
                      variant="secondary"
                      rightIcon={<ChevronRight className="h-4 w-4" />}
                      disabled={(page + 1) >= totalPages}
                      onClick={() => setPage((previous) => previous + 1)}
                    >
                      {labelFor(language, "Next", "Sau")}
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
