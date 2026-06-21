import { ArrowLeft, FileText, MessageSquare } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  getWorkspaceDetail,
  getWorkspaceDocuments,
} from "../../api/workspaceApi";
import type { Document, Workspace } from "../../api/workspaceApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { StatusBadge } from "../../components/common/StatusBadge";
import { useI18n } from "../../hooks/useI18n";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function WorkspaceDetailPage() {
  const { t } = useI18n();
  const { workspaceId } = useParams<{ workspaceId: string }>();

  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!workspaceId) return;

    const loadDetail = async () => {
      try {
        setLoading(true);

        const [workspaceData, documentData] = await Promise.all([
          getWorkspaceDetail(getAccessToken(), workspaceId),
          getWorkspaceDocuments(getAccessToken(), workspaceId),
        ]);

        setWorkspace(workspaceData);
        setDocuments(documentData);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : t("workspace.detailLoadFailed"),
        );
      } finally {
        setLoading(false);
      }
    };

    loadDetail();
  }, [workspaceId, t]);

  if (loading) {
    return (
      <div className="mx-auto max-w-6xl">
        <p className="text-sm text-on-surface-variant dark:text-slate-400">
          {t("workspace.loadingDetail")}
        </p>
      </div>
    );
  }

  if (error || !workspace) {
    return (
      <div className="mx-auto max-w-6xl">
        <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error || t("workspace.notFound")}
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        title={workspace.name}
        subtitle={workspace.description || t("workspace.noDescription")}
        actions={
          <div className="flex gap-sm">
            <Link to="/workspaces">
              <Button
                variant="secondary"
                leftIcon={<ArrowLeft className="h-4 w-4" />}
              >
                {t("workspace.back")}
              </Button>
            </Link>

            <Link to={`/workspaces/${workspace.workspaceId}/chat-sessions`}>
              <Button
                variant="primary"
                rightIcon={<MessageSquare className="h-4 w-4" />}
              >
                {t("chatSession.title")}
              </Button>
            </Link>
          </div>
        }
      />

      <div className="grid gap-gutter lg:grid-cols-[320px_1fr]">
        <Card title={t("workspace.detail")}>
          <div className="space-y-md text-sm">
            <div>
              <p className="label-uppercase">{t("workspace.id")}</p>
              <p className="mt-xs break-all font-semibold">
                {workspace.workspaceId}
              </p>
            </div>

            <div>
              <p className="label-uppercase">{t("workspace.status")}</p>
              <div className="mt-xs">
                <StatusBadge status={workspace.status} />
              </div>
            </div>

            <div>
              <p className="label-uppercase">{t("workspace.createdAt")}</p>
              <p className="mt-xs">
                {new Date(workspace.createdAt).toLocaleString()}
              </p>
            </div>

            <div>
              <Badge tone="gold">
                {documents.length} {t("workspace.documents")}
              </Badge>
            </div>
          </div>
        </Card>

        <Card title={t("workspace.documents")}>
          {documents.length === 0 && (
            <p className="text-sm text-on-surface-variant dark:text-slate-400">
              {t("workspace.noDocuments")}
            </p>
          )}

          <div className="space-y-md">
            {documents.map((document) => (
              <div
                key={document.documentId}
                className="rounded-xl border border-legal-border p-md dark:border-slate-700"
              >
                <div className="flex items-start justify-between gap-md">
                  <div className="flex items-start gap-md">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                      <FileText className="h-5 w-5" />
                    </div>

                    <div>
                      <p className="font-semibold">
                        {document.originalFileName}
                      </p>
                      <p className="text-sm text-on-surface-variant dark:text-slate-400">
                        {(document.fileSize / 1024 / 1024).toFixed(2)} MB ·{" "}
                        {document.fileType}
                      </p>
                      <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-500">
                        {new Date(document.uploadedAt).toLocaleString()}
                      </p>
                    </div>
                  </div>

                  <StatusBadge status={document.status} />
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}