import { FileText, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  createWorkspace,
  getWorkspaceDocuments,
  getWorkspaces,
  uploadDocument,
} from "../../api/workspaceApi";
import type { Document, Workspace } from "../../api/workspaceApi";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { StatusBadge } from "../../components/common/StatusBadge";
import { FileUploadZone } from "../../components/upload/FileUploadZone";
import { ProcessingTimeline } from "../../components/upload/ProcessingTimeline";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function UploadPage() {
  const { t } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState(
    searchParams.get("workspaceId") ?? "",
  );

  const [workspaceName, setWorkspaceName] = useState("");
  const [workspaceDescription, setWorkspaceDescription] = useState("");

  const [documents, setDocuments] = useState<Document[]>([]);
  const [uploading, setUploading] = useState(false);
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);
  const [loadingWorkspaces, setLoadingWorkspaces] = useState(false);
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [error, setError] = useState("");

  const hasProcessingDocument = documents.some(
    (document) => document.status === "processing",
  );
  const selectedWorkspace = useMemo(
    () => workspaces.find((workspace) => workspace.workspaceId === selectedWorkspaceId),
    [selectedWorkspaceId, workspaces],
  );

  useEffect(() => {
    const loadWorkspaces = async () => {
      try {
        setLoadingWorkspaces(true);
        const data = await getWorkspaces(getAccessToken());
        setWorkspaces(data);

        const workspaceIdFromQuery = searchParams.get("workspaceId") ?? "";
        const matchedWorkspace =
          data.find((workspace) => workspace.workspaceId === workspaceIdFromQuery) ??
          data[0];

        if (matchedWorkspace) {
          setSelectedWorkspaceId(matchedWorkspace.workspaceId);
          if (workspaceIdFromQuery !== matchedWorkspace.workspaceId) {
            setSearchParams({ workspaceId: matchedWorkspace.workspaceId });
          }
        }
      } catch (err) {
        setError(
          err instanceof Error ? err.message : t("upload.loadWorkspacesFailed"),
        );
      } finally {
        setLoadingWorkspaces(false);
      }
    };

    loadWorkspaces();
  }, [searchParams, setSearchParams, t]);

  useEffect(() => {
    if (!selectedWorkspaceId) {
      setDocuments([]);
      return undefined;
    }

    const loadDocuments = async () => {
      try {
        setLoadingDocuments(true);
        const data = await getWorkspaceDocuments(
          getAccessToken(),
          selectedWorkspaceId,
        );
        setDocuments(data);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : t("upload.loadDocumentsFailed"),
        );
      } finally {
        setLoadingDocuments(false);
      }
    };

    loadDocuments();
  }, [selectedWorkspaceId, t]);

  useEffect(() => {
    if (!selectedWorkspaceId || !hasProcessingDocument) return undefined;

    const timer = window.setInterval(async () => {
      try {
        const data = await getWorkspaceDocuments(
          getAccessToken(),
          selectedWorkspaceId,
        );
        setDocuments(data);
      } catch {
        // avoid UI spam while polling
      }
    }, 3000);

    return () => window.clearInterval(timer);
  }, [selectedWorkspaceId, hasProcessingDocument]);

  const handleCreateWorkspace = async () => {
    setError("");

    if (!workspaceName.trim()) {
      setError(t("upload.workspaceNameRequired"));
      toast.warning(t("upload.workspaceNameRequired"), t("toast.warningTitle"));
      return;
    }

    try {
      setCreatingWorkspace(true);

      const workspace = await createWorkspace(getAccessToken(), {
        name: workspaceName.trim(),
        description: workspaceDescription.trim(),
      });

      setWorkspaces((previous) => [workspace, ...previous]);
      setSelectedWorkspaceId(workspace.workspaceId);
      setSearchParams({ workspaceId: workspace.workspaceId });
      setWorkspaceName("");
      setWorkspaceDescription("");
      toast.success(t("workspace.createSuccess"), t("toast.successTitle"));
    } catch (err) {
      const message =
        err instanceof Error ? err.message : t("workspace.createError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setCreatingWorkspace(false);
    }
  };

  const handleUploadFile = async (file: File) => {
    setError("");

    if (!selectedWorkspaceId) {
      setError(t("upload.selectWorkspaceRequired"));
      toast.warning(t("upload.selectWorkspaceRequired"), t("toast.warningTitle"));
      return;
    }

    try {
      setUploading(true);

      const uploadedDocument = await uploadDocument(
        getAccessToken(),
        selectedWorkspaceId,
        file,
      );

      setDocuments((previous) => [uploadedDocument, ...previous]);
      toast.success(t("workspace.uploadDocumentSuccess"), t("toast.successTitle"));
      void navigate(`/projects/${selectedWorkspaceId}`, { replace: false });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : t("workspace.uploadDocumentError");
      setError(message);
      toast.error(message, t("toast.errorTitle"));
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <PageHeader title={t("upload.title")} subtitle={t("upload.subtitle")} />

      <div className="grid gap-gutter xl:grid-cols-[1fr_340px]">
        <div className="space-y-gutter">
            <Card title={t("upload.createWorkspace")}>
              <div className="space-y-md">
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("upload.createWorkspaceHelp")}
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
                    />
                  </div>

                  <div>
                    <label
                      className="label-uppercase"
                      htmlFor="workspaceDescription"
                    >
                      {t("upload.workspaceDescription")}
                    </label>
                    <input
                      id="workspaceDescription"
                      className="form-field mt-xs"
                      placeholder={t("upload.workspaceDescriptionPlaceholder")}
                      value={workspaceDescription}
                      onChange={(event) =>
                        setWorkspaceDescription(event.target.value)
                      }
                    />
                  </div>
                </div>

                <Button
                  variant="primary"
                  onClick={handleCreateWorkspace}
                  disabled={creatingWorkspace}
                >
                  {creatingWorkspace
                    ? t("upload.creatingWorkspace")
                    : t("upload.createWorkspace")}
                </Button>
              </div>
            </Card>

          <Card title={t("upload.uploadDocument")}>
            <div className="space-y-md">
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("upload.workspaceHelp")}
              </p>
              <div>
                <label className="label-uppercase" htmlFor="workspaceSelect">
                  {t("upload.selectWorkspace")}
                </label>
                <select
                  id="workspaceSelect"
                  className="form-field mt-xs"
                  value={selectedWorkspaceId}
                  onChange={(event) =>
                    setSelectedWorkspaceId(event.target.value)
                  }
                  disabled={loadingWorkspaces || workspaces.length === 0}
                >
                  <option value="">
                    {loadingWorkspaces
                      ? t("upload.loadingWorkspaces")
                      : t("upload.selectWorkspacePlaceholder")}
                  </option>

                  {workspaces.map((workspace) => (
                    <option
                      key={workspace.workspaceId}
                      value={workspace.workspaceId}
                    >
                      {workspace.name}
                    </option>
                  ))}
                </select>
                {selectedWorkspaceId && (
                  <p className="mt-xs text-xs text-on-surface-variant dark:text-slate-400">
                    {selectedWorkspace?.description}
                  </p>
                )}{" "}
              </div>

              {selectedWorkspaceId && (
                <div className="flex flex-wrap gap-sm">
                  <Link to={`/projects/${selectedWorkspaceId}`}>
                    <Button variant="secondary">{t("actions.openWorkspace")}</Button>
                  </Link>
                  <Link to={`/chat?workspaceId=${selectedWorkspaceId}`}>
                    <Button variant="secondary">{t("actions.openChat")}</Button>
                  </Link>
                </div>
              )}

              <FileUploadZone
                onUpload={handleUploadFile}
                disabled={uploading || !selectedWorkspaceId}
              />

              {!selectedWorkspaceId && (
                <p className="mt-md text-sm text-on-surface-variant dark:text-slate-400">
                  {t("upload.selectWorkspaceRequired")}
                </p>
              )}
            </div>
          </Card>

          {error && (
            <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
              {error}
            </div>
          )}

          <Card
            title={t("upload.processingQueue")}
            actions={
              hasProcessingDocument ? (
                <Badge tone="gold">{t("upload.estimatedTime")}</Badge>
              ) : undefined
            }
          >
            <div className="space-y-md">
              {loadingDocuments && (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("upload.loadingDocuments")}
                </p>
              )}

              {documents.length === 0 && !loadingDocuments && (
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
                        <p className="font-semibold">
                          {document.originalFileName}
                        </p>
                        <p className="text-sm text-on-surface-variant dark:text-slate-400">
                          {(document.fileSize / 1024 / 1024).toFixed(2)} MB ·{" "}
                          {document.fileType}
                        </p>
                      </div>
                    </div>

                    <StatusBadge status={document.status} />
                  </div>

                  {document.status === "processing" && (
                    <div className="mt-md rounded-lg bg-surface-container-low p-sm text-sm text-on-surface-variant dark:bg-slate-800 dark:text-slate-400">
                      {t("upload.processingStatusDescription")}
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
                {t("upload.workspaceStatus")}
              </h2>
            </div>

            <div className="mt-md space-y-md text-sm">
              <p className="leading-6 text-on-surface-variant dark:text-slate-300">
                {selectedWorkspace
                  ? t("upload.workspaceStatusDescription")
                      .replace("{workspace}", selectedWorkspace.name)
                      .replace("{count}", String(documents.length))
                  : t("upload.selectWorkspaceForStatus")}
              </p>

              {selectedWorkspaceId && (
                <Link to={`/projects/${selectedWorkspaceId}`}>
                  <Button variant="gold">{t("actions.openWorkspace")}</Button>
                </Link>
              )}
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}
