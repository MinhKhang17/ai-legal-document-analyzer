import { Grid2X2, List, Plus, Search, SlidersHorizontal } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { PageHeader } from "../../components/common/PageHeader";
import { SearchInput } from "../../components/common/SearchInput";
import { StatusBadge } from "../../components/common/StatusBadge";
import { getWorkspaces } from "../../api/workspaceApi";
import { useI18n } from "../../hooks/useI18n";
import type { Workspace } from "../../types/workspace";

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function ProjectsPage() {
  const { t, language } = useI18n();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"all" | "active">("all");
  const [view, setView] = useState<"grid" | "list">("grid");
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let isMounted = true;

    const loadWorkspaces = async () => {
      try {
        setLoading(true);
        const data = (await getWorkspaces(getAccessToken())).filter(
          (ws) => ws.description !== "System workspace for general contract assistant chat"
        );
        if (isMounted) {
          setWorkspaces(data);
        }
      } catch (err) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : t("workspace.loadError"));
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    void loadWorkspaces();

    return () => {
      isMounted = false;
    };
  }, []);

  const filteredWorkspaces = useMemo(
    () =>
      workspaces.filter((workspace) => {
        const matchesQuery = `${workspace.name} ${workspace.description}`.toLowerCase().includes(query.toLowerCase());
        const matchesStatus = status === "all" || workspace.status === "active";
        return matchesQuery && matchesStatus;
      }),
    [query, status, workspaces],
  );

  const columns: DataTableColumn<Workspace>[] = [
    {
      header: "Workspace",
      cell: (workspace) => (
        <Link
          to={`/projects/${workspace.workspaceId}`}
          className="font-semibold text-primary hover:underline dark:text-inverse-primary"
        >
          {workspace.name}
        </Link>
      ),
    },
    {
      header: "Description",
      cell: (workspace) => workspace.description || t("workspace.noDescription"),
    },
    {
      header: t("table.status"),
      cell: (workspace) => <StatusBadge status={workspace.status} />,
    },
    {
      header: t("workspace.createdAt"),
      cell: (workspace) =>
        new Intl.DateTimeFormat(language === "vi" ? "vi-VN" : "en-US", {
          dateStyle: "medium",
          timeStyle: "short",
        }).format(new Date(workspace.createdAt)),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t("workspace.title")}
        subtitle={t("workspace.subtitle")}
        actions={
          <Link to="/upload">
            <Button leftIcon={<Plus className="h-4 w-4" />}>{t("upload.createWorkspace")}</Button>
          </Link>
        }
      />

      <Card className="mb-xl">
        <div className="flex flex-col gap-md lg:flex-row lg:items-center lg:justify-between">
          <SearchInput
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t("workspace.searchPlaceholder")}
            containerClassName="lg:w-96"
          />
          <div className="flex flex-wrap items-center gap-sm">
            {(["all", "active"] as const).map((item) => (
              <button
                key={item}
                className={`rounded-lg border px-md py-sm text-sm font-semibold transition ${
                  status === item
                    ? "border-primary bg-primary text-white"
                    : "border-legal-border bg-white text-on-surface-variant hover:bg-surface-container-low dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
                }`}
                type="button"
                onClick={() => setStatus(item)}
              >
                {item === "all" ? t("workspace.all") : t("workspace.active")}
              </button>
            ))}
            <Button
              variant="secondary"
              leftIcon={<SlidersHorizontal className="h-4 w-4" />}
            >
              {t("workspace.filters")}
            </Button>
            <div className="flex rounded-lg border border-legal-border bg-white p-xs dark:border-slate-700 dark:bg-slate-900">
              <button
                className={`rounded-md p-sm ${
                  view === "grid"
                    ? "bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary"
                    : "text-on-surface-variant"
                }`}
                type="button"
                aria-label="Grid view"
                onClick={() => setView("grid")}
              >
                <Grid2X2 className="h-4 w-4" />
              </button>
              <button
                className={`rounded-md p-sm ${
                  view === "list"
                    ? "bg-surface-container-high text-primary dark:bg-slate-800 dark:text-inverse-primary"
                    : "text-on-surface-variant"
                }`}
                type="button"
                aria-label="List view"
                onClick={() => setView("list")}
              >
                <List className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </Card>

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      {loading ? (
        <Card>
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("workspace.loading")}
          </p>
        </Card>
      ) : view === "list" ? (
        <DataTable
          columns={columns}
          data={filteredWorkspaces}
          getRowKey={(workspace) => workspace.workspaceId}
        />
      ) : filteredWorkspaces.length === 0 ? (
        <Card>
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            {t("workspace.empty")}
          </p>
        </Card>
      ) : (
        <div className="grid gap-gutter md:grid-cols-2 xl:grid-cols-3">
          {filteredWorkspaces.map((workspace) => (
            <Card
              key={workspace.workspaceId}
              className="h-full transition hover:-translate-y-1 hover:shadow-raised"
            >
              <div className="flex items-start justify-between gap-md">
                <div>
                  <h2 className="text-title-lg font-semibold text-primary dark:text-inverse-primary">
                    {workspace.name}
                  </h2>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    {workspace.description || t("workspace.noDescription")}
                  </p>
                </div>
                <StatusBadge status={workspace.status} />
              </div>
              <div className="mt-lg rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                <p className="label-uppercase">{t("workspace.createdAt")}</p>
                <p className="mt-xs text-sm">
                  {new Intl.DateTimeFormat(language === "vi" ? "vi-VN" : "en-US", {
                    dateStyle: "medium",
                    timeStyle: "short",
                  }).format(new Date(workspace.createdAt))}
                </p>
              </div>
              <div className="mt-lg flex flex-wrap gap-sm">
                <Link to={`/projects/${workspace.workspaceId}`}>
                  <Button variant="secondary">{t("actions.openWorkspace")}</Button>
                </Link>
                <Link to={`/upload?workspaceId=${workspace.workspaceId}`}>
                  <Button>{t("actions.uploadFile")}</Button>
                </Link>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Card tone="ai" className="mt-xl">
        <div className="flex flex-col gap-md md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-title-lg font-semibold">
              {t("workspace.aiSummary")}
            </h2>
            <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-300">
              {t("workspace.summaryWithDocuments")}
            </p>
          </div>
          <Link to="/upload">
            <Button variant="gold" leftIcon={<Search className="h-4 w-4" />}>
              {t("actions.uploadFile")}
            </Button>
          </Link>
        </div>
      </Card>
    </div>
  );
}
