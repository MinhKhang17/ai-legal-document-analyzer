import { AlertTriangle, CheckCircle2, FileText, Plus, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import {
  createWorkspace,
  getWorkspaceDocuments,
  uploadDocument,
} from "../../api/workspaceApi";
import type { Document } from "../../api/workspaceApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { ProgressBar } from "../../components/common/ProgressBar";
import { StatusBadge } from "../../components/common/StatusBadge";
import { FileUploadZone } from "../../components/upload/FileUploadZone";
import { ProcessingTimeline } from "../../components/upload/ProcessingTimeline";
import { useI18n } from "../../hooks/useI18n";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function UploadPage() {
  const { t } = useI18n();

  const [workspaceId, setWorkspaceId] = useState("");
  const [workspaceName, setWorkspaceName] = useState("");
  const [workspaceDescription, setWorkspaceDescription] = useState("");

  const [documents, setDocuments] = useState<Document[]>([]);
  const [uploading, setUploading] = useState(false);
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);
  const [progress, setProgress] = useState(65);
  const [error, setError] = useState("");

  const hasProcessingDocument = documents.some(
    (document) => document.status === "processing",
  );

  useEffect(() => {
    if (!hasProcessingDocument) return undefined;

    const timer = window.setInterval(() => {
      setProgress((previous) => (previous >= 96 ? 65 : previous + 5));
    }, 900);

    return () => window.clearInterval(timer);
  }, [hasProcessingDocument]);

  useEffect(() => {
    if (!workspaceId || !hasProcessingDocument) return undefined;

    const timer = window.setInterval(async () => {
      try {
        const data = await getWorkspaceDocuments(getAccessToken(), workspaceId);
        setDocuments(data);
      } catch {
        // avoid UI spam while polling
      }
    }, 3000);

    return () => window.clearInterval(timer);
  }, [workspaceId, hasProcessingDocument]);

  const handleCreateWorkspace = async () => {
    setError("");

    if (!workspaceName.trim()) {
      setError(t("upload.workspaceNameRequired"));
      return "";
    }

    try {
      setCreatingWorkspace(true);

      const workspace = await createWorkspace(getAccessToken(), {
        name: workspaceName.trim(),
        description: workspaceDescription.trim(),
      });

      setWorkspaceId(workspace.workspaceId);
      return workspace.workspaceId;
    } catch (err) {
      setError(err instanceof Error ? err.message : t("upload.createWorkspaceFailed"));
      return "";
    } finally {
      setCreatingWorkspace(false);
    }
  };

  const ensureWorkspace = async () => {
    if (workspaceId) return workspaceId;
    return handleCreateWorkspace();
  };

  const handleUploadFile = async (file: File) => {
    setError("");

    try {
      setUploading(true);

      const currentWorkspaceId = await ensureWorkspace();

      if (!currentWorkspaceId) return;

      const uploadedDocument = await uploadDocument(
        getAccessToken(),
        currentWorkspaceId,
        file,
      );

      setDocuments((previous) => [uploadedDocument, ...previous]);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("upload.uploadFailed"));
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <PageHeader title={t("upload.title")} subtitle={t("upload.subtitle")} />

      <div className="grid gap-gutter xl:grid-cols-[1fr_340px]">
        <div className="space-y-gutter">
          <Card
            title={t("upload.step1")}
            actions={
              workspaceId ? (
                <Badge tone="gold">{t("upload.workspaceCreated")}</Badge>
              ) : (
                <Badge tone="gold">{t("upload.required")}</Badge>
              )
            }
          >
            <div className="space-y-md">
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("upload.workspaceHelp")}
              </p>

              <div className="grid gap-md md:grid-cols-2">
                <div>
                  <label className="label-uppercase" htmlFor="workspaceName">
                    {t("upload.workspaceName")}
                  </label>
                  <input
                    id="workspaceName"
                    className="form-field mt-xs"
                    placeholder={t("upload.workspaceNamePlaceholder")}
                    value={workspaceName}
                    onChange={(event) => setWorkspaceName(event.target.value)}
                    disabled={Boolean(workspaceId)}
                  />
                </div>

                <div>
                  <label className="label-uppercase" htmlFor="workspaceDescription">
                    {t("upload.workspaceDescription")}
                  </label>
                  <input
                    id="workspaceDescription"
                    className="form-field mt-xs"
                    placeholder={t("upload.workspaceDescriptionPlaceholder")}
                    value={workspaceDescription}
                    onChange={(event) => setWorkspaceDescription(event.target.value)}
                    disabled={Boolean(workspaceId)}
                  />
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-sm">
                <Button
                  variant="primary"
                  onClick={handleCreateWorkspace}
                  disabled={Boolean(workspaceId) || creatingWorkspace}
                >
                  {creatingWorkspace
                    ? t("upload.creatingWorkspace")
                    : t("upload.createWorkspace")}
                </Button>

                {workspaceId && (
                  <div className="flex items-center gap-xs rounded-lg bg-surface-container-low px-md py-sm text-sm dark:bg-slate-800">
                    <CheckCircle2 className="h-4 w-4 text-success" />
                    <span>
                      {t("upload.workspaceId")}:{" "}
                      <span className="font-semibold">{workspaceId}</span>
                    </span>
                  </div>
                )}
              </div>
            </div>
          </Card>

          <Card
            title={t("upload.step2")}
            actions={
              workspaceId ? (
                <Badge tone="gold">{t("upload.ready")}</Badge>
              ) : (
                <Badge tone="gold">{t("upload.needWorkspace")}</Badge>
              )
            }
          >
            <FileUploadZone onUpload={handleUploadFile} disabled={uploading} />

            {!workspaceId && (
              <p className="mt-md text-sm text-on-surface-variant dark:text-slate-400">
                {t("upload.autoCreateWorkspace")}
              </p>
            )}
          </Card>

          {error && (
            <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
              {error}
            </div>
          )}

          <Card
            title={t("upload.step3")}
            actions={<Badge tone="gold">{t("upload.estimatedTime")}</Badge>}
          >
            <div className="space-y-md">
              {documents.length === 0 && (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("upload.noDocuments")}
                </p>
              )}

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
                        <p className="font-semibold">{document.originalFileName}</p>
                        <p className="text-sm text-on-surface-variant dark:text-slate-400">
                          {(document.fileSize / 1024 / 1024).toFixed(2)} MB ·{" "}
                          {document.fileType}
                        </p>
                      </div>
                    </div>

                    <StatusBadge status={document.status} />
                  </div>

                  {document.status === "processing" && (
                    <div className="mt-md">
                      <div className="mb-xs flex items-center justify-between text-sm">
                        <span>{t("upload.extractingTextAndTables")}</span>
                        <span>{progress}%</span>
                      </div>
                      <ProgressBar value={progress} />
                    </div>
                  )}
                </div>
              ))}
            </div>
          </Card>
        </div>

        <aside className="space-y-gutter">
          <Card title={t("upload.pipeline")}>
            <ProcessingTimeline />
          </Card>

          <Card tone="ai">
            <div className="flex items-center gap-sm">
              <Sparkles className="h-5 w-5 text-secondary dark:text-accent-gold" />
              <h2 className="text-title-lg font-semibold">
                {t("upload.recentIntelligence")}
              </h2>
            </div>

            <div className="mt-md space-y-md text-sm">
              <div className="rounded-lg bg-white p-md dark:bg-slate-950">
                <p className="label-uppercase text-secondary dark:text-accent-gold">
                  {t("upload.aiSuggestion")}
                </p>
                <p className="mt-xs font-semibold">
                  {t("upload.aiSuggestionTitle")}
                </p>
                <p className="mt-xs text-on-surface-variant dark:text-slate-400">
                  {t("upload.aiSuggestionDescription")}
                </p>
              </div>

              <div className="flex items-start gap-sm text-on-surface-variant dark:text-slate-400">
                <AlertTriangle className="mt-0.5 h-4 w-4 text-error" />
                {t("upload.riskVariance")}
              </div>

              <Button variant="gold" rightIcon={<Plus className="h-4 w-4" />}>
                {t("actions.viewDetails")}
              </Button>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}