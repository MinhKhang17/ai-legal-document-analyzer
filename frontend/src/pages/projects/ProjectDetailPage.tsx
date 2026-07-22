import { Edit3, MessageSquareText, ShieldAlert } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { PageHeader } from "../../components/common/PageHeader";
import { ProgressBar } from "../../components/common/ProgressBar";
import { StatusBadge } from "../../components/common/StatusBadge";
import { getWorkspaceDetail, getWorkspaceDocuments } from "../../api/workspaceApi";
import { useI18n } from "../../hooks/useI18n";
import type { Document, Workspace } from "../../types/workspace";
import { formatDisplayDateTime, localeForLanguage } from "../../utils/format";

import { getAccessToken as getSessionAccessToken } from "../../services/authSession";
const getAccessToken = () => getSessionAccessToken() ?? "";

export function ProjectDetailPage() {
  const { id = "" } = useParams();
  const { t, language } = useI18n();
  const locale = localeForLanguage(language);
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    const loadWorkspace = async () => {
      try {
        setLoading(true);
        const [workspaceData, documentData] = await Promise.all([
          getWorkspaceDetail(getAccessToken(), id),
          getWorkspaceDocuments(getAccessToken(), id),
        ]);

        if (!active) return;
        setWorkspace(workspaceData);
        setDocuments(documentData);
      } catch {
        if (active) {
          setError(t("workspace.loadError"));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    if (id) {
      void loadWorkspace();
    }

    return () => {
      active = false;
    };
  }, [id, t]);

  const summary = useMemo(() => {
    const readyDocuments = documents.filter((document) => document.status === "ready").length;
    const processingDocuments = documents.filter((document) => document.status === "processing").length;
    const failedDocuments = documents.filter((document) => document.status === "failed").length;

    return {
      readyDocuments,
      processingDocuments,
      failedDocuments,
    };
  }, [documents]);

  const columns: DataTableColumn<Document>[] = [
    { header: t("common.file"), cell: (document) => document.originalFileName },
    { header: t("documents.type"), cell: (document) => document.fileType },
    { header: t("table.status"), cell: (document) => <StatusBadge status={document.status} /> },
    {
      header: t("table.date"),
      cell: (document) =>
        formatDisplayDateTime(document.uploadedAt, "-", locale),
    },
  ];

  const workspaceChatUrl = `/chat?workspaceId=${id}`;
  const workspaceUploadUrl = `/upload?workspaceId=${id}`;

  return (
    <div>
      <PageHeader
        title={workspace?.name ?? t("workspace.detailsTitle")}
        subtitle={workspace?.description ?? t("workspace.detailsSubtitle")}
        eyebrow={workspace ? `${workspace.workspaceId} · ${t(`status.${workspace.status}`)}` : t("workspace.title")}
        actions={
          <>
            <Link to={workspaceUploadUrl}>
              <Button variant="secondary" leftIcon={<Edit3 className="h-4 w-4" />}>
                {t("actions.uploadFile")}
              </Button>
            </Link>
            <Link to={workspaceChatUrl}>
              <Button leftIcon={<MessageSquareText className="h-4 w-4" />}>
                {t("actions.openChat")}
              </Button>
            </Link>
          </>
        }
      />

      {error && (
        <div role="alert" className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      {loading ? (
        <Card>
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("workspace.loading")}
          </p>
        </Card>
      ) : workspace ? (
        <>
          <Card className="mb-xl">
            <div className="grid gap-lg lg:grid-cols-[1fr_320px]">
              <div>
                <div className="flex flex-wrap gap-xs">
                  <Badge tone="blue">{workspace.workspaceId}</Badge>
                  <StatusBadge status={workspace.status} />
                </div>
                <div className="mt-lg grid gap-md sm:grid-cols-3">
                  <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                    <p className="label-uppercase">{t("nav.documents")}</p>
                    <p className="mt-xs text-2xl font-bold">{documents.length}</p>
                  </div>
                  <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                    <p className="label-uppercase">{t("status.ready")}</p>
                    <p className="mt-xs text-2xl font-bold text-emerald-600">
                      {summary.readyDocuments}
                    </p>
                  </div>
                  <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                    <p className="label-uppercase">{t("status.processing")}</p>
                    <p className="mt-xs text-2xl font-bold text-secondary">
                      {summary.processingDocuments}
                    </p>
                  </div>
                </div>
                <div className="mt-lg">
                  <div className="mb-sm flex items-center justify-between text-sm">
                    <span className="font-semibold">{t("workspace.health")}</span>
                    <span>{documents.length === 0 ? 0 : Math.round((summary.readyDocuments / documents.length) * 100)}%</span>
                  </div>
                  <ProgressBar
                    value={documents.length === 0 ? 0 : Math.round((summary.readyDocuments / documents.length) * 100)}
                  />
                </div>
              </div>
              <Card tone="ai" className="m-0">
                <div className="flex items-center gap-sm">
                  <ShieldAlert className="h-5 w-5 text-secondary dark:text-accent-gold" />
                  <h2 className="text-title-lg font-semibold">
                    {t("workspace.aiSummary")}
                  </h2>
                </div>
                <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
                  {documents.length > 0
                    ? t("workspace.summaryWithDocuments")
                    : t("workspace.summaryNoDocuments")}
                </p>
                <Link to={workspaceChatUrl}>
                  <Button className="mt-md" variant="gold">
                    {t("actions.openChat")}
                  </Button>
                </Link>
              </Card>
            </div>
          </Card>

          <Card title={t("workspace.documents")}>
            {documents.length === 0 ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("upload.noDocuments")}
              </p>
            ) : (
              <DataTable
                columns={columns}
                data={documents}
                getRowKey={(document) => document.documentId}
              />
            )}
          </Card>

          <div className="mt-xl">
            <Card title={t("workspace.quickActions")}>
              <div className="grid gap-sm sm:grid-cols-2">
                <Link
                  to={workspaceUploadUrl}
                  className="rounded-xl border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                >
                  <p className="font-semibold">{t("workspace.uploadAnotherFile")}</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    {t("workspace.uploadAnotherFileDescription")}
                  </p>
                </Link>
                <Link
                  to={workspaceChatUrl}
                  className="rounded-xl border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                >
                  <p className="font-semibold">{t("workspace.askAi")}</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    {t("workspace.askAiDescription")}
                  </p>
                </Link>
              </div>
            </Card>
          </div>
        </>
      ) : (
        <Card>
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("workspace.notFound")}
          </p>
        </Card>
      )}
    </div>
  );
}
