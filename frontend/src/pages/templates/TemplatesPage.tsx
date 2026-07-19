import { Edit3, FileText, Plus, RefreshCw, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { DataTable, type DataTableColumn } from "../../components/common/DataTable";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Pagination } from "../../components/common/Pagination";
import { parsePageParam, toPageParam } from "../../utils/pagination";
import { useI18n } from "../../hooks/useI18n";
import { useToast } from "../../hooks/useToast";
import {
  createContractTemplate,
  getContractTemplates,
  updateContractTemplate,
} from "../../services/contract.service";
import { useAppStore } from "../../store/AppStore";
import type { ContractTemplate, CreateContractTemplateRequest } from "../../types/contract";
import { SUPPORTED_CONTRACT_TYPES } from "../../config/supportedContractTypes";
import { formatDisplayDate } from "../../utils/format";

const emptyTemplateForm: CreateContractTemplateRequest = {
  templateCode: "",
  name: "",
  description: "",
  category: "",
  jurisdiction: "VN",
  content: "",
};

const getStatusTone = (status?: string) => {
  if (status === "ACTIVE") return "green";
  if (status === "ARCHIVED") return "slate";
  return "amber";
};

export function TemplatesPage() {
  const { t, language } = useI18n();
  const toast = useToast();
  const { user } = useAppStore();
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(() => parsePageParam(searchParams.get('page')));
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const isAdmin = user?.role === "ADMIN";
  const locale = language === "vi" ? "vi-VN" : "en-US";
  const [templates, setTemplates] = useState<ContractTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<ContractTemplate | null>(null);
  const [form, setForm] = useState<CreateContractTemplateRequest>(emptyTemplateForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const loadTemplates = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const response = await getContractTemplates(page, 20);
      setTemplates((response.items ?? []).filter((template) => SUPPORTED_CONTRACT_TYPES.some((type) => type.value === template.category)));
      setTotalPages(response.totalPages ?? 0);
      setTotalItems(response.totalItems ?? 0);
    } catch (loadError) {
      const message =
        loadError instanceof Error ? loadError.message : t("templates.loadError");
      setError(message);
      toast.error(message);
      setTemplates([]);
    } finally {
      setLoading(false);
    }
  }, [page, toast, t]);

  useEffect(() => {
    void loadTemplates();
  }, [loadTemplates]);

  const selectedTemplatePreview = useMemo(
    () => selectedTemplate ?? templates[0] ?? null,
    [selectedTemplate, templates],
  );

  const resetForm = () => {
    setSelectedTemplate(null);
    setForm(emptyTemplateForm);
  };

  const selectTemplate = (template: ContractTemplate) => {
    setSelectedTemplate(template);
    setForm({
      templateCode: template.templateCode,
      name: template.name,
      description: template.description ?? "",
      category: template.category,
      jurisdiction: template.jurisdiction ?? "",
      content: template.content,
    });
  };

  const handleSubmit = async () => {
    if (!isAdmin) {
      toast.warning(t("templates.adminOnly"));
      return;
    }

    if (!form.name.trim() || !form.category.trim() || !form.content.trim()) {
      toast.warning(t("templates.requiredFields"));
      return;
    }

    if (!selectedTemplate && !form.templateCode.trim()) {
      toast.warning(t("templates.codeRequired"));
      return;
    }

    setSaving(true);
    setError("");

    try {
      const payload = {
        ...form,
        templateCode: form.templateCode.trim(),
        name: form.name.trim(),
        description: form.description?.trim() || null,
        category: form.category.trim(),
        jurisdiction: form.jurisdiction?.trim() || null,
        content: form.content,
      };

      const savedTemplate = selectedTemplate
        ? await updateContractTemplate(selectedTemplate.id, {
            name: payload.name,
            description: payload.description,
            category: payload.category,
            jurisdiction: payload.jurisdiction,
            content: payload.content,
          })
        : await createContractTemplate(payload);

      setSelectedTemplate(savedTemplate);
      setForm({
        templateCode: savedTemplate.templateCode,
        name: savedTemplate.name,
        description: savedTemplate.description ?? "",
        category: savedTemplate.category,
        jurisdiction: savedTemplate.jurisdiction ?? "",
        content: savedTemplate.content,
      });
      toast.success(selectedTemplate ? t("templates.updated") : t("templates.created"));
      await loadTemplates();
    } catch (saveError) {
      const message =
        saveError instanceof Error ? saveError.message : t("templates.saveError");
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const columns: DataTableColumn<ContractTemplate>[] = [
    {
      header: t("templates.template"),
      cell: (template) => (
        <div>
          <p className="font-semibold">{template.name}</p>
          <p className="text-xs text-on-surface-variant dark:text-slate-400">
            {template.templateCode}
          </p>
        </div>
      ),
    },
    { header: t("templates.category"), cell: (template) => template.category },
    { header: t("templates.jurisdiction"), cell: (template) => template.jurisdiction || "-" },
    {
      header: t("table.status"),
      cell: (template) => (
        <Badge tone={getStatusTone(template.status)}>{template.status}</Badge>
      ),
    },
    { header: t("contracts.version"), cell: (template) => template.version ?? "-" },
    {
      header: t("table.updated"),
      cell: (template) => formatDisplayDate(template.updatedAt, "-", locale),
    },
    {
      header: "",
      className: "text-right",
      cell: (template) => (
        <Button
          size="sm"
          variant="secondary"
          leftIcon={<Edit3 className="h-4 w-4" />}
          onClick={() => selectTemplate(template)}
        >
          {isAdmin ? t("actions.edit") : t("actions.viewDetails")}
        </Button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t("templates.title")}
        subtitle={t("templates.subtitle")}
        actions={
          <>
            {isAdmin && (
              <Button
                variant="secondary"
                leftIcon={<Plus className="h-4 w-4" />}
                onClick={resetForm}
              >
                {t("templates.new")}
              </Button>
            )}
            <Button
              variant="secondary"
              leftIcon={<RefreshCw className="h-4 w-4" />}
              onClick={() => void loadTemplates()}
              disabled={loading}
            >
              {t("common.refresh")}
            </Button>
          </>
        }
      />

      {error && (
        <div className="mb-lg rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error">
          {error}
        </div>
      )}

      <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_420px]">
        <main>
          <Card title={t("templates.catalog")} actions={<Badge tone="blue">{templates.length}</Badge>}>
            {loading ? (
              <p className="text-sm text-on-surface-variant dark:text-slate-400">
                {t("templates.loading")}
              </p>
            ) : templates.length === 0 ? (
              <EmptyState
                icon={<FileText className="h-6 w-6" aria-hidden="true" />}
                title={t("templates.emptyTitle")}
                description={t("templates.emptyDescription")}
              />
            ) : (
              <DataTable
                columns={columns}
                data={templates}
                getRowKey={(template) => String(template.id)}
              />
            )}
            <Pagination page={page} totalPages={totalPages} totalItems={totalItems} disabled={loading} onPageChange={(nextPage) => { setPage(nextPage); const next = new URLSearchParams(searchParams); next.set('page', toPageParam(nextPage)); setSearchParams(next); }} />
          </Card>
        </main>

        <aside className="space-y-gutter">
          {isAdmin ? (
            <Card
              title={selectedTemplate ? t("templates.editTemplate") : t("templates.createTemplate")}
              subtitle={
                selectedTemplate
                  ? `ID ${selectedTemplate.id}`
                  : t("templates.adminTemplateEndpoint")
              }
              actions={
                selectedTemplate && (
                  <Button
                    size="icon"
                    variant="ghost"
                    aria-label={t("templates.clearSelection")}
                    onClick={resetForm}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                )
              }
            >
              <div className="space-y-md">
                <label className="block text-sm font-semibold">
                  {t("templates.code")}
                  <input
                    className="form-field mt-xs"
                    value={form.templateCode}
                    disabled={Boolean(selectedTemplate)}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        templateCode: event.target.value,
                      }))
                    }
                  />
                </label>

                <label className="block text-sm font-semibold">
                  {t("common.name")}
                  <input
                    className="form-field mt-xs"
                    value={form.name}
                    onChange={(event) =>
                      setForm((current) => ({ ...current, name: event.target.value }))
                    }
                  />
                </label>

                <div className="grid gap-md sm:grid-cols-2">
                  <label className="block text-sm font-semibold">
                    {t("templates.category")}
                    <select
                      className="form-field mt-xs"
                      value={form.category}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          category: event.target.value,
                        }))
                      }
                    >
                      <option value="">{t("templates.category")}</option>
                      {SUPPORTED_CONTRACT_TYPES.map((item) => <option key={item.value} value={item.value}>{language === "vi" ? item.vi : item.en}</option>)}
                    </select>
                  </label>
                  <label className="block text-sm font-semibold">
                    {t("templates.jurisdiction")}
                    <input
                      className="form-field mt-xs"
                      value={form.jurisdiction ?? ""}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          jurisdiction: event.target.value,
                        }))
                      }
                    />
                  </label>
                </div>

                <label className="block text-sm font-semibold">
                  {t("common.description")}
                  <textarea
                    className="form-field mt-xs min-h-20"
                    value={form.description ?? ""}
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        description: event.target.value,
                      }))
                    }
                  />
                </label>

                <label className="block text-sm font-semibold">
                  {t("templates.content")}
                  <textarea
                    className="form-field mt-xs min-h-56 font-mono text-xs"
                    value={form.content}
                    onChange={(event) =>
                      setForm((current) => ({ ...current, content: event.target.value }))
                    }
                  />
                </label>

                <Button onClick={() => void handleSubmit()} disabled={saving}>
                  {saving ? t("contracts.saving") : selectedTemplate ? t("templates.updateTemplate") : t("templates.createTemplate")}
                </Button>
              </div>
            </Card>
          ) : (
            <Card title={t("templates.templatePreview")}>
              {selectedTemplatePreview ? (
                <div className="space-y-sm text-sm">
                  <div className="flex flex-wrap gap-xs">
                    <Badge tone={getStatusTone(selectedTemplatePreview.status)}>
                      {selectedTemplatePreview.status}
                    </Badge>
                    <Badge>{selectedTemplatePreview.category}</Badge>
                  </div>
                  <h3 className="text-lg font-semibold">{selectedTemplatePreview.name}</h3>
                  <p className="text-on-surface-variant dark:text-slate-400">
                    {selectedTemplatePreview.description || t("templates.noDescription")}
                  </p>
                  <pre className="max-h-96 overflow-auto rounded-lg bg-surface-container-low p-md text-xs dark:bg-slate-950">
                    {selectedTemplatePreview.content}
                  </pre>
                </div>
              ) : (
                <p className="text-sm text-on-surface-variant dark:text-slate-400">
                  {t("templates.selectPreview")}
                </p>
              )}
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}
