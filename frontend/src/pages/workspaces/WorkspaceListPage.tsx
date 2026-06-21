import { FileText, FolderOpen, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getWorkspaces } from "../../api/workspaceApi";
import type { Workspace } from "../../api/workspaceApi";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function WorkspaceListPage() {
  const { t } = useI18n();
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadWorkspaces = async () => {
      try {
        setLoading(true);
        const data = await getWorkspaces(getAccessToken());
        setWorkspaces(data);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : t("workspace.loadFailed"),
        );
      } finally {
        setLoading(false);
      }
    };

    loadWorkspaces();
  }, [t]);

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        title={t("workspace.title")}
        subtitle={t("workspace.subtitle")}
        actions={
          <Link to="/upload">
            <Button variant="primary" rightIcon={<Plus className="h-4 w-4" />}>
              {t("workspace.createOrUpload")}
            </Button>
          </Link>
        }
      />

      <Card title={t("workspace.myWorkspaces")}>
        {loading && (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("workspace.loading")}
          </p>
        )}

        {error && (
          <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
            {error}
          </div>
        )}

        {!loading && !error && workspaces.length === 0 && (
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("workspace.empty")}
          </p>
        )}

        <div className="grid gap-md md:grid-cols-2 xl:grid-cols-3">
          {workspaces.map((workspace) => (
            <Link
              key={workspace.workspaceId}
              to={`/workspaces/${workspace.workspaceId}`}
              className="relative rounded-xl border border-legal-border p-md transition hover:border-primary hover:shadow-raised dark:border-slate-700"
            >
              <div className="mb-md flex items-start justify-between gap-md">
                <div className="flex items-center gap-sm">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary">
                    <FolderOpen className="h-5 w-5" />
                  </div>

                  <div>
                    <p className="font-semibold">{workspace.name}</p>
                    <p className="mt-1 flex items-center gap-xs text-xs text-on-surface-variant dark:text-slate-400">
                      <FileText className="h-3.5 w-3.5" />
                      {workspace.workspaceId}
                    </p>
                  </div>
                </div>
                <span className="absolute top-4 right-4 rounded-full bg-green-100 px-3 py-1 text-xs font-bold text-green-700">
                  {workspace.status}
                </span>{" "}
              </div>

              <p className="line-clamp-2 text-sm text-on-surface-variant dark:text-slate-400">
                {workspace.description || t("workspace.noDescription")}
              </p>

              <p className="mt-md text-xs text-on-surface-variant dark:text-slate-500">
                {new Date(workspace.createdAt).toLocaleString()}
              </p>
            </Link>
          ))}
        </div>
      </Card>
    </div>
  );
}
