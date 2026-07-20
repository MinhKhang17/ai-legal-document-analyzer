import { ArrowLeft, RotateCcw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import {
  getContract,
  getContractVersions,
  revertContractVersion,
} from "../../services/contract.service";
import type { ContractVersion, UserContract } from "../../types/contract";
import { formatDisplayDate } from "../../utils/format";
import { getSupportedContractTypeLabel } from "../../config/supportedContractTypes";

const getStatusTone = (status?: string) => {
  if (status === "ACTIVE" || status === "GENERATED") return "green";
  if (status === "ARCHIVED") return "slate";
  return "amber";
};

export function ContractDetailPage() {
  const { id = "" } = useParams();
  const { t, language } = useI18n();
  const toast = useToast();
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [contract, setContract] = useState<UserContract | null>(null);
  const [versions, setVersions] = useState<ContractVersion[]>([]);
  const [selectedVersionNo, setSelectedVersionNo] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const loadContract = useCallback(async () => {
    if (!id) return;

    setLoading(true);
    setError("");

    const [contractResult, versionsResult] = await Promise.allSettled([
      getContract(id),
      getContractVersions(id),
    ]);

    if (contractResult.status === "fulfilled") {
      setContract(contractResult.value);
      setSelectedVersionNo(String(contractResult.value.currentVersionNo ?? ""));
    } else {
      const message =
        contractResult.reason instanceof Error
          ? contractResult.reason.message
          : t("contracts.loadDetailError");
      setError(message);
      toast.error(message);
      setContract(null);
    }

    setVersions(versionsResult.status === "fulfilled" ? versionsResult.value : []);
    setLoading(false);
  }, [id, toast, t]);

  useEffect(() => {
    void loadContract();
  }, [loadContract]);

  const currentVersion = useMemo(() => {
    if (!contract) return versions[0] ?? null;
    return (
      versions.find((version) => version.versionNo === contract.currentVersionNo) ??
      versions[0] ??
      null
    );
  }, [contract, versions]);

  const handleRevert = async () => {
    if (!id || !selectedVersionNo) {
      toast.warning(t("contracts.selectVersionFirst"));
      return;
    }

    if (!reason.trim()) {
      toast.warning(t("contracts.revertReasonRequired"));
      return;
    }

    setSaving(true);
    setError("");

    try {
      const updatedContract = await revertContractVersion(id, Number(selectedVersionNo), {
        reason: reason.trim(),
      });
      setContract(updatedContract);
      setReason("");
      toast.success(t("contracts.revertSuccess"));
      await loadContract();
    } catch (revertError) {
      const message =
        revertError instanceof Error ? revertError.message : t("contracts.revertError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const versionColumns: DataTableColumn<ContractVersion>[] = [
    { header: t("contracts.version"), cell: (version) => version.versionNo },
    {
      header: t("contracts.source"),
      cell: (version) => (
        <Badge tone={version.generatedByAi ? "blue" : "slate"}>
          {version.generatedByAi ? t("contracts.sourceAi") : t("contracts.sourceManual")}
        </Badge>
      ),
    },
    {
      header: t("contracts.summary"),
      cell: (version) => version.changeSummary || "-",
    },
    {
      header: t("contracts.created"),
      cell: (version) => formatDisplayDate(version.createdAt, "-", locale),
    },
  ];

  return (
    <div>
      <PageHeader
        title={contract?.title ?? t("contracts.detailTitle")}
        subtitle={id}
        actions={
          <Link to="/contracts">
            <Button variant="secondary" leftIcon={<ArrowLeft className="h-4 w-4" />}>
              {t("contracts.backToList")}
            </Button>
          </Link>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      {loading ? (
        <Card>{t("common.loading")}</Card>
      ) : !contract ? (
        <EmptyState
          title={t("contracts.notFound")}
          description={t("contracts.notFoundDescription")}
        />
      ) : (
        <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_380px]">
          <main className="space-y-gutter">
            <Card
              title={contract.title}
              subtitle={getSupportedContractTypeLabel(contract.contractType, language) ?? (language === "vi" ? "Ngoài phạm vi hỗ trợ" : "Outside supported scope")}
              actions={<Badge tone={getStatusTone(contract.status)}>{contract.status}</Badge>}
            >
              <dl className="grid gap-md text-sm md:grid-cols-2">
                <div>
                  <dt className="label-uppercase">{t("contracts.workspace")}</dt>
                  <dd className="mt-xs">{contract.workspaceId}</dd>
                </div>
                <div>
                  <dt className="label-uppercase">{t("contracts.template")}</dt>
                  <dd className="mt-xs">{contract.templateId ?? "-"}</dd>
                </div>
                <div>
                  <dt className="label-uppercase">{t("contracts.currentVersion")}</dt>
                  <dd className="mt-xs">{contract.currentVersionNo}</dd>
                </div>
                <div>
                  <dt className="label-uppercase">{t("contracts.generatedAt")}</dt>
                  <dd className="mt-xs">
                    {formatDisplayDate(contract.lastGeneratedAt, "-", locale)}
                  </dd>
                </div>
                <div>
                  <dt className="label-uppercase">{t("contracts.created")}</dt>
                  <dd className="mt-xs">{formatDisplayDate(contract.createdAt, "-", locale)}</dd>
                </div>
                <div>
                  <dt className="label-uppercase">{t("table.updated")}</dt>
                  <dd className="mt-xs">{formatDisplayDate(contract.updatedAt, "-", locale)}</dd>
                </div>
              </dl>
            </Card>

            <Card title={t("contracts.currentContent")}>
              {currentVersion?.content ? (
                <pre className="max-h-[560px] overflow-auto rounded-lg bg-surface-container-low p-md text-xs leading-6 dark:bg-slate-950">
                  {currentVersion.content}
                </pre>
              ) : (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("contracts.noVersionContent")}
                </p>
              )}
            </Card>

            <Card title={t("contracts.versionHistory")} actions={<Badge>{versions.length}</Badge>}>
              <DataTable
                columns={versionColumns}
                data={versions}
                getRowKey={(version) => version.id}
                emptyMessage={t("contracts.noVersions")}
              />
            </Card>
          </main>

          <aside>
            <Card title={t("contracts.revertVersion")}>
              <div className="space-y-md">
                <label className="block text-sm font-semibold">
                  {t("contracts.version")}
                  <select
                    className="form-field mt-xs"
                    value={selectedVersionNo}
                    onChange={(event) => setSelectedVersionNo(event.target.value)}
                  >
                    <option value="">{t("contracts.selectVersion")}</option>
                    {versions.map((version) => (
                      <option key={version.id} value={version.versionNo}>
                        {t("contracts.versionOption").replace("{version}", String(version.versionNo))}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="block text-sm font-semibold">
                  {t("contracts.reason")}
                  <textarea
                    className="form-field mt-xs min-h-28"
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                  />
                </label>

                <Button
                  variant="secondary"
                  leftIcon={<RotateCcw className="h-4 w-4" />}
                  onClick={() => void handleRevert()}
                  disabled={saving || versions.length === 0}
                >
                  {saving ? t("contracts.reverting") : t("contracts.revert")}
                </Button>
              </div>
            </Card>
          </aside>
        </div>
      )}
    </div>
  );
}
