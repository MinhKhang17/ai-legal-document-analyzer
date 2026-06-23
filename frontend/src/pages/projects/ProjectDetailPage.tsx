import { Edit3, MessageSquareText, ShieldAlert, UsersRound } from "lucide-react";
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

const getAccessToken = () => localStorage.getItem("accessToken") ?? "";

export function ProjectDetailPage() {
  const { id = "" } = useParams();
  const { language } = useI18n();
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
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Không thể tải workspace");
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
  }, [id]);

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
    { header: "File", cell: (document) => document.originalFileName },
    { header: "Type", cell: (document) => document.fileType },
    { header: "Status", cell: (document) => <StatusBadge status={document.status} /> },
    {
      header: "Uploaded",
      cell: (document) =>
        new Intl.DateTimeFormat(language === "vi" ? "vi-VN" : "en-US", {
          dateStyle: "medium",
          timeStyle: "short",
        }).format(new Date(document.uploadedAt)),
    },
  ];

  const workspaceChatUrl = `/chat?workspaceId=${id}`;
  const workspaceUploadUrl = `/upload?workspaceId=${id}`;

  return (
    <div>
      <PageHeader
        title={workspace?.name ?? "Workspace details"}
        subtitle={workspace?.description ?? "View documents and continue the analysis flow."}
        eyebrow={workspace ? `${workspace.workspaceId} · ${workspace.status}` : "Workspace"}
        actions={
          <>
            <Link to={workspaceUploadUrl}>
              <Button variant="secondary" leftIcon={<Edit3 className="h-4 w-4" />}>
                Upload file
              </Button>
            </Link>
            <Link to={workspaceChatUrl}>
              <Button leftIcon={<MessageSquareText className="h-4 w-4" />}>
                Open chat
              </Button>
            </Link>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      {loading ? (
        <Card>
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            Loading workspace...
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
                    <p className="label-uppercase">Documents</p>
                    <p className="mt-xs text-2xl font-bold">{documents.length}</p>
                  </div>
                  <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                    <p className="label-uppercase">Ready</p>
                    <p className="mt-xs text-2xl font-bold text-emerald-600">
                      {summary.readyDocuments}
                    </p>
                  </div>
                  <div className="rounded-lg bg-surface-container-low p-md dark:bg-slate-800">
                    <p className="label-uppercase">Processing</p>
                    <p className="mt-xs text-2xl font-bold text-secondary">
                      {summary.processingDocuments}
                    </p>
                  </div>
                </div>
                <div className="mt-lg">
                  <div className="mb-sm flex items-center justify-between text-sm">
                    <span className="font-semibold">Workspace health</span>
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
                    AI workspace summary
                  </h2>
                </div>
                <p className="mt-sm text-sm leading-6 text-on-surface-variant dark:text-slate-300">
                  {documents.length > 0
                    ? "Use the chat screen to ask questions over the uploaded documents in this workspace."
                    : "Upload a document first so the AI can ingest and index it before chat."}
                </p>
                <Link to={workspaceChatUrl}>
                  <Button className="mt-md" variant="gold">
                    Open workspace chat
                  </Button>
                </Link>
              </Card>
            </div>
          </Card>

          <Card title="Workspace documents">
            {documents.length === 0 ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                No documents uploaded yet.
              </p>
            ) : (
              <DataTable
                columns={columns}
                data={documents}
                getRowKey={(document) => document.documentId}
              />
            )}
          </Card>

          <div className="mt-xl grid gap-gutter xl:grid-cols-[1fr_360px]">
            <Card title="Quick actions">
              <div className="grid gap-sm sm:grid-cols-2">
                <Link
                  to={workspaceUploadUrl}
                  className="rounded-xl border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                >
                  <p className="font-semibold">Upload another file</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    Add more documents into the same workspace.
                  </p>
                </Link>
                <Link
                  to={workspaceChatUrl}
                  className="rounded-xl border border-legal-border p-md hover:bg-surface-container-low dark:border-slate-700 dark:hover:bg-slate-800"
                >
                  <p className="font-semibold">Ask the AI</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    Ask questions based on the documents in this workspace.
                  </p>
                </Link>
              </div>
            </Card>

            <Card title="Workspace info" actions={<UsersRound className="h-5 w-5 text-primary dark:text-inverse-primary" />}>
              <div className="space-y-md">
                <div>
                  <p className="label-uppercase">Workspace ID</p>
                  <p className="mt-xs break-all text-sm">{workspace.workspaceId}</p>
                </div>
                <div>
                  <p className="label-uppercase">Description</p>
                  <p className="mt-xs text-sm text-on-surface-variant dark:text-slate-400">
                    {workspace.description || "No description"}
                  </p>
                </div>
                <div>
                  <p className="label-uppercase">Created at</p>
                  <p className="mt-xs text-sm">
                    {new Intl.DateTimeFormat(language === "vi" ? "vi-VN" : "en-US", {
                      dateStyle: "medium",
                      timeStyle: "short",
                    }).format(new Date(workspace.createdAt))}
                  </p>
                </div>
                <div>
                  <p className="label-uppercase">Failed documents</p>
                  <p className="mt-xs text-sm">{summary.failedDocuments}</p>
                </div>
              </div>
            </Card>
          </div>
        </>
      ) : (
        <Card>
          <p className="text-sm text-on-surface-variant dark:text-slate-400">
            Workspace not found.
          </p>
        </Card>
      )}
    </div>
  );
}
